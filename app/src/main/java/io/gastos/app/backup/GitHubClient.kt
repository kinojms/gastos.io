package io.gastos.app.backup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.Base64

/** A GitHub call failed. [userMessage] is safe to show; it never contains the token. */
class GitHubException(val code: Int, val userMessage: String, cause: Throwable? = null) :
    Exception(userMessage, cause) {
    /** Network failures and server-side errors are worth retrying; auth and repo errors are not. */
    val isRetryable: Boolean get() = code == NETWORK || code >= 500

    companion object {
        /** [code] used when no HTTP response arrived. */
        const val NETWORK = 0
    }
}

/** Minimal GitHub Contents API client. Open so tests can fake it without the network. */
open class GitHubClient(
    private val http: OkHttpClient,
    private val baseUrl: HttpUrl = "https://api.github.com/".toHttpUrl(),
) {
    /** Blob sha of [path], or null if it does not exist. Works past 1 MB, where `content` is empty. */
    open suspend fun getSha(repo: String, path: String, token: String): String? {
        val request = request(repo, path, token).header("Accept", "application/vnd.github+json").get().build()
        return execute(request, nullOn404 = true) { response ->
            val body = response.body?.string().orEmpty()
            val obj = try {
                Json.parseToJsonElement(body).jsonObject
            } catch (e: IllegalArgumentException) {
                throw GitHubException(response.code, "Unexpected response from GitHub.", e)
            }
            obj["sha"]?.jsonPrimitive?.contentOrNull
                ?: throw GitHubException(response.code, "Unexpected response from GitHub.")
        }
    }

    /** Raw file contents of [path], or null if it does not exist. The raw media type works past 1 MB. */
    open suspend fun download(repo: String, path: String, token: String): String? {
        val request = request(repo, path, token).header("Accept", "application/vnd.github.raw+json").get().build()
        return execute(request, nullOn404 = true) { it.body?.string().orEmpty() }
    }

    /** Creates or updates [path] with [content] as one commit. [sha] is required when the file exists. */
    open suspend fun upload(repo: String, path: String, token: String, content: String, sha: String?, message: String) {
        val payload = buildJsonObject {
            put("message", message)
            put("content", Base64.getEncoder().encodeToString(content.toByteArray(Charsets.UTF_8)))
            if (sha != null) put("sha", sha)
        }.toString()
        val request = request(repo, path, token)
            .header("Accept", "application/vnd.github+json")
            .put(payload.toRequestBody(JSON))
            .build()
        // 404 here means the repo itself is missing (or hidden from this token).
        execute(request, nullOn404 = false) { }
    }

    private fun request(repo: String, path: String, token: String): Request.Builder {
        val parts = repo.trim().split('/')
        if (parts.size != 2 || parts.any { it.isBlank() }) {
            throw GitHubException(400, "Repo must look like owner/name.")
        }
        val url = baseUrl.newBuilder()
            .addPathSegment("repos")
            .addPathSegment(parts[0])
            .addPathSegment(parts[1])
            .addPathSegment("contents")
            .addPathSegments(path)
            .build()
        return Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("X-GitHub-Api-Version", "2022-11-28")
    }

    /** Runs [request] off the main thread. 404 yields null when [nullOn404]; other failures throw [GitHubException]. */
    private suspend fun <T : Any> execute(request: Request, nullOn404: Boolean, onSuccess: (Response) -> T): T? =
        withContext(Dispatchers.IO) {
            try {
                http.newCall(request).execute().use { response ->
                    when {
                        response.isSuccessful -> onSuccess(response)
                        response.code == 404 && nullOn404 -> null
                        else -> throw httpError(response.code)
                    }
                }
            } catch (e: IOException) {
                throw GitHubException(GitHubException.NETWORK, "No internet connection.", e)
            }
        }

    private fun httpError(code: Int): GitHubException = when (code) {
        401 -> GitHubException(code, "GitHub token is invalid or expired.")
        403 -> GitHubException(code, "Token can't access this repo.")
        404 -> GitHubException(code, "Repo not found.")
        else -> GitHubException(code, "GitHub error ($code). Try again later.")
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
