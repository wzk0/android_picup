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

/** GitHub (github.com, or a GitHub Enterprise host when [host] is set). */
class GitHubClient(private val api: String) : GitClient {

    private fun headers(token: String) = mapOf(
        "Authorization" to "Bearer $token",
        "Accept" to "application/vnd.github+json",
        "Content-Type" to Http.CONTENT_TYPE_JSON,
        "X-GitHub-Api-Version" to "2022-11-28",
    )

    override suspend fun verify(token: String): AccountInfo = withContext(Dispatchers.IO) {
        val response = Http.get("$api/user", headers(token))
        requireSuccess(response, "校验 GitHub Token")
        AccountInfo(loginFromUserJson(response.body) ?: throw GitException("GitHub 没有返回用户名"))
    }

    override suspend fun listRepos(token: String): List<RepoSummary> = withContext(Dispatchers.IO) {
        val repos = mutableListOf<RepoSummary>()
        for (page in 1..MAX_PAGES) {
            val response = Http.get(
                "$api/user/repos?per_page=100&page=$page&sort=updated" +
                    "&affiliation=owner,collaborator,organization_member",
                headers(token),
            )
            requireSuccess(response, "读取仓库列表")
            val array = JsonBody.arrayOf(response.body).orEmpty()
            array.forEach { element ->
                val obj = runCatching { element.jsonObject }.getOrNull() ?: return@forEach
                // The endpoint reports what this token may do; a read-only
                // repo is useless for an image host, so hide it.
                if (obj.obj("permissions")?.bool("push") == false) return@forEach
                val owner = obj.obj("owner")?.str("login") ?: return@forEach
                val name = obj.str("name") ?: return@forEach
                repos += RepoSummary(
                    owner = owner,
                    name = name,
                    defaultBranch = obj.str("default_branch") ?: "main",
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
            "$api/repos/${remote.owner}/${remote.repo}/branches?per_page=100",
            headers(token),
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
        val url = "$api/repos/${remote.owner}/${remote.repo}/contents/${Http.encodePath(path)}"
        val content = base64(bytes)
        var response = Http.put(url, headers(token), putPayload(message, content, remote.branch, sha = null))
        if (response.code == 422 && response.body.contains("sha", ignoreCase = true)) {
            // The path already exists; GitHub requires the blob sha to overwrite it.
            existingSha(token, remote, path)?.let { sha ->
                response = Http.put(url, headers(token), putPayload(message, content, remote.branch, sha))
            }
        }
        requireSuccess(response, "上传 $path")
        val body = JsonBody.objectOf(response.body)
        PutResult(
            path = path,
            commitUrl = body?.obj("commit")?.str("html_url").orEmpty(),
            created = response.code == 201,
        )
    }

    override suspend fun deleteFile(
        token: String,
        remote: Remote,
        path: String,
        message: String,
    ): Boolean = withContext(Dispatchers.IO) {
        val sha = existingSha(token, remote, path) ?: return@withContext false
        val url = "$api/repos/${remote.owner}/${remote.repo}/contents/${Http.encodePath(path)}"
        val payload = JsonBody.build {
            put("message", message)
            put("sha", sha)
            put("branch", remote.branch)
        }
        val response = Http.delete(url, headers(token), payload.toByteArray())
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

    private fun putPayload(message: String, content: String, branch: String, sha: String?): ByteArray =
        JsonBody.build {
            put("message", message)
            put("content", content)
            put("branch", branch)
            if (sha != null) put("sha", sha)
        }.toByteArray()

    private companion object {
        const val MAX_PAGES = 3
    }
}