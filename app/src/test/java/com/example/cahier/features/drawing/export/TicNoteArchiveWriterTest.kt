package com.example.cahier.features.drawing.export

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory
import java.util.zip.ZipFile

class TicNoteArchiveWriterTest {
    @Test
    fun writeArchive_contains_required_entries() {
        val root = createTempDirectory("ticnote-test").toFile()
        val md = File(root, "note.md").apply { writeText("# note") }
        val html = File(root, "note.html").apply { writeText("<html/>") }
        val bg = File(root, "background.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val asset = File(root, "img.png").apply { writeBytes(byteArrayOf(9, 8, 7)) }
        val out = File(root, "note.ticnote")

        TicNoteArchiveWriter.writeArchive(
            archiveFile = out,
            documentJson = """{"version":1}""",
            inkStrokesJson = """["stroke-a","stroke-b"]""",
            markdownFile = md,
            htmlFile = html,
            title = "Neo",
            finalizedStrokeCount = 42,
            backgroundFile = bg,
            imageAssetFiles = listOf(asset)
        )

        ZipFile(out).use { zip ->
            val names = zip.entries().asSequence().map { it.name }.toSet()
            assertTrue("document.json missing", "document.json" in names)
            assertTrue("manifest.json missing", "manifest.json" in names)
            assertTrue("ink.json missing", "ink.json" in names)
            assertTrue("ink/strokes.json missing", "ink/strokes.json" in names)
            assertTrue("assets/background.png missing", "assets/background.png" in names)
            assertTrue("assets/img.png missing", "assets/img.png" in names)
        }
    }
}
