package com.example.picup.data

import com.example.picup.git.plugins.CodebergPlugin
import com.example.picup.git.plugins.GitHostPlugins
import com.example.picup.git.plugins.GiteaPlugin
import com.example.picup.git.plugins.GiteePlugin
import com.example.picup.git.plugins.GitHubPlugin
import com.example.picup.git.plugins.GitLabPlugin
import com.example.picup.git.plugins.cleanHostInput
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class NamingTest {

    @Test
    fun `extension is split from the display name`() {
        assertEquals("IMG_0001" to "jpg", Naming.splitExtension("IMG_0001.JPG"))
        assertEquals("holiday.2026" to "png", Naming.splitExtension("holiday.2026.png"))
    }

    @Test
    fun `a name without extension keeps a fallback extension`() {
        assertEquals("photo" to "jpg", Naming.splitExtension("photo"))
        assertEquals("image" to "jpg", Naming.splitExtension(null))
        assertEquals("image" to "jpg", Naming.splitExtension("   "))
    }

    @Test
    fun `only the last dotted segment becomes the extension`() {
        assertEquals("archive.tar" to "gz", Naming.splitExtension("archive.tar.gz"))
    }

    @Test
    fun `slug drops characters a URL would have to escape`() {
        assertEquals("my-photo-1", Naming.slug("My Photo #1"))
        assertEquals("sunset-over-sea", Naming.slug("  sunset / over  sea  "))
        assertEquals("img", Naming.slug("///"))
    }

    @Test
    fun `slug keeps non-latin letters and can preserve case`() {
        assertEquals("海边日落", Naming.slug("海边日落"))
        assertEquals("Photo", Naming.slug("Photo", lowerCase = false))
    }

    @Test
    fun `slug truncates without leaving a trailing dash`() {
        val slug = Naming.slug("abcdefghij-klmnopqrst", maxLength = 12)
        assertEquals("abcdefghij-k".length, slug.length)
        assertEquals(false, slug.endsWith("-"))
    }

    @Test
    fun `byte sizes use readable units`() {
        assertEquals("512 B", Naming.formatBytes(512))
        assertEquals("1.0 KB", Naming.formatBytes(1024))
        assertEquals("2.4 MB", Naming.formatBytes((2.4 * 1024 * 1024).toLong()))
        assertEquals("1.50 GB", Naming.formatBytes((1.5 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun `sha256 is stable and lowercase hex`() {
        val digest = Naming.sha256Hex("PicUp".toByteArray())
        assertEquals(64, digest.length)
        assertEquals(digest, Naming.sha256Hex("PicUp".toByteArray()))
        assertEquals("6ecea981bf2c43fe", digest.take(16))
    }

    @Test
    fun `day labels use today and yesterday`() {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.of(2026, 3, 4)
        val todayMillis = today.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val yesterdayMillis = today.minusDays(1).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals("今天", Naming.dayLabel(todayMillis, today))
        assertEquals("昨天", Naming.dayLabel(yesterdayMillis, today))
    }
}

class PathTemplateTest {

    private val fixedNow: LocalDateTime = LocalDateTime.of(2026, 1, 5, 10, 20, 30)

    private fun values(random: String = "a1b2c3d4e5f6") = TemplateValues(
        name = "sunset",
        ext = "webp",
        width = 1920,
        height = 1080,
        sizeBytes = 248_000,
        hash = "3f9a1c2d5e6b",
        owner = "you",
        repo = "images",
        branch = "main",
        provider = "github",
        now = fixedNow,
        random = random,
    )

    @Test
    fun `the default template renders a dated path`() {
        val path = PathTemplate.render("uploads/{yyyy}/{MM}/{name}-{rand6}.{ext}", values())
        assertEquals("uploads/2026/01/sunset-a1b2c3.webp", path)
    }

    @Test
    fun `all time placeholders use the supplied clock`() {
        val path = PathTemplate.render("{yyyy}-{MM}-{dd}_{HH}{mm}{ss}", values())
        assertEquals("2026-01-05_102030", path)
    }

    @Test
    fun `size and hash placeholders resolve`() {
        val path = PathTemplate.render("{size}-{sizeHuman}-{hash}", values())
        assertEquals("248000-242.2KB-3f9a1c2d5e6b", path)
    }

    @Test
    fun `unknown placeholders are left visible instead of being erased`() {
        val path = PathTemplate.render("uploads/{nope}/{name}.{ext}", values())
        assertEquals("uploads/{nope}/sunset.webp", path)
    }

    @Test
    fun `sanitize removes traversal and duplicate separators`() {
        assertEquals("etc/passwd", PathTemplate.sanitizePath("../../etc/passwd"))
        assertEquals("a/b/c.jpg", PathTemplate.sanitizePath("/a//b///c.jpg"))
        assertEquals("a/b.jpg", PathTemplate.sanitizePath("a\\b.jpg"))
        assertEquals("my-photo.jpg", PathTemplate.sanitizePath("  my photo.jpg  "))
    }

    @Test
    fun `sanitize keeps nested directories untouched`() {
        assertEquals("uploads/2026/01/sunset.webp", PathTemplate.sanitizePath("uploads/2026/01/sunset.webp"))
    }

    @Test
    fun `random hex has the requested length and alphabet`() {
        val hex = PathTemplate.randomHex(16)
        assertEquals(16, hex.length)
        assertEquals(true, hex.all { it in "0123456789abcdef" })
    }
}

class CopyFormatRendererTest {

    private val link = LinkValues(
        url = "https://raw.githubusercontent.com/you/images/main/uploads/sunset.webp",
        path = "uploads/sunset.webp",
        fileName = "sunset.webp",
        ext = "webp",
        repo = "you/images",
        branch = "main",
        sizeBytes = 248_000,
        width = 1920,
        height = 1080,
        uploadedAtMillis = 0,
    )

    @Test
    fun `default markdown uses the base name as alt text`() {
        val format = CopyFormatDefaults.preset(CopyFormatDefaults.MARKDOWN_ID)!!
        assertEquals("![sunset](${link.url})", CopyFormatRenderer.render(format.template, link))
    }

    @Test
    fun `raw preset is just the URL`() {
        val format = CopyFormatDefaults.preset("raw")!!
        assertEquals(link.url, CopyFormatRenderer.render(format.template, link))
    }

    @Test
    fun `html preset quotes the URL`() {
        val format = CopyFormatDefaults.preset("html")!!
        assertEquals("<img src=\"${link.url}\" alt=\"sunset\">", CopyFormatRenderer.render(format.template, link))
    }

    @Test
    fun `custom template may use any known placeholder`() {
        val rendered = CopyFormatRenderer.render(
            "{markdown}\n{width}x{height} {size} on {repo}@{branch} ({ext})",
            link,
        )
        assertEquals(
            "![sunset](${link.url})\n1920x1080 242.2 KB on you/images@main (webp)",
            rendered,
        )
    }

    @Test
    fun `unknown placeholders survive rendering`() {
        assertEquals("{oops}-sunset", CopyFormatRenderer.render("{oops}-{name}", link))
    }

    @Test
    fun `{file} keeps the extension while {name} drops it`() {
        assertEquals("sunset.webp/sunset", CopyFormatRenderer.render("{file}/{name}", link))
    }
}

class GitHostPluginsTest {

    @Test
    fun `every plugin has a unique id and the fallback is github`() {
        val ids = GitHostPlugins.all.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
        assertEquals("github", GitHostPlugins.fallback.id)
        assertEquals("github", GitHostPlugins.byId("nope").id)
        assertEquals("gitee", GitHostPlugins.byId("Gitee").id)
    }

    @Test
    fun `enabled plugins keep catalogue order and ignore unknown ids`() {
        val enabled = GitHostPlugins.enabled(listOf("gitee", "nope", "github"))
        assertEquals(listOf("github", "gitee"), enabled.map { it.id })
        assertEquals(listOf("github"), GitHostPlugins.normaliseEnabled(emptyList()))
        assertEquals(
            listOf("gitee", "gitlab"),
            GitHostPlugins.normaliseEnabled(listOf("gitee", "gitee", "gitlab", "nope")),
        )
    }

    @Test
    fun `api bases follow each platform`() {
        assertEquals("https://api.github.com", GitHubPlugin.apiBase(""))
        assertEquals("https://ghe.example.com/api/v3", GitHubPlugin.apiBase("ghe.example.com"))
        assertEquals("https://gitlab.com/api/v4", GitLabPlugin.apiBase(""))
        assertEquals("https://git.example.com/api/v1", GiteaPlugin.apiBase("https://git.example.com/"))
        assertEquals("https://gitee.com/api/v5", GiteePlugin.apiBase(""))
        assertEquals("https://codeberg.org/api/v1", CodebergPlugin.apiBase(""))
    }

    @Test
    fun `default public urls match the platform raw endpoints`() {
        assertEquals(
            "https://raw.githubusercontent.com/you/images/main/uploads/a.webp",
            GitHubPlugin.publicUrl("", "you", "images", "main", "uploads/a.webp", ""),
        )
        assertEquals(
            "https://gitlab.com/you/images/-/raw/main/uploads/a.webp",
            GitLabPlugin.publicUrl("", "you", "images", "main", "uploads/a.webp", ""),
        )
        assertEquals(
            "https://git.example.com/you/images/raw/branch/main/uploads/a.webp",
            GiteaPlugin.publicUrl("git.example.com", "you", "images", "main", "uploads/a.webp", ""),
        )
        assertEquals(
            "https://gitee.com/you/images/raw/main/uploads/a.webp",
            GiteePlugin.publicUrl("", "you", "images", "main", "uploads/a.webp", ""),
        )
        assertEquals(
            "https://codeberg.org/you/images/raw/branch/main/uploads/a.webp",
            CodebergPlugin.publicUrl("", "you", "images", "main", "uploads/a.webp", ""),
        )
    }

    @Test
    fun `a public base with placeholders wins over the default`() {
        assertEquals(
            "https://cdn.jsdelivr.net/gh/you/images@main/uploads/a.webp",
            GitHubPlugin.publicUrl(
                "",
                "you",
                "images",
                "main",
                "uploads/a.webp",
                "https://cdn.jsdelivr.net/gh/{owner}/{repo}@{branch}",
            ),
        )
    }

    @Test
    fun `a public base containing the path placeholder replaces it verbatim`() {
        assertEquals(
            "https://img.example.com/uploads/a.webp",
            GitHubPlugin.publicUrl("", "you", "images", "main", "uploads/a.webp", "https://img.example.com/{path}"),
        )
    }

    @Test
    fun `jsdelivr base pins the branch`() {
        assertEquals("https://cdn.jsdelivr.net/gh/you/images@main", RemoteUrls.jsdelivrBase("you", "images", "main"))
    }

    @Test
    fun `host input is cleaned of scheme and path`() {
        assertEquals("git.example.com", RemoteUrls.cleanHost("https://git.example.com/"))
        assertEquals("git.example.com", RemoteUrls.cleanHost("http://git.example.com/sub/dir"))
        assertEquals("git.example.com:3000", RemoteUrls.cleanHost("https://git.example.com:3000/"))
        assertEquals("git.example.com", cleanHostInput("https://git.example.com/x"))
    }

    @Test
    fun `token pages point at the right settings screen`() {
        assertEquals(
            "https://github.com/settings/tokens/new?scopes=repo&description=PicUp",
            GitHubPlugin.tokenPageUrl(""),
        )
        assertEquals(
            "https://ghe.example.com/settings/tokens/new?scopes=repo&description=PicUp",
            GitHubPlugin.tokenPageUrl("ghe.example.com"),
        )
        assertEquals(
            "https://gitlab.example.com/-/user_settings/personal_access_tokens",
            GitLabPlugin.tokenPageUrl("https://gitlab.example.com"),
        )
        assertEquals(
            "https://git.example.com/user/settings/applications",
            GiteaPlugin.tokenPageUrl("git.example.com"),
        )
        assertEquals("https://gitee.com/profile/personal_access_tokens", GiteePlugin.tokenPageUrl(""))
        assertEquals("https://codeberg.org/user/settings/applications", CodebergPlugin.tokenPageUrl(""))
    }

    @Test
    fun `hosted-only plugins ignore a custom host`() {
        assertEquals("gitee.com", GiteePlugin.displayHost("elsewhere.org"))
        assertEquals("codeberg.org", CodebergPlugin.displayHost("elsewhere.org"))
        assertEquals("git.example.com", GiteaPlugin.displayHost("https://git.example.com/"))
        assertEquals("gitlab.com", GitLabPlugin.displayHost(""))
    }
}

class AppSettingsTest {

    private val account = AccountProfile(id = "acc", provider = "github", login = "you")

    private fun remote(id: String, accountId: String = "acc") = Remote(
        id = id,
        accountId = accountId,
        owner = "you",
        repo = "images",
        branch = "main",
    )

    @Test
    fun `an invalid active remote falls back to the first usable one`() {
        val settings = AppSettings(
            accounts = listOf(account),
            remotes = listOf(remote("r1"), remote("r2")),
            activeRemoteId = "gone",
        ).normalized()
        assertEquals("r1", settings.activeRemoteId)
        assertEquals("you/images", settings.activeRemote?.name)
    }

    @Test
    fun `remotes whose account disappeared are dropped`() {
        val settings = AppSettings(
            accounts = listOf(account),
            remotes = listOf(remote("r1"), remote("orphan", accountId = "missing")),
            activeRemoteId = "orphan",
        ).normalized()
        assertEquals(listOf("r1"), settings.remotes.map { it.id })
        assertEquals("r1", settings.activeRemoteId)
    }

    @Test
    fun `an active format must exist and be enabled`() {
        val formats = CopyFormatDefaults.presets().map { it.copy(enabled = false) }
        val settings = AppSettings(formats = formats, activeFormatId = "raw").normalized()
        assertEquals(true, settings.formats.any { it.enabled })
        assertEquals(true, settings.activeFormat()?.enabled)
    }

    @Test
    fun `quality and max edge are clamped to sane values`() {
        val settings = AppSettings(upload = UploadOptions(quality = 5, maxEdge = 40)).normalized()
        assertEquals(20, settings.upload.quality)
        assertEquals(512, settings.upload.maxEdge)
    }

    @Test
    fun `the original-format choice is kept and a missing size cap gets the default`() {
        val settings = AppSettings(upload = UploadOptions(format = OutputFormat.PRESERVE, maxEdge = 0)).normalized()
        assertEquals(OutputFormat.PRESERVE, settings.upload.format)
        assertEquals(2048, settings.upload.maxEdge)
    }

    @Test
    fun `quality presets map to their three stops`() {
        assertEquals(QualityPreset.ORIGINAL, QualityPreset.nearest(100))
        assertEquals(QualityPreset.BALANCED, QualityPreset.nearest(85))
        assertEquals(QualityPreset.LIGHT, QualityPreset.nearest(60))
        assertEquals(QualityPreset.BALANCED, UploadOptions(quality = 80).qualityPreset)
    }

    @Test
    fun `new upload options default to a safe configuration`() {
        val options = UploadOptions()
        assertEquals(OutputFormat.WEBP, options.format)
        assertEquals(true, options.stripExif)
        assertEquals(true, options.lowerCaseNames)
        assertEquals(QualityPreset.BALANCED, options.qualityPreset)
    }

    @Test
    fun `a blank path template is replaced by the default`() {
        val settings = AppSettings(upload = UploadOptions(pathTemplate = "   ")).normalized()
        assertEquals(DEFAULT_PATH_TEMPLATE, settings.upload.pathTemplate)
    }

    @Test
    fun `unknown json fields are ignored and missing fields keep defaults`() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val decoded = json.decodeFromString(
            AppSettings.serializer(),
            """{"theme":"DARK","newField":42,"remotes":[]}""",
        )
        assertEquals(ThemeChoice.DARK, decoded.theme)
        assertEquals(true, decoded.dynamicColor)
    }

    @Test
    fun `legacy provider enum names migrate to plugin ids`() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val raw = """
            {"accounts":[{"id":"a","provider":"GITHUB","login":"you"}],
             "remotes":[{"id":"r","accountId":"a","owner":"o","repo":"r"}]}
        """.trimIndent()
        val decoded = json.decodeFromString(AppSettings.serializer(), raw)
        assertEquals("github", decoded.normalized().accounts.single().provider)
    }

    @Test
    fun `enabled plugins always contain something known`() {
        assertEquals(listOf("github"), AppSettings().enabledPlugins)
        assertEquals(listOf("github"), AppSettings(enabledPlugins = listOf("nope")).normalized().enabledPlugins)
        assertEquals(
            listOf("gitee", "gitlab"),
            AppSettings(enabledPlugins = listOf("gitee", "gitee", "gitlab")).normalized().enabledPlugins,
        )
    }

    @Test
    fun `a settings document survives serialization unchanged`() {
        val original = AppSettings(
            accounts = listOf(account),
            remotes = listOf(remote("r1").copy(publicBase = "https://cdn.example.com")),
            activeRemoteId = "r1",
            upload = UploadOptions(format = OutputFormat.JPEG, quality = 70),
            theme = ThemeChoice.DARK,
        ).normalized()
        val text = AtomicJsonFile.lenientJson.encodeToString(AppSettings.serializer(), original)
        val restored = AtomicJsonFile.lenientJson.decodeFromString(AppSettings.serializer(), text)
        assertEquals(original, restored)
    }
}
class UploadKindsTest {

    @Test
    fun `a real image mime always uses the image pipeline`() {
        assertEquals(true, UploadKinds.isImage("image/png", "photo"))
        assertEquals(true, UploadKinds.isImage("image/gif", "whatever.txt"))
    }

    @Test
    fun `a non-image mime is opaque even with an image name`() {
        assertEquals(false, UploadKinds.isImage("text/plain", "photo.png"))
        assertEquals(false, UploadKinds.isImage("application/pdf", "scan.jpg"))
    }

    @Test
    fun `a missing mime falls back to the extension`() {
        assertEquals(true, UploadKinds.isImage("", "IMG_0001.JPG"))
        assertEquals(true, UploadKinds.isImage("", "sunset.webp"))
        assertEquals(false, UploadKinds.isImage("", "clipboard-20261117-0310.txt"))
        assertEquals(false, UploadKinds.isImage("", "notes"))
        assertEquals(false, UploadKinds.isImage("", "archive.tar.gz"))
    }
}
