package com.example.picup.git.plugins

import com.example.picup.git.GitClient
import com.example.picup.git.GiteaClient

/** Self-hosted Gitea; the host is mandatory. */
object GiteaPlugin : GitHostPlugin {
    override val id = "gitea"
    override val displayName = "Gitea / Forgejo"
    override val summary = "轻量自建托管，填入个人实例地址"
    override val defaultHost = ""
    override val requiresHost = true
    override val hostEditable = true
    override val hostFieldLabel = "实例地址（必填）"
    override val hostPlaceholder = "git.example.com"
    override val tokenHint = "设置 → 应用 → 生成令牌，权限勾选 write:repository。"

    override fun tokenPageUrl(host: String): String =
        "https://${cleanHostInput(host)}/user/settings/applications"

    override fun apiBase(host: String): String = "https://${cleanHostInput(host)}/api/v1"

    override fun publicUrl(
        host: String,
        owner: String,
        repo: String,
        branch: String,
        path: String,
        publicBase: String,
    ): String = expandPublicBase(publicBase, owner, repo, branch, path)
        ?: "https://${cleanHostInput(host)}/$owner/$repo/raw/branch/$branch/$path"

    override fun createClient(host: String): GitClient = GiteaClient(apiBase(host))
}

/** Codeberg is hosted Forgejo: same API, fixed domain. */
object CodebergPlugin : GitHostPlugin {
    override val id = "codeberg"
    override val displayName = "Codeberg"
    override val summary = "非营利组织运营的 Forgejo 实例"
    override val defaultHost = "codeberg.org"
    override val requiresHost = false
    override val hostEditable = false
    override val hostFieldLabel = ""
    override val hostPlaceholder = ""
    override val tokenHint = "设置 → 应用 → 生成令牌，权限勾选 write:repository。"

    override fun tokenPageUrl(host: String): String =
        "https://$defaultHost/user/settings/applications"

    override fun apiBase(host: String): String = "https://$defaultHost/api/v1"

    override fun publicUrl(
        host: String,
        owner: String,
        repo: String,
        branch: String,
        path: String,
        publicBase: String,
    ): String = expandPublicBase(publicBase, owner, repo, branch, path)
        ?: "https://$defaultHost/$owner/$repo/raw/branch/$branch/$path"

    override fun displayHost(host: String): String = defaultHost

    override fun createClient(host: String): GitClient = GiteaClient(apiBase(host))
}
