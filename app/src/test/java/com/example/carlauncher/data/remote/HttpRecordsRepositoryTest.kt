package com.example.carlauncher.data.remote

import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.Call
import okhttp3.EventListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONException
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class HttpRecordsRepositoryTest {
    private val transportCancelled = AtomicBoolean(false)
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var repository: HttpRecordsRepository

    @Before fun setup() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient.Builder()
            .eventListener(object : EventListener() {
                override fun canceled(call: Call) {
                    transportCancelled.set(true)
                }
            })
            .readTimeout(500, TimeUnit.MILLISECONDS)
            .callTimeout(3, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
        repository = HttpRecordsRepository(client, server.url("/records"))
    }

    @After fun cleanup() {
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
        server.shutdown()
    }

    @Test fun parsesAllRecordFields() = runBlocking {
        server.enqueue(MockResponse().setBody(JSON))
        val records = repository.fetchRecords(false)
        assertEquals(1, records.size)
        assertEquals(NetworkRecord(7, "speed", "42", "sample", 1234), records.single())
        assertEquals("/records", server.takeRequest().path)
    }

    @Test fun delayedResponseTimesOut() = runBlocking {
        server.enqueue(MockResponse().setHeadersDelay(2, TimeUnit.SECONDS).setBody(JSON))
        try {
            repository.fetchRecords(true)
            fail("Expected timeout")
        } catch (_: InterruptedIOException) {
            assertEquals("/records?delay=1", server.takeRequest().path)
        }
    }

    @Test fun cancelUnblocksRequestAndNextRequestSucceeds() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val pending = async { repository.fetchRecords(true) }
        val received = withContext(Dispatchers.IO) {
            server.takeRequest(2, TimeUnit.SECONDS)
        }
        assertNotNull(received)
        pending.cancelAndJoin()
        assertTrue(pending.isCancelled)
        assertTrue(transportCancelled.get())

        server.enqueue(MockResponse().setBody(JSON))
        assertEquals(7L, repository.fetchRecords(false).single().id)
    }

    @Test fun invalidJsonDoesNotBecomeSuccess() = runBlocking {
        server.enqueue(MockResponse().setBody("{"))
        try {
            repository.fetchRecords(false)
            fail("Expected invalid JSON")
        } catch (_: JSONException) {
            // Expected parsing failure.
        }
    }

    @Test fun httpFailurePreservesStatus() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503))
        try {
            repository.fetchRecords(false)
            fail("Expected HTTP failure")
        } catch (exception: HttpStatusException) {
            assertEquals(503, exception.statusCode)
        }
    }

    @Test fun unavailableServerIsAnIoFailure() = runBlocking {
        server.shutdown()
        try {
            repository.fetchRecords(false)
            fail("Expected connection failure")
        } catch (_: IOException) {
            // Transport unavailable, distinct from emulator network-off acceptance.
        }
    }

    companion object {
        private const val JSON =
            """[{"id":7,"name":"speed","value":"42","note":"sample","createdAt":1234}]"""
    }
}