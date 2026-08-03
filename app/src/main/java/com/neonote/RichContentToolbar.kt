package com.neonote

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.neonote.engine.AssetDraft
import com.neonote.engine.FileAssetStore
import com.neonote.engine.InlineStyle
import com.neonote.model.ListKind
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MaximumImportedImageBytes = 32 * 1024 * 1024

@Composable
internal fun RichContentToolbar(
    boxId: String,
    controller: NeoNoteEditorController,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val assetStore = remember(context) {
        FileAssetStore(FileAssetStore.defaultDirectory(context.filesDir))
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val draft = context.contentResolver.readImageAssetDraft(uri) ?: return@launch
                val reference = assetStore.put(draft)
                controller.insertRichContentImagePlaceholder(
                    boxId = boxId,
                    assetId = reference.id,
                    altText = reference.fileName ?: "Imported image",
                )
            }
        }
    }

    Surface(
        modifier = modifier.focusProperties { canFocus = false },
        shape = RoundedCornerShape(12.dp),
        color = Color(0xF8FFFFFF),
        shadowElevation = 6.dp,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ToolbarAction(label = "B", contentDescription = "Bold") {
                controller.toggleActiveRichContentStyle(boxId = boxId, style = InlineStyle.Bold)
            }
            ToolbarAction(label = "I", contentDescription = "Italic") {
                controller.toggleActiveRichContentStyle(boxId = boxId, style = InlineStyle.Italic)
            }
            ToolbarAction(label = "U", contentDescription = "Underline") {
                controller.toggleActiveRichContentStyle(boxId = boxId, style = InlineStyle.Underline)
            }
            ToolbarAction(label = "•", contentDescription = "Bullet list") {
                controller.toggleActiveRichContentList(boxId = boxId, kind = ListKind.Bullet)
            }
            ToolbarAction(label = "1.", contentDescription = "Numbered list") {
                controller.toggleActiveRichContentList(boxId = boxId, kind = ListKind.Numbered)
            }
            ToolbarAction(label = "☐", contentDescription = "Todo list") {
                controller.toggleActiveRichContentList(boxId = boxId, kind = ListKind.Todo)
            }
            ToolbarAction(label = "▦", contentDescription = "Insert table") {
                controller.insertRichContentTablePlaceholder(boxId = boxId)
            }
            ToolbarAction(label = "ƒ", contentDescription = "Insert formula") {
                controller.insertRichContentFormulaPlaceholder(boxId = boxId)
            }
            ToolbarAction(label = "▧", contentDescription = "Import image") {
                imagePicker.launch("image/*")
            }
        }
    }
}

@Composable
private fun ToolbarAction(
    label: String,
    contentDescription: String,
    onAction: () -> Unit,
) {
    Text(
        text = label,
        modifier = Modifier
            .focusProperties { canFocus = false }
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
                onClick {
                    onAction()
                    true
                }
            }
            .pointerInput(onAction) {
                detectTapGestures(onTap = { onAction() })
            }
            .background(Color(0xFFEDE9FE), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        color = Color(0xFF4C1D95),
        style = MaterialTheme.typography.labelLarge,
    )
}

private suspend fun android.content.ContentResolver.readImageAssetDraft(uri: Uri): AssetDraft? =
    withContext(Dispatchers.IO) {
        runCatching {
            val bytes = openInputStream(uri)?.use { input ->
                input.readBytesLimited(MaximumImportedImageBytes)
            } ?: return@runCatching null
            if (bytes.isEmpty()) return@runCatching null
            AssetDraft(
                mediaType = getType(uri) ?: "application/octet-stream",
                bytes = bytes,
                fileName = displayName(uri),
            )
        }.getOrNull()
    }

private fun android.content.ContentResolver.displayName(uri: Uri): String? =
    query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0) cursor.getString(index) else null
    }

private fun InputStream.readBytesLimited(maximumBytes: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        total += count
        require(total <= maximumBytes) { "Imported image exceeds the size limit" }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
