package io.gastos.app.ui.settings

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import io.gastos.app.backup.BackupManager
import io.gastos.app.backup.GitHubClient
import io.gastos.app.backup.GitHubException
import io.gastos.app.backup.TokenCipher
import io.gastos.app.backup.TokenStore
import io.gastos.app.data.AppPrefs
import io.gastos.app.data.GastosDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
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
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest {
    private lateinit var db: GastosDatabase
    private lateinit var prefs: AppPrefs
    private lateinit var tokens: TokenStore
    private lateinit var scope: CoroutineScope
    private lateinit var prefsFile: File
    private var downloadError: Exception? = null
    private var downloadGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
    private var downloads = 0

    private object PlainCipher : TokenCipher {
        override fun encrypt(plain: ByteArray) = plain
        override fun decrypt(blob: ByteArray) = blob
    }

    /** Never touches the network. */
    private inner class FakeGitHub : GitHubClient(OkHttpClient()) {
        override suspend fun getSha(repo: String, path: String, token: String): String? = null

        override suspend fun download(repo: String, path: String, token: String): String? {
            downloads++
            downloadGate?.await()
            downloadError?.let { throw it }
            return null
        }

        override suspend fun upload(repo: String, path: String, token: String, content: String, sha: String?, message: String) = Unit
    }

    @Before fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = GastosDatabase.createInMemory(context)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        prefsFile = File.createTempFile("prefs", ".preferences_pb").also { it.delete() }
        prefs = AppPrefs(PreferenceDataStoreFactory.create(scope = scope) { prefsFile })
        tokens = TokenStore(context.getSharedPreferences("settings_test_tokens", Context.MODE_PRIVATE), PlainCipher)
        tokens.clear()
    }

    @After fun tearDown() {
        db.close()
        scope.cancel()
        prefsFile.delete()
        Dispatchers.resetMain()
    }

    private fun vm(): SettingsViewModel {
        val manager = BackupManager(db, prefs, tokens, FakeGitHub(), Clock.systemUTC()) { }
        return SettingsViewModel(prefs, tokens, manager, io = Dispatchers.Unconfined)
    }

    @Test fun invalidRepoShowsErrorAndDoesNotSave() = runBlocking {
        val vm = vm()
        vm.repoInput.value = "not a repo"
        vm.tokenInput.value = "secret"
        assertNull(vm.save())
        assertEquals("Use the format owner/repo", vm.repoError.value)
        assertNull(prefs.backupRepo.first())
        assertNull(tokens.load())
    }

    @Test fun rejectsMissingOrExtraSegments() {
        val vm = vm()
        for (bad in listOf("", "owner", "owner/", "/repo", "a/b/c", "a b/c")) {
            vm.repoInput.value = bad
            assertFalse(bad, vm.validate())
        }
        vm.repoInput.value = "My-Name/repo_1.x"
        assertTrue(vm.validate())
        assertNull(vm.repoError.value)
    }

    @Test fun saveStoresRepoAndToken() = runBlocking {
        val vm = vm()
        vm.repoInput.value = " owner/repo "
        vm.tokenInput.value = "ghp_secret"
        vm.save()!!.join()
        assertEquals("owner/repo", prefs.backupRepo.first())
        assertEquals("ghp_secret", tokens.load())
        assertTrue(vm.hasToken.value)
        assertEquals("", vm.tokenInput.value)
    }

    @Test fun emptyTokenKeepsExistingToken() = runBlocking {
        tokens.save("old")
        val vm = vm()
        withTimeout(5_000) { vm.hasToken.first { it } }
        vm.repoInput.value = "owner/repo"
        vm.save()!!.join()
        assertEquals("old", tokens.load())
        assertEquals("owner/repo", prefs.backupRepo.first())
    }

    @Test fun backupActionsNeedRepoAndToken() = runBlocking {
        val vm = vm()
        assertFalse(vm.canUseBackup.value)
        vm.repoInput.value = "o/r"
        vm.save()!!.join()
        assertFalse(vm.canUseBackup.value)
        vm.tokenInput.value = "t"
        vm.save()!!.join()
        assertTrue(withTimeout(5_000) { vm.canUseBackup.first { it } })
    }

    @Test fun statusFormats() {
        val zone = ZoneId.of("Asia/Manila")
        assertEquals(BackupStatus("Not backed up yet", false), SettingsViewModel.formatStatus(null, null, zone))
        val at = ZonedDateTime.of(2026, 10, 16, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(BackupStatus("Last backup: Oct 16, 12:00 PM", false), SettingsViewModel.formatStatus(at, null, zone))
        assertEquals(BackupStatus("Bad token.", true), SettingsViewModel.formatStatus(at, "Bad token.", zone))
        val utc = ZonedDateTime.of(2026, 10, 16, 0, 5, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals("Last backup: Oct 16, 12:05 AM", SettingsViewModel.formatStatus(utc, null, ZoneOffset.UTC).text)
    }

    @Test fun restoreFailureShowsGitHubUserMessage() = runBlocking {
        tokens.save("t")
        prefs.setBackupRepo("o/r")
        downloadError = GitHubException(401, "Token was rejected.")
        val vm = vm()
        withTimeout(5_000) { vm.canUseBackup.first { it } }
        vm.restore().join()
        assertEquals(ActionResult("Token was rejected.", true), vm.result.value)
        assertFalse(vm.busy.value)
    }

    @Test fun restoreWithNoBackupReportsFailure() = runBlocking {
        tokens.save("t")
        prefs.setBackupRepo("o/r")
        val vm = vm()
        vm.restore().join()
        assertEquals(ActionResult("No backup found in this repo.", true), vm.result.value)
    }

    @Test fun startFreshEnablesBackupAndReportsSuccess() = runBlocking {
        tokens.save("t")
        prefs.setBackupRepo("o/r")
        val vm = vm()
        vm.startFresh().join()
        assertTrue(prefs.backupEnabled.first())
        assertEquals(ActionResult("Backed up", false), vm.result.value)
        assertFalse(vm.busy.value)
    }

    @Test fun saveSuccessShowsSaved() = runBlocking {
        val vm = vm()
        vm.repoInput.value = "o/r"
        vm.save()!!.join()
        assertEquals(ActionResult("Saved", false), vm.result.value)
    }

    @Test fun tokenWriteFailureDoesNotSaveRepo() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val failing = object : TokenCipher {
            override fun encrypt(plain: ByteArray): ByteArray = throw java.security.GeneralSecurityException("boom")
            override fun decrypt(blob: ByteArray) = blob
        }
        val badTokens = TokenStore(context.getSharedPreferences("settings_test_bad", Context.MODE_PRIVATE), failing)
        val manager = BackupManager(db, prefs, badTokens, FakeGitHub(), Clock.systemUTC()) { }
        val vm = SettingsViewModel(prefs, badTokens, manager, io = Dispatchers.Unconfined)
        vm.repoInput.value = "o/r"
        vm.tokenInput.value = "secret"
        vm.save()!!.join()
        assertNull(prefs.backupRepo.first())
        assertFalse(vm.hasToken.value)
        assertEquals(ActionResult("Couldn't save settings.", true), vm.result.value)
    }

    @Test fun keystoreFailureOnLoadReadsAsNoToken() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val broken = object : TokenCipher {
            override fun encrypt(plain: ByteArray) = plain
            override fun decrypt(blob: ByteArray): ByteArray = throw java.security.ProviderException("keystore gone")
        }
        val badTokens = TokenStore(context.getSharedPreferences("settings_test_provider", Context.MODE_PRIVATE), broken)
        badTokens.save("secret")
        assertNull(badTokens.load())
        prefs.setBackupRepo("o/r")
        val manager = BackupManager(db, prefs, badTokens, FakeGitHub(), Clock.systemUTC()) { }
        val vm = SettingsViewModel(prefs, badTokens, manager, io = Dispatchers.Unconfined)
        withTimeout(5_000) { vm.repoInput.first { it == "o/r" } } // init ran past the token load
        kotlinx.coroutines.delay(100)
        assertFalse(vm.hasToken.value)
        assertFalse(vm.canUseBackup.value)
    }

    @Test fun secondActionIgnoredWhileBusy() = runBlocking {
        tokens.save("t")
        prefs.setBackupRepo("o/r")
        val vm = vm()
        downloadGate = kotlinx.coroutines.CompletableDeferred()
        val first = vm.restore()
        assertTrue(vm.busy.value)
        withTimeout(5_000) { while (downloads == 0) kotlinx.coroutines.delay(10) }
        vm.restore().join()
        vm.repoInput.value = "o/r"
        assertNull(vm.save())
        assertEquals(1, downloads)
        downloadGate!!.complete(Unit)
        first.join()
        assertFalse(vm.busy.value)
    }
}
