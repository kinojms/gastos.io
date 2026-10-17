package io.gastos.app.feedback

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

enum class FeedbackType(val label: String) {
    SUGGESTION("Suggestion"),
    BUG("Bug report"),
    QUESTION("Question"),
    OTHER("Something else"),
}

/** What the relay accepts; field names match the Worker and the PWA. */
@Serializable
data class FeedbackPayload(
    val type: String,
    val message: String,
    val contact: String,
    val appVersion: String,
    val platform: String = "Android",
)

/** A send failed; [message] is safe to show as-is. */
class FeedbackException(message: String) : Exception(message)

/** Posts feedback to the Cloudflare relay, which forwards it to the owner's Telegram. Open so tests can fake it. */
open class FeedbackClient(http: OkHttpClient, private val url: String) {
    private val http = http.newBuilder().callTimeout(15, TimeUnit.SECONDS).build()

    open suspend fun send(p: FeedbackPayload): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .post(json.encodeToString(FeedbackPayload.serializer(), p).toRequestBody(JSON))
                .build()
            http.newCall(request).execute().use { response ->
                if (response.isSuccessful) Result.success(Unit) else Result.failure(FeedbackException(FAILED))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            // A timeout is also an IOException, but it means the relay was slow, not that the phone is offline.
            val message = if (e is java.io.InterruptedIOException) FAILED else NO_CONNECTION
            Result.failure(FeedbackException(message))
        } catch (e: Exception) {
            Result.failure(FeedbackException(FAILED)) // e.g. a blank or malformed URL
        }
    }

    companion object {
        const val NO_CONNECTION = "No internet connection. Try again when you're online."
        const val FAILED = "Couldn't send feedback. Try again later."
        private val JSON = "application/json; charset=utf-8".toMediaType()

        /** The relay requires every field, including the defaulted platform. */
        private val json = Json { encodeDefaults = true }
    }
}
