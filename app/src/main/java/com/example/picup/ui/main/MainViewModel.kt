package com.example.picup.ui.main

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.picup.AppContainer
import com.example.picup.data.AppSettings
import com.example.picup.data.CopyFormat
import com.example.picup.data.CopyFormatRenderer
import com.example.picup.data.HistoryLayout
import com.example.picup.data.Naming
import com.example.picup.data.PathTemplate
import com.example.picup.data.LinkValues
import com.example.picup.data.UploadRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

enum class UploadStatus { QUEUED, RUNNING, DONE, FAILED }

data class UploadTask(
    val id: String,
    val uri: Uri,
    val displayName: String,
    val status: UploadStatus = UploadStatus.QUEUED,
    val stage: String = "",
    val error: String? = null,
    val record: UploadRecord? = null,
)

/**
 * Upload queue plus the two actions history rows perform.
 *
 * Uploads run one at a time behind [uploadLock]. Parallel commits to the same
 * branch produce non-fast-forward conflicts on every host, and a user who
 * selects twelve photos wants a queue they can watch, not twelve simultaneous
 * spinners competing for bandwidth.
 */
class MainViewModel(private val app: AppContainer) : ViewModel() {

    val settings: StateFlow<AppSettings> = app.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val history: StateFlow<List<UploadRecord>> = app.history.entries

    private val _tasks = MutableStateFlow<List<UploadTask>>(emptyList())
    val tasks: StateFlow<List<UploadTask>> = _tasks.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** Records whose remote file is being deleted right now. */
    private val _deleting = MutableStateFlow<Set<String>>(emptySet())
    val deleting: StateFlow<Set<String>> = _deleting.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val uploadLock = Mutex()

    init {
        viewModelScope.launch { app.history.load() }
    }

    fun enqueue(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val queued = uris.map { uri ->
            UploadTask(
                id = UUID.randomUUID().toString(),
                uri = uri,
                displayName = app.displayNames.resolve(uri),
            )
        }
        _tasks.update { it + queued }
        queued.forEach { task ->
            viewModelScope.launch {
                uploadLock.withLock { runTask(task) }
            }
        }
    }

    /** Image clips: copy the bytes out immediately, the grant may not outlive the queue. */
    fun enqueueClipboardImage(uri: Uri) {
        viewModelScope.launch {
            val payload = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = app.context.contentResolver.openInputStream(uri)
                        ?.use { it.readBytes() }
                        ?: return@runCatching null
                    val name = app.displayNames.resolve(uri)
                    name to bytes
                }.getOrNull()
            }
            if (payload == null) {
                _messages.emit("无法读取剪贴板里的图片")
                return@launch
            }
            val (name, bytes) = payload
            val file = withContext(Dispatchers.IO) { app.writeCacheFile("clipboard", name, bytes) }
            enqueue(listOf(Uri.fromFile(file)))
        }
    }

    /** Text clips: uploaded as a plain-text file under the name the user picks. */
    fun enqueueClipboardText(text: String, fileName: String) {
        viewModelScope.launch {
            if (text.length > MAX_CLIPBOARD_TEXT) {
                _messages.emit("剪贴板文本过大（上限 ${MAX_CLIPBOARD_TEXT / 1024} KB）")
                return@launch
            }
            val file = withContext(Dispatchers.IO) {
                app.writeCacheFile("clipboard", ensureTxt(fileName), text.toByteArray())
            }
            enqueue(listOf(Uri.fromFile(file)))
        }
    }

    private fun ensureTxt(fileName: String): String {
        val safe = Naming.slug(fileName.substringBeforeLast('.', fileName), lowerCase = false)
        return if (fileName.contains('.')) "$safe.${fileName.substringAfterLast('.').lowercase()}" else "$safe.txt"
    }

    fun retry(taskId: String) {
        val task = _tasks.value.firstOrNull { it.id == taskId } ?: return
        if (task.status !in setOf(UploadStatus.FAILED, UploadStatus.DONE)) return
        val fresh = task.copy(status = UploadStatus.QUEUED, stage = "", error = null, record = null)
        _tasks.update { list -> list.map { if (it.id == taskId) fresh else it } }
        viewModelScope.launch { uploadLock.withLock { runTask(fresh) } }
    }

    fun removeTask(taskId: String) {
        _tasks.update { list -> list.filterNot { it.id == taskId } }
    }

    fun clearFinished() {
        _tasks.update { list -> list.filter { it.status == UploadStatus.QUEUED || it.status == UploadStatus.RUNNING } }
    }

    fun notify(message: String) {
        _messages.tryEmit(message)
    }

    fun renderCopy(record: UploadRecord, format: CopyFormat): String {
        val values = LinkValues(
            url = record.url,
            path = record.path,
            fileName = record.fileName,
            ext = record.ext,
            repo = if (record.owner.isBlank() && record.repo.isBlank()) record.repoLabel else "${record.owner}/${record.repo}",
            branch = record.branch,
            sizeBytes = record.bytes,
            width = record.width,
            height = record.height,
            uploadedAtMillis = record.uploadedAt,
        )
        return CopyFormatRenderer.render(format.template, values)
    }

    fun deleteRemoteFile(record: UploadRecord) {
        viewModelScope.launch {
            _deleting.update { it + record.id }
            _busy.value = true
            try {
                val existed = app.uploads.deleteFromRemote(record, app.settings.current())
                app.history.remove(record.id)
                _messages.emit(
                    if (existed) "已从仓库删除 ${record.fileName}" else "远端已不存在，已从历史移除",
                )
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                _messages.emit(error.message ?: "删除失败")
            } finally {
                _busy.value = false
                _deleting.update { it - record.id }
            }
        }
    }

    fun setHistoryLayout(layout: HistoryLayout) {
        viewModelScope.launch { app.settings.update { it.copy(historyLayout = layout) } }
    }

    /** Deletes several records in one pass; failures are counted, not fatal. */
    fun deleteRemoteFiles(records: List<UploadRecord>) {
        if (records.isEmpty()) return
        viewModelScope.launch {
            _busy.value = true
            _deleting.update { current -> current + records.map { it.id } }
            var removed = 0
            var failed = 0
            try {
                val snapshot = app.settings.current()
                records.forEach { record ->
                    try {
                        app.uploads.deleteFromRemote(record, snapshot)
                        app.history.remove(record.id)
                        removed++
                    } catch (cancel: CancellationException) {
                        throw cancel
                    } catch (error: Exception) {
                        failed++
                    } finally {
                        _deleting.update { current -> current - record.id }
                    }
                }
            } finally {
                _busy.value = false
            }
            _messages.emit(
                if (failed == 0) "已删除 $removed 个文件" else "已删除 $removed 个，$failed 个失败",
            )
        }
    }

    fun removeFromHistory(record: UploadRecord) {
        viewModelScope.launch {
            app.history.remove(record.id)
            _messages.emit("已从历史移除 ${record.fileName}")
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            app.history.clear()
            _messages.emit("历史已清空，仓库里的图片不受影响")
        }
    }

    private suspend fun runTask(task: UploadTask) {
        updateTask(task.id) { it.copy(status = UploadStatus.RUNNING, stage = "准备中", error = null) }
        try {
            val snapshot = app.settings.current()
            val record = app.uploads.upload(task.uri, snapshot, task.displayName) { stage ->
                updateTask(task.id) { it.copy(stage = stage) }
            }
            updateTask(task.id) {
                it.copy(status = UploadStatus.DONE, stage = "", record = record, error = null)
            }
        } catch (cancel: CancellationException) {
            updateTask(task.id) { it.copy(status = UploadStatus.FAILED, error = "已取消") }
            throw cancel
        } catch (error: Exception) {
            updateTask(task.id) {
                it.copy(status = UploadStatus.FAILED, stage = "", error = error.message ?: "上传失败")
            }
            _messages.emit(error.message ?: "${task.displayName} 上传失败")
        } finally {
            scheduleFinishedCleanup()
        }
    }

    /**
     * Finished rows are only a progress report. A moment after the queue drains
     * they leave, and the page animates over to the recent-uploads list — which
     * is where the copy chips live. Failed rows stay for a retry.
     */
    private fun scheduleFinishedCleanup() {
        viewModelScope.launch {
            delay(FINISHED_CLEANUP_MS)
            if (_tasks.value.none { it.status == UploadStatus.QUEUED || it.status == UploadStatus.RUNNING }) {
                _tasks.update { list -> list.filterNot { it.status == UploadStatus.DONE } }
            }
        }
    }

    private fun updateTask(id: String, transform: (UploadTask) -> UploadTask) {
        _tasks.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }

    companion object {
        private const val FINISHED_CLEANUP_MS = 260L
        private const val MAX_CLIPBOARD_TEXT = 512 * 1024

        fun factory(container: AppContainer): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(container) as T
        }
    }
}