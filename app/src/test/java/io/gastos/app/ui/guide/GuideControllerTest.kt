package io.gastos.app.ui.guide

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.gastos.app.data.AppPrefs
import io.gastos.app.ui.home.HomeSwipe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class GuideControllerTest {
    private lateinit var storeScope: CoroutineScope
    private lateinit var file: File
    private lateinit var prefs: AppPrefs
    private lateinit var guideScope: TestScope
    private lateinit var guide: GuideController

    @Before fun setUp() {
        storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        file = File.createTempFile("guide", ".preferences_pb").also { it.delete() }
        prefs = AppPrefs(PreferenceDataStoreFactory.create(scope = storeScope) { file })
        guideScope = TestScope(UnconfinedTestDispatcher())
        guide = GuideController(prefs, guideScope)
    }

    @After fun tearDown() {
        storeScope.cancel()
        file.delete()
    }

    private suspend fun seen(): Int {
        guideScope.coroutineContext.job.children.forEach { it.join() }
        return prefs.guideSeenVersion.first()
    }

    private fun walkTo(target: GuideStep) {
        guide.startAuto()
        while (guide.step.value != target) {
            when (val s = guide.step.value!!) {
                GuideStep.METHOD, GuideStep.LABEL -> guide.onSwipe(s.swipeAxis!!)
                else -> guide.next()
            }
        }
    }

    @Test fun autoStartsWhenUnseen() = runBlocking {
        guide.startIfUnseen()
        assertEquals(GuideStep.AMOUNT, guide.step.value)
    }

    @Test fun doesNotAutoStartWhenSeen() = runBlocking {
        prefs.setGuideSeenVersion(2)
        guide.startIfUnseen()
        assertNull(guide.step.value)
    }

    @Test fun returningV1UserSeesOnlyTheNewStep() = runBlocking {
        prefs.setGuideSeenVersion(1)
        guide.startIfUnseen()
        assertEquals(GuideStep.SWIPE_HINT, guide.step.value)
        guide.next()
        assertNull(guide.step.value)
        assertEquals(2, seen())
    }

    @Test fun historyStepNowLeadsToTheSwipeHint() {
        walkTo(GuideStep.HISTORY)
        assertFalse(GuideStep.HISTORY.isLast)
        guide.next()
        assertEquals(GuideStep.SWIPE_HINT, guide.step.value)
        assertTrue(GuideStep.SWIPE_HINT.isLast)
    }

    @Test fun nextWalksNonSwipeSteps() {
        guide.start()
        guide.next()
        assertEquals(GuideStep.METHOD, guide.step.value)
    }

    @Test fun nextIgnoredOnSwipeStep() {
        walkTo(GuideStep.METHOD)
        guide.next()
        assertEquals(GuideStep.METHOD, guide.step.value)
    }

    @Test fun rightAxisSwipeAdvances() {
        walkTo(GuideStep.METHOD)
        guide.onSwipe(HomeSwipe.Axis.METHOD)
        assertEquals(GuideStep.LABEL, guide.step.value)
        guide.onSwipe(HomeSwipe.Axis.LABEL)
        assertEquals(GuideStep.DESCRIPTION, guide.step.value)
    }

    @Test fun wrongAxisSwipeIgnored() {
        walkTo(GuideStep.METHOD)
        guide.onSwipe(HomeSwipe.Axis.LABEL)
        assertEquals(GuideStep.METHOD, guide.step.value)
    }

    @Test fun doneOnLastMarksSeen() = runBlocking {
        guide.startAuto()
        walkTo(GuideStep.SWIPE_HINT)
        guide.next()
        assertNull(guide.step.value)
        assertEquals(2, seen())
    }

    @Test fun skipMarksSeen() = runBlocking {
        guide.startAuto()
        guide.skip()
        assertNull(guide.step.value)
        assertEquals(2, seen())
    }

    @Test fun appStoppedEndsAndMarksSeen() = runBlocking {
        guide.startAuto()
        guide.onAppStopped()
        assertNull(guide.step.value)
        assertEquals(2, seen())
    }

    @Test fun replayDoesNotResetSeen() = runBlocking {
        prefs.setGuideSeenVersion(2)
        guide.start()
        assertEquals(GuideStep.AMOUNT, guide.step.value)
        guide.skip()
        assertNull(guide.step.value)
        assertEquals(2, seen())
        guide.start()
        assertEquals(GuideStep.AMOUNT, guide.step.value)
    }

    @Test fun replayDoesNotWriteWhenUnseen() = runBlocking {
        guide.start()
        guide.skip()
        assertEquals(0, seen())
    }

    @Test fun stepTextsVerbatim() {
        assertEquals(
            listOf(
                "Enter the amount of your expense",
                "You can change which payment method you used for this expense",
                "Then, you can choose from a set of expense types",
                "You can also specify what you bought (ex: Jollibee or MTG Cards)",
                "Finally, keep track of your expenses history here!",
                "In your history, swipe an expense left to edit or delete it",
            ),
            GuideStep.values().map { it.text },
        )
        assertEquals(6, GUIDE_TOTAL)
        assertEquals(2, GUIDE_VERSION)
    }
}
