package com.opic.android.util

object CsvUtil {

    fun escape(s: String): String = s.replace("\"", "\"\"")

    /** RFC 4180 CSV 파서 — 멀티라인 필드, 이중따옴표 이스케이프 지원 */
    fun parse(content: String): List<List<String>> {
        val result = mutableListOf<List<String>>()
        val currentRow = mutableListOf<String>()
        val currentField = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < content.length) {
            val ch = content[i]
            when {
                inQuotes && ch == '"' && i + 1 < content.length && content[i + 1] == '"' -> {
                    currentField.append('"'); i += 2; continue
                }
                ch == '"' -> inQuotes = !inQuotes
                !inQuotes && ch == ',' -> {
                    currentRow.add(currentField.toString()); currentField.clear()
                }
                !inQuotes && ch == '\r' && i + 1 < content.length && content[i + 1] == '\n' -> {
                    currentRow.add(currentField.toString())
                    result.add(currentRow.toList()); currentRow.clear(); currentField.clear(); i += 2; continue
                }
                !inQuotes && (ch == '\n' || ch == '\r') -> {
                    currentRow.add(currentField.toString())
                    result.add(currentRow.toList()); currentRow.clear(); currentField.clear()
                }
                else -> currentField.append(ch)
            }
            i++
        }
        if (currentField.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(currentField.toString())
            result.add(currentRow.toList())
        }
        return result
    }
}
