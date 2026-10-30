package com.pomodorofocus.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pomodorofocus.app.model.SessionType

@Composable
fun SessionTypeSelector(
    selectedType: SessionType,
    onSelectType: (SessionType) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("session_selector_row")
    ) {
        SessionType.entries.forEach { sessionType ->
            val isSelected = sessionType == selectedType
            val icon = when (sessionType) {
                SessionType.FOCUS -> Icons.Default.LocalFireDepartment
                SessionType.SHORT_BREAK -> Icons.Default.Coffee
                SessionType.LONG_BREAK -> Icons.Default.SelfImprovement
            }

            FilterChip(
                selected = isSelected,
                onClick = { onSelectType(sessionType) },
                label = { Text(stringResource(id = sessionType.labelRes)) },
                leadingIcon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("session_chip_${sessionType.name.lowercase()}")
            )
        }
    }
}
