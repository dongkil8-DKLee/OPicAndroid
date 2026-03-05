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
        userMessage: String
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
                put("max_tokens", MAX_TOKENS)
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
