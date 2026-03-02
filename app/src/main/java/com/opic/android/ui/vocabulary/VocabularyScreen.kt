package com.opic.android.ui.vocabulary

import android.content.Intent
import android.net.Uri
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.opic.android.data.local.entity.VocabularyEntity
import com.opic.android.ui.theme.OPicColors
import java.util.Locale

private val CardBg = Color(0xFFF5F5F5)

@Composable
fun VocabularyScreen(
    viewModel: VocabularyViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // TTS 초기화 (안정화: 언어 fallback + dispose 방어)
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val res = engine?.setLanguage(Locale.US)
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    engine?.language = Locale.ENGLISH
                }
            }
        }
        tts = engine
        onDispose {
            runCatching { engine?.stop() }
            runCatching { engine?.shutdown() }
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // ===== 탭 + Add 버튼 =====
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TabRow(
                    selectedTabIndex = state.selectedTab,
                    modifier = Modifier.weight(1f)
                ) {
                    Tab(
                        selected = state.selectedTab == 0,
                        onClick = { viewModel.selectTab(0) },
                        text = { Text("단어장", fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = state.selectedTab == 1,
                        onClick = { viewModel.selectTab(1) },
                        text = { Text("암기장", fontWeight = FontWeight.Bold) }
                    )
                }
                IconButton(
                    onClick = { viewModel.showAddDialog() },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "단어 추가", tint = OPicColors.Primary)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ===== 단어 목록 =====
            val words = if (state.selectedTab == 0) state.allWords else state.unmemorizedWords

            if (words.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (state.selectedTab == 0) "단어장이 비어있습니다.\n+ 버튼으로 단어를 추가하세요."
                        else "암기하지 못한 단어가 없습니다.",
                        color = OPicColors.DisabledBg,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(words, key = { it.wordId }) { word ->
                        VocabularyCard(
                            word = word,
                            isExpanded = word.wordId in state.expandedWordIds,
                            onTap = { viewModel.toggleWordExpanded(word.wordId) },
                            onToggleMemorized = { viewModel.toggleMemorized(word.wordId) },
                            onToggleFavorite = { viewModel.toggleFavorite(word.wordId) },
                            onEdit = { viewModel.showEditDialog(word) },
                            onDelete = { viewModel.deleteWord(word) },
                            onYouglish = {
                                val url = "https://youglish.com/pronounce/${word.word}/english"
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                }
                            },
                            onTts = {
                                runCatching {
                                    tts?.speak(word.word, TextToSpeech.QUEUE_FLUSH, null, "vocab_${word.wordId}")
                                }
                            }
                        )
                    }
                }
            }
        }

        // ===== 추가 다이얼로그 =====
        if (state.showAddDialog) {
            WordDialog(
                title = "단어 추가",
                word = state.addWord,
                meaning = state.addMeaning,
                memo = state.addMemo,
                pronunciation = state.addPronunciation,
                loadingPronunciation = state.loadingPronunciation,
                onWordChange = { viewModel.updateAddWord(it) },
                onMeaningChange = { viewModel.updateAddMeaning(it) },
                onMemoChange = { viewModel.updateAddMemo(it) },
                onPronunciationChange = { viewModel.updateAddPronunciation(it) },
                onFetchPronunciation = { viewModel.fetchPronunciation() },
                onConfirm = { viewModel.saveNewWord() },
                onDismiss = { viewModel.dismissAddDialog() }
            )
        }

        // ===== 수정 다이얼로그 =====
        if (state.showEditDialog) {
            WordDialog(
                title = "단어 수정",
                word = state.addWord,
                meaning = state.addMeaning,
                memo = state.addMemo,
                pronunciation = state.addPronunciation,
                loadingPronunciation = state.loadingPronunciation,
                onWordChange = { viewModel.updateAddWord(it) },
                onMeaningChange = { viewModel.updateAddMeaning(it) },
                onMemoChange = { viewModel.updateAddMemo(it) },
                onPronunciationChange = { viewModel.updateAddPronunciation(it) },
                onFetchPronunciation = { viewModel.fetchPronunciation() },
                onConfirm = { viewModel.saveEditedWord() },
                onDismiss = { viewModel.dismissEditDialog() }
            )
        }
    }
}

// ==================== 단어 카드 ====================

@Composable
private fun VocabularyCard(
    word: VocabularyEntity,
    isExpanded: Boolean,
    onTap: () -> Unit,
    onToggleMemorized: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onYouglish: () -> Unit,
    onTts: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTap() },
        colors = CardDefaults.cardColors(
            containerColor = CardBg
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // 상단: 단어 + 암기상태 뱃지 + 더보기
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = word.word,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = OPicColors.TextOnLight,
                    modifier = Modifier.weight(1f)
                )

                // 암기상태 뱃지
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (word.isMemorized) Color(0xFF2ECC71).copy(alpha = 0.2f)
                    else Color(0xFFE74C3C).copy(alpha = 0.2f),
                    modifier = Modifier.clickable { onToggleMemorized() }
                ) {
                    Text(
                        text = if (word.isMemorized) "쉬워요" else "어려워요",
                        fontSize = 11.sp,
                        color = if (word.isMemorized) Color(0xFF2ECC71) else Color(0xFFE74C3C),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // 더보기 메뉴
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = "더보기",
                            tint = OPicColors.TextOnLight,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("수정") },
                            onClick = { showMenu = false; onEdit() }
                        )
                        DropdownMenuItem(
                            text = { Text("삭제", color = Color.Red) },
                            onClick = { showMenu = false; onDelete() }
                        )
                    }
                }
            }

            // 뜻 (탭하면 표시/숨기기)
            if (isExpanded && !word.meaning.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = word.meaning,
                    fontSize = 14.sp,
                    color = OPicColors.TextOnLight.copy(alpha = 0.8f)
                )
            }

            // 발음
            if (!word.pronunciation.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = word.pronunciation,
                    fontSize = 13.sp,
                    color = OPicColors.TextOnLight.copy(alpha = 0.6f)
                )
            }

            // 메모
            if (isExpanded && !word.memo.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = word.memo,
                    fontSize = 12.sp,
                    color = OPicColors.TextOnLight.copy(alpha = 0.5f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 하단: 즐겨찾기 + youglish + TTS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.weight(1f))

                // 즐겨찾기
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (word.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = "즐겨찾기",
                        tint = if (word.isFavorite) OPicColors.TimerOrange else OPicColors.TextOnLight.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Youglish
                IconButton(
                    onClick = onYouglish,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Filled.OndemandVideo,
                        contentDescription = "Youglish",
                        tint = OPicColors.TextOnLight.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // TTS
                IconButton(
                    onClick = onTts,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "TTS",
                        tint = OPicColors.TextOnLight.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// ==================== 단어 추가/수정 다이얼로그 ====================

@Composable
private fun WordDialog(
    title: String,
    word: String,
    meaning: String,
    memo: String,
    pronunciation: String,
    loadingPronunciation: Boolean,
    onWordChange: (String) -> Unit,
    onMeaningChange: (String) -> Unit,
    onMemoChange: (String) -> Unit,
    onPronunciationChange: (String) -> Unit,
    onFetchPronunciation: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = word,
                    onValueChange = onWordChange,
                    label = { Text("단어 *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = meaning,
                    onValueChange = onMeaningChange,
                    label = { Text("뜻") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = memo,
                    onValueChange = onMemoChange,
                    label = { Text("메모/설명") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = pronunciation,
                        onValueChange = onPronunciationChange,
                        label = { Text("발음") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    if (loadingPronunciation) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        TextButton(onClick = onFetchPronunciation) {
                            Text("자동", fontSize = 12.sp, color = OPicColors.Primary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = word.isNotBlank()
            ) {
                Text("저장", color = OPicColors.Primary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}
