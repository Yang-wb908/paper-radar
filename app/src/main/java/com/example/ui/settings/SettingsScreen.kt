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
            // Fields Section
            item {
                SectionTitle("관심 분야")
                uiState.fields.forEach { (field, isEnabled) ->
                    SwitchRow(label = field, checked = isEnabled, onCheckedChange = { onToggleField(field) })
                }
            }

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

            // Notifications Section
            item {
                SectionTitle("알림")
                SwitchRow(label = "새 논문 알림 받기", checked = uiState.notificationsEnabled, onCheckedChange = { onToggleNotifications() })
                
                if (uiState.notificationsEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Sync Period Dropdown
                    ExposedDropdownMenuBox(
                        expanded = expandedSyncPeriod,
                        onExpandedChange = { expandedSyncPeriod = !expandedSyncPeriod }
                    ) {
                        OutlinedTextField(
                            value = uiState.syncPeriod,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("동기화 주기") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedSyncPeriod) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expandedSyncPeriod,
                            onDismissRequest = { expandedSyncPeriod = false }
                        ) {
                            listOf("1시간", "3시간", "6시간").forEach { selectionOption ->
                                DropdownMenuItem(
                                    text = { Text(selectionOption) },
                                    onClick = {
                                        onSetSyncPeriod(selectionOption)
                                        expandedSyncPeriod = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Quiet Time
                    OutlinedTextField(
                        value = "${uiState.quietTimeStart} - ${uiState.quietTimeEnd}",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("방해금지 시간") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Keywords
                    Text("키워드 워치리스트", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = keywordInput,
                        onValueChange = { keywordInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("키워드 입력 후 엔터") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            onAddKeyword(keywordInput)
                            keywordInput = ""
                        }),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        uiState.keywords.forEach { keyword ->
                            InputChip(
                                selected = false,
                                onClick = { onRemoveKeyword(keyword) },
                                label = { Text(keyword) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "삭제",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // Data Section
            item {
                SectionTitle("데이터")
                Text("마지막 동기화: ${uiState.lastSyncTime}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("저장된 더미 논문 건수: ${uiState.savedPaperCount}건", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
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
