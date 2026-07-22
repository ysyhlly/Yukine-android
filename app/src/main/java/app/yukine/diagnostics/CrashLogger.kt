package app.yukine.diagnostics

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 轻量本地崩溃记录器。包裹默认的 [Thread.UncaughtExceptionHandler]，在进程崩溃前把异常栈写到
 * 应用私有目录 `filesDir/crash-logs/` 下，便于事后排障——无需任何第三方 SaaS、不联网、不申请额外权限。
 *
 * 行为约束：
 * - 记录完成后必定调用原始 handler，绝不吞掉系统崩溃流程（保证 ANR/崩溃弹窗、上报等照常）。
 * - 仅保留最近 [MAX_LOG_FILES] 个日志文件，超出删旧，避免无限增长。
 * - handler 内部全程 try-catch，自身绝不二次抛异常导致崩溃流程混乱。
 */
object CrashLogger {

    private const val DIR_NAME = "crash-logs"
    private const val MAX_LOG_FILES = 10
    private const val PREFIX = "crash-"

    @Volatile
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeCrash(appContext, thread, throwable)
            } catch (_: Throwable) {
                // 记录失败绝不能影响崩溃传递
            } finally {
                previous?.uncaughtException(thread, throwable)
            }
        }
    }

    private fun writeCrash(context: Context, thread: Thread, throwable: Throwable) {
        val dir = File(context.filesDir, DIR_NAME)
        if (!dir.exists()) dir.mkdirs()

        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date())
        val stack = StringWriter().also { sw ->
            PrintWriter(sw).use { throwable.printStackTrace(it) }
        }.toString()

        val content = buildString {
            append("time: ").append(timestamp).append('\n')
            append("thread: ").append(thread.name).append('\n')
            append("message: ").append(throwable.message ?: "(none)").append('\n')
            append("----\n")
            append(stack)
        }

        File(dir, "$PREFIX$timestamp.log").writeText(
            DiagnosticRedactor.redact(content),
            Charsets.UTF_8
        )
        pruneOldLogs(dir)
    }

    private fun pruneOldLogs(dir: File) {
        val logs = dir.listFiles { file -> file.isFile && file.name.startsWith(PREFIX) } ?: return
        if (logs.size <= MAX_LOG_FILES) return
        logs.sortedBy { it.lastModified() }
            .take(logs.size - MAX_LOG_FILES)
            .forEach { runCatching { it.delete() } }
    }
}
