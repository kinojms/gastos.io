package io.gastos.app.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRulesTest {
    @Test fun firstRepoCountsAsChanged() = assertTrue(BackupRules.credentialsChanged(null, "me/data", false))

    @Test fun sameRepoNoTokenIsUnchanged() = assertFalse(BackupRules.credentialsChanged("me/data", "me/data", false))

    @Test fun caseAndSpacesIgnored() = assertFalse(BackupRules.credentialsChanged("me/data", " Me/Data ", false))

    @Test fun differentRepoIsChanged() = assertTrue(BackupRules.credentialsChanged("me/data", "me/other", false))

    @Test fun typedTokenIsChanged() = assertTrue(BackupRules.credentialsChanged("me/data", "me/data", true))
}
