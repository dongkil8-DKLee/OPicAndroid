package com.opic.android.ui.settings

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opic.android.audio.TtsManager
import com.opic.android.data.local.dao.QuestionDao
import com.opic.android.data.local.dao.VocabularyDao
import com.opic.android.data.local.entity.QuestionEntity
import com.opic.android.data.local.entity.VocabularyEntity
import com.opic.android.data.prefs.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class SettingsUiState(
    // 기존 필드
    val textSize: Int = 18,
    val levelImageDir: String = "",
    val soundDir: String = "",
    val targetGrade: String = "IM2",

    // 데이터 편집
    val allSets: List<String> = emptyList(),
    val selectedSet: String = "전체",
    val filteredQuestions: List<QuestionEntity> = emptyList(),
    val currentQuestionIndex: Int = 0,
    val editTitle: String = "",
    val editSet: String = "",
    val editType: String = "",
    val editCombo: String = "",
    val editQAudio: String = "",
    val editAAudio: String = "",

    // CSV
    val csvPath: String = "",

    // TTS Voice
    val availableVoices: List<String> = emptyList(),
    val selectedVoice: String = "",

    // Theme
    val themeMode: String = "light",

    // 피드백
    val snackbarMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPrefs: AppPreferences,
    private val questionDao: QuestionDao,
    private val vocabularyDao: VocabularyDao,
    private val ttsManager: TtsManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState

    private var allQuestions: List<QuestionEntity> = emptyList()

    init {
        _uiState.update {
            it.copy(
                textSize = appPrefs.textSize,
                levelImageDir = appPrefs.levelImageDir,
                soundDir = appPrefs.soundDir,
                targetGrade = appPrefs.targetGrade,
                selectedVoice = appPrefs.selectedVoice,
                themeMode = appPrefs.themeMode
            )
        }
        viewModelScope.launch {
            loadQuestions()
            loadAvailableVoices()
        }
    }

    // ==================== TTS Voice ====================

    private fun loadAvailableVoices() {
        // TTS init이 비동기이므로 약간 지연 후 조회
        viewModelScope.launch {
            ttsManager.init()
            // TTS 초기화 완료 대기 (최대 3초)
            repeat(30) {
                val voices = ttsManager.getAvailableEnglishVoices()
                if (voices.isNotEmpty()) {
                    _uiState.update { it.copy(availableVoices = voices) }
                    return@launch
                }
                kotlinx.coroutines.delay(100)
            }
        }
    }

    fun onVoiceSelected(voice: String) {
        val voiceName = if (voice == "Default") "" else voice
        appPrefs.selectedVoice = voiceName
        _uiState.update { it.copy(selectedVoice = voiceName) }
        if (voiceName.isNotBlank()) {
            ttsManager.setVoice(voiceName)
        }
    }

    // ==================== Theme ====================

    fun onThemeModeChanged(mode: String) {
        appPrefs.themeMode = mode
        _uiState.update { it.copy(themeMode = mode) }
    }

    // ==================== 기존 설정 ====================

    fun onTextSizeChanged(size: Int) {
        appPrefs.textSize = size
        _uiState.update { it.copy(textSize = size) }
    }

    fun onLevelImageDirChanged(dir: String) {
        val realPath = convertTreeUriToFilePath(dir)
        appPrefs.levelImageDir = realPath
        _uiState.update { it.copy(levelImageDir = realPath) }
    }

    fun onSoundDirChanged(dir: String) {
        val realPath = convertTreeUriToFilePath(dir)
        appPrefs.soundDir = realPath
        _uiState.update { it.copy(soundDir = realPath) }
    }

    /**
     * SAF OpenDocumentTree URI(content://...) → 실제 파일 경로(/storage/emulated/0/...) 변환.
     * primary 내부 저장소에서만 동작. SD카드 등 외부 저장소는 원본 URI 그대로 반환.
     */
    private fun convertTreeUriToFilePath(uriStr: String): String {
        return try {
            val uri = Uri.parse(uriStr)
            if (DocumentsContract.isTreeUri(uri)) {
                val docId = DocumentsContract.getTreeDocumentId(uri)
                val parts = docId.split(":")
                val type = parts.getOrElse(0) { "" }
                val relativePath = parts.getOrElse(1) { "" }
                if (type.equals("primary", ignoreCase = true)) {
                    val base = Environment.getExternalStorageDirectory().absolutePath
                    val result = if (relativePath.isNotEmpty()) "$base/$relativePath" else base
                    Log.d("SettingsVM", "SAF URI 변환: $uriStr → $result")
                    return result
                }
            }
            Log.w("SettingsVM", "SAF URI 변환 불가 (primary 아님): $uriStr")
            uriStr
        } catch (e: Exception) {
            Log.e("SettingsVM", "SAF URI 변환 오류: $uriStr", e)
            uriStr
        }
    }

    fun onTargetGradeChanged(grade: String) {
        appPrefs.targetGrade = grade
        _uiState.update { it.copy(targetGrade = grade) }
    }

    // ==================== 데이터 편집 ====================

    private suspend fun loadQuestions() {
        allQuestions = questionDao.getAllQuestionsOnce()
        val sets = allQuestions.mapNotNull { it.set }.distinct().sorted()
        val filtered = allQuestions
        _uiState.update { state ->
            state.copy(
                allSets = sets,
                filteredQuestions = filtered,
                currentQuestionIndex = 0
            )
        }
        syncEditFields(0, allQuestions)
    }

    fun onSetFilterChanged(set: String) {
        val filtered = if (set == "전체") allQuestions
                       else allQuestions.filter { it.set == set }
        _uiState.update { it.copy(selectedSet = set, filteredQuestions = filtered, currentQuestionIndex = 0) }
        syncEditFields(0, filtered)
    }

    fun onPrevQuestion() {
        val state = _uiState.value
        val newIndex = (state.currentQuestionIndex - 1).coerceAtLeast(0)
        _uiState.update { it.copy(currentQuestionIndex = newIndex) }
        syncEditFields(newIndex, state.filteredQuestions)
    }

    fun onNextQuestion() {
        val state = _uiState.value
        val newIndex = (state.currentQuestionIndex + 1).coerceAtMost(state.filteredQuestions.size - 1)
        _uiState.update { it.copy(currentQuestionIndex = newIndex) }
        syncEditFields(newIndex, state.filteredQuestions)
    }

    private fun syncEditFields(index: Int, questions: List<QuestionEntity>) {
        val q = questions.getOrNull(index) ?: return
        _uiState.update {
            it.copy(
                editTitle = q.title,
                editSet = q.set ?: "",
                editType = q.type ?: "",
                editCombo = q.combo ?: "",
                editQAudio = q.questionAudio ?: "",
                editAAudio = q.answerAudio ?: ""
            )
        }
    }

    fun onEditTitleChanged(v: String)  { _uiState.update { it.copy(editTitle = v) } }
    fun onEditSetChanged(v: String)    { _uiState.update { it.copy(editSet = v) } }
    fun onEditTypeChanged(v: String)   { _uiState.update { it.copy(editType = v) } }
    fun onEditComboChanged(v: String)  { _uiState.update { it.copy(editCombo = v) } }
    fun onEditQAudioChanged(v: String) { _uiState.update { it.copy(editQAudio = v) } }
    fun onEditAAudioChanged(v: String) { _uiState.update { it.copy(editAAudio = v) } }

    fun saveQuestion() = viewModelScope.launch {
        val state = _uiState.value
        val current = state.filteredQuestions.getOrNull(state.currentQuestionIndex) ?: return@launch
        val updated = current.copy(
            title = state.editTitle,
            set = state.editSet.ifBlank { null },
            type = state.editType.ifBlank { null },
            combo = state.editCombo.ifBlank { null },
            questionAudio = state.editQAudio.ifBlank { null },
            answerAudio = state.editAAudio.ifBlank { null }
        )
        questionDao.updateQuestion(updated)
        _uiState.update { it.copy(snackbarMessage = "저장되었습니다.") }
        loadQuestions()
    }

    fun deleteQuestion() = viewModelScope.launch {
        val state = _uiState.value
        val current = state.filteredQuestions.getOrNull(state.currentQuestionIndex) ?: return@launch
        questionDao.deleteById(current.questionId)
        _uiState.update { it.copy(snackbarMessage = "삭제되었습니다.") }
        loadQuestions()
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    // ==================== CSV (Questions) ====================

    fun onCsvPathChanged(path: String) {
        _uiState.update { it.copy(csvPath = path) }
    }

    fun exportCsv(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val questions = questionDao.getAllQuestionsOnce()
            context.contentResolver.openOutputStream(uri)?.use { out ->
                val writer = out.bufferedWriter()
                writer.write("question_id,title,set,type,combo,question_text,answer_script,question_audio,answer_audio,user_script\n")
                questions.forEach { q ->
                    val row = listOf(
                        q.questionId.toString(),
                        q.title.csvEscape(),
                        (q.set ?: "").csvEscape(),
                        (q.type ?: "").csvEscape(),
                        (q.combo ?: "").csvEscape(),
                        (q.questionText ?: "").csvEscape(),
                        (q.answerScript ?: "").csvEscape(),
                        (q.questionAudio ?: "").csvEscape(),
                        (q.answerAudio ?: "").csvEscape(),
                        (q.userScript ?: "").csvEscape()
                    ).joinToString(",")
                    writer.write("$row\n")
                }
                writer.flush()
            }
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(snackbarMessage = "CSV 내보내기 완료") }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(snackbarMessage = "내보내기 실패: ${e.message}") }
            }
        }
    }

    fun importCsv(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { ins ->
                val lines = ins.bufferedReader().readLines()
                if (lines.isEmpty()) return@use
                val header = lines.first().split(",")
                val idxId    = header.indexOf("question_id")
                val idxTitle = header.indexOf("title")
                val idxSet   = header.indexOf("set")
                val idxType  = header.indexOf("type")
                val idxCombo = header.indexOf("combo")
                val idxQText = header.indexOf("question_text")
                val idxAScript = header.indexOf("answer_script")
                val idxQAudio  = header.indexOf("question_audio")
                val idxAAudio  = header.indexOf("answer_audio")
                val idxUScript = header.indexOf("user_script")

                lines.drop(1).forEach { line ->
                    val cols = parseCsvLine(line)
                    val id = cols.getOrNull(idxId)?.toIntOrNull() ?: return@forEach
                    val entity = QuestionEntity(
                        questionId  = id,
                        title       = cols.getOrElse(idxTitle) { "" },
                        set         = cols.getOrNull(idxSet)?.ifBlank { null },
                        type        = cols.getOrNull(idxType)?.ifBlank { null },
                        combo       = cols.getOrNull(idxCombo)?.ifBlank { null },
                        questionText  = cols.getOrNull(idxQText)?.ifBlank { null },
                        answerScript  = cols.getOrNull(idxAScript)?.ifBlank { null },
                        questionAudio = cols.getOrNull(idxQAudio)?.ifBlank { null },
                        answerAudio   = cols.getOrNull(idxAAudio)?.ifBlank { null },
                        userScript    = cols.getOrNull(idxUScript)?.ifBlank { null }
                    )
                    questionDao.upsert(entity)
                }
            }
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(snackbarMessage = "CSV 가져오기 완료") }
                loadQuestions()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(snackbarMessage = "가져오기 실패: ${e.message}") }
            }
        }
    }

    // ==================== Vocabulary CSV ====================

    fun exportVocabCsv(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val words = vocabularyDao.getAllWordsSync()
            context.contentResolver.openOutputStream(uri)?.use { out ->
                val writer = out.bufferedWriter()
                writer.write("wordId,word,meaning,memo,pronunciation,isMemorized,isFavorite,sourceQuestionId,createdAt\n")
                words.forEach { w ->
                    val row = listOf(
                        w.wordId.toString(),
                        w.word.csvEscape(),
                        (w.meaning ?: "").csvEscape(),
                        (w.memo ?: "").csvEscape(),
                        (w.pronunciation ?: "").csvEscape(),
                        if (w.isMemorized) "1" else "0",
                        if (w.isFavorite) "1" else "0",
                        (w.sourceQuestionId?.toString() ?: ""),
                        (w.createdAt ?: "").csvEscape()
                    ).joinToString(",")
                    writer.write("$row\n")
                }
                writer.flush()
            }
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(snackbarMessage = "단어장 CSV 내보내기 완료") }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(snackbarMessage = "단어장 내보내기 실패: ${e.message}") }
            }
        }
    }

    fun importVocabCsv(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { ins ->
                val lines = ins.bufferedReader().readLines()
                if (lines.isEmpty()) return@use
                val header = lines.first().split(",")
                val idxWord = header.indexOf("word")
                val idxMeaning = header.indexOf("meaning")
                val idxMemo = header.indexOf("memo")
                val idxPronunciation = header.indexOf("pronunciation")
                val idxIsMemorized = header.indexOf("isMemorized")
                val idxIsFavorite = header.indexOf("isFavorite")
                val idxSourceQId = header.indexOf("sourceQuestionId")
                val idxCreatedAt = header.indexOf("createdAt")

                var imported = 0
                lines.drop(1).forEach { line ->
                    val cols = parseCsvLine(line)
                    val word = cols.getOrNull(idxWord)?.ifBlank { null } ?: return@forEach
                    val entity = VocabularyEntity(
                        word = word,
                        meaning = cols.getOrNull(idxMeaning)?.ifBlank { null },
                        memo = cols.getOrNull(idxMemo)?.ifBlank { null },
                        pronunciation = cols.getOrNull(idxPronunciation)?.ifBlank { null },
                        isMemorized = cols.getOrNull(idxIsMemorized) == "1",
                        isFavorite = cols.getOrNull(idxIsFavorite) == "1",
                        sourceQuestionId = cols.getOrNull(idxSourceQId)?.toIntOrNull(),
                        createdAt = cols.getOrNull(idxCreatedAt)?.ifBlank { null }
                    )
                    vocabularyDao.insertWord(entity)
                    imported++
                }

                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(snackbarMessage = "단어장 가져오기 완료 ($imported 단어)") }
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(snackbarMessage = "단어장 가져오기 실패: ${e.message}") }
            }
        }
    }

    // ==================== 유틸 ====================

    private fun String.csvEscape(): String {
        return if (contains(',') || contains('"') || contains('\n')) {
            "\"${replace("\"", "\"\"")}\""
        } else this
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var inQuotes = false
        val current = StringBuilder()
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && !inQuotes -> inQuotes = true
                c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"')
                    i++
                }
                c == '"' && inQuotes -> inQuotes = false
                c == ',' && !inQuotes -> {
                    result.add(current.toString())
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        result.add(current.toString())
        return result
    }
}
