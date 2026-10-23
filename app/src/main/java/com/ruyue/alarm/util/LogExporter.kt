package com.ruyue.alarm.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.ruyue.alarm.BuildConfig
import com.ruyue.alarm.data.EventLog
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 运行日志导出。
 *
 * 为什么需要：排障时要看的是**时间线**，一张张截图既慢又容易漏。导出成一个 txt
 * 直接分享（微信/邮件/存文件）比截图高效得多。
 *
 * 用 FileProvider 暴露缓存目录里的文件，不申请任何存储权限。
 */
object LogExporter {

    fun share(context: Context, logs: List<EventLog>) {
        val text = buildText(logs)

        val dir = File(context.cacheDir, "logs").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(dir, "ruyue-log-$stamp.txt")
        file.writeText(text, Charsets.UTF_8)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "如约运行日志 $stamp")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, "分享运行日志")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** 按时间正序导出，方便从前往后读时间线 */
    private fun buildText(logs: List<EventLog>): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return buildString {
            appendLine("如约 v${BuildConfig.VERSION_NAME} 运行日志")
            appendLine("导出时间：${fmt.format(Date())}")
            appendLine("条数：${logs.size}")
            appendLine("=".repeat(50))
            logs.sortedBy { it.timestamp }.forEach { log ->
                append(fmt.format(Date(log.timestamp)))
                append("  ")
                append(log.type.padEnd(16))
                if (!log.ssid.isNullOrBlank()) append("  [${log.ssid}]")
                log.detail?.let { append("  ").append(it) }
                appendLine()
            }
        }
    }
}
