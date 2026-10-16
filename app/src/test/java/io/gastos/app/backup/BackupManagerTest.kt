package io.gastos.app.backup

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import io.gastos.app.data.AppPrefs
import io.gastos.app.data.ExpenseEntity
import io.gastos.app.data.GastosDatabase
import io.gastos.app.data.LabelEntity
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
        db.expenseDao().insert(ExpenseEntity(amountCentavos = 500, labelId = localLabels().first().id, timestamp = 9))
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
        db.expenseDao().insert(ExpenseEntity(amountCentavos = 999, labelId = localLabels().first().id, timestamp = 5))
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
        assertEquals(listOf(ExpenseEntity(id = 40, amountCentavos = 12_345, labelId = 10, timestamp = 1_000)), localExpenses())
        assertTrue(prefs.backupEnabled.first())
        assertEquals(1, dataChangedCalls)
        assertEquals(0, github.uploads.size)
    }

    @Test fun failedRestoreKeepsLocal() = runBlocking {
        db.expenseDao().insert(ExpenseEntity(amountCentavos = 999, labelId = localLabels().first().id, timestamp = 5))
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

    @Test fun newerSchemaRestoreKeepsLocal() = runBlocking {
        val labelsBefore = localLabels()
        github.remote = """{"schemaVersion":2,"exportedAt":"x","labels":[],"expenses":[]}"""

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
}
