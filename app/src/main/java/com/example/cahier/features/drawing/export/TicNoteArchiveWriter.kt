package com.example.cahier.features.drawing.export

import com.example.cahier.core.document.AssetManifestEntry
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.encodeToJsonElement
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class TicNoteArchiveAsset(
    val file: File,
    val archivePath: String
)

object TicNoteArchiveWriter {
    private val json = Json { encodeDefaults = true }

    fun manifestJson(title: String, assets: List<AssetManifestEntry> = emptyList()): String {
        val manifest = buildJsonObject {
            put("title", title)
            put("version", 3)
            put("assets_dir", "assets")
            put("ink_file", "ink/strokes.json")
            put("ink_summary_file", "ink.json")
            put("document_file", "document.json")
            putJsonArray("layout_preserving_exports") {
                add(".html")
                add(".md")
                add(".pdf")
            }
            put("asset_manifest", json.encodeToJsonElement(ListSerializer(AssetManifestEntry.serializer()), assets))
        }
        return manifest.toString()
    }

    fun inkJson(finalizedStrokeCount: Int): String {
        return """{"finalizedStrokeCount":$finalizedStrokeCount}"""
    }

    fun writeArchive(
        archiveFile: File,
        documentJson: String,
        inkStrokesJson: String,
        markdownFile: File,
        htmlFile: File,
        title: String,
        finalizedStrokeCount: Int,
        backgroundFile: File?,
        imageAssetFiles: List<File>,
        assetManifest: List<AssetManifestEntry> = emptyList(),
        archiveAssets: List<TicNoteArchiveAsset> = imageAssetFiles.map { TicNoteArchiveAsset(it, "assets/${it.name}") }
    ) {
        ZipOutputStream(FileOutputStream(archiveFile)).use { zip ->
            zip.putNextEntry(ZipEntry("document.json"))
            zip.write(documentJson.toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("strokes-count.txt"))
            zip.write(finalizedStrokeCount.toString().toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifestJson(title, assetManifest).toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("ink.json"))
            zip.write(inkJson(finalizedStrokeCount).toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("ink/strokes.json"))
            zip.write(inkStrokesJson.toByteArray())
            zip.closeEntry()

            if (markdownFile.exists()) {
                zip.putNextEntry(ZipEntry(markdownFile.name))
                zip.write(markdownFile.readBytes())
                zip.closeEntry()
            }
            if (htmlFile.exists()) {
                zip.putNextEntry(ZipEntry(htmlFile.name))
                zip.write(htmlFile.readBytes())
                zip.closeEntry()
            }
            backgroundFile?.takeIf { it.exists() }?.let { file ->
                zip.putNextEntry(ZipEntry("assets/background.png"))
                zip.write(file.readBytes())
                zip.closeEntry()
            }
            archiveAssets.forEach { asset ->
                if (asset.file.exists()) {
                    zip.putNextEntry(ZipEntry(asset.archivePath))
                    zip.write(asset.file.readBytes())
                    zip.closeEntry()
                }
            }
        }
    }
}
