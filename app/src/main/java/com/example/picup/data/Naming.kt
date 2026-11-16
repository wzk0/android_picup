package com.example.picup.data

import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Filename/path helpers. Pure functions only — unit tested on the JVM. */
object Naming {
    private val unsafeRuns = Regex("[^\\p{L}\\p{N}._-]+")
    private val invalidFileChars = Regex("[\\u0000-\\u001f:*?\"<>|]")
    private val whitespaceRuns = Regex("\\s+")

    /**
     * Turns a display name into something every host and URL accepts. Letters
     * and digits of any script are kept (a Chinese filename stays readable
     * once percent-encoded); everything else collapses to a single dash.
     */
    fun slug(raw: String, lowerCase: Boolean = true, maxLength: Int = 64): String {
        var value = raw.trim()
        value = unsafeRuns.replace(value, "-")
        value = value.trim('-', '.', '_', ' ')
        if (lowerCase) value = value.lowercase()
        if (value.length > maxLength) value = value.take(maxLength).trimEnd('-', '.')
        return value.ifBlank { "img" }
    }

    /** Splits `IMG_0001.JPG` into (`IMG_0001`, `jpg`). */
    fun splitExtension(displayName: String?): Pair<String, String> {
        val name = displayName?.substringAfterLast('/')?.substringAfterLast('\\')?.trim().orEmpty()
        if (name.isBlank()) return "image" to "jpg"
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot == name.length - 1) return name to "jpg"
        val base = name.substring(0, dot)
        val ext = name.substring(dot + 1).lowercase()
        if (ext.length > 5) return name to "jpg"
        return base.ifBlank { "image" } to ext
    }

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "%.1f KB".format(kb)
        val mb = kb / 1024.0
        if (mb < 1024) return "%.1f MB".format(mb)
        return "%.2f GB".format(mb / 1024.0)
    }

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun dateTime(millis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDateTime =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime()

    fun formatDate(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        F_DATE.format(dateTime(millis, zone))

    fun formatTime(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        F_TIME.format(dateTime(millis, zone))

    fun formatDateTime(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        F_DATE_TIME.format(dateTime(millis, zone))

    fun dayLabel(millis: Long, today: LocalDate = LocalDate.now()): String {
        val date = dateTime(millis).toLocalDate()
        return when (date) {
            today -> "今天"
            today.minusDays(1) -> "昨天"
            else -> F_DAY.format(date)
        }
    }

    private val F_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val F_TIME = DateTimeFormatter.ofPattern("HH:mm")
    private val F_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val F_DAY = DateTimeFormatter.ofPattern("M月d日")
}

/** Everything a path template or copy template can interpolate. */
data class TemplateValues(
    val name: String,
    val ext: String,
    val width: Int = 0,
    val height: Int = 0,
    val sizeBytes: Long = 0,
    val hash: String = "",
    val owner: String = "",
    val repo: String = "",
    val branch: String = "",
    val provider: String = "",
    val now: LocalDateTime = LocalDateTime.now(),
    /** Deterministic in tests, random in production. */
    val random: String = "",
)

/**
 * Renders `uploads/{yyyy}/{MM}/{name}-{rand6}.{ext}` style paths.
 *
 * Unknown placeholders are left alone instead of being erased: a typo should
 * be visible in the preview, not silently produce `{名}.jpg`.
 */
object PathTemplate {
    private val tokens = listOf(
        "yyyy", "yy", "MM", "dd", "HH", "mm", "ss",
        "epoch", "date", "time", "name", "ext", "width", "height",
        "sizeHuman", "size", "hash", "owner", "repo", "branch", "provider",
        "rand64", "rand16", "rand8", "rand6", "rand4", "rand",
    ).sortedByDescending { it.length }

    fun render(template: String, values: TemplateValues): String {
        val random = values.random.ifBlank { randomHex(64) }
        val map = mapOf(
            "yyyy" to "%04d".format(values.now.year),
            "yy" to "%02d".format(values.now.year % 100),
            "MM" to "%02d".format(values.now.monthValue),
            "dd" to "%02d".format(values.now.dayOfMonth),
            "HH" to "%02d".format(values.now.hour),
            "mm" to "%02d".format(values.now.minute),
            "ss" to "%02d".format(values.now.second),
            "epoch" to (values.now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() / 1000).toString(),
            "date" to "%04d-%02d-%02d".format(values.now.year, values.now.monthValue, values.now.dayOfMonth),
            "time" to "%02d%02d%02d".format(values.now.hour, values.now.minute, values.now.second),
            "name" to values.name,
            "ext" to values.ext,
            "width" to values.width.toString(),
            "height" to values.height.toString(),
            "sizeHuman" to Naming.formatBytes(values.sizeBytes).replace(" ", ""),
            "size" to values.sizeBytes.toString(),
            "hash" to values.hash,
            "owner" to values.owner,
            "repo" to values.repo,
            "branch" to values.branch,
            "provider" to values.provider,
            "rand64" to random.take(64),
            "rand16" to random.take(16),
            "rand8" to random.take(8),
            "rand6" to random.take(6),
            "rand4" to random.take(4),
            "rand" to random.take(8),
        )
        var out = template
        tokens.forEach { token -> out = out.replace("{$token}", map[token].orEmpty()) }
        return sanitizePath(out)
    }

    /**
     * Collapses the path into something Git and every web server accept:
     * no empty/`.`/`..` segments, no leading slash, no control or reserved
     * characters, spaces turned into dashes.
     */
    fun sanitizePath(path: String): String {
        val segments = path.replace('\\', '/').split('/')
        val cleaned = segments.mapNotNull { raw ->
            val trimmed = raw.trim()
            if (trimmed.isEmpty() || trimmed == "." || trimmed == "..") {
                null
            } else {
                trimmed
                    .replace(invalidFileChars, "")
                    .replace(whitespaceRuns, "-")
                    .trim('.', ' ', '-')
                    .ifBlank { null }
            }
        }
        return cleaned.joinToString("/")
    }

    fun randomHex(length: Int = 8): String =
        (1..length).map { HEX[((Math.random() * 16).toInt()).coerceIn(0, 15)] }.joinToString("")

    private const val HEX = "0123456789abcdef"
}

internal val invalidFileChars = Regex("[\\u0000-\\u001f:*?\"<>|]")
internal val whitespaceRuns = Regex("\\s+")