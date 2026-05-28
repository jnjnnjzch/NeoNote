package com.example.cahier.features.drawing.export

import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object TicNoteArchiveWriter {
    fun manifestJson(title: String): String {
        return """{"title":"$title","version":2,"assets_dir":"assets","ink_file":"ink.json","document_file":"document.json","layout_preserving_exports":[".html",".md",".pdf"]}"""
    }

    fun inkJson(finalizedStrokeCount: Int): String {
        return """{"finalizedStrokeCount":$finalizedStrokeCount}"""
    }

    fun writeArchive(
        archiveFile: File,
        documentJson: String,
        markdownFile: File,
        htmlFile: File,
        title: String,
        finalizedStrokeCount: Int,
        backgroundFile: File?,
        imageAssetFiles: List<File>
    ) {
        ZipOutputStream(FileOutputStream(archiveFile)).use { zip ->
            zip.putNextEntry(ZipEntry("document.json"))
            zip.write(documentJson.toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("strokes-count.txt"))
            zip.write(finalizedStrokeCount.toString().toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifestJson(title).toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("ink.json"))
            zip.write(inkJson(finalizedStrokeCount).toByteArray())
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
            imageAssetFiles.forEach { file ->
                if (file.exists()) {
                    zip.putNextEntry(ZipEntry("assets/${file.name}"))
                    zip.write(file.readBytes())
                    zip.closeEntry()
                }
            }
        }
    }
}
