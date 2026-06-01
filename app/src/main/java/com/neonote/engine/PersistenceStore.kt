package com.neonote.engine

import com.neonote.model.NeoNoteDocument
import java.io.File
import java.util.Locale
import kotlinx.serialization.json.Json

/**
 * Persistence boundary for saving and loading documents.
 *
 * Sync engines, cloud backends, export formats, and conflict resolution are intentionally out of scope.
 */
public interface PersistenceStore {
    public suspend fun save(document: NeoNoteDocument): PersistenceResult.Saved
    public suspend fun load(documentId: String): PersistenceResult.Loaded
}

/**
 * Minimal local JSON implementation of [PersistenceStore].
 *
 * The JSON payload serializes document-space coordinates from the document model directly. It does not use,
 * derive, or persist viewport pan/zoom as a replacement for canvas object or ink positions.
 */
public class JsonFilePersistenceStore(
    private val documentsDirectory: File,
    private val json: Json = DefaultDocumentJson,
) : PersistenceStore {
    override suspend fun save(document: NeoNoteDocument): PersistenceResult.Saved {
        documentsDirectory.mkdirs()
        val file = documentFile(document.id)
        val encodeResult = timed { json.encodeToString(NeoNoteDocument.serializer(), document) }
        val writeTimeMillis = elapsedMillis { file.writeText(encodeResult.value) }
        val inkCounts = document.inkCounts()
        return PersistenceResult.Saved(
            documentId = document.id,
            revision = document.revision,
            diagnostics = PersistenceDiagnostics(
                documentId = document.id,
                fileSizeBytes = file.length(),
                pageCount = document.pages.size,
                strokeCount = inkCounts.strokeCount,
                pointCount = inkCounts.pointCount,
                encodeTimeMillis = encodeResult.elapsedMillis,
                writeTimeMillis = writeTimeMillis,
            ),
        )
    }

    override suspend fun load(documentId: String): PersistenceResult.Loaded {
        val file = documentFile(documentId)
        if (!file.exists()) return PersistenceResult.Loaded(document = null)
        val readResult = timed { file.readText() }
        val decodeResult = timed { json.decodeFromString(NeoNoteDocument.serializer(), readResult.value) }
        val document = decodeResult.value
        val inkCounts = document.inkCounts()
        return PersistenceResult.Loaded(
            document = document,
            diagnostics = PersistenceDiagnostics(
                documentId = document.id,
                fileSizeBytes = file.length(),
                pageCount = document.pages.size,
                strokeCount = inkCounts.strokeCount,
                pointCount = inkCounts.pointCount,
                readTimeMillis = readResult.elapsedMillis,
                decodeTimeMillis = decodeResult.elapsedMillis,
            ),
        )
    }

    private fun documentFile(documentId: String): File = File(documentsDirectory, "${documentId.toSafeFileName()}.json")

    private fun String.toSafeFileName(): String = map { char ->
        when {
            char.isLetterOrDigit() || char == '-' || char == '_' || char == '.' -> char
            else -> '_'
        }
    }.joinToString(separator = "").ifBlank { "document" }
}

/**
 * Canonical whole-document JSON format. Model constructor defaults remain the source of truth for
 * fields omitted by compact encoding, so older pretty-printed files with explicit defaults still decode.
 */
public val DefaultDocumentJson: Json = Json {
    classDiscriminator = "type"
    encodeDefaults = false
    prettyPrint = false
}

public data class PersistenceDiagnostics(
    val documentId: String,
    val fileSizeBytes: Long = 0L,
    val pageCount: Int = 0,
    val strokeCount: Int = 0,
    val pointCount: Int = 0,
    val encodeTimeMillis: Double? = null,
    val writeTimeMillis: Double? = null,
    val readTimeMillis: Double? = null,
    val decodeTimeMillis: Double? = null,
    val cacheRebuildTimeMillis: Double? = null,
) {
    public fun toDebugSummary(): String {
        val timings = listOfNotNull(
            encodeTimeMillis?.let { "encode=${it.formatMillis()}ms" },
            writeTimeMillis?.let { "write=${it.formatMillis()}ms" },
            readTimeMillis?.let { "read=${it.formatMillis()}ms" },
            decodeTimeMillis?.let { "decode=${it.formatMillis()}ms" },
            cacheRebuildTimeMillis?.let { "cache=${it.formatMillis()}ms" },
        ).joinToString(separator = ", ")
        return "doc=$documentId, file=${fileSizeBytes}B, pages=$pageCount, strokes=$strokeCount, points=$pointCount" +
            if (timings.isBlank()) "" else ", $timings"
    }
}

public sealed interface PersistenceResult {
    public data class Saved(
        val documentId: String,
        val revision: Long,
        val diagnostics: PersistenceDiagnostics? = null,
    ) : PersistenceResult

    public data class Loaded(
        val document: NeoNoteDocument?,
        val diagnostics: PersistenceDiagnostics? = null,
    ) : PersistenceResult
}

private data class InkCounts(val strokeCount: Int, val pointCount: Int)

private data class TimedResult<T>(val value: T, val elapsedMillis: Double)

private fun NeoNoteDocument.inkCounts(): InkCounts {
    var strokeCount = 0
    var pointCount = 0
    pages.forEach { page ->
        page.canvas.inkLayer.strokes.forEach { stroke ->
            strokeCount += 1
            pointCount += stroke.points.size
        }
    }
    return InkCounts(strokeCount = strokeCount, pointCount = pointCount)
}

private fun <T> timed(block: () -> T): TimedResult<T> {
    val startNanos = System.nanoTime()
    val value = block()
    return TimedResult(value = value, elapsedMillis = nanosToMillis(System.nanoTime() - startNanos))
}

private fun elapsedMillis(block: () -> Unit): Double {
    val startNanos = System.nanoTime()
    block()
    return nanosToMillis(System.nanoTime() - startNanos)
}

private fun nanosToMillis(nanos: Long): Double = nanos / 1_000_000.0

private fun Double.formatMillis(): String = String.format(Locale.US, "%.2f", this)
