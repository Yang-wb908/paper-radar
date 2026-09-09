package com.example.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onToggleField: (String) -> Unit,
    onToggleJournal: (String) -> Unit,
    onToggleAllJournals: (Boolean) -> Unit,
    onToggleNotifications: () -> Unit,
    onSetSyncPeriod: (String) -> Unit,
    onAddKeyword: (String) -> Unit,
    onRemoveKeyword: (String) -> Unit,
    onSyncNow: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var keywordInput by remember { mutableStateOf("") }
    var expandedSyncPeriod by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("설정", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Journals Section
            item {
                SectionTitle("저널 소스")
                val allJournalsEnabled = uiState.journals.values.all { it }
                SwitchRow(
                    label = "전체 선택",
                    checked = allJournalsEnabled,
                    onCheckedChange = { onToggleAllJournals(it) },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                )
                
                uiState.journals.forEach { (journal, isEnabled) ->
                    SwitchRow(label = journal, checked = isEnabled, onCheckedChange = { onToggleJournal(journal) })
                }
            }

            // Data Section
            item {
                SectionTitle("데이터")
                Text("마지막 동기화: ${uiState.lastSyncTime}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("저장된 논문: ${uiState.savedPaperCount}건", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        onSyncNow()
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("동기화를 시작합니다.")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("지금 동기화")
                }
            }
        }
    }
}

@Composable
fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, textStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = textStyle, color = MaterialTheme.colorScheme.onSurface)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
