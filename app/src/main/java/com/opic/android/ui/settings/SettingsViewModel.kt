package com.opic.android.ui.settings

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opic.android.audio.EngineOption
import com.opic.android.audio.TtsManager
import com.opic.android.audio.VoiceOption
import com.opic.android.data.local.dao.QuestionDao
import com.opic.android.data.local.dao.VocabularyDao
import com.opic.android.data.local.db.OPicDatabase
import com.opic.android.data.local.entity.QuestionEntity
import com.opic.android.data.local.entity.VocabularyEntity
import com.opic.android.data.prefs.AppPreferences
import com.opic.android.data.prefs.UserProfile
import com.opic.android.util.CsvUtil
import com.opic.android.util.SpeechAnalyzer
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

    // TTS Engine & Voice
    val availableEngines: List<EngineOption> = emptyList(),
    val selectedEnginePackage: String = "",
    val availableVoiceOptions: List<VoiceOption> = emptyList(),
    val selectedVoice: String = "",

    // Theme
    val themeMode: String = "light",

    // AI 설정
    val claudeApiKey: String = "",
    val profileJob: String = "",
    val profileHobbies: String = "",
    val profileFamily: String = "",
    val profileCountry: String = "",
    val profileBackground: String = "",

    // DB 백업/복원
    val dbBackupBytes: ByteArray? = null,
    val isBackingUp: Boolean = false,
    val isRestoring: Boolean = false,

    // Q&A CSV 내보내기/가져오기
    val qaCsvContent: String? = null,
    val qaCsvExporting: Boolean = false,
    val importingCsv: Boolean = false,

    // UI 상태 보존
    val expandedCategory: String? = null,

    // 피드백
    val snackbarMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPrefs: AppPreferences,
    private val questionDao: QuestionDao,
    private val vocabularyDao: VocabularyDao,
    private val ttsManager: TtsManager,
    private val database: OPicDatabase
) : ViewModel() {

    companion object {
        private const val TAG = "SettingsViewModel"
        private const val USER_ID = 1
    }

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState

    private var allQuestions: List<QuestionEntity> = emptyList()

    init {
        _uiState.update {
            it.copy(
                textSize = appPrefs.textSize,
                soundDir = appPrefs.soundDir,
                targetGrade = appPrefs.targetGrade,
                selectedVoice = appPrefs.selectedVoice,
                selectedEnginePackage = appPrefs.ttsEnginePackage,
                themeMode = appPrefs.themeMode,
                claudeApiKey = appPrefs.claudeApiKey,
                profileJob = appPrefs.userProfile.job,
                profileHobbies = appPrefs.userProfile.hobbies,
                profileFamily = appPrefs.userProfile.family,
                profileCountry = appPrefs.userProfile.country,
                profileBackground = appPrefs.userProfile.background
            )
        }
        viewModelScope.launch {
            loadQuestions()
            loadAvailableVoices()
        }
    }

    // ==================== TTS Voice ====================

    private fun loadAvailableVoices() {
        viewModelScope.launch {
            val savedEngine = appPrefs.ttsEnginePackage.ifBlank { null }
            ttsManager.init(savedEngine)
            // TTS 초기화 완료 대기 (최대 3초)
            repeat(30) {
                val options = ttsManager.getAvailableEnglishVoiceOptions()
                if (options.isNotEmpty()) {
                    _uiState.update {
                        it.copy(
                            availableVoiceOptions = options,
                            availableEngines = ttsManager.getInstalledEngines()
                        )
                    }
                    return@launch
                }
                kotlinx.coroutines.delay(100)
            }
            // 음성 없어도 엔진 목록은 표시
            _uiState.update { it.copy(availableEngines = ttsManager.getInstalledEngines()) }
        }
    }

    fun onEngineSelected(packageName: String) {
        val pkg = packageName.ifBlank { null }
        appPrefs.ttsEnginePackage = pkg ?: ""
        appPrefs.selectedVoice = ""   // 엔진 바뀌면 음성 초기화
        _uiState.update {
            it.copy(selectedEnginePackage = packageName, selectedVoice = "", availableVoiceOptions = emptyList())
        }
        viewModelScope.launch {
            ttsManager.init(pkg)
            repeat(30) {
                val options = ttsManager.getAvailableEnglishVoiceOptions()
                if (options.isNotEmpty()) {
                    _uiState.update { it.copy(availableVoiceOptions = options) }
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

    // ==================== AI 설정 ====================

    fun onApiKeyChanged(key: String) {
        appPrefs.claudeApiKey = key
        _uiState.update { it.copy(claudeApiKey = key) }
    }

    fun onProfileJobChanged(v: String)        { _uiState.update { it.copy(profileJob = v) };        saveProfile() }
    fun onProfileHobbiesChanged(v: String)    { _uiState.update { it.copy(profileHobbies = v) };    saveProfile() }
    fun onProfileFamilyChanged(v: String)     { _uiState.update { it.copy(profileFamily = v) };     saveProfile() }
    fun onProfileCountryChanged(v: String)    { _uiState.update { it.copy(profileCountry = v) };    saveProfile() }
    fun onProfileBackgroundChanged(v: String) { _uiState.update { it.copy(profileBackground = v) }; saveProfile() }

    private fun saveProfile() {
        val s = _uiState.value
        appPrefs.userProfile = UserProfile(
            job        = s.profileJob,
            hobbies    = s.profileHobbies,
            family     = s.profileFamily,
            country    = s.profileCountry,
            background = s.profileBackground
        )
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

    fun onCategoryToggle(key: String) {
        _uiState.update { it.copy(expandedCategory = if (it.expandedCategory == key) null else key) }
    }

    // ==================== Vocabulary CSV ====================

    fun exportVocabCsv(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val words = vocabularyDao.getAllWordsSync()
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
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

    // ==================== DB 백업/복원 ====================

    fun backupDatabase() {
        if (_uiState.value.isBackingUp) return
        _uiState.update { it.copy(isBackingUp = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bytes = context.getDatabasePath("opic.db").readBytes()
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isBackingUp = false, dbBackupBytes = bytes) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "DB 백업 실패", e)
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isBackingUp = false, snackbarMessage = "백업 실패: ${e.message}") }
                }
            }
        }
    }

    fun clearDbBackupBytes() = _uiState.update { it.copy(dbBackupBytes = null) }

    fun restoreDatabaseFromUri(uri: Uri) {
        if (_uiState.value.isRestoring) return
        _uiState.update { it.copy(isRestoring = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.readBytes()
                    ?: run {
                        withContext(Dispatchers.Main) {
                            _uiState.update { it.copy(isRestoring = false, snackbarMessage = "파일 읽기 실패") }
                        }
                        return@launch
                    }
                database.close()
                context.getDatabasePath("opic.db").writeBytes(bytes)
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isRestoring = false, snackbarMessage = "복원 완료 — 앱을 재시작해주세요") }
                }
            } catch (e: Exception) {
                Log.e(TAG, "DB 복원 실패", e)
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isRestoring = false, snackbarMessage = "복원 실패: ${e.message}") }
                }
            }
        }
    }

    // ==================== Q&A CSV 내보내기/가져오기 ====================

    fun prepareQaCsvExport() {
        if (_uiState.value.qaCsvExporting) return
        _uiState.update { it.copy(qaCsvExporting = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val rows = questionDao.getAllQuestionsWithProgressFull(USER_ID)
                val sb = StringBuilder()
                sb.append("question_id,title,set,type,question_text,answer_script,user_script,ai_answer,study_count,is_favorite,stt_grade\n")
                for (r in rows) {
                    val analysis = r.analysisResult?.let { SpeechAnalyzer.fromJson(it) }
                    sb.append("${r.questionId},")
                    sb.append("\"${CsvUtil.escape(r.title)}\",")
                    sb.append("\"${CsvUtil.escape(r.set ?: "")}\",")
                    sb.append("\"${CsvUtil.escape(r.type ?: "")}\",")
                    sb.append("\"${CsvUtil.escape(r.questionText ?: "")}\",")
                    sb.append("\"${CsvUtil.escape(r.answerScript ?: "")}\",")
                    sb.append("\"${CsvUtil.escape(r.userScript ?: "")}\",")
                    sb.append("\"${CsvUtil.escape(r.aiAnswer ?: "")}\",")
                    sb.append("${r.studyCount ?: 0},")
                    sb.append("${(r.isFavorite ?: 0) == 1},")
                    sb.append("${analysis?.grade ?: ""}")
                    sb.append("\n")
                }
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(qaCsvContent = sb.toString(), qaCsvExporting = false) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Q&A CSV 생성 실패", e)
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(qaCsvExporting = false, snackbarMessage = "CSV 생성 실패: ${e.message}") }
                }
            }
        }
    }

    fun clearQaCsvContent() = _uiState.update { it.copy(qaCsvContent = null) }

    fun importQaCsvFromUri(uri: Uri) {
        if (_uiState.value.importingCsv) return
        _uiState.update { it.copy(importingCsv = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val content = context.contentResolver.openInputStream(uri)?.use {
                    it.readBytes().toString(Charsets.UTF_8)
                } ?: run {
                    withContext(Dispatchers.Main) {
                        _uiState.update { it.copy(importingCsv = false, snackbarMessage = "파일 읽기 실패") }
                    }
                    return@launch
                }
                val rows = CsvUtil.parse(content)
                if (rows.size < 2) {
                    withContext(Dispatchers.Main) {
                        _uiState.update { it.copy(importingCsv = false, snackbarMessage = "유효한 데이터 없음") }
                    }
                    return@launch
                }
                val headers = rows[0]
                val idIdx = headers.indexOf("question_id")
                if (idIdx < 0) {
                    withContext(Dispatchers.Main) {
                        _uiState.update { it.copy(importingCsv = false, snackbarMessage = "question_id 컬럼이 없습니다") }
                    }
                    return@launch
                }
                val qtIdx = headers.indexOf("question_text")
                val asIdx = headers.indexOf("answer_script")
                val usIdx = headers.indexOf("user_script")
                val aiIdx = headers.indexOf("ai_answer")
                var count = 0
                for (row in rows.drop(1)) {
                    val id = row.getOrNull(idIdx)?.toIntOrNull() ?: continue
                    if (qtIdx >= 0) row.getOrNull(qtIdx)?.let { v -> if (v.isNotBlank()) questionDao.updateQuestionText(id, v) }
                    if (asIdx >= 0) row.getOrNull(asIdx)?.let { v -> if (v.isNotBlank()) questionDao.updateAnswerScript(id, v) }
                    if (usIdx >= 0) row.getOrNull(usIdx)?.let { v -> if (v.isNotBlank()) questionDao.updateUserScript(id, v) }
                    if (aiIdx >= 0) row.getOrNull(aiIdx)?.let { v -> if (v.isNotBlank()) questionDao.updateAiAnswer(id, v) }
                    count++
                }
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(importingCsv = false, snackbarMessage = "${count}개 문제 업데이트 완료") }
                    loadQuestions()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Q&A 가져오기 실패", e)
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(importingCsv = false, snackbarMessage = "가져오기 실패: ${e.message}") }
                }
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
