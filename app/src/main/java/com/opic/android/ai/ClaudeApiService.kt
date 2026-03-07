package com.opic.android.ai

import android.util.Log
import com.opic.android.data.prefs.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

data class GeneratedQuestion(
    val title: String,
    val questionText: String,
    val answerScript: String
)

data class VocabInfo(
    val meaning: String,       // "n. 어휘, 단어; syn: term, expression"
    val pronunciation: String, // "/vəˈkæbjʊləri/ (보캐뷸러리)"
    val memo: String           // "[예문] ...\n[한글] ...\n[OPic] ..."
)

/**
 * Claude API HTTP 클라이언트.
 * 추가 의존성 없이 Android 내장 HttpURLConnection 사용.
 * 모델: claude-haiku-4-5 (빠르고 저렴, OPic 피드백에 적합)
 */
@Singleton
class ClaudeApiService @Inject constructor(
    private val appPrefs: AppPreferences
) {
    companion object {
        private const val TAG = "ClaudeApiService"
        private const val API_URL = "https://api.anthropic.com/v1/messages"
        private const val MODEL = "claude-haiku-4-5-20251001"
        private const val MAX_TOKENS = 1024
    }

    /**
     * OPic 모범 답안 생성.
     * questionText + 개인 프로필 + targetGrade → Claude → 영어 모범 답변
     */
    suspend fun generateModelAnswer(
        questionText: String,
        targetGrade: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = appPrefs.claudeApiKey
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                Exception("Claude API 키가 설정되지 않았습니다.\nSettings > AI 설정에서 입력해주세요.")
            )
        }

        val profile = appPrefs.userProfile
        val profilePart = buildString {
            if (profile.job.isNotBlank())        appendLine("- 직업: ${profile.job}")
            if (profile.hobbies.isNotBlank())    appendLine("- 취미: ${profile.hobbies}")
            if (profile.family.isNotBlank())     appendLine("- 가족: ${profile.family}")
            if (profile.country.isNotBlank())    appendLine("- 국적/거주지: ${profile.country}")
            if (profile.background.isNotBlank()) appendLine("- 기타: ${profile.background}")
        }

        val prompt = buildString {
            appendLine("OPic 목표 등급: $targetGrade")
            appendLine("문제: $questionText")
            if (profilePart.isNotBlank()) {
                appendLine()
                appendLine("개인 정보:")
                append(profilePart)
            }
            appendLine()
            append("위 정보를 바탕으로 $targetGrade 수준의 OPic 모범 답변을 영어로 작성해주세요. 5~7문장, 자연스러운 구어체, 개인화된 내용 포함.")
        }

        callClaudeApi(
            apiKey = apiKey,
            systemPrompt = "You are an OPic (Oral Proficiency Interview for Computer) English speaking expert. " +
                "Generate natural, personalized model answers in English suitable for the requested OPic grade level.",
            userMessage = prompt
        )
    }

    /**
     * 단어 정보 자동 입력.
     * word → Claude → VocabInfo (meaning, pronunciation, memo)
     */
    suspend fun generateVocabInfo(word: String): Result<VocabInfo> = withContext(Dispatchers.IO) {
        val apiKey = appPrefs.claudeApiKey
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                Exception("Claude API 키가 설정되지 않았습니다.\nSettings > AI 설정에서 입력해주세요.")
            )
        }

        val grade = appPrefs.targetGrade

        val prompt = """
단어: $word

다음 형식으로 정확히 답하세요. 라벨 외 다른 내용은 쓰지 마세요:
MEANING: [품사약어]. [한국어 뜻]; syn: [영어 동의어1], [영어 동의어2]
PRONUNCIATION: /[IPA]/
OPIC_EN: [OPic $grade 수준에서 직접 쓸 수 있는 영어 예문 1문장]
OPIC_KO: [위 OPIC_EN의 한국어 번역]
        """.trimIndent()

        val raw = callClaudeApi(
            apiKey = apiKey,
            systemPrompt = "You are an English dictionary and OPic speaking expert. Answer strictly in the requested format.",
            userMessage = prompt
        )
        raw.map { text ->
            fun extract(label: String) = text.lines()
                .firstOrNull { it.startsWith("$label:") }
                ?.removePrefix("$label:")?.trim() ?: ""
            val opicEn = extract("OPIC_EN")
            val opicKo = extract("OPIC_KO")
            val memo = buildString {
                if (opicEn.isNotBlank()) appendLine("[예문 Opic $grade] $opicEn")
                if (opicKo.isNotBlank()) append("[한글] $opicKo")
            }.trim()
            VocabInfo(
                meaning       = extract("MEANING"),
                pronunciation = extract("PRONUNCIATION"),
                memo          = memo
            )
        }
    }

    /** API Key 설정 여부 */
    fun hasApiKey(): Boolean = appPrefs.claudeApiKey.isNotBlank()

    /**
     * OPic 테스트 문제 자동 생성 (서베이 선택 주제에 DB 문제 없을 때).
     * topic + type + grade → Claude → count개 GeneratedQuestion
     */
    suspend fun generateTestQuestions(
        topic: String,
        type: String,
        targetGrade: String,
        count: Int = 3
    ): Result<List<GeneratedQuestion>> = withContext(Dispatchers.IO) {
        val apiKey = appPrefs.claudeApiKey
        if (apiKey.isBlank()) {
            return@withContext Result.failure(Exception("API 키 미설정"))
        }

        val prompt = buildString {
            appendLine("OPic 토픽: $topic")
            appendLine("유형: $type")
            appendLine("목표 등급: $targetGrade")
            appendLine()
            appendLine("다음 형식으로 정확히 ${count}개의 OPic 문제를 생성하세요.")
            appendLine("각 항목은 반드시 한 줄로 작성하세요. 라벨 외 다른 내용은 쓰지 마세요.")
            appendLine()
            for (i in 1..count) {
                appendLine("Q${i}_TITLE: [한국어 제목 5~10자]")
                appendLine("Q${i}_TEXT: [영어 OPic 질문 1문장, Tell me about / Describe / Have you ever 형식]")
                appendLine("Q${i}_ANSWER: [${targetGrade} 수준 영어 모범 답변, 5~7문장을 이어서 한 줄로]")
                if (i < count) appendLine()
            }
        }

        val raw = callClaudeApi(
            apiKey = apiKey,
            systemPrompt = "You are an OPic test question designer. Generate OPic questions and model answers strictly in the requested format. Each field must be on a single line.",
            userMessage = prompt,
            maxTokens = 2048
        )
        raw.map { text ->
            val lines = text.lines()
            fun extractLine(label: String) = lines
                .firstOrNull { it.trimStart().startsWith("$label:") }
                ?.substringAfter(":")?.trim() ?: ""
            (1..count).map { i ->
                GeneratedQuestion(
                    title = extractLine("Q${i}_TITLE"),
                    questionText = extractLine("Q${i}_TEXT"),
                    answerScript = extractLine("Q${i}_ANSWER")
                )
            }.filter { it.title.isNotBlank() && it.questionText.isNotBlank() }
        }
    }

    /**
     * STT 결과 피드백 생성.
     * questionText + sttResult + targetGrade → Claude → 한국어 피드백
     */
    suspend fun generateSttFeedback(
        questionText: String,
        sttResult: String,
        targetGrade: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = appPrefs.claudeApiKey
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                Exception("Claude API 키가 설정되지 않았습니다.\nSettings > AI 설정에서 입력해주세요.")
            )
        }

        val prompt = buildString {
            appendLine("OPic 목표 등급: $targetGrade")
            appendLine("문제: $questionText")
            appendLine("학생 답변 (STT): $sttResult")
            appendLine()
            append(
                "다음을 한국어로 간략히 피드백해주세요:\n" +
                "1. 문법/표현 교정 (틀린 부분 → 올바른 표현)\n" +
                "2. 더 자연스러운 영어 표현 제안\n" +
                "3. $targetGrade 등급 관점 한줄 평가"
            )
        }

        callClaudeApi(
            apiKey = apiKey,
            systemPrompt = "You are an OPic English speaking coach. Provide brief, practical feedback in Korean.",
            userMessage = prompt
        )
    }

    private fun callClaudeApi(
        apiKey: String,
        systemPrompt: String,
        userMessage: String,
        maxTokens: Int = MAX_TOKENS
    ): Result<String> {
        return try {
            val conn = URL(API_URL).openConnection() as HttpURLConnection
            conn.apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("x-api-key", apiKey)
                setRequestProperty("anthropic-version", "2023-06-01")
                doOutput = true
                connectTimeout = 30_000
                readTimeout = 60_000
            }

            val body = JSONObject().apply {
                put("model", MODEL)
                put("max_tokens", maxTokens)
                put("system", systemPrompt)
                put("messages", JSONArray().put(
                    JSONObject().apply {
                        put("role", "user")
                        put("content", userMessage)
                    }
                ))
            }.toString()

            OutputStreamWriter(conn.outputStream).use { it.write(body) }

            val responseCode = conn.responseCode
            val stream = if (responseCode == HttpURLConnection.HTTP_OK) conn.inputStream else conn.errorStream
            val response = BufferedReader(InputStreamReader(stream)).use { it.readText() }

            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "API 오류 $responseCode: $response")
                val errMsg = try {
                    JSONObject(response).optJSONObject("error")?.optString("message") ?: response
                } catch (_: Exception) { response }
                return Result.failure(Exception("API 오류 ($responseCode): $errMsg"))
            }

            val text = JSONObject(response)
                .getJSONArray("content")
                .getJSONObject(0)
                .getString("text")
            Result.success(text)
        } catch (e: Exception) {
            Log.e(TAG, "Claude API 호출 실패", e)
            Result.failure(e)
        }
    }
}
