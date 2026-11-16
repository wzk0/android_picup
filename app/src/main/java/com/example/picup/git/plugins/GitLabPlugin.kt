package com.example.picup.git.plugins

import com.example.picup.git.GitClient
import com.example.picup.git.GitLabClient

object GitLabPlugin : GitHostPlugin {
    override val id = "gitlab"
    override val displayName = "GitLab"
    override val summary = "内置 CI/CD 的平台，也支持自建实例"
    override val defaultHost = "gitlab.com"
    override val requiresHost = false
    override val hostEditable = true
    override val hostFieldLabel = "实例地址（留空即 gitlab.com）"
    override val hostPlaceholder = "gitlab.example.com"
    override val tokenHint = "User settings → Access tokens，勾选 api。"

    override fun tokenPageUrl(host: String): String =
        "https://${cleanHostInput(host).ifBlank { defaultHost }}/-/user_settings/personal_access_tokens"

    override fun apiBase(host: String): String =
        "https://${cleanHostInput(host).ifBlank { defaultHost }}/api/v4"

    override fun publicUrl(
        host: String,
        owner: String,
        repo: String,
        branch: String,
        path: String,
        publicBase: String,
    ): String = expandPublicBase(publicBase, owner, repo, branch, path)
        ?: "https://${cleanHostInput(host).ifBlank { defaultHost }}/$owner/$repo/-/raw/$branch/$path"

    override fun createClient(host: String): GitClient = GitLabClient(apiBase(host))
}
