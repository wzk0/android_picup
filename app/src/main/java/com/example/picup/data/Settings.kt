package com.example.picup.data

import kotlinx.serialization.Serializable

/**
 * Legacy provider enum names map to plugin ids. Accounts written before the
 * plugin refactor stored `GITHUB`/`GITLAB`/`GITEA` here.
 */
private val LegacyProviderIds = mapOf(
    "GITHUB" to "github",
    "GITLAB" to "gitlab",
    "GITEA" to "gitea",
)

fun normalisePluginId(raw: String): String {
    val trimmed = raw.trim()
    LegacyProviderIds[trimmed.uppercase()]?.let { return it }
    return trimmed.lowercase(java.util.Locale.ROOT).ifBlank { "github" }
}

/** What the uploaded bytes end up being. [PRESERVE] copies the picked file
 *  untouched (the only sane choice for GIF animations). */
@Serializable
enum class OutputFormat {
    PRESERVE,
    JPEG,
    PNG,
    WEBP,
}

/**
 * Three stops instead of a raw slider: a photo host does not need 97 vs 93,
 * and the coarse labels are easier to reason about than a number.
 */
@Serializable
enum class QualityPreset(val quality: Int) {
    ORIGINAL(100),
    BALANCED(85),
    LIGHT(60),
    ;

    companion object {
        fun nearest(quality: Int): QualityPreset =
            entries.minByOrNull { kotlin.math.abs(it.quality - quality) } ?: BALANCED
    }
}

/** How the history page lays its records out. */
@Serializable
enum class HistoryLayout {
    LIST,
    GRID,
}

@Serializable
enum class ThemeChoice {
    SYSTEM,
    LIGHT,
    DARK,
}

/**
 * One authenticated account. The token itself never lives in this model or in
 * the settings document: it is kept in the Android Keystore-backed
 * [com.example.picup.data.TokenStore] under [id].
 */
@Serializable
data class AccountProfile(
    val id: String,
    /** Plugin id such as `github`, `gitlab`, `gitee`; see the plugin catalogue. */
    val provider: String = "github",
    /** Custom instance host for self-hosted platforms; blank means the plugin default. */
    val host: String = "",
    val login: String = "",
)

/**
 * One repository an account can push to. [owner] is the GitHub user/org, the
 * GitLab namespace (nested groups included) or the Gitea owner.
 */
@Serializable
data class Remote(
    val id: String,
    val accountId: String,
    val owner: String,
    val repo: String,
    val branch: String = "main",
    /** Optional per-repository override of [UploadOptions.pathTemplate]. */
    val pathTemplate: String = "",
    /** Optional public URL base, e.g. `https://cdn.jsdelivr.net/gh/{owner}/{repo}@{branch}`. */
    val publicBase: String = "",
    val label: String = "",
) {
    val name: String get() = label.ifBlank { "$owner/$repo" }
    val slug: String get() = "$owner/$repo"
}

/** A copy template such as `![{name}]({url})`. Users can add, edit and disable these. */
@Serializable
data class CopyFormat(
    val id: String,
    val label: String,
    val template: String,
    val enabled: Boolean = true,
)

object CopyFormatDefaults {
    const val MARKDOWN_ID = "markdown"

    fun presets(): List<CopyFormat> = listOf(
        CopyFormat("raw", "直链", "{url}"),
        CopyFormat(MARKDOWN_ID, "Markdown", "![{name}]({url})"),
        CopyFormat("html", "HTML", "<img src=\"{url}\" alt=\"{name}\">"),
        CopyFormat("bbcode", "BBCode", "[img]{url}[/img]"),
        CopyFormat("markdown-title", "Markdown 带标题", "![{name}]({url} \"{name}\")"),
        CopyFormat("html-link", "HTML 链接", "<a href=\"{url}\">{name}</a>"),
    )

    fun preset(id: String): CopyFormat? = presets().firstOrNull { it.id == id }
}

/** Default repository path; the date folders keep a single directory from
 *  collecting thousands of entries (GitHub starts choking around 1000). */
const val DEFAULT_PATH_TEMPLATE = "uploads/{yyyy}/{MM}/{name}-{rand6}.{ext}"

@Serializable
data class UploadOptions(
    val format: OutputFormat = OutputFormat.WEBP,
    val quality: Int = 85,
    /** Only has an effect for the preserve-format path; a re-encode drops metadata anyway. */
    val stripExif: Boolean = true,
    /** Longest edge in pixels; 0 keeps the original dimensions. */
    val maxEdge: Int = 2048,
    /**
     * When the re-encoded file is bigger than the original (already-optimised
     * WebP, small PNG screenshots) the original bytes win.
     */
    val keepSmallerOriginal: Boolean = true,
    /** Used when a [Remote] does not override it. */
    val pathTemplate: String = DEFAULT_PATH_TEMPLATE,
    val lowerCaseNames: Boolean = true,
) {
    val qualityPreset: QualityPreset get() = QualityPreset.nearest(quality)
}

@Serializable
data class AppSettings(
    val accounts: List<AccountProfile> = emptyList(),
    val remotes: List<Remote> = emptyList(),
    val activeRemoteId: String = "",
    val upload: UploadOptions = UploadOptions(),
    val formats: List<CopyFormat> = CopyFormatDefaults.presets(),
    val activeFormatId: String = CopyFormatDefaults.MARKDOWN_ID,
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    val dynamicColor: Boolean = true,
    val historyLayout: HistoryLayout = HistoryLayout.LIST,
    /** Plugins offered in the setup wizard; a fresh install starts with GitHub only. */
    val enabledPlugins: List<String> = listOf("github"),
) {
    val activeRemote: Remote?
        get() = remotes.firstOrNull { it.id == activeRemoteId } ?: remotes.firstOrNull()

    fun accountOf(remote: Remote?): AccountProfile? =
        if (remote == null) null else accounts.firstOrNull { it.id == remote.accountId }

    fun formatsEnabled(): List<CopyFormat> = formats.filter { it.enabled }

    fun activeFormat(): CopyFormat? =
        formats.firstOrNull { it.id == activeFormatId && it.enabled } ?: formatsEnabled().firstOrNull()

    /**
     * Keeps the document self-consistent after any edit: ids must resolve, at
     * least one copy format must exist and stay enabled, and quality/size live
     * in a range the compressor accepts.
     */
    fun normalized(): AppSettings {
        val cleanRemotes = remotes.distinctBy { it.id }
        val cleanAccounts = accounts
            .distinctBy { it.id }
            .map { it.copy(provider = normalisePluginId(it.provider)) }
        val usableRemotes = cleanRemotes.filter { r -> cleanAccounts.any { it.id == r.accountId } }
        val resolvedActive = when {
            usableRemotes.any { it.id == activeRemoteId } -> activeRemoteId
            usableRemotes.isNotEmpty() -> usableRemotes.first().id
            else -> ""
        }
        var cleanFormats = formats.distinctBy { it.id }
        if (cleanFormats.isEmpty()) cleanFormats = CopyFormatDefaults.presets()
        if (cleanFormats.none { it.enabled }) cleanFormats = cleanFormats.map { it.copy(enabled = true) }
        val resolvedFormat = if (cleanFormats.any { it.id == activeFormatId && it.enabled }) {
            activeFormatId
        } else {
            cleanFormats.first { it.enabled }.id
        }
        return copy(
            accounts = cleanAccounts,
            remotes = usableRemotes,
            activeRemoteId = resolvedActive,
            formats = cleanFormats,
            activeFormatId = resolvedFormat,
            enabledPlugins = com.example.picup.git.plugins.GitHostPlugins.normaliseEnabled(enabledPlugins),
            upload = upload.copy(
                quality = upload.quality.coerceIn(20, 100),
                maxEdge = if (upload.maxEdge <= 0) 2048 else upload.maxEdge.coerceIn(512, 8192),
                pathTemplate = upload.pathTemplate.ifBlank { DEFAULT_PATH_TEMPLATE },
            ),
        )
    }
}