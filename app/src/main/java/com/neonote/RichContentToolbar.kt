package com.neonote

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
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
private val ToolbarPurple = Color(0xFF5B3FD1)

@Composable
internal fun RichContentToolbar(
    boxId: String,
    controller: NeoNoteEditorController,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
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
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFCFFFFFF),
        shadowElevation = 8.dp,
        tonalElevation = 3.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E1EC)),
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(scrollState)
                .padding(horizontal = 7.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolbarAction(
                label = "B",
                contentDescription = "Bold",
                fontWeight = FontWeight.Bold,
            ) {
                controller.toggleActiveRichContentStyle(boxId = boxId, style = InlineStyle.Bold)
            }
            ToolbarAction(
                label = "I",
                contentDescription = "Italic",
                fontStyle = FontStyle.Italic,
            ) {
                controller.toggleActiveRichContentStyle(boxId = boxId, style = InlineStyle.Italic)
            }
            ToolbarAction(
                label = "U",
                contentDescription = "Underline",
                textDecoration = TextDecoration.Underline,
            ) {
                controller.toggleActiveRichContentStyle(boxId = boxId, style = InlineStyle.Underline)
            }
            ToolbarDivider()
            ToolbarAction(label = "•", contentDescription = "Bullet list") {
                controller.toggleActiveRichContentList(boxId = boxId, kind = ListKind.Bullet)
            }
            ToolbarAction(label = "1.", contentDescription = "Numbered list") {
                controller.toggleActiveRichContentList(boxId = boxId, kind = ListKind.Numbered)
            }
            ToolbarAction(label = "☐", contentDescription = "Checklist") {
                controller.toggleActiveRichContentList(boxId = boxId, kind = ListKind.Todo)
            }
            ToolbarDivider()
            ToolbarAction(label = "Table", contentDescription = "Insert table", compact = false) {
                controller.insertRichContentTablePlaceholder(boxId = boxId)
            }
            ToolbarAction(label = "fx", contentDescription = "Insert formula") {
                controller.insertRichContentFormulaPlaceholder(boxId = boxId)
            }
            ToolbarAction(label = "Image", contentDescription = "Import image", compact = false) {
                imagePicker.launch("image/*")
            }
        }
    }
}

@Composable
private fun ToolbarDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .width(1.dp)
            .height(24.dp)
            .background(Color(0xFFE5E1EC)),
    )
}

@Composable
private fun ToolbarAction(
    label: String,
    contentDescription: String,
    compact: Boolean = true,
    fontWeight: FontWeight? = null,
    fontStyle: FontStyle? = null,
    textDecoration: TextDecoration? = null,
    onAction: () -> Unit,
) {
    Text(
        text = label,
        modifier = Modifier
            .focusProperties { canFocus = false }
            .clip(RoundedCornerShape(9.dp))
            .background(Color(0xFFF3F0FB))
            .clickable(role = Role.Button, onClick = onAction)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            }
            .padding(horizontal = if (compact) 10.dp else 12.dp, vertical = 6.dp),
        color = ToolbarPurple,
        style = MaterialTheme.typography.labelLarge.copy(
            fontWeight = fontWeight ?: FontWeight.SemiBold,
            fontStyle = fontStyle,
            textDecoration = textDecoration,
        ),
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
