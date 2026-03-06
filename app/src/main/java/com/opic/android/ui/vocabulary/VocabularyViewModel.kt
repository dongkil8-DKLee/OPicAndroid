package com.opic.android.ui.vocabulary

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opic.android.ai.ClaudeApiService
import com.opic.android.data.local.dao.VocabularyDao
import com.opic.android.data.local.entity.VocabularyEntity
import com.opic.android.util.DictionaryApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class VocabularyUiState(
    val selectedTab: Int = 0, // 0=단어장, 1=암기장
    val allWords: List<VocabularyEntity> = emptyList(),
    val unmemorizedWords: List<VocabularyEntity> = emptyList(),
    val showAddDialog: Boolean = false,
    val showEditDialog: Boolean = false,
    val editingWord: VocabularyEntity? = null,
    val addWord: String = "",
    val addMeaning: String = "",
    val addMemo: String = "",
    val addPronunciation: String = "",
    val loadingPronunciation: Boolean = false,
    val expandedWordIds: Set<Int> = emptySet(), // 뜻 표시/숨김
    val aiLoadingWordId: Int? = null            // AI 자동완성 진행 중인 단어 ID
)

@HiltViewModel
class VocabularyViewModel @Inject constructor(
    private val vocabularyDao: VocabularyDao,
    private val claudeApiService: ClaudeApiService
) : ViewModel() {

    companion object {
        private const val TAG = "VocabularyViewModel"
    }

    private val _uiState = MutableStateFlow(VocabularyUiState())
    val uiState: StateFlow<VocabularyUiState> = _uiState

    init {
        observeWords()
    }

    private fun observeWords() {
        viewModelScope.launch {
            vocabularyDao.getAllWords().collect { words ->
                _uiState.update { it.copy(allWords = words) }
            }
        }
        viewModelScope.launch {
            vocabularyDao.getUnmemorizedWords().collect { words ->
                _uiState.update { it.copy(unmemorizedWords = words) }
            }
        }
    }

    fun selectTab(tab: Int) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun toggleWordExpanded(wordId: Int) {
        _uiState.update {
            val newSet = it.expandedWordIds.toMutableSet()
            if (wordId in newSet) newSet.remove(wordId) else newSet.add(wordId)
            it.copy(expandedWordIds = newSet)
        }
    }

    // ==================== 단어 추가 다이얼로그 ====================

    fun showAddDialog() {
        _uiState.update {
            it.copy(
                showAddDialog = true,
                addWord = "",
                addMeaning = "",
                addMemo = "",
                addPronunciation = ""
            )
        }
    }

    fun dismissAddDialog() {
        _uiState.update { it.copy(showAddDialog = false) }
    }

    fun updateAddWord(word: String) { _uiState.update { it.copy(addWord = word) } }
    fun updateAddMeaning(meaning: String) { _uiState.update { it.copy(addMeaning = meaning) } }
    fun updateAddMemo(memo: String) { _uiState.update { it.copy(addMemo = memo) } }
    fun updateAddPronunciation(pron: String) { _uiState.update { it.copy(addPronunciation = pron) } }

    /** 발음 자동 입력 */
    fun fetchPronunciation() {
        val word = _uiState.value.addWord.trim()
        if (word.isBlank()) return

        _uiState.update { it.copy(loadingPronunciation = true) }
        viewModelScope.launch {
            val pronunciation = DictionaryApi.fetchPronunciation(word)
            _uiState.update {
                it.copy(
                    addPronunciation = pronunciation ?: it.addPronunciation,
                    loadingPronunciation = false
                )
            }
        }
    }

    fun saveNewWord() {
        val state = _uiState.value
        val word = state.addWord.trim()
        if (word.isBlank()) return

        viewModelScope.launch {
            try {
                val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val entity = VocabularyEntity(
                    word = word.lowercase(),
                    meaning = state.addMeaning.ifBlank { null },
                    memo = state.addMemo.ifBlank { null },
                    pronunciation = state.addPronunciation.ifBlank { null },
                    createdAt = now
                )
                vocabularyDao.insertWord(entity)
                _uiState.update { it.copy(showAddDialog = false) }
                Log.d(TAG, "Word added: $word")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add word: $word", e)
            }
        }
    }

    // ==================== 단어 수정 다이얼로그 ====================

    fun showEditDialog(entity: VocabularyEntity) {
        _uiState.update {
            it.copy(
                showEditDialog = true,
                editingWord = entity,
                addWord = entity.word,
                addMeaning = entity.meaning ?: "",
                addMemo = entity.memo ?: "",
                addPronunciation = entity.pronunciation ?: ""
            )
        }
    }

    fun dismissEditDialog() {
        _uiState.update { it.copy(showEditDialog = false, editingWord = null) }
    }

    fun saveEditedWord() {
        val state = _uiState.value
        val entity = state.editingWord ?: return

        viewModelScope.launch {
            try {
                val updated = entity.copy(
                    word = state.addWord.trim().lowercase(),
                    meaning = state.addMeaning.ifBlank { null },
                    memo = state.addMemo.ifBlank { null },
                    pronunciation = state.addPronunciation.ifBlank { null }
                )
                vocabularyDao.updateWord(updated)
                _uiState.update { it.copy(showEditDialog = false, editingWord = null) }
                Log.d(TAG, "Word updated: ${updated.word}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update word", e)
            }
        }
    }

    // ==================== AI 자동완성 ====================

    /** 단어의 meaning/pronunciation/memo를 AI로 한번에 채웁니다 (기존 내용 덮어쓰기). */
    fun fillWordWithAi(entity: VocabularyEntity) {
        if (_uiState.value.aiLoadingWordId != null) return // 이미 진행 중
        _uiState.update { it.copy(aiLoadingWordId = entity.wordId) }
        viewModelScope.launch {
            val result = claudeApiService.generateVocabInfo(entity.word)
            result.onSuccess { info ->
                try {
                    val updated = entity.copy(
                        meaning       = info.meaning.ifBlank { entity.meaning },
                        pronunciation = info.pronunciation.ifBlank { entity.pronunciation },
                        memo          = info.memo.ifBlank { entity.memo }
                    )
                    vocabularyDao.updateWord(updated)
                    Log.d(TAG, "AI vocab filled: ${entity.word}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save AI vocab info", e)
                }
            }.onFailure { e ->
                Log.e(TAG, "AI vocab error: ${entity.word}", e)
            }
            _uiState.update { it.copy(aiLoadingWordId = null) }
        }
    }

    // ==================== 단어 삭제 ====================

    fun deleteWord(entity: VocabularyEntity) {
        viewModelScope.launch {
            try {
                vocabularyDao.deleteWord(entity)
                Log.d(TAG, "Word deleted: ${entity.word}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete word", e)
            }
        }
    }

    // ==================== 상태 토글 ====================

    fun toggleMemorized(wordId: Int) {
        viewModelScope.launch {
            vocabularyDao.toggleMemorized(wordId)
        }
    }

    fun toggleFavorite(wordId: Int) {
        viewModelScope.launch {
            vocabularyDao.toggleFavorite(wordId)
        }
    }
}
