package io.gastos.app.backup

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import io.gastos.app.data.AppPrefs
import io.gastos.app.data.ExpenseEntity
import io.gastos.app.data.GastosDatabase
import io.gastos.app.data.LabelEntity
import io.gastos.app.data.PaymentMethodEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class BackupManagerTest {
    private val manila = ZoneId.of("Asia/Manila")
    // 2026-10-16 12:00 in Manila.
    private val clock = Clock.fixed(Instant.parse("2026-10-16T04:00:00Z"), manila)

    private lateinit var db: GastosDatabase
    private lateinit var prefs: AppPrefs
    private lateinit var tokens: TokenStore
    private lateinit var github: FakeGitHub
    private lateinit var manager: BackupManager
    private lateinit var scope: CoroutineScope
    private lateinit var prefsFile: File
    private var dataChangedCalls = 0

    /** Records calls; never touches the network. */
    private class FakeGitHub : GitHubClient(OkHttpClient()) {
        var remote: String? = null
        var sha: String? = null
        var downloadError: Exception? = null
        val calls = mutableListOf<String>()
        val uploads = mutableListOf<Triple<String, String?, String>>() // content, sha, message

        override suspend fun getSha(repo: String, path: String, token: String): String? {
            calls += "getSha $repo $path $token"; return sha
        }

        override suspend fun download(repo: String, path: String, token: String): String? {
            calls += "download $repo $path $token"
            downloadError?.let { throw it }
            return remote
        }

        override suspend fun upload(repo: String, path: String, token: String, content: String, sha: String?, message: String) {
            calls += "upload $repo $path $token"
            uploads += Triple(content, sha, message)
        }
    }

    /** Identity "encryption"; Robolectric has no Android Keystore. */
    private object PlainCipher : TokenCipher {
        override fun encrypt(plain: ByteArray) = plain
        override fun decrypt(blob: ByteArray) = blob
    }

    @Before fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = GastosDatabase.createInMemory(context)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        prefsFile = File.createTempFile("prefs", ".preferences_pb").also { it.delete() }
        prefs = AppPrefs(PreferenceDataStoreFactory.create(scope = scope) { prefsFile })
        tokens = TokenStore(context.getSharedPreferences("test_tokens", Context.MODE_PRIVATE), PlainCipher)
        tokens.save("t")
        prefs.setBackupRepo("o/r")
        github = FakeGitHub()
        manager = BackupManager(db, prefs, tokens, github, clock, zone = { manila }) { dataChangedCalls++ }
    }

    @After fun tearDown() {
        db.close()
        scope.cancel()
        prefsFile.delete()
    }

    private suspend fun localLabels() = db.labelDao().all()
    private suspend fun localExpenses() = db.expenseDao().all()
    private suspend fun cash() = db.paymentMethodDao().active().first { it.name == "Cash" }.id

    private fun remoteBackup() = BackupSerializer.encode(
        BackupFile(
            exportedAt = "2026-10-15T00:00:00Z",
            labels = listOf(
                BackupLabel(id = 10, name = "Groceries", colorArgb = 1, position = 0, isOther = false),
                BackupLabel(id = 11, name = "Other", colorArgb = 2, position = 1, isOther = true),
            ),
            expenses = listOf(BackupExpense(id = 40, amountCentavos = 12_345, labelId = 10, timestamp = 1_000)),
        ),
    )

    @Test fun freshInstallDoesNotUpload() = runBlocking {
        assertFalse(prefs.backupEnabled.first())
        val result = manager.backupNow()
        assertTrue(result.isSuccess)
        assertEquals(emptyList<String>(), github.calls)
        assertNull(prefs.lastBackupAt.first())
    }

    @Test fun backupUploadsAllDataWithCommitMessage() = runBlocking {
        db.expenseDao().insert(ExpenseEntity(amountCentavos = 500, labelId = localLabels().first().id, timestamp = 9, paymentMethodId = cash()))
        github.sha = "abc"
        manager.startFresh()

        val (content, sha, message) = github.uploads.single()
        assertEquals("abc", sha)
        assertEquals("Backup 2026-10-16 12:00", message)
        val file = BackupSerializer.decode(content)
        assertEquals(5, file.labels.size)
        assertEquals(500L, file.expenses.single().amountCentavos)
        assertEquals("2026-10-16T04:00:00Z", file.exportedAt)
        assertTrue(prefs.backupEnabled.first())
        assertEquals(clock.millis(), prefs.lastBackupAt.first())
        assertNull(prefs.lastBackupError.first())
    }

    @Test fun backupFailureRecordsError() = runBlocking {
        prefs.setBackupEnabled(true)
        val failing = object : GitHubClient(OkHttpClient()) {
            override suspend fun getSha(repo: String, path: String, token: String): String? =
                throw GitHubException(403, "Token can't access this repo.")
        }
        val m = BackupManager(db, prefs, tokens, failing, clock, zone = { manila }) { }
        val result = m.backupNow()
        assertTrue(result.isFailure)
        assertEquals("Token can't access this repo.", prefs.lastBackupError.first())
    }

    @Test fun restoreReplacesData() = runBlocking {
        db.expenseDao().insert(ExpenseEntity(amountCentavos = 999, labelId = localLabels().first().id, timestamp = 5, paymentMethodId = cash()))
        github.remote = remoteBackup()

        val result = manager.restore()

        assertTrue(result.exceptionOrNull()?.toString(), result.isSuccess)
        assertEquals(
            listOf(
                LabelEntity(id = 10, name = "Groceries", colorArgb = 1, position = 0, isOther = false),
                LabelEntity(id = 11, name = "Other", colorArgb = 2, position = 1, isOther = true),
            ),
            localLabels(),
        )
        assertEquals(
            listOf(ExpenseEntity(id = 40, amountCentavos = 12_345, labelId = 10, timestamp = 1_000, paymentMethodId = cash())),
            localExpenses(),
        )
        assertTrue(prefs.backupEnabled.first())
        assertEquals(1, dataChangedCalls)
        assertEquals(0, github.uploads.size)
    }

    @Test fun failedRestoreKeepsLocal() = runBlocking {
        db.expenseDao().insert(ExpenseEntity(amountCentavos = 999, labelId = localLabels().first().id, timestamp = 5, paymentMethodId = cash()))
        val labelsBefore = localLabels()
        val expensesBefore = localExpenses()
        github.downloadError = GitHubException(401, "GitHub token is invalid or expired.")

        val result = manager.restore()

        assertTrue(result.isFailure)
        assertEquals("GitHub token is invalid or expired.", result.exceptionOrNull()?.message)
        assertEquals(labelsBefore, localLabels())
        assertEquals(expensesBefore, localExpenses())
        assertFalse(prefs.backupEnabled.first())
        assertEquals(0, dataChangedCalls)
    }

    @Test fun hiddenRepoRestoreFailsWithoutEnablingBackup() = runBlocking {
        val labelsBefore = localLabels()
        github.downloadError = GitHubException(404, "Repo not found, or this token can't access it.")

        val result = manager.restore()

        assertEquals("Repo not found, or this token can't access it.", result.exceptionOrNull()?.message)
        assertEquals(labelsBefore, localLabels())
        assertFalse(prefs.backupEnabled.first())
        assertEquals(0, dataChangedCalls)
    }

    @Test fun newerSchemaRestoreKeepsLocal() = runBlocking {
        val labelsBefore = localLabels()
        github.remote = """{"schemaVersion":3,"exportedAt":"x","labels":[],"expenses":[]}"""

        val result = manager.restore()

        assertEquals("This backup is from a newer version of Gastos.io. Update the app first.", result.exceptionOrNull()?.message)
        assertEquals(labelsBefore, localLabels())
        assertFalse(prefs.backupEnabled.first())
    }

    @Test fun missingBackupFails() = runBlocking {
        github.remote = null
        val result = manager.restore()
        assertTrue(result.isFailure)
        assertEquals("No backup found in this repo.", result.exceptionOrNull()?.message)
        assertEquals(5, localLabels().size)
        assertFalse(prefs.backupEnabled.first())
    }

    private val v1Json = """{"schemaVersion":1,"exportedAt":"2026-10-15T00:00:00Z",
        "labels":[{"id":10,"name":"Groceries","colorArgb":1,"position":0,"isOther":false},
                  {"id":11,"name":"Other","colorArgb":2,"position":1,"isOther":true}],
        "expenses":[{"id":40,"amountCentavos":12345,"labelId":10,"timestamp":1000},
                    {"id":41,"amountCentavos":500,"labelId":11,"timestamp":2000}]}"""

    private val defaults = listOf(
        PaymentMethodEntity(1, "Cash", 0xFF77808B.toInt(), 0, false),
        PaymentMethodEntity(2, "GCash", 0xFF007DFE.toInt(), 1, false),
        PaymentMethodEntity(3, "Maribank", 0xFFFF6A13.toInt(), 2, false),
        PaymentMethodEntity(4, "BPI", 0xFFA6192E.toInt(), 3, false),
        PaymentMethodEntity(5, "Maya", 0xFF00B26B.toInt(), 4, false),
        PaymentMethodEntity(6, "BDO", 0xFF0033A0.toInt(), 5, false),
    )

    /** Makes this phone's methods differ from the defaults, so restore can't lean on them. */
    private suspend fun scrambleLocalMethods() {
        val dao = db.paymentMethodDao()
        val cashId = cash()
        dao.insert(PaymentMethodEntity(name = "Card", colorArgb = 7, position = 6))
        dao.rename(cashId, "Coins")
        dao.archive(cashId)
    }

    private fun v2Backup(
        methods: List<BackupPaymentMethod>,
        expenses: List<BackupExpense>,
    ) = BackupSerializer.encode(
        BackupFile(
            exportedAt = "2026-10-15T00:00:00Z",
            labels = listOf(
                BackupLabel(id = 10, name = "Groceries", colorArgb = 1, position = 0, isOther = false),
                BackupLabel(id = 11, name = "Other", colorArgb = 2, position = 1, isOther = true),
            ),
            paymentMethods = methods,
            expenses = expenses,
        ),
    )

    private suspend fun assertRejectedAsDamaged(remote: String) {
        db.expenseDao().insert(ExpenseEntity(amountCentavos = 999, labelId = localLabels().first().id, timestamp = 5, paymentMethodId = cash()))
        val labelsBefore = localLabels()
        val methodsBefore = db.paymentMethodDao().all()
        val expensesBefore = localExpenses()
        github.remote = remote

        val result = manager.restore()

        assertEquals("This backup file is damaged or isn't a Gastos.io backup.", result.exceptionOrNull()?.message)
        assertEquals(labelsBefore, localLabels())
        assertEquals(methodsBefore, db.paymentMethodDao().all())
        assertEquals(expensesBefore, localExpenses())
        assertFalse(prefs.backupEnabled.first())
        assertEquals(0, dataChangedCalls)
    }

    @Test fun restoresSchema1BackupWithCash() = runBlocking {
        scrambleLocalMethods()
        github.remote = v1Json

        val result = manager.restore()

        assertTrue(result.exceptionOrNull()?.toString(), result.isSuccess)
        assertEquals(defaults, db.paymentMethodDao().all())
        assertEquals(
            listOf(
                ExpenseEntity(id = 40, amountCentavos = 12_345, labelId = 10, timestamp = 1_000, paymentMethodId = 1),
                ExpenseEntity(id = 41, amountCentavos = 500, labelId = 11, timestamp = 2_000, paymentMethodId = 1),
            ),
            localExpenses(),
        )
        assertEquals(2, localLabels().size)
    }

    @Test fun roundTripKeepsMethodsDescriptionNotes() = runBlocking {
        val dao = db.paymentMethodDao()
        val labelId = localLabels().first().id
        val oldCard = dao.insert(PaymentMethodEntity(name = "Old card", colorArgb = 7, position = 6))
        db.expenseDao().insert(
            ExpenseEntity(
                amountCentavos = 18_500, labelId = labelId, timestamp = 9, paymentMethodId = oldCard,
                description = "Jollibee", notes = "With \"friends\",\nlate",
            ),
        )
        db.expenseDao().insert(ExpenseEntity(amountCentavos = 300, labelId = labelId, timestamp = 10, paymentMethodId = cash()))
        dao.archive(oldCard)
        dao.rename(cash(), "Wallet")
        val labelsBefore = localLabels()
        val methodsBefore = dao.all()
        val expensesBefore = localExpenses()

        manager.startFresh()
        val uploaded = github.uploads.single().first
        val file = BackupSerializer.decode(uploaded)
        assertEquals(2, file.schemaVersion)
        assertEquals(methodsBefore.size, file.paymentMethods.size)
        assertTrue(file.paymentMethods.single { it.id == oldCard }.archived)

        // Wipe the phone's copy, then restore what was uploaded.
        db.replaceAll(emptyList(), emptyList(), emptyList())
        github.remote = uploaded
        val result = manager.restore()

        assertTrue(result.exceptionOrNull()?.toString(), result.isSuccess)
        assertEquals(labelsBefore, localLabels())
        assertEquals(methodsBefore, dao.all())
        assertEquals(expensesBefore, localExpenses())
    }

    @Test fun rejectsUnknownPaymentMethodId() = runBlocking {
        assertRejectedAsDamaged(
            v2Backup(
                methods = listOf(BackupPaymentMethod(id = 1, name = "Cash", colorArgb = 1, position = 0)),
                expenses = listOf(BackupExpense(id = 40, amountCentavos = 1, labelId = 10, timestamp = 1, paymentMethodId = 9)),
            ),
        )
    }

    @Test fun rejectsMissingPaymentMethodIdWhenMethodsPresent() = runBlocking {
        assertRejectedAsDamaged(
            v2Backup(
                methods = listOf(BackupPaymentMethod(id = 1, name = "Cash", colorArgb = 1, position = 0)),
                expenses = listOf(BackupExpense(id = 40, amountCentavos = 1, labelId = 10, timestamp = 1)),
            ),
        )
    }

    @Test fun rejectsAllArchivedMethods() = runBlocking {
        assertRejectedAsDamaged(
            v2Backup(
                methods = listOf(BackupPaymentMethod(id = 1, name = "Cash", colorArgb = 1, position = 0, archived = true)),
                expenses = listOf(BackupExpense(id = 40, amountCentavos = 1, labelId = 10, timestamp = 1, paymentMethodId = 1)),
            ),
        )
    }

    @Test fun rejectsDuplicateOrNonPositiveMethodIds() = runBlocking {
        assertRejectedAsDamaged(
            v2Backup(
                methods = listOf(
                    BackupPaymentMethod(id = 1, name = "Cash", colorArgb = 1, position = 0),
                    BackupPaymentMethod(id = 1, name = "GCash", colorArgb = 2, position = 1),
                ),
                expenses = emptyList(),
            ),
        )
        assertRejectedAsDamaged(
            v2Backup(
                methods = listOf(BackupPaymentMethod(id = 0, name = "Cash", colorArgb = 1, position = 0)),
                expenses = emptyList(),
            ),
        )
    }

    private suspend fun seedTwoExpenses() {
        val labelId = localLabels().first().id
        db.expenseDao().insert(ExpenseEntity(amountCentavos = 500, labelId = labelId, timestamp = 9, paymentMethodId = cash()))
        db.expenseDao().insert(ExpenseEntity(amountCentavos = 700, labelId = labelId, timestamp = 10, paymentMethodId = cash(), description = "Jollibee"))
    }

    @Test fun exportJson_roundTripsThroughRestoreFromJson() = runBlocking {
        seedTwoExpenses()
        val expensesBefore = localExpenses()
        val json = manager.exportJson()
        db.replaceAll(emptyList(), emptyList(), emptyList())

        val result = manager.restoreFromJson(json)

        assertTrue(result.exceptionOrNull()?.toString(), result.isSuccess)
        assertEquals(expensesBefore, localExpenses())
        assertEquals(2, localExpenses().size)
        assertTrue(prefs.backupEnabled.first())
        assertEquals(1, dataChangedCalls)
        assertEquals(emptyList<String>(), github.calls)
    }

    @Test fun restoreFromJson_garbage_leavesDataAndReturnsDamaged() = runBlocking {
        seedTwoExpenses()
        val before = localExpenses()
        for (garbage in listOf("PNG binary bytes", "{}", "[1,2]", "")) {
            val result = manager.restoreFromJson(garbage)
            assertEquals(
                "This backup file is damaged or isn't a Gastos.io backup.",
                result.exceptionOrNull()?.message,
            )
        }
        assertEquals(before, localExpenses())
        assertFalse(prefs.backupEnabled.first())
        assertEquals(0, dataChangedCalls)
    }

    @Test fun restoreFromJson_newerSchema_refused() = runBlocking {
        seedTwoExpenses()
        val before = localExpenses()
        val result = manager.restoreFromJson("""{"schemaVersion":3,"exportedAt":"x","labels":[],"expenses":[]}""")
        assertEquals("This backup is from a newer version of Gastos.io. Update the app first.", result.exceptionOrNull()?.message)
        assertEquals(before, localExpenses())
    }

    @Test fun preview_reportsCountAndExportedAt() = runBlocking {
        seedTwoExpenses()
        val preview = manager.preview(manager.exportJson()).getOrThrow()
        assertEquals(2, preview.expenseCount)
        assertEquals(clock.instant(), preview.exportedAt)
        assertTrue(manager.preview("nope").isFailure)
        assertEquals(2, localExpenses().size)
    }

    @Test fun backupNow_enabledWithoutRepo_isSilentSuccess() = runBlocking {
        prefs.setBackupRepo(null)
        prefs.setBackupEnabled(true)
        val result = manager.backupNow()
        assertTrue(result.isSuccess)
        assertEquals(emptyList<String>(), github.calls)
        assertNull(prefs.lastBackupError.first())
    }
}
