package com.example.picup.git

import com.example.picup.data.Remote
import com.example.picup.net.Http
import com.example.picup.net.JsonBody
import com.example.picup.net.base64
import com.example.picup.net.bool
import com.example.picup.net.obj
import com.example.picup.net.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/** Gitea and its hard fork Forgejo. [host] is required. */
class GiteaClient(private val api: String) : GitClient {

    private fun headers(token: String) = mapOf(
        "Authorization" to "token $token",
        "Content-Type" to Http.CONTENT_TYPE_JSON,
    )

    override suspend fun verify(token: String): AccountInfo = withContext(Dispatchers.IO) {
        val response = Http.get("$api/user", headers(token))
        requireSuccess(response, "校验 Gitea Token")
        AccountInfo(loginFromUserJson(response.body) ?: throw GitException("Gitea 没有返回用户名"))
    }

    override suspend fun listRepos(token: String): List<RepoSummary> = withContext(Dispatchers.IO) {
        val repos = mutableListOf<RepoSummary>()
        for (page in 1..MAX_PAGES) {
            val response = Http.get("$api/user/repos?limit=50&page=$page", headers(token))
            requireSuccess(response, "读取仓库列表")
            val array = JsonBody.arrayOf(response.body).orEmpty()
            array.forEach { element ->
                val obj = runCatching { element.jsonObject }.getOrNull() ?: return@forEach
                val owner = obj.obj("owner")?.str("login") ?: return@forEach
                val name = obj.str("name") ?: return@forEach
                if (obj.obj("permissions")?.bool("push") == false) return@forEach
                repos += RepoSummary(
                    owner = owner,
                    name = name,
                    defaultBranch = obj.str("default_branch") ?: "main",
                    private = obj.bool("private") == true,
                    description = obj.str("description").orEmpty(),
                )
            }
            if (array.size < 50) break
        }
        repos.distinctBy { it.fullName }
    }

    override suspend fun listBranches(token: String, remote: Remote): List<String> = withContext(Dispatchers.IO) {
        val response = Http.get("$api/repos/${remote.owner}/${remote.repo}/branches", headers(token))
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
        val url = "$api/repos/${remote.owner}/${remote.repo}/contents/${Http.encodePath(path)}"
        val payload = JsonBody.build {
            put("branch", remote.branch)
            put("content", base64(bytes))
            put("message", message)
        }.toByteArray()
        var created = true
        var response = Http.post(url, headers(token), payload)
        if (response.code == 422 || response.code == 409) {
            created = false
            response = Http.put(url, headers(token), payload)
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
        val url = "$api/repos/${remote.owner}/${remote.repo}/contents/${Http.encodePath(path)}" +
            "?sha=${Http.encodeAll(sha)}&branch=${Http.encodeAll(remote.branch)}" +
            "&message=${Http.encodeAll(message)}"
        val response = Http.delete(url, headers(token))
        if (response.code == 404) return@withContext false
        requireSuccess(response, "删除 $path")
        true
    }

    private fun existingSha(token: String, remote: Remote, path: String): String? {
        val url = "$api/repos/${remote.owner}/${remote.repo}/contents/${Http.encodePath(path)}" +
            "?ref=${Http.encodeAll(remote.branch)}"
        val response = Http.get(url, headers(token))
        if (!response.ok) return null
        return JsonBody.objectOf(response.body)?.str("sha")
    }

    private companion object {
        const val MAX_PAGES = 3
    }
}