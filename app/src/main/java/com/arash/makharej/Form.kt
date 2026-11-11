package com.arash.makharej

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * یک بخش از فرم با پس‌زمینه‌ی جدا؛ بخش‌ها یکی‌درمیان روشن و خاکستری‌اند
 * تا معلوم باشد کجا باید پر شود. label اختیاری بالای بخش می‌آید.
 */
@Composable
fun FormBlock(index: Int, label: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val bg = if (index % 2 == 0) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        if (label != null) Text(
            label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 4.dp)
        )
        content()
    }
}
