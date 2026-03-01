package com.opic.android.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

object DictionaryApi {
    private const val TAG = "DictionaryApi"
    private const val BASE_URL = "https://api.dictionaryapi.dev/api/v2/entries/en/"

    /**
     * Dictionary API에서 단어의 phonetic(발음) 정보를 가져온다.
     * @return phonetic 문자열 또는 null
     */
    suspend fun fetchPronunciation(word: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL${word.trim().lowercase()}")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000

            if (conn.responseCode != 200) {
                conn.disconnect()
                return@withContext null
            }

            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val jsonArray = JSONArray(body)
            if (jsonArray.length() == 0) return@withContext null

            val entry = jsonArray.getJSONObject(0)

            // 1. top-level "phonetic" field
            val phonetic = entry.optString("phonetic", "")
            if (phonetic.isNotBlank()) return@withContext phonetic

            // 2. "phonetics" array - find first non-empty text
            val phonetics = entry.optJSONArray("phonetics")
            if (phonetics != null) {
                for (i in 0 until phonetics.length()) {
                    val text = phonetics.getJSONObject(i).optString("text", "")
                    if (text.isNotBlank()) return@withContext text
                }
            }

            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch pronunciation for '$word'", e)
            null
        }
    }
}
