package com.example.picup.git.plugins

import com.example.picup.git.GitClient
import com.example.picup.git.GiteeClient

object GiteePlugin : GitHostPlugin {
    override val id = "gitee"
    override val displayName = "Gitee 码云"
    override val summary = "国内访问快，raw 直链可直接引用"
    override val defaultHost = "gitee.com"
    override val requiresHost = false
    override val hostEditable = false
    override val hostFieldLabel = ""
    override val hostPlaceholder = ""
    override val tokenHint = "设置 → 安全设置 → 私人令牌，勾选 projects 权限。"

    override fun tokenPageUrl(host: String): String =
        "https://$defaultHost/profile/personal_access_tokens"

    override fun apiBase(host: String): String = "https://$defaultHost/api/v5"

    override fun publicUrl(
        host: String,
        owner: String,
        repo: String,
        branch: String,
        path: String,
        publicBase: String,
    ): String = expandPublicBase(publicBase, owner, repo, branch, path)
        ?: "https://$defaultHost/$owner/$repo/raw/$branch/$path"

    override fun displayHost(host: String): String = defaultHost

    override fun createClient(host: String): GitClient = GiteeClient(apiBase(host))
}
