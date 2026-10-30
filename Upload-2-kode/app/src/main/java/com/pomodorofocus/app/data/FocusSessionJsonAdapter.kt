package com.pomodorofocus.app.data

import com.pomodorofocus.app.model.FocusSessionRecord
import com.pomodorofocus.app.model.SessionTag
import com.pomodorofocus.app.model.sanitizeSessionTitle
import org.json.JSONArray
import org.json.JSONObject

/**
 * Lightweight JSON serializer/deserializer for focus session records.
 * Uses Android's built-in org.json library with zero external dependencies.
 */
object FocusSessionJsonAdapter {

    private const val KEY_ID = "id"
    private const val KEY_TIMESTAMP = "timestamp"
    private const val KEY_DURATION = "durationMillis"
    private const val KEY_TITLE = "title"
    private const val KEY_TAG = "tag"

    fun toJson(records: List<FocusSessionRecord>): String {
        val array = JSONArray()
        for (record in records) {
            val obj = JSONObject()
            obj.put(KEY_ID, record.id)
            obj.put(KEY_TIMESTAMP, record.timestamp)
            obj.put(KEY_DURATION, record.durationMillis)
            // Optional fields are only written when set, so untitled sessions keep the old JSON shape.
            if (record.title.isNotBlank()) obj.put(KEY_TITLE, record.title)
            record.tag?.let { obj.put(KEY_TAG, it.name) }
            array.put(obj)
        }
        return array.toString()
    }

    fun fromJson(json: String?): List<FocusSessionRecord> {
        if (json.isNullOrBlank()) return emptyList()
        val list = mutableListOf<FocusSessionRecord>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.optString(KEY_ID, "")
                val timestamp = obj.optLong(KEY_TIMESTAMP, 0L)
                val duration = obj.optLong(KEY_DURATION, 0L)
                if (id.isNotBlank() && timestamp > 0L) {
                    list.add(
                        FocusSessionRecord(
                            id = id,
                            timestamp = timestamp,
                            durationMillis = duration,
                            title = sanitizeSessionTitle(obj.optString(KEY_TITLE, "")),
                            tag = SessionTag.fromName(obj.optString(KEY_TAG, ""))
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // Return whatever parsed successfully or empty list
        }
        return list
    }
}
