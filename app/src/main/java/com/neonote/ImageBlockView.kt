package com.neonote

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.neonote.engine.RichContentLayoutDefaults
import com.neonote.model.BlockImage

@Composable
internal fun ImageBlockView(
    block: BlockImage,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    val borderColor = if (selected) Color(0xFF2563EB) else Color(0xFFCBD5E1)
    val backgroundColor = if (selected) Color(0xFFEFF6FF) else Color(0xFFF8FAFC)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(RichContentLayoutDefaults.ImageCardHeight.dp)
            .clip(shape)
            .background(backgroundColor)
            .border(width = if (selected) 2.dp else 1.dp, color = borderColor, shape = shape)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics {
                contentDescription = "Image block ${block.displayTitle()}"
                role = Role.Button
                this.selected = selected
            }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 88.dp, height = 68.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFE2E8F0))
                .border(width = 1.dp, color = Color(0xFFCBD5E1), shape = RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "▧", color = Color(0xFF475569), style = MaterialTheme.typography.headlineSmall)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = block.displayTitle(),
                color = Color(0xFF0F172A),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "assetId: ${block.assetId}",
                color = Color(0xFF64748B),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (selected) {
                Text(
                    text = "Selected image block",
                    color = Color(0xFF2563EB),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

private fun BlockImage.displayTitle(): String = altText?.takeIf { it.isNotBlank() } ?: "Image placeholder"
