package io.gastos.app.feedback

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FeedbackClientTest {
    private lateinit var server: MockWebServer
    private val payload = FeedbackPayload(type = "Bug report", message = "hi", contact = "", appVersion = "1.4.0")

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After fun tearDown() {
        runCatching { server.shutdown() }
    }

    private fun client() = FeedbackClient(OkHttpClient(), server.url("/").toString())

    @Test fun postsJsonAndSucceeds() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))
        assertTrue(client().send(payload).isSuccess)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertTrue(request.getHeader("Content-Type")!!.startsWith("application/json"))
        val json = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("Bug report", json["type"]!!.jsonPrimitive.content)
        assertEquals("hi", json["message"]!!.jsonPrimitive.content)
        assertEquals("", json["contact"]!!.jsonPrimitive.content)
        assertEquals("1.4.0", json["appVersion"]!!.jsonPrimitive.content)
        assertEquals("Android", json["platform"]!!.jsonPrimitive.content)
    }

    @Test fun relayErrorIsAFailure() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(502))
        assertEquals(FeedbackClient.FAILED, client().send(payload).exceptionOrNull()?.message)
    }

    @Test fun unreachableRelayIsNoConnection() = runBlocking {
        val c = client()
        server.shutdown()
        assertEquals(FeedbackClient.NO_CONNECTION, c.send(payload).exceptionOrNull()?.message)
    }

    @Test fun blankUrlFailsWithoutCrashing() = runBlocking {
        assertEquals(FeedbackClient.FAILED, FeedbackClient(OkHttpClient(), "").send(payload).exceptionOrNull()?.message)
    }

    @Test fun copiesTheUserFacingMessages() {
        assertEquals("No internet connection. Try again when you're online.", FeedbackClient.NO_CONNECTION)
        assertEquals("Couldn't send feedback. Try again later.", FeedbackClient.FAILED)
    }
}
