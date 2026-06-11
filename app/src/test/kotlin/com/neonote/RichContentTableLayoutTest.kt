package com.neonote

import com.neonote.engine.RichContentLayoutDefaults
import com.neonote.engine.RichContentMeasurer
import com.neonote.model.CanvasSize
import com.neonote.model.InlineText
import com.neonote.model.ParagraphNode
import com.neonote.model.RichContent
import com.neonote.model.RichContentBox
import com.neonote.model.TableCell
import com.neonote.model.TableColumnPolicy
import com.neonote.model.TableColumnWidthMode
import com.neonote.model.TableNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RichContentTableLayoutTest {
    @Test
    fun `empty 2 by 2 table reports stable min cell size and measured height`() {
        val layout = RichContentMeasurer().measure(
            RichContent(
                blocks = listOf(
                    TableNode(rows = List(2) { List(2) { TableCell() } }),
                ),
            ),
            availableWidth = 240f,
        )

        val tableLayout = layout.tableLayouts.single()

        assertEquals(listOf(104f, 104f), tableLayout.columnWidths)
        assertEquals(listOf(RichContentLayoutDefaults.TableMinCellHeight, RichContentLayoutDefaults.TableMinCellHeight), tableLayout.rowHeights)
        assertEquals(208f, tableLayout.tableWidth)
        assertEquals(64f, tableLayout.tableHeight)
        assertEquals(2, tableLayout.cellRects.size)
        assertEquals(2, tableLayout.cellRects.first().size)
        assertTrue(tableLayout.cellRects.flatten().all { rect ->
            rect.right - rect.left >= RichContentLayoutDefaults.TableMinCellWidth &&
                rect.bottom - rect.top >= RichContentLayoutDefaults.TableMinCellHeight
        })
        assertEquals(
            RichContentLayoutDefaults.VerticalPadding * 2 + tableLayout.tableHeight,
            layout.measuredSize.height,
        )
    }

    @Test
    fun `short cell text remains stable while long text changes preferred column width`() {
        val shortLayout = tableLayoutFor(
            firstCellText = "short",
            secondCellText = "ok",
            availableWidth = 520f,
        )
        val longLayout = tableLayoutFor(
            firstCellText = "this is a much longer cell value that prefers more width",
            secondCellText = "ok",
            availableWidth = 520f,
        )

        assertTrue(shortLayout.rowHeights.single() >= RichContentLayoutDefaults.TableMinCellHeight)
        assertTrue(longLayout.columnWidths[0] > shortLayout.columnWidths[0])
        assertEquals(520f - RichContentLayoutDefaults.HorizontalPadding * 2, longLayout.tableWidth)
    }

    @Test
    fun `long text wraps after available width and increases row height`() {
        val layout = tableLayoutFor(
            firstCellText = "x".repeat(120),
            secondCellText = "ok",
            availableWidth = 180f,
        )

        assertEquals(148f, layout.tableWidth)
        assertTrue(layout.columnWidths.all { it >= RichContentLayoutDefaults.TableMinCellWidth })
        assertTrue(layout.rowHeights.single() > RichContentLayoutDefaults.TableMinCellHeight)
    }

    @Test
    fun `multiline cell drives row height for every cell in the row`() {
        val layout = tableLayoutFor(
            firstCellText = "one\ntwo\nthree",
            secondCellText = "ok",
            availableWidth = 280f,
        )

        assertEquals(1, layout.rowHeights.size)
        assertEquals(layout.rowHeights.single(), layout.cellRects[0][0].bottom - layout.cellRects[0][0].top)
        assertEquals(layout.rowHeights.single(), layout.cellRects[0][1].bottom - layout.cellRects[0][1].top)
        assertTrue(layout.rowHeights.single() > RichContentLayoutDefaults.TableMinCellHeight)
    }

    @Test
    fun `table height pushes rich content box height and following paragraph down`() {
        val box = RichContentBox(
            id = "box-1",
            size = CanvasSize(width = 180f, height = 1f),
            content = RichContent(
                blocks = listOf(
                    TableNode(
                        rows = listOf(
                            listOf(cell("x".repeat(120)), TableCell()),
                            listOf(TableCell(), TableCell()),
                        ),
                    ),
                    ParagraphNode(inlines = listOf(InlineText("after table"))),
                ),
            ),
        )

        val layout = RichContentMeasurer().measure(box)
        val tableRect = layout.blockRects[0].rect
        val paragraphRect = layout.blockRects[1].rect
        val tableLayout = layout.tableLayouts.single()
        val resized = RichContentMeasurer().resizeBoxToMeasuredContent(box)

        assertEquals(tableLayout.tableHeight, tableRect.bottom - tableRect.top)
        assertTrue(layout.measuredSize.height > RichContentLayoutDefaults.MinimumBoxHeight)
        assertEquals(layout.measuredSize.height, resized.size.height)
        assertTrue(paragraphRect.top > tableRect.bottom)
    }

    @Test
    fun `manual column policy is preserved for future editor support`() {
        val table = TableNode(
            rows = listOf(listOf(TableCell())),
            columnPolicies = listOf(
                TableColumnPolicy(
                    minWidth = 64f,
                    preferredWidth = 96f,
                    maxWidth = 160f,
                    manualWidth = 120f,
                    mode = TableColumnWidthMode.Manual,
                ),
            ),
        )

        val policy = table.columnPolicies.single()

        assertEquals(TableColumnWidthMode.Manual, policy.mode)
        assertEquals(120f, policy.manualWidth)
    }

    private fun tableLayoutFor(firstCellText: String, secondCellText: String, availableWidth: Float) =
        RichContentMeasurer().measure(
            content = RichContent(
                blocks = listOf(
                    TableNode(rows = listOf(listOf(cell(firstCellText), cell(secondCellText)))),
                ),
            ),
            availableWidth = availableWidth,
        ).tableLayouts.single()

    private fun cell(text: String): TableCell = TableCell(
        content = RichContent(blocks = listOf(ParagraphNode(inlines = listOf(InlineText(text))))),
    )
}
