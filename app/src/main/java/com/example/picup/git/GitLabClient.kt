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

/** GitLab.com and self-hosted GitLab (`host` = the instance domain). */
class GitLabClient(private val api: String) : GitClient {

    private fun headers(token: String) = mapOf(
        "PRIVATE-TOKEN" to token,
        "Content-Type" to Http.CONTENT_TYPE_JSON,
    )

    private fun projectId(remote: Remote): String = Http.encodeAll("${remote.owner}/${remote.repo}")

    override suspend fun verify(token: String): AccountInfo = withContext(Dispatchers.IO) {
        val response = Http.get("$api/user", headers(token))
        requireSuccess(response, "校验 GitLab Token")
        AccountInfo(loginFromUserJson(response.body) ?: throw GitException("GitLab 没有返回用户名"))
    }

    override suspend fun listRepos(token: String): List<RepoSummary> = withContext(Dispatchers.IO) {
        val repos = mutableListOf<RepoSummary>()
        for (page in 1..MAX_PAGES) {
            val response = Http.get(
                "$api/projects?membership=true&simple=true&per_page=100&page=$page" +
                    "&order_by=last_activity_at&min_access_level=30",
                headers(token),
            )
            requireSuccess(response, "读取仓库列表")
            val array = JsonBody.arrayOf(response.body).orEmpty()
            array.forEach { element ->
                val obj = runCatching { element.jsonObject }.getOrNull() ?: return@forEach
                val namespace = obj.obj("namespace")?.str("full_path")
                    ?: obj.str("path_with_namespace")?.substringBeforeLast('/', "")
                    ?: return@forEach
                val name = obj.str("path") ?: obj.str("name") ?: return@forEach
                repos += RepoSummary(
                    owner = namespace,
                    name = name,
                    defaultBranch = obj.str("default_branch") ?: "main",
                    private = obj.str("visibility") != "public",
                    description = obj.str("description").orEmpty(),
                )
            }
            if (array.size < 100) break
        }
        repos.distinctBy { it.fullName }
    }

    override suspend fun listBranches(token: String, remote: Remote): List<String> = withContext(Dispatchers.IO) {
        val response = Http.get(
            "$api/projects/${projectId(remote)}/repository/branches?per_page=100",
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
        val url = "$api/projects/${projectId(remote)}/repository/files/${Http.encodeAll(path)}"
        val payload = JsonBody.build {
            put("branch", remote.branch)
            put("content", base64(bytes))
            put("commit_message", message)
            put("encoding", "base64")
        }.toByteArray()
        var created = true
        var response = Http.post(url, headers(token), payload)
        if (response.code == 400 && response.body.contains("already exists", ignoreCase = true)) {
            // The file endpoint distinguishes create from update by HTTP verb.
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
        val url = "$api/projects/${projectId(remote)}/repository/files/${Http.encodeAll(path)}" +
            "?branch=${Http.encodeAll(remote.branch)}" +
            "&commit_message=${Http.encodeAll(message)}"
        val response = Http.delete(url, headers(token))
        if (response.code == 404) return@withContext false
        requireSuccess(response, "删除 $path")
        true
    }

    private companion object {
        const val MAX_PAGES = 3
    }
}