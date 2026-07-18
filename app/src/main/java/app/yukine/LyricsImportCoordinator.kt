package app.yukine

import android.content.ContentResolver
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import app.yukine.ui.EchoDialog
import app.yukine.data.CustomLyricsRecordingWrite
import app.yukine.data.CustomLyricsRepository
import app.yukine.data.CustomLyricsTarget
import app.yukine.data.LyricsBatchMatch
import app.yukine.data.LyricsBatchMatcher
import app.yukine.data.LyricsBatchTrack
import app.yukine.data.LyricsDocumentParser
import app.yukine.data.LyricsImportCandidate
import app.yukine.data.MusicLibraryRepository
import app.yukine.model.LyricsDocument
import app.yukine.model.Track
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Locale
import java.util.concurrent.Executor
import java.util.function.Consumer

enum class LyricsImportResultKind {
    SUCCESS,
    EXISTING,
    AMBIGUOUS,
    UNMATCHED,
    FORMAT_ERROR,
    READ_ERROR
}

data class LyricsImportReportItem(
    val sourceName: String,
    val kind: LyricsImportResultKind,
    val reason: String,
    val candidates: List<String> = emptyList()
)

data class LyricsImportReport(val items: List<LyricsImportReportItem>) {
    fun count(kind: LyricsImportResultKind): Int = items.count { it.kind == kind }

    fun summary(): String = buildString {
        append("歌词批量导入：成功 ")
        append(count(LyricsImportResultKind.SUCCESS))
        append("，已有 ")
        append(count(LyricsImportResultKind.EXISTING))
        append("，歧义 ")
        append(count(LyricsImportResultKind.AMBIGUOUS))
        append("，未匹配 ")
        append(count(LyricsImportResultKind.UNMATCHED))
        append("，格式错误 ")
        append(count(LyricsImportResultKind.FORMAT_ERROR))
        append("，读取错误 ")
        append(count(LyricsImportResultKind.READ_ERROR))
    }
}

/**
 * Owns lyric document pickers and import policy. Parsing, identity capture, matching and Room
 * writes stay outside the Activity and Compose destinations.
 */
internal class LyricsImportCoordinator(
    private val activity: ComponentActivity,
    private val customLyricsRepository: CustomLyricsRepository,
    private val libraryRepository: MusicLibraryRepository,
    private val ioExecutor: Executor,
    private val mainExecutor: Executor,
    private val statusSink: Consumer<String>,
    private val reloadLyrics: () -> Unit
) {
    companion object {
        private const val SAVED_STATE_KEY = "custom_lyrics_import_target"
        private const val MAX_FILE_BYTES = 2 * 1024 * 1024
        private const val MAX_BATCH_CANDIDATES = 2_000
        private const val MAX_TREE_DEPTH = 32
    }

    private val resolver: ContentResolver = activity.contentResolver
    private val parser = LyricsDocumentParser()
    private var pendingTarget: PendingTarget? = restorePendingTarget()
    private var lastReport: LyricsImportReport? = null

    private val singleDocumentLauncher = activity.registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val target = pendingTarget
        pendingTarget = null
        if (uri != null && target != null) importSingleResult(uri, target)
    }

    private val directoryLauncher = activity.registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) importDirectoryResult(uri)
    }

    init {
        activity.savedStateRegistry.registerSavedStateProvider(SAVED_STATE_KEY) {
            Bundle().apply { pendingTarget?.writeTo(this) }
        }
    }

    fun importForTrack(track: Track) {
        ioExecutor.execute {
            val targetResult = runCatching { customLyricsRepository.targetForTrack(track) }
            mainExecutor.execute {
                targetResult.fold(
                    onSuccess = { target ->
                        if (!target.isStable()) {
                            statusSink.accept("当前歌曲还没有可持久化的歌词身份")
                            return@fold
                        }
                        pendingTarget = PendingTarget(target, track.title)
                        runCatching {
                            singleDocumentLauncher.launch(
                                arrayOf(
                                    "text/plain",
                                    "application/xml",
                                    "text/xml",
                                    "application/octet-stream"
                                )
                            )
                        }.onFailure {
                            pendingTarget = null
                            statusSink.accept("无法打开歌词文件选择器")
                        }
                    },
                    onFailure = {
                        statusSink.accept("无法读取当前歌曲的歌词身份")
                    }
                )
            }
        }
    }

    fun clearForTrack(track: Track) {
        EchoDialog.builder(activity)
            .setTitle("清除自定义歌词")
            .setMessage("将删除“${track.title}”的自定义歌词，并重新使用旁挂或在线歌词。")
            .setNegativeButton("取消", null)
            .setPositiveButton("清除") { _, _ ->
                ioExecutor.execute {
                    val deleted = customLyricsRepository.deleteForTrack(track)
                    mainExecutor.execute {
                        statusSink.accept(if (deleted) "已清除自定义歌词" else "当前歌曲没有自定义歌词")
                        reloadLyrics()
                    }
                }
            }
            .show()
    }

    fun openBatchDirectory() {
        lastReport = null
        directoryLauncher.launch(null)
    }

    fun latestReport(): LyricsImportReport? = lastReport

    fun showLatestReport() {
        val report = lastReport
        if (report == null) {
            statusSink.accept("还没有批量歌词导入报告")
            return
        }
        val detail = report.items.take(100).joinToString("\n\n") { item ->
            buildString {
                append(item.sourceName)
                append(" · ")
                append(item.kind.name)
                append("\n")
                append(item.reason)
                if (item.candidates.isNotEmpty()) {
                    append("\n候选：")
                    append(item.candidates.joinToString("；"))
                }
            }
        } + if (report.items.size > 100) {
            "\n\n其余 ${report.items.size - 100} 项未在对话框中展开"
        } else {
            ""
        }
        EchoDialog.builder(activity)
            .setTitle(report.summary())
            .setMessage(detail.ifBlank { "本次目录中没有候选歌词文件" })
            .setPositiveButton("关闭", null)
            .show()
    }

    private fun importSingleResult(uri: Uri, pending: PendingTarget) {
        ioExecutor.execute {
            val result = runCatching {
                val sourceName = displayName(uri)
                val document = parser.parse(readBounded(uri), sourceName)
                document to customLyricsRepository.hasForTarget(pending.target)
            }
            mainExecutor.execute {
                result.fold(
                    onSuccess = { (document, exists) ->
                        if (exists) {
                            confirmReplace(pending, document)
                        } else {
                            saveSingle(pending, document)
                        }
                    },
                    onFailure = { error ->
                        statusSink.accept("歌词导入失败：${error.message ?: "无法读取或解析文件"}")
                    }
                )
            }
        }
    }

    private fun confirmReplace(pending: PendingTarget, document: LyricsDocument) {
        EchoDialog.builder(activity)
            .setTitle("替换自定义歌词")
            .setMessage("“${pending.trackTitle}”已有自定义歌词，是否替换？")
            .setNegativeButton("取消", null)
            .setPositiveButton("替换") { _, _ -> saveSingle(pending, document) }
            .show()
    }

    private fun saveSingle(pending: PendingTarget, document: LyricsDocument) {
        ioExecutor.execute {
            val result = runCatching {
                customLyricsRepository.saveForTarget(
                    target = pending.target,
                    document = document,
                    sourceName = document.sourceName,
                    format = document.format
                )
            }
            mainExecutor.execute {
                result.fold(
                    onSuccess = {
                        statusSink.accept("已导入“${pending.trackTitle}”的自定义歌词")
                        reloadLyrics()
                    },
                    onFailure = { statusSink.accept("歌词保存失败：${it.message.orEmpty()}") }
                )
            }
        }
    }

    private fun importDirectoryResult(treeUri: Uri) {
        statusSink.accept("正在扫描歌词目录…")
        ioExecutor.execute {
            val report = runCatching { prepareAndCommitBatch(treeUri) }
                .getOrElse {
                    LyricsImportReport(
                        listOf(
                            LyricsImportReportItem(
                                sourceName = displayName(treeUri),
                                kind = LyricsImportResultKind.READ_ERROR,
                                reason = it.message ?: "目录读取失败"
                            )
                        )
                    )
                }
            lastReport = report
            mainExecutor.execute { statusSink.accept(report.summary()) }
        }
    }

    private fun prepareAndCommitBatch(treeUri: Uri): LyricsImportReport {
        val documents = collectDocuments(treeUri)
        val canonicalTracks = libraryRepository.loadCachedTracks()
            .mapNotNull { track ->
                customLyricsRepository.recordingIdForTrack(track)?.let {
                    LyricsBatchTrack(it, track)
                }
            }
            .groupBy(LyricsBatchTrack::recordingId)
            .map { (_, tracks) -> tracks.first() }

        val report = mutableListOf<LyricsImportReportItem>()
        val parsed = mutableListOf<LyricsImportCandidate>()
        documents.forEach { document ->
            try {
                parsed += LyricsImportCandidate(
                    document.name,
                    parser.parse(readBounded(document.uri), document.name)
                )
            } catch (error: IOException) {
                report += LyricsImportReportItem(
                    document.name,
                    LyricsImportResultKind.READ_ERROR,
                    error.message ?: "读取失败"
                )
            } catch (error: Exception) {
                report += LyricsImportReportItem(
                    document.name,
                    LyricsImportResultKind.FORMAT_ERROR,
                    error.message ?: "格式错误"
                )
            }
        }

        val writes = mutableListOf<CustomLyricsRecordingWrite>()
        val writeItems = mutableMapOf<Long, LyricsImportReportItem>()
        parsed.forEach { candidate ->
            when (val match = LyricsBatchMatcher.match(candidate, canonicalTracks)) {
                is LyricsBatchMatch.Unique -> {
                    if (customLyricsRepository.hasForRecording(match.track.recordingId)) {
                        report += LyricsImportReportItem(
                            candidate.sourceName,
                            LyricsImportResultKind.EXISTING,
                            "目标歌曲已有自定义歌词"
                        )
                    } else if (writes.any { it.recordingId == match.track.recordingId }) {
                        report += LyricsImportReportItem(
                            candidate.sourceName,
                            LyricsImportResultKind.AMBIGUOUS,
                            "本批次有多个文件匹配同一歌曲",
                            listOf(trackLabel(match.track.track))
                        )
                    } else {
                        writes += CustomLyricsRecordingWrite(
                            recordingId = match.track.recordingId,
                            document = candidate.document,
                            sourceName = candidate.sourceName,
                            format = candidate.document.format
                        )
                        writeItems[match.track.recordingId] = LyricsImportReportItem(
                            candidate.sourceName,
                            LyricsImportResultKind.SUCCESS,
                            "通过 ${match.rule} 唯一匹配"
                        )
                    }
                }
                is LyricsBatchMatch.Ambiguous -> report += LyricsImportReportItem(
                    candidate.sourceName,
                    LyricsImportResultKind.AMBIGUOUS,
                    "匹配到多个规范录音",
                    match.candidates.take(3).map { trackLabel(it.track) }
                )
                LyricsBatchMatch.Unmatched -> report += LyricsImportReportItem(
                    candidate.sourceName,
                    LyricsImportResultKind.UNMATCHED,
                    "没有安全的唯一匹配"
                )
            }
        }

        val savedIds = customLyricsRepository.saveBatchForRecordings(writes)
        writes.forEach { write ->
            val item = writeItems.getValue(write.recordingId)
            report += if (write.recordingId in savedIds) {
                item
            } else {
                item.copy(
                    kind = LyricsImportResultKind.EXISTING,
                    reason = "提交前目标歌曲已出现自定义歌词"
                )
            }
        }
        return LyricsImportReport(report.sortedBy(LyricsImportReportItem::sourceName))
    }

    private fun collectDocuments(treeUri: Uri): List<DocumentEntry> {
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val root = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId)
        val queue = ArrayDeque<Pair<Uri, Int>>()
        val results = mutableListOf<DocumentEntry>()
        queue += root to 0
        while (queue.isNotEmpty() && results.size < MAX_BATCH_CANDIDATES) {
            val (parent, depth) = queue.removeFirst()
            if (depth > MAX_TREE_DEPTH) continue
            val parentId = DocumentsContract.getDocumentId(parent)
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
            resolver.query(
                children,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ),
                null,
                null,
                null
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (cursor.moveToNext() && results.size < MAX_BATCH_CANDIDATES) {
                    val child = DocumentsContract.buildDocumentUriUsingTree(
                        treeUri,
                        cursor.getString(idColumn)
                    )
                    val name = cursor.getString(nameColumn).orEmpty()
                    val mime = cursor.getString(mimeColumn).orEmpty()
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        queue += child to depth + 1
                    } else if (supportedName(name)) {
                        results += DocumentEntry(child, name)
                    }
                }
            }
        }
        return results
    }

    private fun readBounded(uri: Uri): ByteArray {
        resolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_FILE_BYTES) throw IOException("文件超过 2 MB")
                output.write(buffer, 0, read)
            }
            return output.toByteArray()
        }
        throw IOException("无法打开文件")
    }

    private fun displayName(uri: Uri): String {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0).orEmpty()
        }
        return uri.lastPathSegment.orEmpty()
    }

    private fun restorePendingTarget(): PendingTarget? =
        activity.savedStateRegistry.consumeRestoredStateForKey(SAVED_STATE_KEY)
            ?.let(PendingTarget::from)

    private fun supportedName(name: String): Boolean = when (
        name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    ) {
        "lrc", "ttml", "xml", "txt" -> true
        else -> false
    }

    private fun trackLabel(track: Track): String =
        listOf(track.artist, track.title).filter(String::isNotBlank).joinToString(" - ")

    private data class DocumentEntry(val uri: Uri, val name: String)

    private data class PendingTarget(
        val target: CustomLyricsTarget,
        val trackTitle: String
    ) {
        fun writeTo(bundle: Bundle) {
            target.recordingId?.let { bundle.putLong("recording_id", it) }
            bundle.putString("provider", target.provider)
            bundle.putString("provider_track_id", target.providerTrackId)
            bundle.putString("track_title", trackTitle)
        }

        companion object {
            fun from(bundle: Bundle): PendingTarget? {
                val recordingId = bundle.getLong("recording_id", -1L).takeIf { it > 0L }
                val target = CustomLyricsTarget(
                    recordingId = recordingId,
                    provider = bundle.getString("provider").orEmpty(),
                    providerTrackId = bundle.getString("provider_track_id").orEmpty()
                )
                if (!target.isStable()) return null
                return PendingTarget(target, bundle.getString("track_title").orEmpty())
            }
        }
    }
}
