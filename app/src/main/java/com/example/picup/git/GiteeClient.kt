package com.example.picup.git

import com.example.picup.data.Remote
import com.example.picup.net.Http
import com.example.picup.net.JsonBody
import com.example.picup.net.base64
import com.example.picup.net.bool
import com.example.picup.net.int
import com.example.picup.net.obj
import com.example.picup.net.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import java.net.URLEncoder

/**
 * Gitee (码云) API v5.
 *
 * Gitee authenticates with an `access_token` query/form parameter rather than a
 * header, so every request goes through [withToken]. Writes are form-encoded,
 * and an update additionally needs the current blob sha — `POST` creates,
 * `PUT` updates, exactly like the other hosts.
 */
class GiteeClient(private val apiBase: String) : GitClient {

    private fun withToken(url: String, token: String): String {
        val separator = if (url.contains('?')) '&' else '?'
        return "$url${separator}access_token=$token"
    }

    private fun form(vararg pairs: Pair<String, String>): ByteArray = pairs.joinToString("&") { (key, value) ->
        "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
    }.toByteArray()

    private fun formHeaders(): Map<String, String> = mapOf(
        "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
    )

    override suspend fun verify(token: String): AccountInfo = withContext(Dispatchers.IO) {
        val response = Http.get(withToken("$apiBase/user", token))
        requireSuccess(response, "校验 Gitee 令牌")
        AccountInfo(loginFromUserJson(response.body) ?: throw GitException("Gitee 没有返回用户名"))
    }

    override suspend fun listRepos(token: String): List<RepoSummary> = withContext(Dispatchers.IO) {
        val repos = mutableListOf<RepoSummary>()
        for (page in 1..MAX_PAGES) {
            val response = Http.get(
                withToken("$apiBase/user/repos?per_page=100&page=$page&sort=updated&direction=desc", token),
            )
            requireSuccess(response, "读取仓库列表")
            val array = JsonBody.arrayOf(response.body).orEmpty()
            array.forEach { element ->
                val obj = runCatching { element.jsonObject }.getOrNull() ?: return@forEach
                // `permission.push` marks what this token may write to.
                if (obj.obj("permission")?.bool("push") == false) return@forEach
                val owner = obj.obj("owner")?.str("login")
                    ?: obj.str("full_name")?.substringBeforeLast('/', "")
                    ?: return@forEach
                val name = obj.str("name") ?: obj.str("path") ?: return@forEach
                repos += RepoSummary(
                    owner = owner,
                    name = name,
                    defaultBranch = obj.str("default_branch") ?: "master",
                    private = obj.bool("private") == true,
                    description = obj.str("description").orEmpty(),
                )
            }
            if (array.size < 100) break
        }
        repos.distinctBy { it.fullName }
    }

    override suspend fun listBranches(token: String, remote: Remote): List<String> = withContext(Dispatchers.IO) {
        val response = Http.get(
            withToken("$apiBase/repos/${remote.owner}/${remote.repo}/branches?per_page=100", token),
        )
        requireSuccess(response, "读取分支列表")
        JsonBody.arrayOf(response.body)
            ?.mapNotNull { runCatching { it.jsonObject.str("name") }.getOrNull() }
            .orEmpty()
    }

    override suspend fun putFile(
        token: String,
        remote: Remote,
        path: String,
        bytes: ByteArray,
        message: String,
    ): PutResult = withContext(Dispatchers.IO) {
        val url = "$apiBase/repos/${remote.owner}/${remote.repo}/contents/${Http.encodePath(path)}"
        val payload = form(
            "access_token" to token,
            "content" to base64(bytes),
            "message" to message,
            "branch" to remote.branch,
        )
        var created = true
        var response = Http.post(url, formHeaders(), payload)
        if (response.code == 400 || response.code == 401 || response.code == 404 || response.code == 409) {
            // Gitee answers "file already exists" with 400; retry as an update
            // carrying the current sha.
            existingSha(token, remote, path)?.let { sha ->
                val update = form(
                    "access_token" to token,
                    "content" to base64(bytes),
                    "message" to message,
                    "branch" to remote.branch,
                    "sha" to sha,
                )
                created = false
                response = Http.put(url, formHeaders(), update)
            }
        }
        requireSuccess(response, "上传 $path")
        PutResult(path = path, created = created)
    }

    override suspend fun deleteFile(
        token: String,
        remote: Remote,
        path: String,
        message: String,
    ): Boolean = withContext(Dispatchers.IO) {
        val sha = existingSha(token, remote, path) ?: return@withContext false
        val url = "$apiBase/repos/${remote.owner}/${remote.repo}/contents/${Http.encodePath(path)}"
        val payload = form(
            "access_token" to token,
            "sha" to sha,
            "message" to message,
            "branch" to remote.branch,
        )
        val response = Http.delete(url, formHeaders(), payload)
        if (response.code == 404) return@withContext false
        requireSuccess(response, "删除 $path")
        true
    }

    private fun existingSha(token: String, remote: Remote, path: String): String? {
        val url = withToken(
            "$apiBase/repos/${remote.owner}/${remote.repo}/contents/${Http.encodePath(path)}" +
                "?ref=${Http.encodeAll(remote.branch)}",
            token,
        )
        val response = Http.get(url)
        if (!response.ok) return null
        return JsonBody.objectOf(response.body)?.str("sha")
    }

    private companion object {
        const val MAX_PAGES = 3
    }
}
