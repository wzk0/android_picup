package com.example.picup.upload

import android.content.Context
import android.net.Uri
import com.example.picup.data.AppSettings
import com.example.picup.data.HistoryRepository
import com.example.picup.data.ImageProcessor
import com.example.picup.data.Naming
import com.example.picup.data.PathTemplate
import com.example.picup.data.Remote
import com.example.picup.git.plugins.GitHostPlugins
import com.example.picup.data.SettingsRepository
import com.example.picup.data.TemplateValues
import com.example.picup.data.TokenStore
import com.example.picup.data.UploadRecord
import com.example.picup.git.GitAuthException
import com.example.picup.git.GitClients
import com.example.picup.git.GitException
import com.example.picup.git.PutResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.io.IOException
import java.util.UUID

/**
 * Decides *what* gets committed and *where*, then hands the bytes to the
 * provider client. Deliberately sequential per upload: two parallel commits to
 * the same branch make hosts return `422 sha does not match`, and a photo set
 * is not worth a retry storm.
 *
 * The bytes go straight from the picker to the API. Nothing is copied into the
 * app's storage except the small history thumbnail, so a 50 MP photo costs no
 * permanent disk space.
 */
class UploadManager(
    private val context: Context,
    private val settings: SettingsRepository,
    private val tokens: TokenStore,
    private val history: HistoryRepository,
    private val images: ImageProcessor,
) {
    suspend fun upload(
        uri: Uri,
        snapshot: AppSettings,
        displayName: String,
        onStage: (String) -> Unit,
    ): UploadRecord {
        val remote = snapshot.activeRemote ?: throw GitException("还没有选择仓库，请先在设置里配置图床")
        val account = snapshot.accountOf(remote)
            ?: throw GitException("仓库所属的账号已被删除，请重新添加")
        val token = tokens.read(account.id)
            ?: throw GitAuthException("这个账号的 Token 已失效，请重新登录一次")

        onStage("处理图片")
        val mime = context.contentResolver.getType(uri).orEmpty()
        val prepared = if (mime.startsWith("image/") || mime.isBlank()) {
            images.prepare(uri, snapshot.upload)
        } else {
            images.prepareOpaque(uri, mime, displayName)
        }
        val (rawBase, _) = Naming.splitExtension(displayName)
        val base = Naming.slug(rawBase, snapshot.upload.lowerCaseNames)
        val hash = Naming.sha256Hex(prepared.bytes)
        val template = remote.pathTemplate.ifBlank { snapshot.upload.pathTemplate }
        val path = PathTemplate.render(
            template,
            TemplateValues(
                name = base,
                ext = prepared.ext,
                width = prepared.width,
                height = prepared.height,
                sizeBytes = prepared.bytes.size.toLong(),
                hash = hash.take(12),
                owner = remote.owner,
                repo = remote.repo,
                branch = remote.branch,
                provider = account.provider,
                random = PathTemplate.randomHex(16),
            ),
        )
        if (path.isBlank()) throw GitException("路径模板渲染为空，请检查设置里的路径模板")

        onStage("上传中")
        val client = GitClients.create(account.provider, account.host)
        val message = "Add $path"
        val result: PutResult = retryingOnce {
            client.putFile(token, remote, path, prepared.bytes, message)
        }

        val url = GitHostPlugins.byId(account.provider).publicUrl(
            host = account.host,
            owner = remote.owner,
            repo = remote.repo,
            branch = remote.branch,
            path = result.path,
            publicBase = remote.publicBase,
        )
        val record = UploadRecord(
            id = UUID.randomUUID().toString(),
            remoteId = remote.id,
            repoLabel = remote.name,
            url = url,
            path = result.path,
            fileName = result.path.substringAfterLast('/'),
            ext = prepared.ext,
            bytes = prepared.bytes.size.toLong(),
            width = prepared.width,
            height = prepared.height,
            uploadedAt = System.currentTimeMillis(),
            owner = remote.owner,
            repo = remote.repo,
            branch = remote.branch,
            provider = account.provider,
        )
        onStage("生成缩略图")
        images.writeThumbnail(history.thumbFile(record.id), prepared.bytes)
        history.savePreview(record.id, prepared.bytes)
        history.add(listOf(record))
        return record
    }

    /** Deletes the committed file. Returns false when it was already gone. */
    suspend fun deleteFromRemote(record: UploadRecord, snapshot: AppSettings): Boolean {
        val remote = snapshot.remotes.firstOrNull { it.id == record.remoteId }
            ?: throw GitException("这个仓库已从设置里删除，无法再操作远端文件")
        val account = snapshot.accountOf(remote) ?: throw GitException("账号已被删除")
        val token = tokens.read(account.id) ?: throw GitAuthException("Token 已失效，请重新登录")
        val client = GitClients.create(account.provider, account.host)
        return client.deleteFile(token, remote, record.path, "Remove ${record.path}")
    }

    /**
     * One retry for transport-level failures only. A 401 or a 422 is a decision,
     * not a hiccup; repeating it just delays the error the user needs to see.
     */
    private suspend fun <T> retryingOnce(block: suspend () -> T): T {
        try {
            return block()
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: GitAuthException) {
            throw error
        } catch (error: GitException) {
            throw error
        } catch (error: IOException) {
            delay(RETRY_DELAY_MS)
            return block()
        }
    }

    @Suppress("unused")
    private fun remoteLabel(remote: Remote): String = remote.name

    private companion object {
        const val RETRY_DELAY_MS = 900L
    }
}