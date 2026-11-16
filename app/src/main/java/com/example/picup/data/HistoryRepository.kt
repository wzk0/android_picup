package com.example.picup.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** One successful upload, kept locally so the URL can be copied again later. */
@Serializable
data class UploadRecord(
    val id: String,
    val remoteId: String,
    val repoLabel: String,
    val url: String,
    val path: String,
    val fileName: String,
    val ext: String,
    val bytes: Long,
    val width: Int = 0,
    val height: Int = 0,
    val uploadedAt: Long,
    val owner: String = "",
    val repo: String = "",
    val branch: String = "",
    val provider: String = "",
)

@Serializable
data class HistoryDocument(val entries: List<UploadRecord> = emptyList())

/**
 * Local upload history plus the thumbnails shown in the list.
 *
 * History is a convenience, not the source of truth — the files live in the
 * user's repository. It is therefore safe to trim it to [MAX_ENTRIES]; git is
 * unaffected.
 */
class HistoryRepository(context: Context) {
    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, "uploads.json")
    private val thumbnailDir = File(appContext.filesDir, "thumbs")
    private val previewDir = File(appContext.filesDir, "previews")
    private val store = AtomicJsonFile(file, AtomicJsonFile.lenientJson, HistoryDocument.serializer())
    private val mutex = Mutex()
    private val state = MutableStateFlow<List<UploadRecord>>(emptyList())
    private var loaded = false

    val entries: StateFlow<List<UploadRecord>> = state.asStateFlow()

    suspend fun load() = mutex.withLock { ensureLoaded() }

    suspend fun add(records: List<UploadRecord>) {
        if (records.isEmpty()) return
        mutex.withLock {
            ensureLoaded()
            val merged = (records + state.value)
                .distinctBy { it.id }
                .sortedByDescending { it.uploadedAt }
            write(merged)
        }
    }

    suspend fun remove(recordId: String) {
        mutex.withLock {
            ensureLoaded()
            write(state.value.filterNot { it.id == recordId })
            withContext(Dispatchers.IO) {
                thumbFile(recordId).delete()
                previewFile(recordId).delete()
            }
        }
    }

    suspend fun clear() {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                thumbnailDir.listFiles()?.forEach { it.delete() }
                previewDir.listFiles()?.forEach { it.delete() }
                store.deleteAll()
            }
            state.value = emptyList()
        }
    }

    suspend fun update(record: UploadRecord) {
        mutex.withLock {
            ensureLoaded()
            write(state.value.map { if (it.id == record.id) record else it })
        }
    }

    fun thumbFile(recordId: String): File = File(thumbnailDir, "$recordId.webp")

    /**
     * The exact bytes that were uploaded. Keeping them lets the viewer show the
     * full-size image offline and without a second download; older records have
     * no file here and fall back to the network.
     */
    fun previewFile(recordId: String): File = File(previewDir, "$recordId.img")

    suspend fun savePreview(recordId: String, bytes: ByteArray) {
        withContext(Dispatchers.IO) {
            previewDir.mkdirs()
            runCatching { previewFile(recordId).writeBytes(bytes) }
            // Bounded cache: the oldest previews are dropped first. Dropping a
            // preview never touches the record or the remote file.
            val files = previewDir.listFiles()?.sortedBy { it.lastModified() } ?: return@withContext
            if (files.size > MAX_PREVIEWS) {
                files.take(files.size - MAX_PREVIEWS).forEach { runCatching { it.delete() } }
            }
        }
    }

    /**
     * Reads the document on first use. An upload that finishes before the
     * screen's initial load would otherwise write a one-entry history over the
     * user's whole list.
     */
    private suspend fun ensureLoaded() {
        if (loaded) return
        state.value = withContext(Dispatchers.IO) {
            store.read()?.entries.orEmpty().sortedByDescending { it.uploadedAt }
        }
        loaded = true
    }

    private suspend fun write(entries: List<UploadRecord>) {
        val trimmed = entries.take(MAX_ENTRIES)
        val removed = entries.drop(MAX_ENTRIES)
        withContext(Dispatchers.IO) {
            store.write(HistoryDocument(trimmed))
            removed.forEach {
                thumbFile(it.id).delete()
                previewFile(it.id).delete()
            }
        }
        state.value = trimmed
    }

    private companion object {
        const val MAX_ENTRIES = 500
        const val MAX_PREVIEWS = 150
    }
}