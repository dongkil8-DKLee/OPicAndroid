package com.opic.android.domain

import android.util.Log
import com.opic.android.data.local.dao.QuestionDao
import com.opic.android.data.local.entity.QuestionEntity
import com.opic.android.data.prefs.SurveyPreferences
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Python QuestionGenerator.generate_test_set() 1:1 이식.
 *
 * 난이도별 조합:
 *   1-2: Self Intro(1) + 선택×2(6) + 돌발×1(3) + RP×1(2q) = 12
 *   3-4: Self Intro(1) + 선택×2(6) + 돌발×1(3) + RP×1(3q) + Ad×1(2) = 15
 *   5-6: Self Intro(1) + 선택×1(3) + 돌발×2(6) + RP×1(3q) + Ad×1(2) = 15
 */
@Singleton
class QuestionGenerator @Inject constructor(
    private val questionDao: QuestionDao,
    private val surveyPrefs: SurveyPreferences
) {
    companion object {
        private const val TAG = "QuestionGenerator"
    }

    /** 문제 세트 생성 결과 */
    data class TestQuestion(
        val questionId: Int,
        val title: String,
        val questionText: String?,
        val answerScript: String?,
        val questionAudio: String?,  // DB link_name (확장자 없음)
        val answerAudio: String?,
        val userScript: String?,
        val type: String,
        val set: String,
        val combo: String,
        var userAudioPath: String? = null  // 녹음 후 설정
    )

    // type → set → combo → [QuestionEntity]
    private data class StructuredSets(
        val map: Map<String, Map<String, Map<String, List<QuestionEntity>>>>
    )

    suspend fun generate(difficulty: Int): List<TestQuestion> {
        val result = mutableListOf<TestQuestion>()

        // Step 1: Self Introduction (Q1)
        val intro = questionDao.getSelfIntroduction()
        if (intro != null) {
            result.add(TestQuestion(
                questionId = intro.questionId,
                title = intro.title,
                questionText = intro.questionText,
                answerScript = intro.answerScript,
                questionAudio = intro.questionAudio,
                answerAudio = intro.answerAudio,
                userScript = intro.userScript,
                type = "Intro",
                set = "Self_Introduction",
                combo = "00"
            ))
        }

        // Step 2: 문제 구조화
        val allQuestions = questionDao.getAllValidQuestions()
        val structured = buildStructuredSets(allQuestions)

        // Step 3: 난이도별 조합 결정
        val config = when (difficulty) {
            1, 2 -> DifficultyConfig(numSurvey = 2, numRandom = 1, numRp = 1, rpTake = 2, numAdv = 0)
            3, 4 -> DifficultyConfig(numSurvey = 2, numRandom = 1, numRp = 1, rpTake = 3, numAdv = 1)
            5, 6 -> DifficultyConfig(numSurvey = 1, numRandom = 2, numRp = 1, rpTake = 3, numAdv = 1)
            else -> DifficultyConfig(numSurvey = 2, numRandom = 1, numRp = 1, rpTake = 3, numAdv = 1)
        }

        val usedSets = mutableSetOf<String>()
        val usedComboKeys = mutableSetOf<Triple<String, String, String>>()
        val preferredTopics = surveyPrefs.selectedTopics

        // Step 4: 선택 (서베이 주제 우선)
        repeat(config.numSurvey) {
            pickComboSet(structured, "선택", usedSets, usedComboKeys, preferredTopics)?.let { (questions, _) ->
                result.addAll(questions)
            }
        }

        // Step 5: 돌발
        repeat(config.numRandom) {
            pickComboSet(structured, "돌발", usedSets, usedComboKeys)?.let { (questions, _) ->
                result.addAll(questions)
            }
        }

        // Step 6: RP (rpTake 개수만 사용)
        repeat(config.numRp) {
            pickComboSet(structured, "RP", usedSets, usedComboKeys)?.let { (questions, _) ->
                result.addAll(questions.take(config.rpTake))
            }
        }

        // Step 7: Ad
        repeat(config.numAdv) {
            pickComboSet(structured, "Ad", usedSets, usedComboKeys)?.let { (questions, _) ->
                result.addAll(questions)
            }
        }

        val expected = if (difficulty <= 2) 12 else 15
        Log.d(TAG, "생성 완료: difficulty=$difficulty, questions=${result.size} (expected=$expected)")
        return result
    }

    private fun buildStructuredSets(questions: List<QuestionEntity>): StructuredSets {
        val map = mutableMapOf<String, MutableMap<String, MutableMap<String, MutableList<QuestionEntity>>>>()

        for (q in questions) {
            val type = q.type ?: continue
            val set = q.set ?: continue
            val combo = q.combo ?: continue

            map.getOrPut(type) { mutableMapOf() }
                .getOrPut(set) { mutableMapOf() }
                .getOrPut(combo) { mutableListOf() }
                .add(q)
        }

        // combo 내 정렬 (questionAudio 기준, Python sorted by link_name)
        for ((_, sets) in map) {
            for ((_, combos) in sets) {
                for ((key, list) in combos) {
                    combos[key] = list.sortedBy { it.questionAudio ?: "" }.toMutableList()
                }
            }
        }

        return StructuredSets(map)
    }

    private fun pickComboSet(
        structured: StructuredSets,
        qType: String,
        usedSets: MutableSet<String>,
        usedComboKeys: MutableSet<Triple<String, String, String>>,
        preferredTopics: Set<String> = emptySet()
    ): Pair<List<TestQuestion>, String>? {
        val typeSets = structured.map[qType] ?: return null

        val availableTopics = typeSets.keys.filter { it !in usedSets }
        if (availableTopics.isEmpty()) return null

        // 서베이에서 선택된 주제 우선
        val preferred = availableTopics.filter { it in preferredTopics }
        val chosenTopic = if (preferred.isNotEmpty()) preferred.random() else availableTopics.random()
        val combos = typeSets[chosenTopic] ?: return null

        val availableCombos = combos.keys.filter { comboId ->
            Triple(qType, chosenTopic, comboId) !in usedComboKeys
        }
        if (availableCombos.isEmpty()) return null

        val chosenCombo = availableCombos.random()
        val entities = combos[chosenCombo] ?: return null

        usedSets.add(chosenTopic)
        usedComboKeys.add(Triple(qType, chosenTopic, chosenCombo))

        val questions = entities.map { e ->
            TestQuestion(
                questionId = e.questionId,
                title = e.title,
                questionText = e.questionText,
                answerScript = e.answerScript,
                questionAudio = e.questionAudio,
                answerAudio = e.answerAudio,
                userScript = e.userScript,
                type = qType,
                set = chosenTopic,
                combo = chosenCombo
            )
        }

        return questions to chosenTopic
    }

    private data class DifficultyConfig(
        val numSurvey: Int,
        val numRandom: Int,
        val numRp: Int,
        val rpTake: Int,
        val numAdv: Int
    )
}
