package com.example.picup.data

/**
 * URL maths for the three supported hosts. Kept free of Android imports so the
 * defaults (`raw.githubusercontent.com`, GitLab `/-/raw/`, Gitea `/raw/branch/`)
 * can be unit tested without a device.
 */
object RemoteUrls {
    const val GITHUB_API = "https://api.github.com"
    const val GITLAB_DEFAULT_HOST = "gitlab.com"

    /** Strips scheme, path and trailing slashes from whatever the user typed. */
    fun cleanHost(raw: String): String {
        var host = raw.trim()
        host = host.removePrefix("https://").removePrefix("http://")
        host = host.substringBefore('/')
        return host.trim().trimEnd('/')
    }

    /** jsDelivr's GitHub mirror: public repos only, but reachable from mainland China. */
    fun jsdelivrBase(owner: String, repo: String, branch: String): String =
        "https://cdn.jsdelivr.net/gh/$owner/$repo@$branch"

}
