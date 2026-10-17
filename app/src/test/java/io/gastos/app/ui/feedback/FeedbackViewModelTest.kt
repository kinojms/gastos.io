package io.gastos.app.ui.feedback

import io.gastos.app.feedback.FeedbackClient
import io.gastos.app.feedback.FeedbackException
import io.gastos.app.feedback.FeedbackPayload
import io.gastos.app.feedback.FeedbackType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FeedbackViewModelTest {
    private class FakeClient(var result: () -> Result<Unit>) : FeedbackClient(OkHttpClient(), "https://relay.test/") {
        val sent = mutableListOf<FeedbackPayload>()
        override suspend fun send(p: FeedbackPayload): Result<Unit> {
            sent += p
            return result()
        }
    }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun cannotSendABlankMessage() {
        val vm = FeedbackViewModel(FakeClient { Result.success(Unit) }, "1.4.0")
        vm.onMessage("   \n ")
        assertFalse(vm.canSend.value)
        vm.onMessage(" ok ")
        assertTrue(vm.canSend.value)
    }

    @Test fun fieldsAreCutAtTheirLimits() {
        val vm = FeedbackViewModel(FakeClient { Result.success(Unit) }, "1.4.0")
        vm.onMessage("x".repeat(2100))
        vm.onContact("y".repeat(150))
        assertEquals(2000, vm.message.value.length)
        assertEquals(100, vm.contact.value.length)
    }

    @Test fun successSendsTrimmedTextClearsTheFormAndCallsBack() = runTest {
        val client = FakeClient { Result.success(Unit) }
        val vm = FeedbackViewModel(client, "1.4.0")
        vm.onType(FeedbackType.QUESTION)
        vm.onMessage("  How do I export?  ")
        vm.onContact(" Ana ")
        var sent = false
        vm.send { sent = true }.join()
        assertTrue(sent)
        assertEquals(FeedbackPayload("Question", "How do I export?", "Ana", "1.4.0", "Android"), client.sent.single())
        assertEquals("", vm.message.value)
        assertEquals("", vm.contact.value)
        assertEquals(FeedbackType.SUGGESTION, vm.type.value)
        assertNull(vm.error.value)
        assertFalse(vm.busy.value)
    }

    @Test fun failureKeepsTheTextAndShowsWhy() = runTest {
        val vm = FeedbackViewModel(FakeClient { Result.failure(FeedbackException(FeedbackClient.NO_CONNECTION)) }, "1.4.0")
        vm.onMessage("Add budgets")
        var sent = false
        vm.send { sent = true }.join()
        assertFalse(sent)
        assertEquals(FeedbackClient.NO_CONNECTION, vm.error.value)
        assertEquals("Add budgets", vm.message.value)
        assertFalse(vm.busy.value)
    }

    @Test fun aClientThatThrowsNeverCrashes() = runTest {
        val vm = FeedbackViewModel(FakeClient { throw RuntimeException("boom") }, "1.4.0")
        vm.onMessage("hello")
        vm.send { }.join()
        assertEquals(FeedbackClient.FAILED, vm.error.value)
    }

    @Test fun typesAreInOrderWithTheirLabels() {
        assertEquals(
            listOf("Suggestion", "Bug report", "Question", "Something else"),
            FeedbackType.entries.map { it.label },
        )
    }
}
