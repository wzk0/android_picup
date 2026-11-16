package com.example.picup.data

import java.time.ZoneId

/** Values available to every copy template. */
data class LinkValues(
    val url: String,
    val path: String = "",
    val fileName: String = "",
    val ext: String = "",
    val repo: String = "",
    val branch: String = "",
    val sizeBytes: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
    val uploadedAtMillis: Long = 0,
    val zone: ZoneId = ZoneId.systemDefault(),
) {
    val baseName: String get() = fileName.substringBeforeLast('.', fileName)
    val size: String get() = Naming.formatBytes(sizeBytes)
    val date: String get() = Naming.formatDate(uploadedAtMillis, zone)
    val time: String get() = Naming.formatTime(uploadedAtMillis, zone)
    val markdown: String get() = "![$baseName]($url)"
}

/**
 * Renders a user-editable copy template.
 *
 * Placeholders: `{url} {path} {name} {file} {ext} {repo} {branch} {size}
 * {width} {height} {date} {time} {markdown}`. Unknown placeholders stay as
 * typed so a broken template is obvious instead of producing empty links.
 */
object CopyFormatRenderer {
    fun render(template: String, values: LinkValues): String {
        val map = mapOf(
            "url" to values.url,
            "path" to values.path,
            "name" to values.baseName,
            "file" to values.fileName,
            "ext" to values.ext,
            "repo" to values.repo,
            "branch" to values.branch,
            "size" to values.size,
            "width" to values.width.toString(),
            "height" to values.height.toString(),
            "date" to values.date,
            "time" to values.time,
            "markdown" to values.markdown,
        )
        var out = template
        // Longest keys first so `{name}` never eats part of `{fileName}`-like tokens.
        map.keys.sortedByDescending { it.length }.forEach { key ->
            out = out.replace("{$key}", map.getValue(key))
        }
        return out
    }

    /** The default format used by a one-tap copy on a history row. */
    fun primary(settings: AppSettings): CopyFormat? = settings.activeFormat()

    fun knownPlaceholders(): List<String> =
        listOf("url", "path", "name", "file", "ext", "repo", "branch", "size", "width", "height", "date", "time", "markdown")
}