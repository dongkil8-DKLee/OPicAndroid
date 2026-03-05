package com.opic.android.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.opic.android.audio.EngineOption
import com.opic.android.ui.common.filter.BottomSheetPicker
import com.opic.android.ui.theme.OPicColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // 스낵바 표시
    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    val levelImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            viewModel.onLevelImageDirChanged(it.toString())
        }
    }

    val soundDirLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            viewModel.onSoundDirChanged(it.toString())
        }
    }

    // CSV Export launcher (파일 생성)
    val csvExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let { viewModel.exportCsv(it) }
    }

    // CSV Import launcher (파일 선택)
    val csvImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importCsv(it) }
    }

    // Vocab CSV Export launcher
    val vocabCsvExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let { viewModel.exportVocabCsv(it) }
    }

    // Vocab CSV Import launcher
    val vocabCsvImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importVocabCsv(it) }
    }

    // 삭제 확인 다이얼로그
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("삭제 확인") },
            text = { Text("'${state.editTitle}' 문항을 삭제하시겠습니까?") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.deleteQuestion()
                }) { Text("삭제", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("취소") }
            }
        )
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Header
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // === AI 설정 ===
                AiSettingsSection(state = state, viewModel = viewModel)

                // === Theme Mode ===
                Text("Theme", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("light" to "Light", "dark" to "Dark", "system" to "System").forEach { (value, label) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = state.themeMode == value,
                                onClick = { viewModel.onThemeModeChanged(value) }
                            )
                            Text(label, fontSize = 13.sp)
                        }
                    }
                }

                // === TTS Engine Selection ===
                Text("TTS 엔진", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                if (state.availableEngines.isNotEmpty()) {
                    val engineDisplayList = remember(state.availableEngines) {
                        listOf("기본 (시스템 설정)") + state.availableEngines.map { it.label }
                    }
                    val selectedEngineDisplay = remember(state.selectedEnginePackage, state.availableEngines) {
                        if (state.selectedEnginePackage.isBlank()) "기본 (시스템 설정)"
                        else state.availableEngines.find { it.packageName == state.selectedEnginePackage }?.label
                            ?: "기본 (시스템 설정)"
                    }
                    BottomSheetPicker(
                        label    = "TTS 엔진",
                        selected = selectedEngineDisplay,
                        options  = engineDisplayList,
                        onSelected = { display ->
                            val pkg = if (display == "기본 (시스템 설정)") ""
                                      else state.availableEngines.find { it.label == display }?.packageName ?: ""
                            viewModel.onEngineSelected(pkg)
                        }
                    )
                }

                // === TTS Voice Selection ===
                Text("TTS Voice", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                if (state.availableVoiceOptions.isNotEmpty()) {
                    val displayList = remember(state.availableVoiceOptions) {
                        listOf("Default") + state.availableVoiceOptions.map { it.displayName }
                    }
                    val selectedDisplay = remember(state.selectedVoice, state.availableVoiceOptions) {
                        if (state.selectedVoice.isBlank()) "Default"
                        else state.availableVoiceOptions.find { it.name == state.selectedVoice }?.displayName
                            ?: "Default"
                    }
                    BottomSheetPicker(
                        label    = "TTS Voice",
                        selected = selectedDisplay,
                        options  = displayList,
                        onSelected = { display ->
                            if (display == "Default") {
                                viewModel.onVoiceSelected("Default")
                            } else {
                                val rawName = state.availableVoiceOptions
                                    .find { it.displayName == display }?.name ?: return@BottomSheetPicker
                                viewModel.onVoiceSelected(rawName)
                            }
                        }
                    )
                } else {
                    Text("음성 목록 로딩 중...", fontSize = 12.sp, color = Color.Gray)
                }

                // === Text Size ===
                Text("Text Size", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${state.textSize}sp", fontSize = 13.sp, modifier = Modifier.width(40.dp))
                    Slider(
                        value = state.textSize.toFloat(),
                        onValueChange = { viewModel.onTextSizeChanged(it.toInt()) },
                        valueRange = 10f..34f,
                        steps = 11,
                        modifier = Modifier.weight(1f)
                    )
                }

                // === Target Grade ===
                Text("OPic Target Grade", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                GradeDropdown(
                    selected = state.targetGrade,
                    onSelected = { viewModel.onTargetGradeChanged(it) }
                )

                // === Level Image Dir ===
                Text("Level Image Folder", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                FolderPickerRow(
                    path = state.levelImageDir,
                    placeholder = "Touch to browse...",
                    onClick = { levelImageLauncher.launch(null) }
                )

                // === Sound Dir ===
                Text("Sound Save Folder", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                FolderPickerRow(
                    path = state.soundDir,
                    placeholder = "Touch to browse...",
                    onClick = { soundDirLauncher.launch(null) }
                )

                // ======================================================
                // === 데이터 편집 섹션 ===
                // ======================================================
                Text("데이터 편집", fontWeight = FontWeight.Bold, fontSize = 14.sp)

                // 주제 네비게이터
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 주제 드롭다운
                    SetFilterDropdown(
                        selected = state.selectedSet,
                        options = listOf("전체") + state.allSets,
                        onSelected = { viewModel.onSetFilterChanged(it) },
                        modifier = Modifier.width(110.dp)
                    )

                    IconButton(
                        onClick = { viewModel.onPrevQuestion() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "이전")
                    }

                    Text(
                        text = state.editTitle.ifBlank { "—" },
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = { viewModel.onNextQuestion() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = "다음")
                    }

                    val idx = state.currentQuestionIndex
                    val total = state.filteredQuestions.size
                    Text(
                        text = if (total > 0) "${idx + 1}/$total" else "0/0",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }

                // 데이터 입력 폼 (오렌지 테두리 박스)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, OPicColors.Primary, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("데이터 입력", color = OPicColors.Primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)

                    DataField("제목(Title)", state.editTitle, viewModel::onEditTitleChanged)
                    DataField("주제(Set)", state.editSet, viewModel::onEditSetChanged)
                    DataField("유형(Type)", state.editType, viewModel::onEditTypeChanged)
                    DataField("콤보(Combo)", state.editCombo, viewModel::onEditComboChanged)
                    DataField("Q_Audio", state.editQAudio, viewModel::onEditQAudioChanged)
                    DataField("A_Audio", state.editAAudio, viewModel::onEditAAudioChanged)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showDeleteDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("삭제", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { viewModel.saveQuestion() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = OPicColors.Primary,
                                contentColor = OPicColors.PrimaryText
                            ),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.weight(2f)
                        ) {
                            Text("저장", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // ======================================================
                // === CSV Import/Export (Questions) ===
                // ======================================================
                Text("CSV Import/Export (Questions)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Gray)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FolderPickerRow(
                        path = state.csvPath,
                        placeholder = "Touch to browse...",
                        onClick = { csvImportLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "*/*")) },
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    // Import 아이콘
                    IconButton(
                        onClick = { csvImportLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "*/*")) }
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = "CSV 가져오기", tint = OPicColors.Primary)
                    }

                    // Export 아이콘
                    IconButton(
                        onClick = { csvExportLauncher.launch("opic_questions.csv") }
                    ) {
                        Icon(Icons.Filled.Upload, contentDescription = "CSV 내보내기", tint = OPicColors.Primary)
                    }
                }

                // ======================================================
                // === 단어장 CSV Import/Export ===
                // ======================================================
                Text("단어장 CSV Import/Export", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Gray)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { vocabCsvImportLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "*/*")) },
                        colors = ButtonDefaults.buttonColors(containerColor = OPicColors.Primary),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("가져오기", fontSize = 12.sp)
                    }

                    Button(
                        onClick = { vocabCsvExportLauncher.launch("opic_vocabulary.csv") },
                        colors = ButtonDefaults.buttonColors(containerColor = OPicColors.Primary),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("내보내기", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            // Snackbar
            SnackbarHost(hostState = snackbarHostState)
        }
    }
}

// ==================== 데이터 입력 필드 ====================

@Composable
private fun DataField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 12.sp) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp)
    )
}

// ==================== 주제 필터 드롭다운 ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetFilterDropdown(
    selected: String,
    options: List<String>,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        TextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text("주제", fontSize = 10.sp) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .height(52.dp)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, fontSize = 13.sp) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

// ==================== 등급 드롭다운 ====================

private val GRADE_OPTIONS = listOf("AL", "IH", "IM3", "IM2", "IM1", "IL", "NH")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GradeDropdown(selected: String, onSelected: (String) -> Unit) {
    val grades = GRADE_OPTIONS
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth()
    ) {
        TextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            grades.forEach { grade ->
                DropdownMenuItem(
                    text = { Text(grade) },
                    onClick = {
                        onSelected(grade)
                        expanded = false
                    }
                )
            }
        }
    }
}

// ==================== 폴더 선택 행 ====================

@Composable
private fun FolderPickerRow(
    path: String,
    placeholder: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, OPicColors.Border, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.FolderOpen,
            contentDescription = "Browse",
            tint = OPicColors.Primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = path.ifBlank { placeholder },
            fontSize = 13.sp,
            color = if (path.isBlank()) Color.Gray else Color.Unspecified,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

// ==================== AI 설정 섹션 ====================

@Composable
private fun AiSettingsSection(
    state: SettingsUiState,
    viewModel: SettingsViewModel
) {
    var showApiKey by remember { mutableStateOf(false) }

    Text("AI 설정 (Claude)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = OPicColors.Primary)

    // API Key 입력
    OutlinedTextField(
        value = state.claudeApiKey,
        onValueChange = { viewModel.onApiKeyChanged(it) },
        label = { Text("Claude API Key", fontSize = 12.sp) },
        placeholder = { Text("sk-ant-...", fontSize = 12.sp, color = Color.Gray) },
        singleLine = true,
        visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { showApiKey = !showApiKey }) {
                Icon(
                    if (showApiKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
    Text(
        "API 키는 기기 내에만 저장됩니다. anthropic.com/console에서 발급 가능합니다.",
        fontSize = 11.sp,
        color = Color.Gray
    )

    Spacer(modifier = Modifier.height(4.dp))

    // 개인 프로필
    Text("개인 프로필 (AI 모범 답안 개인화)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)

    ProfileField("직업", state.profileJob) { viewModel.onProfileJobChanged(it) }
    ProfileField("취미", state.profileHobbies) { viewModel.onProfileHobbiesChanged(it) }
    ProfileField("가족 관계", state.profileFamily) { viewModel.onProfileFamilyChanged(it) }
    ProfileField("국적/거주지", state.profileCountry) { viewModel.onProfileCountryChanged(it) }
    ProfileField("기타 (자유 서술)", state.profileBackground) { viewModel.onProfileBackgroundChanged(it) }
}

@Composable
private fun ProfileField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 12.sp) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}
