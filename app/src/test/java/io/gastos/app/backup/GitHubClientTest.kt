package io.gastos.app.backup

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.Base64
import java.util.concurrent.TimeUnit

class GitHubClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: GitHubClient

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        client = GitHubClient(OkHttpClient(), server.url("/"))
    }

    @After fun tearDown() {
        server.shutdown()
    }

    @Test fun uploadSendsBase64AndSha() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"content":{"sha":"new"}}"""))
        val json = """{"schemaVersion":1,"note":"₱45.50"}"""
        client.upload("o/r", "backup.json", "t", json, "abc", "Backup 2026-10-16 12:00")

        val req = server.takeRequest()
        assertEquals("PUT", req.method)
        assertEquals("/repos/o/r/contents/backup.json", req.path)
        assertEquals("Bearer t", req.getHeader("Authorization"))
        assertEquals("2022-11-28", req.getHeader("X-GitHub-Api-Version"))
        val body = Json.parseToJsonElement(req.body.readUtf8()).jsonObject
        assertEquals(Base64.getEncoder().encodeToString(json.toByteArray(Charsets.UTF_8)), body["content"]!!.jsonPrimitive.content)
        assertEquals("abc", body["sha"]!!.jsonPrimitive.content)
        assertEquals("Backup 2026-10-16 12:00", body["message"]!!.jsonPrimitive.content)
    }

    @Test fun uploadOmitsShaWhenNull() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody("{}"))
        client.upload("o/r", "backup.json", "t", "{}", null, "m")
        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertFalse(body.containsKey("sha"))
    }

    @Test fun getShaReturnsNullOn404() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"message":"Not Found"}"""))
        assertNull(client.getSha("o/r", "backup.json", "t"))
    }

    @Test fun getShaWorksForLargeFiles() = runBlocking {
        // Files over 1 MB come back with empty content and encoding "none".
        server.enqueue(MockResponse().setBody("""{"name":"backup.json","sha":"big1","size":2000000,"content":"","encoding":"none"}"""))
        assertEquals("big1", client.getSha("o/r", "backup.json", "t"))
    }

    @Test fun downloadUsesRawAccept() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"schemaVersion":1}"""))
        assertEquals("""{"schemaVersion":1}""", client.download("o/r", "backup.json", "t"))
        val req = server.takeRequest()
        assertEquals("application/vnd.github.raw+json", req.getHeader("Accept"))
        assertEquals("Bearer t", req.getHeader("Authorization"))
    }

    @Test fun downloadReturnsNullWhenRepoVisibleButFileMissing() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404)) // contents/backup.json
        server.enqueue(MockResponse().setBody("""{"full_name":"o/r","private":true}""")) // repos/o/r
        assertNull(client.download("o/r", "backup.json", "t"))
        assertEquals("/repos/o/r/contents/backup.json", server.takeRequest().path)
        val repoCheck = server.takeRequest(1, TimeUnit.SECONDS) ?: error("no repo check request")
        assertEquals("/repos/o/r", repoCheck.path)
        assertEquals("Bearer t", repoCheck.getHeader("Authorization"))
    }

    @Test fun downloadFailsWhenRepoHiddenFromToken() = runBlocking {
        // GitHub answers 404 for both the file and the repo when the token can't see a private repo.
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setResponseCode(404))
        try {
            client.download("o/r", "backup.json", "t")
            fail("expected GitHubException")
        } catch (e: GitHubException) {
            assertEquals(404, e.code)
            assertEquals("Repo not found, or this token can't access it.", e.userMessage)
        }
        assertEquals(2, server.requestCount)
    }

    @Test fun maps401ToFriendlyMessage() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"message":"Bad credentials"}"""))
        try {
            client.download("o/r", "backup.json", "t")
            fail("expected GitHubException")
        } catch (e: GitHubException) {
            assertEquals(401, e.code)
            assertEquals("GitHub token is invalid or expired.", e.userMessage)
            assertEquals("GitHub token is invalid or expired.", e.message)
        }
    }

    @Test fun maps404OnUploadToRepoNotFound() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        try {
            client.upload("o/r", "backup.json", "t", "{}", null, "m")
            fail("expected GitHubException")
        } catch (e: GitHubException) {
            assertEquals("Repo not found.", e.userMessage)
        }
    }

    @Test fun mapsNetworkFailure() = runBlocking {
        server.shutdown()
        try {
            client.getSha("o/r", "backup.json", "t")
            fail("expected GitHubException")
        } catch (e: GitHubException) {
            assertEquals("No internet connection.", e.userMessage)
            assertEquals(true, e.isRetryable)
        }
    }
}
