package com.liferecorder.data

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import java.text.SimpleDateFormat
import java.util.*

/**
 * 数据导入导出工具
 * 导出格式为 JSON，包含生活记录和免打扰规则
 */
object DataExportImport {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    /** 导出数据模型 */
    data class ExportData(
        @SerializedName("version") val version: Int = 1,
        @SerializedName("exportTime") val exportTime: String,
        @SerializedName("records") val records: List<ExportRecord>,
        @SerializedName("quietRules") val quietRules: List<ExportQuietRule>
    )

    data class ExportRecord(
        @SerializedName("content") val content: String,
        @SerializedName("timestamp") val timestamp: Long,
        @SerializedName("hourLabel") val hourLabel: String
    )

    data class ExportQuietRule(
        @SerializedName("name") val name: String,
        @SerializedName("startHour") val startHour: Int,
        @SerializedName("startMinute") val startMinute: Int,
        @SerializedName("endHour") val endHour: Int,
        @SerializedName("endMinute") val endMinute: Int,
        @SerializedName("applyMode") val applyMode: Int,
        @SerializedName("enabled") val enabled: Boolean
    )

    /**
     * 导出数据为 JSON 字符串
     */
    suspend fun exportToJson(context: Context): String {
        val db = AppDatabase.getDatabase(context)
        val records = db.lifeRecordDao().getAllRecords()
        val rules = db.quietRuleDao().getAllRules_export()

        val exportData = ExportData(
            version = 1,
            exportTime = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date()),
            records = records.map { ExportRecord(it.content, it.timestamp, it.hourLabel) },
            quietRules = rules.map {
                ExportQuietRule(it.name, it.startHour, it.startMinute, it.endHour, it.endMinute, it.applyMode, it.enabled)
            }
        )

        return gson.toJson(exportData)
    }

    /**
     * 将 JSON 写入指定 Uri（通过 SAF 选择的文件）
     */
    suspend fun exportToUri(context: Context, uri: Uri): Result<Int> {
        return try {
            val json = exportToJson(context)
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(json.toByteArray(Charsets.UTF_8))
            } ?: return Result.failure(Exception("无法打开文件"))

            val data = gson.fromJson(json, ExportData::class.java)
            Result.success(data.records.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 从指定 Uri 导入数据
     * @return 导入的记录数量
     */
    suspend fun importFromUri(context: Context, uri: Uri): Result<ImportResult> {
        return try {
            val json = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.bufferedReader().readText()
            } ?: return Result.failure(Exception("无法读取文件"))

            val exportData = gson.fromJson(json, ExportData::class.java)
                ?: return Result.failure(Exception("文件格式错误"))

            val db = AppDatabase.getDatabase(context)

            // 导入记录（跳过已存在的，根据 timestamp 判断）
            val existingRecords = db.lifeRecordDao().getAllRecords()
            val existingTimestamps = existingRecords.map { it.timestamp }.toSet()
            var importedRecords = 0
            for (record in exportData.records) {
                if (record.timestamp !in existingTimestamps) {
                    db.lifeRecordDao().insert(
                        LifeRecord(
                            content = record.content,
                            timestamp = record.timestamp,
                            hourLabel = record.hourLabel
                        )
                    )
                    importedRecords++
                }
            }

            // 导入规则（跳过同名规则）
            val existingRules = db.quietRuleDao().getAllRules_export()
            val existingRuleNames = existingRules.map { it.name }.toSet()
            var importedRules = 0
            for (rule in exportData.quietRules) {
                if (rule.name !in existingRuleNames) {
                    db.quietRuleDao().insert(
                        QuietRule(
                            name = rule.name,
                            startHour = rule.startHour,
                            startMinute = rule.startMinute,
                            endHour = rule.endHour,
                            endMinute = rule.endMinute,
                            applyMode = rule.applyMode,
                            enabled = rule.enabled
                        )
                    )
                    importedRules++
                }
            }

            Result.success(ImportResult(importedRecords, importedRules,
                exportData.records.size - importedRecords, exportData.quietRules.size - importedRules))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    data class ImportResult(
        val importedRecords: Int,
        val importedRules: Int,
        val skippedRecords: Int,
        val skippedRules: Int
    )

    /**
     * 生成导出文件名
     */
    fun generateFileName(): String {
        val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "qbit_backup_$dateStr.json"
    }
}
