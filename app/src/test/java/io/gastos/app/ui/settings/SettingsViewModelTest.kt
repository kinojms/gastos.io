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

    private object PlainCipher : TokenCipher {
        override fun encrypt(plain: ByteArray) = plain
        override fun decrypt(blob: ByteArray) = blob
    }

    /** Never touches the network. */
    private inner class FakeGitHub : GitHubClient(OkHttpClient()) {
        override suspend fun getSha(repo: String, path: String, token: String): String? = null

        override suspend fun download(repo: String, path: String, token: String): String? {
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
}
