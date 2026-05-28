package com.example.cahier.features.drawing

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.input.key.Key
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.cahier.core.document.TableBlock
import com.example.cahier.core.document.TableCell
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TableBlockEditorUiTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun editCell_updatesContent() {
        var table by mutableStateOf(sampleTable())
        rule.setContent {
            TableBlockEditor(
                table = table,
                onCellChange = { r, c, text ->
                    table = table.copy(
                        cells = table.cells.mapIndexed { ri, row ->
                            row.mapIndexed { ci, cell ->
                                if (ri == r && ci == c) cell.copy(text = text) else cell
                            }
                        }
                    )
                },
                onSelectCell = { _, _ -> },
                onPasteImage = {},
                onToggleBold = { _, _ -> },
                onToggleItalic = { _, _ -> },
                onToggleUnderline = { _, _ -> },
                onAppendRow = {},
                onMove = { _, _ -> },
                onResize = { _, _ -> },
                canvasTransform = CanvasTransform()
            )
        }

        rule.onNodeWithTag("table-cell-0-0").performClick()
        rule.onNodeWithTag("table-cell-0-0").performTextInput("neo")
        rule.onNodeWithTag("table-cell-0-0").assertTextContains("neo")
    }

    @Test
    fun tabOnLastCell_appendsRow() {
        var table by mutableStateOf(sampleTable())
        rule.setContent {
            TableBlockEditor(
                table = table,
                onCellChange = { _, _, _ -> },
                onSelectCell = { _, _ -> },
                onPasteImage = {},
                onToggleBold = { _, _ -> },
                onToggleItalic = { _, _ -> },
                onToggleUnderline = { _, _ -> },
                onAppendRow = {
                    table = table.copy(
                        rows = table.rows + 1,
                        cells = table.cells + listOf(List(table.columns) { TableCell() })
                    )
                },
                onMove = { _, _ -> },
                onResize = { _, _ -> },
                canvasTransform = CanvasTransform()
            )
        }

        rule.onNodeWithTag("table-cell-2-2").performClick()
        rule.onNodeWithTag("table-cell-2-2").performKeyInput { pressKey(Key.Tab) }
        rule.onNodeWithTag("table-cell-3-0").assertExists()
    }

    @Test
    fun ctrlB_togglesBoldState() {
        var table by mutableStateOf(sampleTable())
        rule.setContent {
            TableBlockEditor(
                table = table,
                onCellChange = { _, _, _ -> },
                onSelectCell = { _, _ -> },
                onPasteImage = {},
                onToggleBold = { r, c ->
                    table = table.copy(
                        cells = table.cells.mapIndexed { ri, row ->
                            row.mapIndexed { ci, cell ->
                                if (ri == r && ci == c) cell.copy(bold = !cell.bold) else cell
                            }
                        }
                    )
                },
                onToggleItalic = { _, _ -> },
                onToggleUnderline = { _, _ -> },
                onAppendRow = {},
                onMove = { _, _ -> },
                onResize = { _, _ -> },
                canvasTransform = CanvasTransform()
            )
        }

        rule.onNodeWithTag("table-cell-0-0").performClick()
        rule.onNodeWithTag("table-cell-0-0").performKeyInput {
            keyDown(Key.CtrlLeft)
            pressKey(Key.B)
            keyUp(Key.CtrlLeft)
        }
        // Bold is style-based; no direct semantic flag, so we assert no crash and state flip via re-edit.
        assertTrue(table.cells[0][0].bold)
    }

    private fun sampleTable(): TableBlock {
        return TableBlock(
            rows = 3,
            columns = 3,
            cells = List(3) { List(3) { TableCell() } }
        )
    }
}
