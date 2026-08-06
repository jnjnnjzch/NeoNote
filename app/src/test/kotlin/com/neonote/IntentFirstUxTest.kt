package com.neonote

import com.neonote.engine.ActiveRichContentTarget
import com.neonote.engine.InlineStyle
import com.neonote.engine.InputEvent
import com.neonote.engine.InputPointer
import com.neonote.engine.InputRouter
import com.neonote.engine.PointerEventType
import com.neonote.engine.PointerTool
import com.neonote.engine.RichContentEditorSession
import com.neonote.engine.hasMeaningfulContent
import com.neonote.engine.normalizedForCommit
import com.neonote.model.BlockFormula
import com.neonote.model.CanvasPoint
import com.neonote.model.InlineLineBreak
import com.neonote.model.InlineText
import com.neonote.model.ListKind
import com.neonote.model.ParagraphNode
import com.neonote.model.RichContent
import com.neonote.model.RichContentBox
import com.neonote.model.TableCell
import com.neonote.model.TableCellAddress
import com.neonote.model.TableNode
import com.neonote.model.TextCursorPosition
import com.neonote.model.TextRange
import com.neonote.model.TextSelection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class IntentFirstUxTest {
    @Test
    fun `abandoned empty text draft leaves no object history or dirty content`() {
        val controller = NeoNoteEditorController()

        controller.focusOrCreateRichContentBox(CanvasPoint(80f, 120f))
        val boxId = assertIs<RichContentBox>(controller.currentCanvas.objects.single()).id

        assertEquals(0L, controller.contentChangeToken)
        controller.finishRichContentEditing(boxId)

        assertTrue(controller.currentCanvas.objects.isEmpty())
        assertTrue(controller.documentForPersistence().pages.single().canvas.objects.isEmpty())
        assertFalse(controller.canUndo)
    }

    @Test
    fun `typing and then inserting a table are separate understandable undo steps`() {
        val controller = NeoNoteEditorController()
        controller.focusOrCreateRichContentBox(CanvasPoint(40f, 40f))
        val boxId = assertIs<RichContentBox>(controller.currentCanvas.objects.single()).id
        controller.updateRichContentText(boxId, "alpha")

        controller.insertTableAtActiveContext(boxId)
        controller.finishRichContentEditing(boxId)

        val withTable = assertIs<RichContentBox>(controller.currentCanvas.objects.single())
        assertTrue(withTable.content.blocks.any { it is TableNode })

        controller.undo()
        val afterFirstUndo = assertIs<RichContentBox>(controller.currentCanvas.objects.single())
        assertEquals("alpha", afterFirstUndo.content.blocks.filterIsInstance<ParagraphNode>().first().inlines.filterIsInstance<InlineText>().joinToString("") { it.text })
        assertFalse(afterFirstUndo.content.blocks.any { it is TableNode })

        controller.undo()
        assertTrue(controller.currentCanvas.objects.isEmpty())
    }

    @Test
    fun `each page restores its own viewport`() {
        val controller = NeoNoteEditorController()
        val firstPage = controller.state.currentPageId!!
        controller.panViewportBy(125f, -40f)
        controller.zoomViewportBy(1.5f, CanvasPoint(300f, 220f))
        val firstViewport = controller.state.viewport

        controller.addPage()
        val secondPage = controller.state.currentPageId!!
        assertTrue(secondPage != firstPage)
        assertEquals(1f, controller.state.viewport.zoomScale)
        controller.panViewportBy(-90f, 75f)
        val secondViewport = controller.state.viewport

        controller.switchPage(firstPage)
        assertEquals(firstViewport, controller.state.viewport)
        controller.switchPage(secondPage)
        assertEquals(secondViewport, controller.state.viewport)
    }

    @Test
    fun `single selected text box resize changes wrapping width but not height`() {
        val controller = NeoNoteEditorController()
        controller.focusOrCreateRichContentBox(CanvasPoint(30f, 30f))
        val box = assertIs<RichContentBox>(controller.currentCanvas.objects.single())
        controller.updateRichContentText(box.id, "A long line that will wrap when the box becomes narrow")
        controller.finishRichContentEditing(box.id)
        val before = assertIs<RichContentBox>(controller.currentCanvas.objects.single())

        controller.setSelectionMode(true)
        controller.selectCanvasObject(before.id)
        controller.scaleSelection(1.4f, 1.8f)

        val after = assertIs<RichContentBox>(controller.currentCanvas.objects.single())
        assertEquals(before.size.width * 1.4f, after.size.width, 0.01f)
        assertEquals(before.size.height, after.size.height, 0.01f)
    }

    @Test
    fun `deleting a nested table keeps its outer table`() {
        val controller = NeoNoteEditorController()
        controller.focusOrCreateRichContentBox(CanvasPoint(30f, 30f))
        val boxId = assertIs<RichContentBox>(controller.currentCanvas.objects.single()).id

        controller.insertTableAtActiveContext(boxId)
        controller.insertTableAtActiveContext(boxId)
        controller.deleteActiveRichContentTable(boxId)
        controller.finishRichContentEditing(boxId)

        val content = assertIs<RichContentBox>(controller.currentCanvas.objects.single()).content
        val outer = assertIs<TableNode>(content.blocks.single { it is TableNode })
        assertFalse(outer.rows.flatten().flatMap { it.content.blocks }.any { it is TableNode })
    }

    @Test
    fun `lifecycle removes blank formula and structural spacer but keeps an intentional table`() {
        val content = RichContent(listOf(
            ParagraphNode(),
            BlockFormula("   "),
            TableNode(rows = emptyList()),
            ParagraphNode(),
        ))

        val normalized = content.normalizedForCommit()

        assertTrue(normalized.hasMeaningfulContent())
        assertEquals(1, normalized.blocks.size)
        assertIs<TableNode>(normalized.blocks.single())
    }

    @Test
    fun `format toolbar follows existing caret style and toggles it off naturally`() {
        val box = RichContentBox(
            id = "box",
            content = RichContent(listOf(
                ParagraphNode(listOf(InlineText("bold", bold = true), InlineText(" plain"))),
            )),
        )
        val session = RichContentEditorSession(
            initialBox = box,
            initialSelection = TextSelection.cursor(TextCursorPosition(0, 2)),
        )
        session.focusParagraph(0, 2)

        assertTrue(session.contextualTypingStyle().bold)
        session.toggleStyle(InlineStyle.Bold)
        assertFalse(session.contextualTypingStyle().bold)
        session.insertText("X")

        val paragraph = assertIs<ParagraphNode>(session.box.content.blocks.single())
        val inserted = paragraph.inlines.filterIsInstance<InlineText>().first { "X" in it.text }
        assertFalse(inserted.bold)
    }

    @Test
    fun `empty list item exits list instead of creating endless blank bullets`() {
        val box = RichContentBox(
            id = "box",
            content = RichContent(listOf(
                ParagraphNode(listMetadata = com.neonote.model.ListMetadata(kind = ListKind.Bullet)),
            )),
        )
        val session = RichContentEditorSession(box)
        session.focusParagraph(0, 0)

        session.insertParagraph()

        val paragraph = assertIs<ParagraphNode>(session.box.content.blocks.single())
        assertEquals(null, paragraph.listMetadata)
    }
    @Test
    fun `tab traverses table cells and grows a row at the end`() {
        val box = RichContentBox(
            id = "box",
            content = RichContent(listOf(
                TableNode(rows = listOf(listOf(TableCell(), TableCell()))),
            )),
        )
        val session = RichContentEditorSession(box)
        session.focusTableCell(TableCellAddress(0, 0, 0), 0)

        session.moveTableCellFocus(forward = true)
        val secondCell = assertIs<ActiveRichContentTarget.TableCell>(session.activeTarget).address
        assertEquals(0, secondCell.rowIndex)
        assertEquals(1, secondCell.columnIndex)

        session.moveTableCellFocus(forward = true)
        val newRowCell = assertIs<ActiveRichContentTarget.TableCell>(session.activeTarget).address
        val table = assertIs<TableNode>(session.box.content.blocks.single())
        assertEquals(2, table.rows.size)
        assertEquals(1, newRowCell.rowIndex)
        assertEquals(0, newRowCell.columnIndex)

        session.moveTableCellFocus(forward = false)
        val previousCell = assertIs<ActiveRichContentTarget.TableCell>(session.activeTarget).address
        assertEquals(0, previousCell.rowIndex)
        assertEquals(1, previousCell.columnIndex)
    }

    @Test
    fun `soft return stays inside the paragraph and advances the caret`() {
        val box = RichContentBox(
            id = "box",
            content = RichContent(listOf(ParagraphNode(listOf(InlineText("ab"))))),
        )
        val session = RichContentEditorSession(
            initialBox = box,
            initialSelection = TextSelection.cursor(TextCursorPosition(0, 1)),
        )
        session.focusParagraph(0, 1)

        session.insertLineBreak()

        assertEquals(1, session.box.content.blocks.size)
        val paragraph = assertIs<ParagraphNode>(session.box.content.blocks.single())
        assertTrue(paragraph.inlines.any { it === InlineLineBreak })
        assertEquals(2, session.activeParagraphSelection.start)
    }

    @Test
    fun `finishing a formula continues in a normal paragraph without exposing a mode switch`() {
        val box = RichContentBox(
            id = "box",
            content = RichContent(listOf(BlockFormula("x^2"))),
        )
        val session = RichContentEditorSession(box)

        session.continueAfterFormula(0)

        assertEquals(2, session.box.content.blocks.size)
        assertIs<ParagraphNode>(session.box.content.blocks[1])
        val target = assertIs<ActiveRichContentTarget.Paragraph>(session.activeTarget)
        assertEquals(1, target.blockIndex)
        assertEquals(0, session.activeParagraphSelection.start)
    }

    @Test
    fun `writing with the pen between typing bursts keeps three understandable undo steps`() {
        val controller = NeoNoteEditorController()
        controller.focusOrCreateRichContentBox(CanvasPoint(40f, 40f))
        val boxId = assertIs<RichContentBox>(controller.currentCanvas.objects.single()).id
        controller.updateRichContentText(boxId, "a")

        val router = InputRouter()
        controller.routeInputEvent(router, InputEvent(
            PointerEventType.Down,
            listOf(InputPointer(1, CanvasPoint(240f, 240f), PointerTool.SPen)),
        ))
        controller.routeInputEvent(router, InputEvent(
            PointerEventType.Move,
            listOf(InputPointer(1, CanvasPoint(260f, 260f), PointerTool.SPen)),
        ))
        controller.routeInputEvent(router, InputEvent(
            PointerEventType.Up,
            listOf(InputPointer(1, CanvasPoint(260f, 260f), PointerTool.SPen)),
        ))

        controller.updateRichContentText(boxId, "ab")
        controller.finishRichContentEditing(boxId)
        assertEquals(1, controller.currentCanvas.inkLayer.strokes.size)

        controller.undo()
        val afterTypingUndo = assertIs<RichContentBox>(controller.currentCanvas.objects.single())
        assertEquals("a", afterTypingUndo.toPlainTextForTest())
        assertEquals(1, controller.currentCanvas.inkLayer.strokes.size)

        controller.undo()
        assertEquals("a", assertIs<RichContentBox>(controller.currentCanvas.objects.single()).toPlainTextForTest())
        assertTrue(controller.currentCanvas.inkLayer.strokes.isEmpty())

        controller.undo()
        assertTrue(controller.currentCanvas.objects.isEmpty())
    }

    @Test
    fun `lists use the structured editor so their markers remain visible`() {
        val plain = RichContent(listOf(ParagraphNode(inlines = listOf(InlineText("plain")))))
        val list = RichContent(listOf(ParagraphNode(
            inlines = listOf(InlineText("item")),
            listMetadata = com.neonote.model.ListMetadata(kind = ListKind.Bullet),
        )))

        assertTrue(plain.usesUnifiedParagraphEditor())
        assertFalse(list.usesUnifiedParagraphEditor())
    }

    @Test
    fun `automatic table columns respond to user content`() {
        val compact = com.neonote.model.TableNode(
            rows = listOf(listOf(com.neonote.model.TableCell(RichContent(listOf(ParagraphNode()))))),
        )
        val descriptive = com.neonote.model.TableNode(
            rows = listOf(listOf(com.neonote.model.TableCell(
                RichContent(listOf(ParagraphNode(inlines = listOf(InlineText("A substantially longer table value")))))
            ))),
        )

        assertEquals(112f, compact.suggestedEditorColumnWidth(0))
        assertTrue(descriptive.suggestedEditorColumnWidth(0) > compact.suggestedEditorColumnWidth(0))
        assertTrue(descriptive.suggestedEditorColumnWidth(0) <= 280f)
    }

    @Test
    fun `starting to write after an untouched text tap discards the empty draft`() {
        val controller = NeoNoteEditorController()
        controller.focusOrCreateRichContentBox(CanvasPoint(40f, 40f))
        val router = InputRouter()

        controller.routeInputEvent(router, InputEvent(
            PointerEventType.Down,
            listOf(InputPointer(1, CanvasPoint(220f, 220f), PointerTool.SPen)),
        ))
        controller.routeInputEvent(router, InputEvent(
            PointerEventType.Up,
            listOf(InputPointer(1, CanvasPoint(220f, 220f), PointerTool.SPen)),
        ))

        assertTrue(controller.currentCanvas.objects.isEmpty())
        assertEquals(1, controller.currentCanvas.inkLayer.strokes.size)
        assertEquals(com.neonote.model.EditorTool.Pen, controller.state.currentTool)
    }

}


private fun RichContentBox.toPlainTextForTest(): String = content.blocks
    .filterIsInstance<ParagraphNode>()
    .joinToString("\n") { paragraph ->
        paragraph.inlines.filterIsInstance<InlineText>().joinToString("") { it.text }
    }
