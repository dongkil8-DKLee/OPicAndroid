package com.opic.android.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.opic.android.ui.common.filter.BottomSheetPicker
import com.opic.android.ui.theme.OPicColors

private val ColorAppearance = Color(0xFFE67E22)   // 오렌지 (Primary)
private val ColorStudy      = Color(0xFF3498DB)   // 블루
private val ColorAi         = Color(0xFF9B59B6)   // 퍼플
private val ColorEdit       = Color(0xFF27AE60)   // 그린
private val ColorData       = Color(0xFFE74C3C)   // 레드

@Composable
fun SettingsScreen(
    onStudyLink: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var expandedCategory by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    // Launchers — 최상단에 선언
    val soundDirLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            viewModel.onSoundDirChanged(it.toString())
        }
    }
    val vocabCsvExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> uri?.let { viewModel.exportVocabCsv(it) } }

    val vocabCsvImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importVocabCsv(it) } }

    // Q&A CSV launcher
    val qaCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            val content = viewModel.uiState.value.qaCsvContent
            if (!content.isNullOrBlank()) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                        out.write(content.toByteArray(Charsets.UTF_8))
                    }
                    viewModel.clearSnackbar()
                    viewModel.clearQaCsvContent()
                } catch (_: Exception) {}
            }
            viewModel.clearQaCsvContent()
        }
    }
    val qaImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importQaCsvFromUri(it) } }

    // DB 백업/복원 launcher
    val dbBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            val bytes = viewModel.uiState.value.dbBackupBytes
            if (bytes != null) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { out -> out.write(bytes) }
                } catch (_: Exception) {}
            }
            viewModel.clearDbBackupBytes()
        }
    }
    val dbRestoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.restoreDatabaseFromUri(it) } }

    // Q&A CSV 준비 완료 → 파일 저장 다이얼로그
    LaunchedEffect(state.qaCsvContent) {
        if (!state.qaCsvContent.isNullOrBlank()) {
            val date = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())
            qaCsvLauncher.launch("shadowtalk_qa_$date.csv")
        }
    }

    // DB 백업 준비 완료 → 파일 저장 다이얼로그
    LaunchedEffect(state.dbBackupBytes) {
        if (state.dbBackupBytes != null) {
            val date = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())
            dbBackupLauncher.launch("shadowtalk_backup_$date.db")
        }
    }

    // 삭제 확인 다이얼로그
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("삭제 확인") },
            text = { Text("'${state.editTitle}' 문항을 삭제하시겠습니까?") },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; viewModel.deleteQuestion() }) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("취소") }
            }
        )
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                // 헤더
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                )

                HorizontalDivider(color = OPicColors.Border)

                // ─── 1. 앱 외관 ───────────────────────────────────────
                CategorySection(
                    icon = Icons.Filled.Palette,
                    iconTint = ColorAppearance,
                    title = "앱 외관",
                    subtitle = "테마 · 글자 크기",
                    expanded = expandedCategory == "appearance",
                    onToggle = { expandedCategory = if (expandedCategory == "appearance") null else "appearance" }
                ) {
                    // 테마
                    Text("테마", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = OPicColors.TextOnLight)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
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
                    Spacer(Modifier.height(12.dp))
                    // 글자 크기
                    Text("글자 크기", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = OPicColors.TextOnLight)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${state.textSize}sp", fontSize = 13.sp, modifier = Modifier.width(40.dp))
                        Slider(
                            value = state.textSize.toFloat(),
                            onValueChange = { viewModel.onTextSizeChanged(it.toInt()) },
                            valueRange = 10f..34f,
                            steps = 11,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                HorizontalDivider(color = OPicColors.Border)

                // ─── 2. 음성 설정 ─────────────────────────────────────
                CategorySection(
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    iconTint = ColorStudy,
                    title = "음성 설정",
                    subtitle = "TTS 엔진 · 음성 · 저장 폴더",
                    expanded = expandedCategory == "study",
                    onToggle = { expandedCategory = if (expandedCategory == "study") null else "study" }
                ) {
                    // 음성 저장 폴더
                    Text("음성 저장 폴더", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = OPicColors.TextOnLight)
                    Spacer(Modifier.height(6.dp))
                    FolderPickerRow(
                        path = state.soundDir,
                        placeholder = "폴더를 선택하세요...",
                        onClick = { soundDirLauncher.launch(null) }
                    )
                    Spacer(Modifier.height(12.dp))
                    // TTS 엔진
                    if (state.availableEngines.isNotEmpty()) {
                        Text("TTS 엔진", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = OPicColors.TextOnLight)
                        Spacer(Modifier.height(6.dp))
                        val engineDisplayList = remember(state.availableEngines) {
                            listOf("기본 (시스템 설정)") + state.availableEngines.map { it.label }
                        }
                        val selectedEngineDisplay = remember(state.selectedEnginePackage, state.availableEngines) {
                            if (state.selectedEnginePackage.isBlank()) "기본 (시스템 설정)"
                            else state.availableEngines.find { it.packageName == state.selectedEnginePackage }?.label ?: "기본 (시스템 설정)"
                        }
                        BottomSheetPicker(
                            label = "TTS 엔진",
                            selected = selectedEngineDisplay,
                            options = engineDisplayList,
                            onSelected = { display ->
                                val pkg = if (display == "기본 (시스템 설정)") ""
                                          else state.availableEngines.find { it.label == display }?.packageName ?: ""
                                viewModel.onEngineSelected(pkg)
                            }
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    // TTS Voice
                    Text("TTS Voice", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = OPicColors.TextOnLight)
                    Spacer(Modifier.height(6.dp))
                    if (state.availableVoiceOptions.isNotEmpty()) {
                        val displayList = remember(state.availableVoiceOptions) {
                            listOf("Default") + state.availableVoiceOptions.map { it.displayName }
                        }
                        val selectedDisplay = remember(state.selectedVoice, state.availableVoiceOptions) {
                            if (state.selectedVoice.isBlank()) "Default"
                            else state.availableVoiceOptions.find { it.name == state.selectedVoice }?.displayName ?: "Default"
                        }
                        BottomSheetPicker(
                            label = "TTS Voice",
                            selected = selectedDisplay,
                            options = displayList,
                            onSelected = { display ->
                                if (display == "Default") viewModel.onVoiceSelected("Default")
                                else {
                                    val rawName = state.availableVoiceOptions.find { it.displayName == display }?.name ?: return@BottomSheetPicker
                                    viewModel.onVoiceSelected(rawName)
                                }
                            }
                        )
                    } else {
                        Text("음성 목록 로딩 중...", fontSize = 12.sp, color = Color.Gray)
                    }
                }

                HorizontalDivider(color = OPicColors.Border)

                // ─── 3. AI 설정 ───────────────────────────────────────
                CategorySection(
                    icon = Icons.Filled.Psychology,
                    iconTint = ColorAi,
                    title = "AI 설정",
                    subtitle = "OPic 목표 등급 · API Key · 개인 프로필",
                    expanded = expandedCategory == "ai",
                    onToggle = { expandedCategory = if (expandedCategory == "ai") null else "ai" }
                ) {
                    // OPic 목표 등급
                    Text("OPic 목표 등급", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = OPicColors.TextOnLight)
                    Spacer(Modifier.height(6.dp))
                    BottomSheetPicker(
                        label = "목표 등급",
                        selected = state.targetGrade,
                        options = listOf("AL", "IH", "IM3", "IM2", "IM1", "IL", "NH"),
                        onSelected = { viewModel.onTargetGradeChanged(it) }
                    )
                    Spacer(Modifier.height(12.dp))
                    var showApiKey by remember { mutableStateOf(false) }
                    // API Key
                    Text("Claude API Key", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = OPicColors.TextOnLight)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = state.claudeApiKey,
                        onValueChange = { viewModel.onApiKeyChanged(it) },
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
                    Text("API 키는 기기 내에만 저장됩니다.", fontSize = 11.sp, color = Color.Gray)
                    Spacer(Modifier.height(12.dp))
                    // 개인 프로필
                    Text("개인 프로필 (AI 모범 답안 개인화)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = OPicColors.TextOnLight)
                    Spacer(Modifier.height(6.dp))
                    ProfileField("직업", state.profileJob) { viewModel.onProfileJobChanged(it) }
                    ProfileField("취미", state.profileHobbies) { viewModel.onProfileHobbiesChanged(it) }
                    ProfileField("가족 관계", state.profileFamily) { viewModel.onProfileFamilyChanged(it) }
                    ProfileField("국적/거주지", state.profileCountry) { viewModel.onProfileCountryChanged(it) }
                    ProfileField("기타 (자유 서술)", state.profileBackground) { viewModel.onProfileBackgroundChanged(it) }
                }

                HorizontalDivider(color = OPicColors.Border)

                // ─── 4. 데이터 관리 (문제 편집 + 모든 CSV + DB 백업) ──
                CategorySection(
                    icon = Icons.Filled.Storage,
                    iconTint = ColorData,
                    title = "데이터 관리",
                    subtitle = "문제 편집 · CSV · DB 백업/복원",
                    expanded = expandedCategory == "data",
                    onToggle = { expandedCategory = if (expandedCategory == "data") null else "data" }
                ) {
                    // ── 문제 편집 ──────────────────────────────────────
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("문제 편집", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = OPicColors.TextOnLight)
                        TextButton(onClick = onStudyLink) {
                            Text("Study  ›", fontSize = 12.sp, color = OPicColors.Primary)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BottomSheetPicker(
                            label = "주제",
                            selected = state.selectedSet,
                            options = listOf("전체") + state.allSets,
                            onSelected = { viewModel.onSetFilterChanged(it) },
                            modifier = Modifier.width(120.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        IconButton(onClick = { viewModel.onPrevQuestion() }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.ChevronLeft, contentDescription = "이전", modifier = Modifier.size(20.dp))
                        }
                        Text(
                            text = state.editTitle.ifBlank { "—" },
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.onNextQuestion() }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.ChevronRight, contentDescription = "다음", modifier = Modifier.size(20.dp))
                        }
                        val idx = state.currentQuestionIndex
                        val total = state.filteredQuestions.size
                        Text(
                            text = if (total > 0) "${idx + 1}/$total" else "0/0",
                            fontSize = 11.sp, color = Color.Gray
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, OPicColors.Border, RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DataField("제목(Title)", state.editTitle, viewModel::onEditTitleChanged)
                        DataField("주제(Set)", state.editSet, viewModel::onEditSetChanged)
                        DataField("유형(Type)", state.editType, viewModel::onEditTypeChanged)
                        DataField("콤보(Combo)", state.editCombo, viewModel::onEditComboChanged)
                        DataField("Q_Audio", state.editQAudio, viewModel::onEditQAudioChanged)
                        DataField("A_Audio", state.editAAudio, viewModel::onEditAAudioChanged)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { showDeleteDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                ),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
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
                                Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("저장", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // ── 전체 백업/복원 ─────────────────────────────────
                    SectionTitle("전체 백업 (모든 데이터)")
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.backupDatabase() },
                            enabled = !state.isBackingUp,
                            colors = ButtonDefaults.buttonColors(containerColor = OPicColors.Primary),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (state.isBackingUp) {
                                androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                            } else {
                                Icon(Icons.Filled.Upload, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("DB 백업", fontSize = 12.sp)
                            }
                        }
                        androidx.compose.material3.OutlinedButton(
                            onClick = { dbRestoreLauncher.launch(arrayOf("*/*")) },
                            enabled = !state.isRestoring,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (state.isRestoring) {
                                androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = OPicColors.Primary)
                            } else {
                                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(14.dp), tint = OPicColors.Primary)
                                Spacer(Modifier.width(4.dp))
                                Text("DB 복원", fontSize = 12.sp, color = OPicColors.Primary)
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // ── Q&A 스크립트 CSV ───────────────────────────────
                    SectionTitle("Q&A 스크립트\n(question_text · answer_script · user_script · ai_answer)")
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.prepareQaCsvExport() },
                            enabled = !state.qaCsvExporting,
                            colors = ButtonDefaults.buttonColors(containerColor = OPicColors.Primary),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (state.qaCsvExporting) {
                                androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                            } else {
                                Icon(Icons.Filled.Upload, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("내보내기", fontSize = 12.sp)
                            }
                        }
                        androidx.compose.material3.OutlinedButton(
                            onClick = { qaImportLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "*/*")) },
                            enabled = !state.importingCsv,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (state.importingCsv) {
                                androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = OPicColors.Primary)
                            } else {
                                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(14.dp), tint = OPicColors.Primary)
                                Spacer(Modifier.width(4.dp))
                                Text("가져오기", fontSize = 12.sp, color = OPicColors.Primary)
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // ── 단어장 CSV ─────────────────────────────────────
                    SectionTitle("단어장 CSV")
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { vocabCsvImportLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "*/*")) },
                            colors = ButtonDefaults.buttonColors(containerColor = OPicColors.Primary),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("가져오기", fontSize = 12.sp)
                        }
                        Button(
                            onClick = { vocabCsvExportLauncher.launch("opic_vocabulary.csv") },
                            colors = ButtonDefaults.buttonColors(containerColor = OPicColors.Primary),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Upload, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("내보내기", fontSize = 12.sp)
                        }
                    }
                }

                HorizontalDivider(color = OPicColors.Border)
                Spacer(Modifier.height(24.dp))
            }

            SnackbarHost(hostState = snackbarHostState)
        }
    }
}

// ==================== 카테고리 섹션 (Accordion) ====================

@Composable
private fun CategorySection(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = OPicColors.TextOnLight)
                Text(subtitle, fontSize = 12.sp, color = Color.Gray)
            }
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(22.dp)
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(OPicColors.Surface)
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                content = content
            )
        }
    }
}

// ==================== 공통 컴포넌트 ====================

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = OPicColors.TextOnLight
    )
}

@Composable
private fun DataField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 12.sp) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp)
    )
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

@Composable
private fun FolderPickerRow(path: String, placeholder: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, OPicColors.Border, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = OPicColors.Primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = path.ifBlank { placeholder },
            fontSize = 13.sp,
            color = if (path.isBlank()) Color.Gray else OPicColors.TextOnLight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}
