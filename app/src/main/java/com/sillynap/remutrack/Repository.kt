package com.sillynap.remutrack

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

class RemuRepository(context: Context) {
    private val prefs = context.getSharedPreferences("remutrack", Context.MODE_PRIVATE)
    private val entriesKey = "entries"
    fun settings(): RemunerationSettings = RemunerationSettings(rate = prefs.getLong("rate", 0), currency = prefs.getString("currency", "BDT") ?: "BDT")
    fun saveSettings(settings: RemunerationSettings) { prefs.edit().putLong("rate", settings.rate).putString("currency", settings.currency).apply() }
    fun entries(): List<InvigilationEntry> {
        val array = JSONArray(prefs.getString(entriesKey, "[]"))
        return (0 until array.length()).map { fromJson(array.getJSONObject(it)) }
    }
    fun save(entries: List<InvigilationEntry>) { prefs.edit().putString(entriesKey, JSONArray(entries.map(::toJson)).toString()).apply() }
    fun newId() = UUID.randomUUID().toString()

    private fun toJson(e: InvigilationEntry) = JSONObject().apply {
        put("id", e.id); put("date", e.workDate.toString()); put("series", e.examSeries); put("year", e.examYear)
        put("type", e.examType.name); put("custom", e.customExamType); put("rate", e.rate); put("paid", e.paid)
        put("paymentDate", e.paymentDate?.toString()); put("note", e.paymentNote); put("created", e.createdAt); put("updated", e.updatedAt)
    }
    private fun fromJson(o: JSONObject) = InvigilationEntry(
        o.getString("id"), LocalDate.parse(o.getString("date")), o.getString("series"), o.getInt("year"),
        ExamType.valueOf(o.getString("type")), o.opt("custom").takeUnless { it == JSONObject.NULL }?.toString()?.ifBlank { null }, o.getLong("rate"),
        o.optBoolean("paid"), o.optString("paymentDate").ifBlank { null }?.let(LocalDate::parse),
        o.opt("note").takeUnless { it == JSONObject.NULL }?.toString()?.ifBlank { null }, o.optLong("created"), o.optLong("updated")
    )
}
