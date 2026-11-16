package com.example.picup.git.plugins

import com.example.picup.git.GitClient
import com.example.picup.git.GitHubClient

object GitHubPlugin : GitHostPlugin {
    override val id = "github"
    override val displayName = "GitHub"
    override val summary = "全球最大的代码托管平台"
    override val defaultHost = "github.com"
    override val requiresHost = false
    override val hostEditable = true
    override val hostFieldLabel = "GitHub Enterprise 地址（可留空）"
    override val hostPlaceholder = "github.company.com"
    override val tokenHint = "Fine-grained token：Contents → Read and write；经典 token：勾选 repo。"

    override fun tokenPageUrl(host: String): String {
        val clean = cleanHostInput(host)
        return if (clean.isBlank() || clean == defaultHost) {
            "https://github.com/settings/tokens/new?scopes=repo&description=PicUp"
        } else {
            "https://$clean/settings/tokens/new?scopes=repo&description=PicUp"
        }
    }

    override fun apiBase(host: String): String {
        val clean = cleanHostInput(host)
        return if (clean.isBlank() || clean == defaultHost) "https://api.github.com" else "https://$clean/api/v3"
    }

    override fun publicUrl(
        host: String,
        owner: String,
        repo: String,
        branch: String,
        path: String,
        publicBase: String,
    ): String = expandPublicBase(publicBase, owner, repo, branch, path)
        ?: "https://raw.githubusercontent.com/$owner/$repo/$branch/$path"

    override fun createClient(host: String): GitClient = GitHubClient(apiBase(host))
}
