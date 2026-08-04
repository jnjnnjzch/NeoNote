package com.neonote.engine

import com.neonote.model.BlockImage
import com.neonote.model.BlockNode
import com.neonote.model.CanvasObject
import com.neonote.model.FloatingImage
import com.neonote.model.InlineImage
import com.neonote.model.InlineNode
import com.neonote.model.NeoNoteDocument
import com.neonote.model.ParagraphNode
import com.neonote.model.RichContent
import com.neonote.model.RichContentBox
import com.neonote.model.TableCell
import com.neonote.model.TableNode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class NeoNoteArchiveManifest(
    val formatVersion: Int = CurrentArchiveFormatVersion,
    val documentEntry: String = DocumentEntry,
    val assets: List<ArchiveAsset> = emptyList(),
)

@Serializable
private data class ArchiveAsset(
    val originalId: String,
    val entry: String,
    val mediaType: String,
    val fileName: String? = null,
)

public data class ImportedNeoNoteArchive(
    val document: NeoNoteDocument,
    val importedAssetCount: Int,
)

/** Portable .neonote archive containing JSON plus every referenced binary asset. */
public class NeoNoteArchiveCodec(
    private val assetStore: AssetStore,
    private val json: Json = DefaultDocumentJson,
) {
    public suspend fun export(document: NeoNoteDocument): ByteArray {
        val references = document.referencedAssetIds().mapNotNull { id -> assetStore.get(id) }
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            val assets = references.mapIndexed { index, reference ->
                val entry = "assets/${index.toString().padStart(4, '0')}-${reference.id.safeArchiveName()}"
                ArchiveAsset(reference.id, entry, reference.mediaType, reference.fileName)
            }
            writeEntry(zip, ManifestEntry, json.encodeToString(NeoNoteArchiveManifest.serializer(), NeoNoteArchiveManifest(assets = assets)).encodeToByteArray())
            writeEntry(zip, DocumentEntry, json.encodeToString(NeoNoteDocument.serializer(), document).encodeToByteArray())
            assets.forEach { asset ->
                val reference = references.first { it.id == asset.originalId }
                val path = requireNotNull(reference.uri) { "Asset ${reference.id} has no local bytes" }
                val bytes = java.io.File(path).readBytesLimited(MaximumAssetBytes)
                writeEntry(zip, asset.entry, bytes)
            }
        }
        return output.toByteArray()
    }

    public suspend fun import(
        archiveBytes: ByteArray,
        replacementDocumentId: String = "document-${UUID.randomUUID()}",
    ): ImportedNeoNoteArchive {
        require(archiveBytes.size <= MaximumArchiveBytes) { "Archive exceeds ${MaximumArchiveBytes / 1024 / 1024} MB" }
        val entries = readEntries(archiveBytes)
        val manifestPayload = requireNotNull(entries[ManifestEntry]) { "Missing archive manifest" }
        val manifest = json.decodeFromString(NeoNoteArchiveManifest.serializer(), manifestPayload.decodeToString())
        require(manifest.formatVersion in 1..CurrentArchiveFormatVersion) { "Unsupported archive format ${manifest.formatVersion}" }
        val documentPayload = requireNotNull(entries[manifest.documentEntry]) { "Missing document JSON" }
        val decoded = json.decodeFromString(NeoNoteDocument.serializer(), documentPayload.decodeToString())
        val idMap = linkedMapOf<String, String>()
        manifest.assets.forEach { asset ->
            require(asset.entry.startsWith("assets/") && !asset.entry.contains("..")) { "Unsafe asset entry" }
            val bytes = requireNotNull(entries[asset.entry]) { "Missing asset ${asset.originalId}" }
            val imported = assetStore.put(AssetDraft(asset.mediaType, bytes, asset.fileName))
            idMap[asset.originalId] = imported.id
        }
        val remapped = decoded.remapAssetIds(idMap).copy(
            id = replacementDocumentId,
            revision = 0L,
            createdAtEpochMillis = System.currentTimeMillis(),
            updatedAtEpochMillis = System.currentTimeMillis(),
        )
        return ImportedNeoNoteArchive(remapped, idMap.size)
    }

    private fun readEntries(bytes: ByteArray): Map<String, ByteArray> {
        val result = linkedMapOf<String, ByteArray>()
        var total = 0
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(!entry.isDirectory) { "Directory entries are not supported" }
                require(entry.name == ManifestEntry || entry.name == DocumentEntry || entry.name.startsWith("assets/")) {
                    "Unexpected archive entry ${entry.name}"
                }
                require(!entry.name.contains("..") && !entry.name.startsWith('/')) { "Unsafe archive entry" }
                require(result.size < MaximumEntries) { "Archive has too many entries" }
                val limit = if (entry.name.startsWith("assets/")) MaximumAssetBytes else MaximumMetadataBytes
                val payload = zip.readBytesLimited(limit)
                total += payload.size
                require(total <= MaximumArchiveBytes) { "Expanded archive exceeds size limit" }
                require(result.put(entry.name, payload) == null) { "Duplicate archive entry ${entry.name}" }
                zip.closeEntry()
            }
        }
        return result
    }

    private fun writeEntry(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
    }
}

public fun NeoNoteDocument.referencedAssetIds(): Set<String> = buildSet {
    pages.forEach { page ->
        page.canvas.objects.forEach { objectValue ->
            when (objectValue) {
                is FloatingImage -> add(objectValue.assetId)
                is RichContentBox -> addAll(objectValue.content.referencedAssetIds())
            }
        }
    }
}

private fun RichContent.referencedAssetIds(): Set<String> = buildSet {
    blocks.forEach { block ->
        when (block) {
            is BlockImage -> add(block.assetId)
            is ParagraphNode -> block.inlines.forEach { inline -> if (inline is InlineImage) add(inline.assetId) }
            is TableNode -> block.rows.flatten().forEach { addAll(it.content.referencedAssetIds()) }
            else -> Unit
        }
    }
}

private fun NeoNoteDocument.remapAssetIds(mapping: Map<String, String>): NeoNoteDocument = copy(
    pages = pages.map { page -> page.copy(
        canvas = page.canvas.copy(objects = page.canvas.objects.map { it.remapAssetIds(mapping) }),
    ) },
)

private fun CanvasObject.remapAssetIds(mapping: Map<String, String>): CanvasObject = when (this) {
    is FloatingImage -> copy(assetId = mapping[assetId] ?: assetId)
    is RichContentBox -> copy(content = content.remapAssetIds(mapping), isFocused = false)
}

private fun RichContent.remapAssetIds(mapping: Map<String, String>): RichContent = copy(
    blocks = blocks.map { it.remapAssetIds(mapping) },
)

private fun BlockNode.remapAssetIds(mapping: Map<String, String>): BlockNode = when (this) {
    is BlockImage -> copy(assetId = mapping[assetId] ?: assetId)
    is ParagraphNode -> copy(inlines = inlines.map { it.remapAssetIds(mapping) })
    is TableNode -> copy(rows = rows.map { row -> row.map { cell -> cell.remapAssetIds(mapping) } })
    else -> this
}

private fun InlineNode.remapAssetIds(mapping: Map<String, String>): InlineNode = when (this) {
    is InlineImage -> copy(assetId = mapping[assetId] ?: assetId)
    else -> this
}

private fun TableCell.remapAssetIds(mapping: Map<String, String>): TableCell =
    copy(content = content.remapAssetIds(mapping))

private fun InputStream.readBytesLimited(limit: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        total += count
        require(total <= limit) { "Archive entry exceeds size limit" }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

private fun java.io.File.readBytesLimited(limit: Int): ByteArray = inputStream().use { it.readBytesLimited(limit) }
private fun String.safeArchiveName(): String = map { if (it.isLetterOrDigit() || it in "-_.") it else '_' }.joinToString("")

private const val CurrentArchiveFormatVersion = 1
private const val ManifestEntry = "manifest.json"
private const val DocumentEntry = "document.json"
private const val MaximumArchiveBytes = 256 * 1024 * 1024
private const val MaximumAssetBytes = 64 * 1024 * 1024
private const val MaximumMetadataBytes = 8 * 1024 * 1024
private const val MaximumEntries = 2_048
