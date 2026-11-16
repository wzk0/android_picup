package com.example.picup.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * One JSON document on disk, written whole and atomically with the previous
 * good copy kept alongside it.
 *
 * A plain `writeText` truncates first, so a crash mid-write loses both the old
 * and the new document. This sequence never leaves the live file half-written:
 *
 *   1. write everything to `<name>.tmp` and fsync it;
 *   2. copy the current live file to `<name>.bak`;
 *   3. rename the temp file over the live one.
 *
 * [read] prefers the live file and falls back to the backup. Callers serialize
 * their own read-modify-write cycles: the unit that must be atomic is the whole
 * cycle, not a single write.
 */
class AtomicJsonFile<T>(
    val file: File,
    private val json: Json,
    private val serializer: KSerializer<T>,
) {
    val backupFile: File = File(file.parentFile, file.name + ".bak")
    val tempFile: File = File(file.parentFile, file.name + ".tmp")

    fun read(): T? {
        decode(file)?.let { return it }
        return decode(backupFile)
    }

    private fun decode(source: File): T? {
        if (!source.isFile) return null
        return runCatching { json.decodeFromString(serializer, source.readText()) }.getOrNull()
    }

    fun write(value: T) {
        file.parentFile?.mkdirs()
        val bytes = json.encodeToString(serializer, value).toByteArray()
        FileOutputStream(tempFile).use { out ->
            out.write(bytes)
            out.flush()
            out.fd.sync()
        }
        if (file.isFile) {
            runCatching {
                Files.copy(file.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
        Files.move(
            tempFile.toPath(),
            file.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
    }

    fun deleteAll() {
        listOf(file, backupFile, tempFile).forEach { runCatching { it.delete() } }
    }

    companion object {
        val lenientJson: Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = true
        }
    }
}