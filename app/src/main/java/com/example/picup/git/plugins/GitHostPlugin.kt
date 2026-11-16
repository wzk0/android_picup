package com.example.picup.git.plugins

import com.example.picup.git.GitClient

/**
 * One git host, pluggable.
 *
 * Adding a platform means implementing this interface and registering it in
 * [GitHostPlugins] — the setup wizard, the settings screens and the uploader
 * never mention a platform by name. Which plugins a user can sign in with is a
 * user setting ([com.example.picup.data.AppSettings.enabledPlugins]).
 */
interface GitHostPlugin {
    /** Stable id written into accounts; never rename it. */
    val id: String

    val displayName: String

    /** One line shown in the plugin list. */
    val summary: String

    /** Domain used when the user leaves the host field empty. */
    val defaultHost: String

    /** Self-hosted platforms (Gitea) cannot work without an explicit host. */
    val requiresHost: Boolean

    /** False for hosted-only platforms whose domain is fixed (Codeberg). */
    val hostEditable: Boolean

    val hostFieldLabel: String
    val hostPlaceholder: String

    /** What a token needs to be allowed to do. */
    val tokenHint: String

    fun tokenPageUrl(host: String): String

    fun apiBase(host: String): String

    fun displayHost(host: String): String = cleanHostInput(host).ifBlank { defaultHost }

    fun publicUrl(
        host: String,
        owner: String,
        repo: String,
        branch: String,
        path: String,
        publicBase: String,
    ): String

    fun createClient(host: String): GitClient
}

/** `{owner}` / `{repo}` / `{branch}` / `{path}` expansion for user-supplied CDN bases. */
fun expandPublicBase(
    publicBase: String,
    owner: String,
    repo: String,
    branch: String,
    path: String,
): String? {
    val trimmed = publicBase.trim()
    if (trimmed.isEmpty()) return null
    val expanded = trimmed
        .replace("{owner}", owner)
        .replace("{repo}", repo)
        .replace("{branch}", branch)
        .trimEnd('/')
    return if (expanded.contains("{path}")) expanded.replace("{path}", path) else "$expanded/$path"
}

/** Strips scheme, path and trailing slashes from whatever the user typed. */
fun cleanHostInput(raw: String): String {
    var host = raw.trim()
    host = host.removePrefix("https://").removePrefix("http://")
    host = host.substringBefore('/')
    return host.trim().trimEnd('/')
}

/**
 * The plugin catalogue.
 *
 * Order is the order shown in the setup wizard. GitHub is the fallback (and the
 * only one enabled on a fresh install), so an unknown persisted id still
 * resolves to something usable.
 */
object GitHostPlugins {
    val all: List<GitHostPlugin> = listOf(
        GitHubPlugin,
        GitLabPlugin,
        GiteePlugin,
        GiteaPlugin,
        CodebergPlugin,
    )

    val fallback: GitHostPlugin = GitHubPlugin

    fun byId(id: String?): GitHostPlugin =
        all.firstOrNull { it.id.equals(id?.trim().orEmpty(), ignoreCase = true) } ?: fallback

    /** Loaded in the wizard, in catalogue order. */
    fun enabled(enabledIds: List<String>): List<GitHostPlugin> {
        val enabled = enabledIds.map { it.lowercase() }.toSet()
        return all.filter { it.id in enabled }
    }

    /** Keeps only known ids, drops duplicates, and never returns an empty list. */
    fun normaliseEnabled(ids: List<String>): List<String> {
        val known = all.map { it.id }.toSet()
        val clean = ids.map { it.trim().lowercase() }.distinct().filter { it in known }
        return clean.ifEmpty { listOf(fallback.id) }
    }
}
