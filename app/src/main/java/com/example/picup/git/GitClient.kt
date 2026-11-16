package com.example.picup.git

import com.example.picup.data.Remote
import com.example.picup.net.HttpResponse
import com.example.picup.net.JsonBody
import com.example.picup.net.arr
import com.example.picup.net.bool
import com.example.picup.net.obj
import com.example.picup.net.str
import kotlinx.serialization.json.jsonObject

/** The authenticated identity behind a token. */
data class AccountInfo(val login: String)

/** One repository the token can push to. */
data class RepoSummary(
    val owner: String,
    val name: String,
    val defaultBranch: String = "main",
    val private: Boolean = false,
    val description: String = "",
) {
    val fullName: String get() = "$owner/$name"
}

data class PutResult(
    val path: String,
    val commitUrl: String = "",
    val created: Boolean = true,
)

open class GitException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** 401/403 — the token is wrong, expired or lacks the scope. */
class GitAuthException(message: String, cause: Throwable? = null) : GitException(message, cause)

/**
 * The four operations a git-based image host needs. All writes go through the
 * host's REST "create file" endpoint, which is a single atomic commit per
 * upload: no working copy, no clone, no merge conflicts between two photos
 * uploaded at the same time.
 */
interface GitClient {
    suspend fun verify(token: String): AccountInfo

    suspend fun listRepos(token: String): List<RepoSummary>

    suspend fun listBranches(token: String, remote: Remote): List<String>

    suspend fun putFile(
        token: String,
        remote: Remote,
        path: String,
        bytes: ByteArray,
        message: String,
    ): PutResult

    /** True when the file existed and is now gone; false when it was already absent. */
    suspend fun deleteFile(
        token: String,
        remote: Remote,
        path: String,
        message: String,
    ): Boolean
}

object GitClients {
    /** Resolves through the plugin catalogue; an unknown id falls back to GitHub. */
    fun create(pluginId: String, host: String = ""): GitClient =
        com.example.picup.git.plugins.GitHostPlugins.byId(pluginId).createClient(host)
}

internal fun requireSuccess(response: HttpResponse, action: String): HttpResponse {
    if (response.ok) return response
    val snippet = response.body.take(300)
    val message = when (response.code) {
        401 -> "$action 失败：Token 无效或已过期（401）"
        403 -> if (response.body.contains("rate limit", ignoreCase = true) ||
            response.body.contains("secondary rate", ignoreCase = true)
        ) {
            "$action 失败：触发了平台的请求限流，请稍后再试"
        } else {
            "$action 失败：Token 权限不足（403），请确认勾选了写入仓库的权限"
        }

        404 -> "$action 失败：仓库、分支或文件不存在（404）"
        409 -> "$action 失败：文件已存在且无法覆盖（409）"
        413 -> "$action 失败：文件过大（413）"
        422 -> "$action 失败：请求被拒绝（422）：$snippet"
        in 500..599 -> "$action 失败：服务器错误（${response.code}）"
        else -> "$action 失败（${response.code}）：$snippet"
    }
    if (response.code == 401 || response.code == 403) {
        throw GitAuthException(message)
    }
    throw GitException(message)
}

/** Parses the login out of a `/user` response for GitHub, GitLab and Gitea. */
internal fun loginFromUserJson(body: String): String? {
    val obj = JsonBody.objectOf(body) ?: return null
    return obj.str("login") ?: obj.str("username")
}

internal fun reposFromJson(body: String): List<RepoSummary> {
    val array = JsonBody.arrayOf(body) ?: return emptyList()
    return array.mapNotNull { element ->
        val obj = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
        val fullName = obj.str("full_name") ?: obj.str("path_with_namespace") ?: obj.str("full_name")
        val owner = obj.obj("owner")?.str("login")
            ?: obj.obj("namespace")?.str("full_path")
            ?: fullName?.substringBeforeLast('/', "")
            ?: return@mapNotNull null
        val name = obj.str("name")
            ?: obj.str("path")
            ?: fullName?.substringAfterLast('/')
            ?: return@mapNotNull null
        RepoSummary(
            owner = owner,
            name = name,
            defaultBranch = obj.str("default_branch") ?: "main",
            private = obj.bool("private") == true,
            description = obj.str("description").orEmpty(),
        )
    }
}