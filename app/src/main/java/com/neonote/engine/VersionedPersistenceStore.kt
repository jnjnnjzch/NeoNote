package com.neonote.engine

import com.neonote.model.NeoNoteDocument
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.serialization.json.Json

/**
 * Crash-tolerant persistence wrapper retaining a bounded set of known-good snapshots.
 *
 * The active JSON remains compatible with [JsonFilePersistenceStore]. Before a new active
 * file is committed the previous active payload is copied into `.backups/<documentId>`.
 * Loading falls back to the newest decodable snapshot when the active file is corrupt.
 */
public class VersionedPersistenceStore(
    private val documentsDirectory: File,
    private val json: Json = DefaultDocumentJson,
    private val maximumBackups: Int = 5,
) : PersistenceStore {
    private val delegate = JsonFilePersistenceStore(documentsDirectory, json)
    private val backupsDirectory = documentsDirectory.resolve(".backups")

    override suspend fun save(document: NeoNoteDocument): PersistenceResult.Saved {
        val active = documentFile(document.id)
        if (active.isFile) createBackup(document.id, active)
        val saved = delegate.save(document)
        pruneBackups(document.id)
        return saved
    }

    override suspend fun load(documentId: String): PersistenceResult.Loaded {
        val activeResult = runCatching { delegate.load(documentId) }
        activeResult.getOrNull()?.let { loaded ->
            if (loaded.document != null) return loaded
        }

        val recovered = backupFiles(documentId).firstNotNullOfOrNull { backup ->
            runCatching {
                val document = json.decodeFromString(NeoNoteDocument.serializer(), backup.readText())
                PersistenceResult.Loaded(
                    document = document,
                    diagnostics = PersistenceDiagnostics(
                        documentId = document.id,
                        fileSizeBytes = backup.length(),
                        pageCount = document.pages.size,
                    ),
                )
            }.getOrNull()
        }
        if (recovered != null) {
            // Restore a known-good active copy so the next launch does not repeat recovery.
            val destination = documentFile(documentId)
            destination.parentFile?.mkdirs()
            val source = backupFiles(documentId).firstOrNull { backup ->
                runCatching {
                    json.decodeFromString(NeoNoteDocument.serializer(), backup.readText()).id == recovered.document?.id
                }.getOrDefault(false)
            }
            if (source != null) source.copyAtomically(destination)
            return recovered
        }
        return activeResult.getOrElse { PersistenceResult.Loaded(document = null) }
    }

    public fun deleteBackups(documentId: String) {
        backupDirectory(documentId).deleteRecursively()
    }

    public fun allBackupFiles(): List<File> = backupsDirectory.walkTopDown()
        .filter { it.isFile && it.extension.equals("json", ignoreCase = true) }
        .toList()

    private fun createBackup(documentId: String, active: File) {
        val directory = backupDirectory(documentId).also(File::mkdirs)
        val destination = directory.resolve("${System.currentTimeMillis()}-${active.length()}.json")
        active.copyAtomically(destination)
    }

    private fun pruneBackups(documentId: String) {
        backupFiles(documentId).drop(maximumBackups.coerceAtLeast(1)).forEach(File::delete)
    }

    private fun backupFiles(documentId: String): List<File> = backupDirectory(documentId)
        .listFiles { file -> file.isFile && file.extension.equals("json", ignoreCase = true) }
        .orEmpty()
        .sortedByDescending(File::lastModified)

    private fun backupDirectory(documentId: String): File = backupsDirectory.resolve(documentId.safeName())
    private fun documentFile(documentId: String): File = documentsDirectory.resolve("${documentId.safeName()}.json")
}

private fun File.copyAtomically(destination: File) {
    destination.parentFile?.mkdirs()
    val temporary = requireNotNull(destination.parentFile).resolve("${destination.name}.tmp")
    copyTo(temporary, overwrite = true)
    try {
        Files.move(
            temporary.toPath(),
            destination.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    } catch (_: Exception) {
        Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
    } finally {
        temporary.delete()
    }
}

internal fun String.safeName(): String = map { char ->
    if (char.isLetterOrDigit() || char in "-_.") char else '_'
}.joinToString("").ifBlank { "document" }
