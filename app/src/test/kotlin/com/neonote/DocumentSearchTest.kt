package com.neonote

import com.neonote.engine.FileDocumentLibrary
import com.neonote.engine.FileDocumentSearchIndex
import com.neonote.model.BlockFormula
import com.neonote.model.BlockImage
import com.neonote.model.InfiniteCanvas
import com.neonote.model.InlineText
import com.neonote.model.NeoNoteDocument
import com.neonote.model.NotePage
import com.neonote.model.NoteSection
import com.neonote.model.ParagraphNode
import com.neonote.model.RichContent
import com.neonote.model.RichContentBox
import com.neonote.model.TableCell
import com.neonote.model.TableNode
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DocumentSearchTest {
    @Test
    fun `search finds titles formulas image captions and deeply nested table text`() = runBlocking {
        val root = Files.createTempDirectory("neonote-search").toFile()
        try {
            val document = NeoNoteDocument(
                id = "doc",
                title = "Decision research",
                assetStoreId = "assets",
                sections = listOf(NoteSection("section", "Monkey experiments")),
                pages = listOf(NotePage(
                    id = "page",
                    title = "Counterfactual learning",
                    sectionId = "section",
                    canvas = InfiniteCanvas(objects = listOf(RichContentBox(
                        id = "box",
                        content = RichContent(listOf(
                            ParagraphNode(listOf(InlineText("policy update after bad luck"))),
                            BlockFormula("D_{post}=D_{pre}+L"),
                            BlockImage("asset", caption = "neural population geometry"),
                            TableNode(rows = listOf(listOf(TableCell(content = RichContent(listOf(
                                TableNode(rows = listOf(listOf(TableCell(content = RichContent(listOf(
                                    ParagraphNode(listOf(InlineText("deeply nested evidence"))),
                                )))))),
                            )))))),
                        )),
                    ))),
                )),
            )
            FileDocumentLibrary(root).save(document)
            val search = FileDocumentSearchIndex(root)

            assertEquals("page", search.search("counterfactual").single().pageId)
            assertEquals("page", search.search("D_post").single().pageId)
            assertEquals("page", search.search("population geometry").single().pageId)
            val nestedHit = search.search("deeply nested evidence").single()
            assertEquals("page", nestedHit.pageId)
            assertEquals("box", nestedHit.targetObjectId)
            assertTrue(nestedHit.targetX != null && nestedHit.targetY != null)
            assertTrue(search.search("asset").isEmpty())
            assertTrue(search.search("missing phrase").isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }
}
