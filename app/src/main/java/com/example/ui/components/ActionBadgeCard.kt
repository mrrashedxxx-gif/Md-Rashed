package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ArushiCardDark
import com.example.ui.theme.ArushiError
import com.example.ui.theme.ArushiSecondary
import com.example.ui.theme.ArushiSuccess
import com.example.ui.theme.ArushiTextSecondaryDark

@Composable
fun ActionBadgeCard(
    actionName: String,
    success: Boolean,
    modifier: Modifier = Modifier
) {
    val (icon, label) = when {
        actionName.contains("openWhatsApp", ignoreCase = true) -> Icons.AutoMirrored.Filled.Send to "WhatsApp Action"
        actionName.contains("makeCall", ignoreCase = true) -> Icons.Default.Call to "Phone Call Action"
        actionName.contains("callContact", ignoreCase = true) -> Icons.Default.Call to "Contact Lookup & Call"
        actionName.contains("openUrl", ignoreCase = true) -> Icons.Default.Language to "Open Link Action"
        else -> Icons.Default.PlayArrow to "Device App Action"
    }

    val badgeColor = if (success) ArushiSuccess else ArushiError

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(ArushiCardDark.copy(alpha = 0.7f))
            .border(1.dp, badgeColor.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = ArushiSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Text(
                        text = actionName,
                        fontSize = 10.sp,
                        color = ArushiTextSecondaryDark
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (success) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = if (success) "Success" else "Notice",
                    tint = badgeColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (success) "Executed" else "Status",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = badgeColor
                )
            }
        }
    }
}
