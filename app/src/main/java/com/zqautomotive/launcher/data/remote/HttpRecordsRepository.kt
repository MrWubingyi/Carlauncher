package com.zqautomotive.launcher.data.remote

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray

class HttpStatusException(val statusCode: Int) : IOException("HTTP $statusCode")

/**
 * Owns transport, JSON parsing and response cleanup.
 * enqueue reads/parses on an OkHttp worker; cancellation also cancels the socket call.
 */
class HttpRecordsRepository(
    private val client: OkHttpClient = sharedClient,
    private val endpoint: HttpUrl = "http://10.0.2.2:8765/records".toHttpUrl(),
) : RecordsRepository {
    override suspend fun fetchRecords(delayed: Boolean): List<NetworkRecord> =
        suspendCancellableCoroutine { continuation ->
            val url = endpoint.newBuilder()
                .apply { if (delayed) addQueryParameter("delay", "1") }
                .build()
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build()
            val call = client.newCall(request)

            continuation.invokeOnCancellation { call.cancel() }

            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(e)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    // Close the response before resuming: no closeable resource crosses
                    // the coroutine cancellation boundary.
                    val result = runCatching {
                        response.use {
                            if (!continuation.isActive) return
                            if (!it.isSuccessful) throw HttpStatusException(it.code)
                            val body = it.body ?: throw IOException("Empty response body")
                            val array = JSONArray(body.string())
                            List(array.length()) { index ->
                                val item = array.getJSONObject(index)
                                NetworkRecord(
                                    id = item.getLong("id"),
                                    name = item.getString("name"),
                                    value = item.getString("value"),
                                    note = item.optString("note", ""),
                                    createdAt = item.getLong("createdAt"),
                                )
                            }
                        }
                    }
                    if (continuation.isActive) {
                        result.fold(
                            onSuccess = { continuation.resume(it) },
                            onFailure = { continuation.resumeWithException(it) },
                        )
                    }
                }
            })
        }

    companion object {
        private val sharedClient = OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .callTimeout(6, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }
}
