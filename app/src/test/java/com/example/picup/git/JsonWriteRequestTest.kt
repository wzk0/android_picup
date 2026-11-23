package com.example.picup.git

import com.example.picup.data.Remote
import com.example.picup.net.JsonBody
import com.example.picup.net.str
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.util.Base64

/**
 * Pins the wire format of the three JSON hosts.
 *
 * `HttpURLConnection` sends `Content-Type: application/x-www-form-urlencoded`
 * when a request has a body and nobody sets the header. Gitea binds the request
 * body according to that header, so the JSON payload is parsed as a form: every
 * field comes back empty and the API happily commits a zero-byte file. Uploads
 * "succeed" while the image is gone — exactly the bug this test guards.
 */
class JsonWriteRequestTest {

    private lateinit var server: HttpServer
    private val requests = mutableListOf<Recorded>()

    /** "400"-style rejection for a POST, so the update path can be exercised. */
    private var postStatus = 201

    private val bytes = byteArrayOf(0x52, 0x49, 0x46, 0x46, 0x00, 0x7f, 0x1a, 0x33)

    private val base: String get() = "http://127.0.0.1:${server.address.port}"

    private val remote = Remote(id = "r", accountId = "a", owner = "me", repo = "pics", branch = "main")

    @Before
    fun startServer() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val body = exchange.requestBody.use { it.readBytes() }
            requests += Recorded(
                method = exchange.requestMethod,
                contentType = exchange.requestHeaders.getFirst("Content-Type"),
                body = body.toString(Charsets.UTF_8),
            )
            val code = if (exchange.requestMethod == "POST" && postStatus != 201) postStatus else 201
            val payload = "{}".toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(code, payload.size.toLong())
            exchange.responseBody.use { it.write(payload) }
        }
        server.start()
    }

    @After
    fun stopServer() {
        server.stop(0)
    }

    @Test
    fun `gitea announces JSON and carries the bytes`() = runBlocking {
        GiteaClient("$base/api/v1").putFile("t", remote, "uploads/a.webp", bytes, "上传 a.webp")

        val request = requests.single()
        assertEquals("POST", request.method)
        assertJsonContentType(request)
        val payload = JsonBody.objectOf(request.body)!!
        assertArrayEquals(bytes, Base64.getDecoder().decode(payload.str("content")))
        assertEquals("main", payload.str("branch"))
        assertEquals("上传 a.webp", payload.str("message"))
    }

    @Test
    fun `gitea replaces an existing file with the same JSON body`() = runBlocking {
        // 422 on POST means "already exists"; the PUT must be just as well formed.
        postStatus = 422

        GiteaClient("$base/api/v1").putFile("t", remote, "uploads/a.webp", bytes, "上传 a.webp")

        assertEquals(listOf("POST", "PUT"), requests.map { it.method })
        requests.forEach { request ->
            assertJsonContentType(request)
            val payload = JsonBody.objectOf(request.body)!!
            assertArrayEquals(bytes, Base64.getDecoder().decode(payload.str("content")))
        }
    }

    @Test
    fun `github announces JSON and carries the bytes`() = runBlocking {
        GitHubClient("$base/api/v3").putFile("t", remote, "uploads/a.webp", bytes, "上传 a.webp")

        val request = requests.single()
        assertEquals("PUT", request.method)
        assertJsonContentType(request)
        assertArrayEquals(
            bytes,
            Base64.getDecoder().decode(JsonBody.objectOf(request.body)!!.str("content")),
        )
    }

    @Test
    fun `gitlab announces JSON and carries the bytes`() = runBlocking {
        GitLabClient("$base/api/v4").putFile("t", remote, "uploads/a.webp", bytes, "上传 a.webp")

        val request = requests.single()
        assertEquals("POST", request.method)
        assertJsonContentType(request)
        val payload = JsonBody.objectOf(request.body)!!
        assertArrayEquals(bytes, Base64.getDecoder().decode(payload.str("content")))
        assertEquals("base64", payload.str("encoding"))
    }

    private fun assertJsonContentType(request: Recorded) {
        val contentType = request.contentType.orEmpty()
        assertTrue(
            "期望 JSON 请求头，实际为 <$contentType>",
            contentType.startsWith("application/json"),
        )
    }

    private data class Recorded(val method: String, val contentType: String?, val body: String)
}