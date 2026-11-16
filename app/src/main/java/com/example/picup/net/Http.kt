package com.example.picup.net

import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.URL
import java.net.URLEncoder

/** A non-2xx answer from a git host. */
class HttpException(
    val status: Int,
    val body: String,
    message: String,
) : IOException(message)

class HttpResponse(val code: Int, val body: String) {
    val ok: Boolean get() = code in 200..299
}

/**
 * Minimal HTTP over `HttpURLConnection`.
 *
 * No OkHttp, no Retrofit: three small JSON APIs do not justify the dependency,
 * and `HttpURLConnection` already follows the system proxy — so a Clash/mihomo
 * setup that works in the browser also works here.
 *
 * No `Accept-Encoding` header is set on purpose: the JDK then asks for gzip and
 * transparently decodes it, which keeps this code free of decompression bugs.
 */
object Http {
    const val USER_AGENT = "PicUp-Android/1.0"

    fun get(url: String, headers: Map<String, String> = emptyMap()): HttpResponse =
        execute("GET", url, headers, null)

    fun post(url: String, headers: Map<String, String> = emptyMap(), body: ByteArray? = null): HttpResponse =
        execute("POST", url, headers, body)

    fun put(url: String, headers: Map<String, String> = emptyMap(), body: ByteArray? = null): HttpResponse =
        execute("PUT", url, headers, body)

    fun delete(url: String, headers: Map<String, String> = emptyMap(), body: ByteArray? = null): HttpResponse =
        execute("DELETE", url, headers, body)

    /**
     * Fetches raw bytes (a preview image) with a hard size cap. `HttpURLConnection`
     * follows redirects and the system proxy on its own.
     */
    fun downloadBytes(url: String, maxBytes: Long = MAX_IMAGE_BYTES): ByteArray {
        val connection = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (error: IOException) {
            throw IOException("图片地址无效：$url", error)
        }
        try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "image/*,*/*")
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            val declared = connection.contentLengthLong
            if (declared > maxBytes) throw IOException("图片过大")
            val out = java.io.ByteArrayOutputStream(if (declared > 0) declared.toInt() else 64 * 1024)
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            connection.inputStream.use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > maxBytes) throw IOException("图片过大")
                    out.write(buffer, 0, read)
                }
            }
            return out.toByteArray()
        } catch (timeout: SocketTimeoutException) {
            throw IOException("请求超时", timeout)
        } catch (unknown: UnknownHostException) {
            throw IOException("无法解析服务器地址", unknown)
        } finally {
            connection.disconnect()
        }
    }

    fun execute(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: ByteArray? = null,
    ): HttpResponse {
        val connection = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (error: IOException) {
            throw IOException("请求地址无效：$url", error)
        }
        try {
            connection.requestMethod = method
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", USER_AGENT)
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            if (body != null) {
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
            } else if (method != "GET" && method != "DELETE") {
                // Some hosts reject a PUT without a body; sending zero bytes is explicit.
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(0)
                connection.outputStream.use { }
            }
            val code = connection.responseCode
            val stream = if (code >= 400) connection.errorStream else connection.inputStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            return HttpResponse(code, text)
        } catch (timeout: SocketTimeoutException) {
            throw IOException("请求超时，请检查网络或代理设置", timeout)
        } catch (unknown: UnknownHostException) {
            throw IOException("无法解析服务器地址，请检查网络或代理设置", unknown)
        } finally {
            connection.disconnect()
        }
    }

    /** Percent-encodes one path segment, keeping `/` as a separator. */
    fun encodePath(path: String): String =
        path.split('/').joinToString("/") { encodeSegment(it) }

    /** Percent-encodes the whole value, `/` included (GitLab file ids). */
    fun encodeAll(value: String): String = encodeSegment(value)

    private fun encodeSegment(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private const val CONNECT_TIMEOUT_MS = 20_000
    private const val READ_TIMEOUT_MS = 60_000
    private const val MAX_IMAGE_BYTES = 24L * 1024 * 1024
}

internal fun base64(bytes: ByteArray): String = java.util.Base64.getEncoder().encodeToString(bytes)