package com.example.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onToggleField: (String) -> Unit,
    onToggleJournal: (String) -> Unit,
    onToggleAllJournals: (Boolean) -> Unit,
    onToggleNotifications: () -> Unit,
    onSetSyncPeriod: (String) -> Unit,
    onSetTheme: (String) -> Unit = {},
    onAddKeyword: (String) -> Unit,
    onRemoveKeyword: (String) -> Unit,
    onSyncNow: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

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
            // 화면 테마
            item {
                SectionTitle("화면 테마")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    THEME_OPTIONS.forEach { (label, value) ->
                        FilterChip(
                            selected = uiState.themeMode == value,
                            onClick = { onSetTheme(value) },
                            label = { Text(label) }
                        )
                    }
                }
            }

            // Sync period
            item {
                SectionTitle("백그라운드 동기화")
                Text(
                    text = "앱을 켤 때마다 한 번 받아오고, 그 사이에는 아래 주기로 자동 동기화합니다. " +
                        "주기가 짧을수록 배터리와 데이터를 더 씁니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SYNC_PERIOD_OPTIONS.forEach { (label, _) ->
                        FilterChip(
                            selected = uiState.syncPeriod == label,
                            onClick = { onSetSyncPeriod(label) },
                            label = { Text(label) }
                        )
                    }
                }
            }

            // Journals Section
            item {
                SectionTitle("저널 소스")
                var journalQuery by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = journalQuery,
                    onValueChange = { journalQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("저널 이름 검색") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )
                Spacer(modifier = Modifier.height(12.dp))

                val allJournalsEnabled = uiState.journals.values.all { it }
                SwitchRow(
                    label = "전체 선택",
                    checked = allJournalsEnabled,
                    onCheckedChange = { onToggleAllJournals(it) },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                )

                // 40종이 넘는 평면 목록은 찾기 어렵다. 분야별로 묶고 검색으로 좁힌다.
                val visible = uiState.journals.filterKeys { it.contains(journalQuery, ignoreCase = true) }
                val grouped = visible.keys.groupBy { journalGroupLabel(it) }
                for (groupLabel in JOURNAL_GROUP_ORDER) {
                    val names = grouped[groupLabel] ?: continue
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = groupLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    names.forEach { journal ->
                        SwitchRow(
                            label = journal,
                            checked = visible[journal] ?: true,
                            onCheckedChange = { onToggleJournal(journal) }
                        )
                    }
                }
                if (visible.isEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "검색 결과가 없습니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
