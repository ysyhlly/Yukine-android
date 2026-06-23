package app.yukine

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import app.yukine.model.Track
import app.yukine.data.EmbeddedArtwork
import app.yukine.streaming.StreamingAudioQuality
import app.yukine.streaming.StreamingPlaybackHeaderStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

data class TrackDownloadResult(
    val started: Boolean,
    val downloadId: Long = -1L,
    val message: String
)

data class TrackDownloadActionResult(
    val changed: Boolean,
    val message: String
)

enum class TrackDownloadStatus {
    Pending,
    Running,
    Paused,
    Finished,
    Failed,
    Unknown
}

data class TrackDownloadItem(
    val downloadId: Long,
    val title: String,
    val artist: String,
    val status: TrackDownloadStatus,
    val progressPercent: Int,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val localUri: String,
    val reason: Int,
    val quality: String = "high"
)

interface TrackDownloadController {
    fun snapshot(): List<TrackDownloadItem>
    fun pause(downloadId: Long): TrackDownloadActionResult
    fun resume(downloadId: Long): TrackDownloadActionResult
    fun pauseAll(): TrackDownloadActionResult
    fun resumeAll(): TrackDownloadActionResult
}

@Singleton
class TrackDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val streamingHeaders: StreamingPlaybackHeaderStore
) : TrackDownloadController {
    private val records = linkedMapOf<Long, TrackDownloadRecord>()
    private val preferences = context.getSharedPreferences("track_downloads", Context.MODE_PRIVATE)
    private val customExecutor = Executors.newFixedThreadPool(APP_DOWNLOAD_CONCURRENCY)
    private val segmentExecutor = Executors.newFixedThreadPool(SEGMENT_DOWNLOAD_CONCURRENCY)
    private val customIdCounter = AtomicLong(-2L)

    init {
        restoreRecords()
    }

    fun enqueue(track: Track, quality: StreamingAudioQuality = StreamingAudioQuality.HIGH): TrackDownloadResult {
        val uri = downloadableUri(track)
            ?: return TrackDownloadResult(false, message = "当前歌曲暂不支持下载，请先播放解析成功后再试")
        customDownloadTreeUri()?.let { treeUri ->
            return enqueueAppManagedDownload(track, quality, uri, treeUri)
        }
        if (shouldUseAppManagedDownload(track)) {
            return enqueueAppManagedDownload(track, quality, uri, null)
        }
        val request = DownloadManager.Request(uri)
            .setTitle(track.title)
            .setDescription(track.artist)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .setMimeType(mimeTypeFor(track))
            .setDestinationInExternalPublicDir(
                publicDirectoryName(),
                "Yukine/${safeFileName(track)}"
            )
        request.allowScanningByMediaScanner()
        val headers = streamingHeaders.forDataPath(track.dataPath)
        headers.forEach { (name, value) ->
            if (name.isNotBlank() && value.isNotBlank()) {
                request.addRequestHeader(name, value)
            }
        }
        if (headers.keys.none { it.equals("User-Agent", ignoreCase = true) }) {
            request.addRequestHeader("User-Agent", DEFAULT_USER_AGENT)
        }
        if (headers.keys.none { it.equals("Accept", ignoreCase = true) }) {
            request.addRequestHeader("Accept", "audio/*,*/*")
        }
        val manager = context.getSystemService(DownloadManager::class.java)
            ?: return TrackDownloadResult(false, message = "系统下载服务不可用")
        val id = try {
            manager.enqueue(request)
        } catch (error: RuntimeException) {
            return TrackDownloadResult(false, message = "下载任务创建失败：${error.message ?: "系统下载服务拒绝该链接"}")
        }
        synchronized(records) {
            records[id] = TrackDownloadRecord(
                id,
                track.title,
                track.artist,
                quality.wireName,
                album = track.album,
                albumArtUri = track.albumArtUriString()
            )
            persistRecordsLocked()
        }
        scheduleArtworkDownload(track, null)
        return TrackDownloadResult(true, id, downloadStartedMessage("已提交系统下载", track))
    }

    fun downloadDirectory(): String =
        normalizeDirectory(preferences.getString(KEY_DIRECTORY, DOWNLOAD_DIRECTORY_MUSIC))

    fun downloadDirectoryLabel(): String =
        when (downloadDirectory()) {
            DOWNLOAD_DIRECTORY_CUSTOM -> "自定义目录/Yukine"
            DOWNLOAD_DIRECTORY_DOWNLOADS -> "下载/Yukine"
            else -> "音乐/Yukine"
        }

    fun setDownloadDirectory(directory: String) {
        preferences.edit().putString(KEY_DIRECTORY, normalizeDirectory(directory)).apply()
    }

    fun setCustomDownloadDirectory(treeUri: Uri) {
        preferences.edit()
            .putString(KEY_DIRECTORY, DOWNLOAD_DIRECTORY_CUSTOM)
            .putString(KEY_CUSTOM_TREE_URI, treeUri.toString())
            .apply()
    }

    override fun snapshot(): List<TrackDownloadItem> {
        val recordSnapshot = synchronized(records) { records.values.toList() }
        if (recordSnapshot.isEmpty()) {
            return emptyList()
        }
        val queried = linkedMapOf<Long, TrackDownloadItem>()
        val systemIds = recordSnapshot.map { it.downloadId }.filter { it >= 0L }.toLongArray()
        if (systemIds.isNotEmpty()) {
            val manager = context.getSystemService(DownloadManager::class.java)
            manager?.query(DownloadManager.Query().setFilterById(*systemIds))?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.longColumn(DownloadManager.COLUMN_ID)
                    val record = recordSnapshot.firstOrNull { it.downloadId == id }
                    val title = cursor.stringColumn(DownloadManager.COLUMN_TITLE).ifBlank { record?.title.orEmpty() }
                    val artist = cursor.stringColumn(DownloadManager.COLUMN_DESCRIPTION).ifBlank { record?.artist.orEmpty() }
                    val total = cursor.longColumn(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    val downloaded = cursor.longColumn(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    queried[id] = TrackDownloadItem(
                        downloadId = id,
                        title = title.ifBlank { "Yukine Track" },
                        artist = artist,
                        status = mapStatus(cursor.intColumn(DownloadManager.COLUMN_STATUS)),
                        progressPercent = progressPercent(downloaded, total),
                        bytesDownloaded = downloaded.coerceAtLeast(0L),
                        totalBytes = total,
                        localUri = cursor.stringColumn(DownloadManager.COLUMN_LOCAL_URI),
                        reason = cursor.intColumn(DownloadManager.COLUMN_REASON),
                        quality = record?.quality ?: "high"
                    )
                }
            }
        }
        return recordSnapshot.map { record ->
            if (record.downloadId < 0L) {
                TrackDownloadItem(
                    downloadId = record.downloadId,
                    title = record.title,
                    artist = record.artist,
                    status = record.status,
                    progressPercent = progressPercent(record.bytesDownloaded, record.totalBytes),
                    bytesDownloaded = record.bytesDownloaded.coerceAtLeast(0L),
                    totalBytes = record.totalBytes,
                    localUri = record.localUri,
                    reason = 0,
                    quality = record.quality
                )
            } else {
                queried[record.downloadId] ?: TrackDownloadItem(
                    downloadId = record.downloadId,
                    title = record.title,
                    artist = record.artist,
                    status = TrackDownloadStatus.Unknown,
                    progressPercent = 0,
                    bytesDownloaded = 0L,
                    totalBytes = -1L,
                    localUri = "",
                    reason = 0,
                    quality = record.quality
                )
            }
        }.sortedWith(
            compareBy<TrackDownloadItem> { it.status == TrackDownloadStatus.Finished }
                .thenByDescending { it.downloadId }
        )
    }

    override fun pause(downloadId: Long): TrackDownloadActionResult {
        val changed = synchronized(records) {
            val current = records[downloadId] ?: return TrackDownloadActionResult(false, "未找到下载任务")
            if (downloadId >= 0L) {
                return TrackDownloadActionResult(false, "系统下载任务暂不支持单独暂停")
            }
            if (current.status != TrackDownloadStatus.Pending && current.status != TrackDownloadStatus.Running) {
                return TrackDownloadActionResult(false, "当前任务无需暂停")
            }
            records[downloadId] = current.copy(status = TrackDownloadStatus.Paused)
            persistRecordsLocked()
            true
        }
        return if (changed) TrackDownloadActionResult(true, "已暂停下载") else TrackDownloadActionResult(false, "暂停失败")
    }

    override fun resume(downloadId: Long): TrackDownloadActionResult {
        var recordToResume: TrackDownloadRecord? = null
        synchronized(records) {
            val current = records[downloadId] ?: return TrackDownloadActionResult(false, "未找到下载任务")
            if (downloadId >= 0L) {
                return TrackDownloadActionResult(false, "系统下载任务会由系统自动继续")
            }
            if (current.status != TrackDownloadStatus.Paused) {
                return TrackDownloadActionResult(false, "当前任务无需继续")
            }
            val resumed = current.copy(status = TrackDownloadStatus.Pending)
            records[downloadId] = resumed
            persistRecordsLocked()
            recordToResume = resumed
        }
        val record = recordToResume ?: return TrackDownloadActionResult(false, "继续失败")
        if (record.sourceUri.isBlank()) {
            return TrackDownloadActionResult(false, "旧下载任务缺少音源信息，请重新添加下载")
        }
        customExecutor.execute {
            val source = Uri.parse(record.sourceUri)
            val targetTree = record.treeUri.takeIf { it.isNotBlank() }?.let(Uri::parse)
            runAppManagedDownload(record.downloadId, record.toTrack(), source, targetTree)
        }
        return TrackDownloadActionResult(true, "已继续下载")
    }

    override fun pauseAll(): TrackDownloadActionResult {
        var changed = 0
        synchronized(records) {
            for ((id, record) in records) {
                if (id < 0L && (record.status == TrackDownloadStatus.Pending || record.status == TrackDownloadStatus.Running)) {
                    records[id] = record.copy(status = TrackDownloadStatus.Paused)
                    changed += 1
                }
            }
            if (changed > 0) {
                persistRecordsLocked()
            }
        }
        return if (changed > 0) {
            TrackDownloadActionResult(true, "已暂停 $changed 个下载任务")
        } else {
            TrackDownloadActionResult(false, "没有可暂停的应用内下载任务")
        }
    }

    override fun resumeAll(): TrackDownloadActionResult {
        val toResume = mutableListOf<TrackDownloadRecord>()
        synchronized(records) {
            for ((id, record) in records) {
                if (id < 0L && record.status == TrackDownloadStatus.Paused) {
                    val resumed = record.copy(status = TrackDownloadStatus.Pending)
                    records[id] = resumed
                    toResume += resumed
                }
            }
            if (toResume.isNotEmpty()) {
                persistRecordsLocked()
            }
        }
        val resumable = toResume.filter { it.sourceUri.isNotBlank() }
        for (record in resumable) {
            customExecutor.execute {
                val source = Uri.parse(record.sourceUri)
                val targetTree = record.treeUri.takeIf { it.isNotBlank() }?.let(Uri::parse)
                runAppManagedDownload(record.downloadId, record.toTrack(), source, targetTree)
            }
        }
        return if (resumable.isNotEmpty()) {
            TrackDownloadActionResult(true, "已继续 ${resumable.size} 个下载任务")
        } else if (toResume.isNotEmpty()) {
            TrackDownloadActionResult(false, "旧下载任务缺少音源信息，请重新添加下载")
        } else {
            TrackDownloadActionResult(false, "没有可继续的下载任务")
        }
    }

    private fun downloadableUri(track: Track): Uri? {
        val uri = track.contentUri
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
        return if (scheme == "http" || scheme == "https") uri else null
    }

    private fun enqueueAppManagedDownload(
        track: Track,
        quality: StreamingAudioQuality,
        sourceUri: Uri,
        treeUri: Uri?
    ): TrackDownloadResult {
        val id = customIdCounter.getAndDecrement()
        synchronized(records) {
            records[id] = TrackDownloadRecord(
                downloadId = id,
                title = track.title,
                artist = track.artist,
                quality = quality.wireName,
                status = TrackDownloadStatus.Pending,
                album = track.album,
                durationMs = track.durationMs,
                sourceUri = sourceUri.toString(),
                treeUri = treeUri?.toString().orEmpty(),
                dataPath = track.dataPath,
                codec = track.codec,
                albumArtUri = track.albumArtUriString()
            )
            persistRecordsLocked()
        }
        customExecutor.execute { runAppManagedDownload(id, track, sourceUri, treeUri) }
        return TrackDownloadResult(true, id, downloadStartedMessage("已开始下载", track))
    }

    private fun runAppManagedDownload(id: Long, track: Track, sourceUri: Uri, treeUri: Uri?) {
        updateCustomRecord(id) { it.copy(status = TrackDownloadStatus.Running) }
        var targetUri: Uri? = null
        try {
            val headers = streamingHeaders.forDataPath(track.dataPath)
            val probe = probeDownload(sourceUri, headers)
            val total = probe.totalBytes
            val workDir = downloadWorkDir(id)
            if (!workDir.exists() && !workDir.mkdirs()) {
                throw IOException("无法创建断点缓存目录")
            }
            if (probe.acceptRanges && total > MIN_SEGMENTED_DOWNLOAD_BYTES) {
                runSegmentedDownload(id, sourceUri, headers, workDir, total)
            } else {
                runSingleConnectionDownload(id, sourceUri, headers, workDir, total)
            }
            targetUri = synchronized(records) {
                records[id]?.localUri?.takeIf { it.isNotBlank() }?.let(Uri::parse)
            } ?: createTargetUri(treeUri, track)
            updateCustomRecord(id) { it.copy(localUri = targetUri.toString(), totalBytes = total) }
            mergeWorkFileToTarget(id, workDir, targetUri)
            updateCustomRecord(id) {
                it.copy(
                    status = TrackDownloadStatus.Finished,
                    bytesDownloaded = if (total > 0L) total else it.bytesDownloaded,
                    totalBytes = total,
                    localUri = targetUri.toString()
                )
            }
            markPublicTargetFinished(targetUri)
            saveArtworkForTrack(track, treeUri)
        } catch (_: DownloadPausedException) {
            updateCustomRecord(id) { it.copy(status = TrackDownloadStatus.Paused) }
        } catch (_: Exception) {
            targetUri?.let(::deleteTargetUri)
            updateCustomRecord(id) { it.copy(status = TrackDownloadStatus.Failed) }
        }
    }

    private fun probeDownload(sourceUri: Uri, headers: Map<String, String>): DownloadProbe {
        val connection = openDownloadConnection(sourceUri, headers)
        return try {
            connection.setRequestProperty("Range", "bytes=0-0")
            val responseCode = connection.responseCode
            val contentRange = connection.getHeaderField("Content-Range").orEmpty()
            val contentLength = connection.contentLengthLong
            val totalFromRange = contentRange.substringAfterLast('/', "")
                .toLongOrNull()
                ?.takeIf { it > 0L }
            DownloadProbe(
                totalBytes = totalFromRange ?: contentLength,
                acceptRanges = responseCode == HttpURLConnection.HTTP_PARTIAL && totalFromRange != null
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun runSingleConnectionDownload(
        id: Long,
        sourceUri: Uri,
        headers: Map<String, String>,
        workDir: File,
        total: Long
    ) {
        val target = File(workDir, SINGLE_PART_FILE_NAME)
        val existing = target.length().coerceAtLeast(0L)
        val connection = openDownloadConnection(sourceUri, headers)
        if (existing > 0L) {
            connection.setRequestProperty("Range", "bytes=$existing-")
        }
        try {
            val responseCode = connection.responseCode
            val canAppend = existing > 0L && responseCode == HttpURLConnection.HTTP_PARTIAL
            if (responseCode !in 200..299) {
                throw IOException("HTTP $responseCode")
            }
            val startBytes = if (canAppend) existing else 0L
            FileOutputStream(target, canAppend).use { output ->
                connection.inputStream.use { input ->
                    copyDownloadStream(id, input, output, startBytes, total)
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun runSegmentedDownload(
        id: Long,
        sourceUri: Uri,
        headers: Map<String, String>,
        workDir: File,
        total: Long
    ) {
        val segments = buildDownloadSegments(total)
        updateCustomRecord(id) {
            it.copy(bytesDownloaded = downloadedSegmentBytes(workDir, segments), totalBytes = total)
        }
        val futures = mutableListOf<Future<*>>()
        for (segment in segments) {
            val file = segmentFile(workDir, segment.index)
            if (file.length() >= segment.length) {
                continue
            }
            futures += segmentExecutor.submit {
                downloadSegment(id, sourceUri, headers, workDir, segment, total)
            }
        }
        try {
            futures.forEach { it.get() }
        } catch (error: Exception) {
            futures.forEach { it.cancel(true) }
            if (isPaused(id)) {
                throw DownloadPausedException()
            }
            throw IOException(error.message ?: "分片下载失败", error)
        }
        val downloaded = downloadedSegmentBytes(workDir, segments)
        if (downloaded < total) {
            throw IOException("分片下载不完整")
        }
        File(workDir, SINGLE_PART_FILE_NAME).outputStream().use { output ->
            segments.forEach { segment ->
                FileInputStream(segmentFile(workDir, segment.index)).use { input ->
                    input.copyTo(output)
                }
            }
        }
    }

    private fun downloadSegment(
        id: Long,
        sourceUri: Uri,
        headers: Map<String, String>,
        workDir: File,
        segment: DownloadSegment,
        total: Long
    ) {
        val target = segmentFile(workDir, segment.index)
        val existing = target.length().coerceIn(0L, segment.length)
        if (existing >= segment.length) {
            return
        }
        val start = segment.start + existing
        val end = segment.end
        val connection = openDownloadConnection(sourceUri, headers)
        connection.setRequestProperty("Range", "bytes=$start-$end")
        try {
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_PARTIAL) {
                throw IOException("HTTP $responseCode")
            }
            FileOutputStream(target, existing > 0L).use { output ->
                connection.inputStream.use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        if (isPaused(id)) {
                            throw DownloadPausedException()
                        }
                        updateCustomRecord(id) {
                            it.copy(
                                status = TrackDownloadStatus.Running,
                                bytesDownloaded = downloadedSegmentBytes(workDir, buildDownloadSegments(total)),
                                totalBytes = total
                            )
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun copyDownloadStream(
        id: Long,
        input: java.io.InputStream,
        output: OutputStream,
        startBytes: Long,
        total: Long
    ) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var downloaded = startBytes
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            downloaded += read.toLong()
            if (isPaused(id)) {
                throw DownloadPausedException()
            }
            updateCustomRecord(id) {
                it.copy(
                    status = TrackDownloadStatus.Running,
                    bytesDownloaded = downloaded,
                    totalBytes = total
                )
            }
        }
    }

    private fun mergeWorkFileToTarget(id: Long, workDir: File, targetUri: Uri) {
        val source = File(workDir, SINGLE_PART_FILE_NAME)
        if (!source.exists() || source.length() <= 0L) {
            throw IOException("下载缓存为空")
        }
        openTargetOutputStream(targetUri)?.use { output ->
            FileInputStream(source).use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var copied = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    copied += read.toLong()
                    if (isPaused(id)) {
                        throw DownloadPausedException()
                    }
                    updateCustomRecord(id) {
                        it.copy(bytesDownloaded = copied, localUri = targetUri.toString())
                    }
                }
            }
        } ?: throw IOException("无法写入所选目录")
    }

    private fun openDownloadConnection(sourceUri: Uri, headers: Map<String, String>): HttpURLConnection {
        val connection = URL(sourceUri.toString()).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        headers.forEach { (name, value) ->
            if (name.isNotBlank() && value.isNotBlank()) {
                connection.setRequestProperty(name, value)
            }
        }
        if (headers.keys.none { it.equals("User-Agent", ignoreCase = true) }) {
            connection.setRequestProperty("User-Agent", DEFAULT_USER_AGENT)
        }
        if (headers.keys.none { it.equals("Accept", ignoreCase = true) }) {
            connection.setRequestProperty("Accept", "audio/*,*/*")
        }
        connection.connectTimeout = 15000
        connection.readTimeout = 30000
        return connection
    }

    private fun isPaused(id: Long): Boolean =
        synchronized(records) { records[id]?.status == TrackDownloadStatus.Paused }

    private fun downloadWorkDir(id: Long): File =
        File(context.cacheDir, "track-downloads/$id")

    private fun buildDownloadSegments(total: Long): List<DownloadSegment> {
        val segmentCount = when {
            total <= 0L -> 1
            total < MIN_SEGMENTED_DOWNLOAD_BYTES * 2 -> 2
            else -> SEGMENT_DOWNLOAD_CONCURRENCY
        }
        val segmentSize = ((total + segmentCount - 1) / segmentCount).coerceAtLeast(1L)
        return (0 until segmentCount).mapNotNull { index ->
            val start = index * segmentSize
            if (start >= total) {
                null
            } else {
                val end = min(start + segmentSize - 1, total - 1)
                DownloadSegment(index, start, end)
            }
        }
    }

    private fun downloadedSegmentBytes(workDir: File, segments: List<DownloadSegment>): Long =
        segments.sumOf { segment ->
            segmentFile(workDir, segment.index).length().coerceIn(0L, segment.length)
        }

    private fun segmentFile(workDir: File, index: Int): File =
        File(workDir, "part-$index")

    private fun createTargetUri(treeUri: Uri?, track: Track): Uri =
        if (treeUri != null) {
            createCustomTargetUri(treeUri, track)
        } else {
            createPublicTargetUri(track)
        }

    private fun createPublicTargetUri(track: Track): Uri {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, safeFileName(track))
                put(MediaStore.MediaColumns.MIME_TYPE, mimeTypeFor(track))
                put(MediaStore.MediaColumns.RELATIVE_PATH, publicRelativePath())
                put(MediaStore.MediaColumns.IS_PENDING, 1)
                if (downloadDirectory() != DOWNLOAD_DIRECTORY_DOWNLOADS) {
                    put(MediaStore.Audio.AudioColumns.TITLE, track.title)
                    put(MediaStore.Audio.AudioColumns.ARTIST, track.artist)
                    put(MediaStore.Audio.AudioColumns.ALBUM, track.album)
                    put(MediaStore.Audio.AudioColumns.IS_MUSIC, 1)
                }
            }
            val collection = if (downloadDirectory() == DOWNLOAD_DIRECTORY_DOWNLOADS) {
                MediaStore.Downloads.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }
            return resolver.insert(collection, values) ?: throw IOException("无法创建下载文件")
        }
        val directory = File(
            Environment.getExternalStoragePublicDirectory(publicDirectoryName()),
            "Yukine"
        )
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("无法创建下载目录")
        }
        return Uri.fromFile(File(directory, safeFileName(track)))
    }

    private fun openTargetOutputStream(targetUri: Uri): OutputStream? =
        if (targetUri.scheme.equals("file", ignoreCase = true)) {
            FileOutputStream(File(targetUri.path.orEmpty()))
        } else {
            context.contentResolver.openOutputStream(targetUri, "w")
        }

    private fun deleteTargetUri(targetUri: Uri) {
        runCatching {
            if (targetUri.scheme.equals("file", ignoreCase = true)) {
                File(targetUri.path.orEmpty()).delete()
            } else {
                context.contentResolver.delete(targetUri, null, null)
            }
        }
    }

    private fun markPublicTargetFinished(targetUri: Uri) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || targetUri.scheme.equals("file", ignoreCase = true)) {
            return
        }
        runCatching {
            context.contentResolver.update(
                targetUri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null
            )
        }
    }

    private fun createCustomTargetUri(treeUri: Uri, track: Track): Uri {
        val resolver = context.contentResolver
        val rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        val rootDocumentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocumentId)
        val yukineDir = findOrCreateYukineDirectory(rootDocumentUri)
        return DocumentsContract.createDocument(
            resolver,
            yukineDir,
            mimeTypeFor(track),
            safeFileName(track)
        ) ?: throw IOException("无法创建下载文件")
    }

    private fun scheduleArtworkDownload(track: Track, treeUri: Uri?) {
        if (track.albumArtUri == null) {
            return
        }
        customExecutor.execute {
            saveArtworkForTrack(track, treeUri)
        }
    }

    private fun saveArtworkForTrack(track: Track, treeUri: Uri?) {
        runCatching {
            val artworkUri = track.albumArtUri ?: return
            val source = readArtworkBytes(artworkUri) ?: return
            val targetUri = createArtworkTargetUri(treeUri, track, artworkUri, source.mimeType)
            try {
                openTargetOutputStream(targetUri)?.use { output ->
                    output.write(source.bytes)
                } ?: throw IOException("无法写入封面")
                markPublicTargetFinished(targetUri)
            } catch (_: Exception) {
                deleteTargetUri(targetUri)
            }
        }
    }

    private fun createArtworkTargetUri(
        treeUri: Uri?,
        track: Track,
        artworkUri: Uri,
        mimeType: String?
    ): Uri {
        val safeMimeType = artworkMimeTypeFor(artworkUri, mimeType)
        val fileName = safeArtworkFileName(track, artworkUri, safeMimeType)
        if (treeUri != null) {
            return createCustomArtworkTargetUri(treeUri, safeMimeType, fileName)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, safeMimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, publicRelativePath())
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val collection = if (downloadDirectory() == DOWNLOAD_DIRECTORY_DOWNLOADS) {
                MediaStore.Downloads.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            return resolver.insert(collection, values) ?: throw IOException("无法创建封面文件")
        }
        val directory = File(
            Environment.getExternalStoragePublicDirectory(publicDirectoryName()),
            "Yukine"
        )
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("无法创建下载目录")
        }
        return Uri.fromFile(File(directory, fileName))
    }

    private fun createCustomArtworkTargetUri(treeUri: Uri, mimeType: String, fileName: String): Uri {
        val resolver = context.contentResolver
        val rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        val rootDocumentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocumentId)
        val yukineDir = findOrCreateYukineDirectory(rootDocumentUri)
        return DocumentsContract.createDocument(
            resolver,
            yukineDir,
            mimeType,
            fileName
        ) ?: throw IOException("无法创建封面文件")
    }

    private fun readArtworkBytes(uri: Uri): ArtworkDownloadSource? {
        if (EmbeddedArtwork.isEmbeddedArtworkUri(uri)) {
            val bytes = EmbeddedArtwork.read(context, uri) ?: return null
            return ArtworkDownloadSource(bytes, null)
        }
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme != "http" && scheme != "https") {
            val mimeType = context.contentResolver.getType(uri)
            val bytes = context.contentResolver.openInputStream(uri)?.use(::readLimitedBytes) ?: return null
            return ArtworkDownloadSource(bytes, mimeType)
        }
        val connection = URL(uri.toString()).openConnection() as HttpURLConnection
        return try {
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 8000
            connection.readTimeout = 12000
            connection.setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
            connection.setRequestProperty("User-Agent", DEFAULT_USER_AGENT)
            connection.setRequestProperty("Referer", "https://music.163.com/")
            if (connection.responseCode !in 200..299) {
                null
            } else {
                connection.inputStream.use { input ->
                    ArtworkDownloadSource(readLimitedBytes(input), connection.contentType)
                }
            }
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun readLimitedBytes(input: java.io.InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_ARTWORK_BYTES) {
                throw IOException("封面过大")
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun updateCustomRecord(id: Long, transform: (TrackDownloadRecord) -> TrackDownloadRecord) {
        synchronized(records) {
            val current = records[id] ?: return
            records[id] = transform(current)
            persistRecordsLocked()
        }
    }

    private fun customDownloadTreeUri(): Uri? {
        if (downloadDirectory() != DOWNLOAD_DIRECTORY_CUSTOM) {
            return null
        }
        return preferences.getString(KEY_CUSTOM_TREE_URI, null)?.let(Uri::parse)
    }

    private fun shouldUseAppManagedDownload(track: Track): Boolean =
        track.dataPath.startsWith("streaming:", ignoreCase = true) ||
            streamingHeaders.forDataPath(track.dataPath).isNotEmpty()

    private fun safeFileName(track: Track): String {
        return "${safeBaseFileName(track)}.${extensionFor(track)}"
    }

    private fun safeBaseFileName(track: Track): String {
        val base = listOf(track.artist, track.title)
            .filter { it.isNotBlank() }
            .joinToString(" - ")
            .ifBlank { "Yukine Track ${track.id}" }
            .replace(Regex("[\\\\/:*?\"<>|\\r\\n]+"), "_")
            .trim()
            .take(120)
            .ifBlank { "Yukine Track ${track.id}" }
        return base
    }

    private fun safeArtworkFileName(track: Track, artworkUri: Uri, mimeType: String): String {
        return "${safeBaseFileName(track)} - cover.${artworkExtensionFor(artworkUri, mimeType)}"
    }

    private fun extensionFor(track: Track): String {
        val fromPath = track.contentUri.lastPathSegment
            ?.substringAfterLast('.', "")
            ?.lowercase(Locale.ROOT)
            ?.takeIf { it.length in 2..5 && it.all { c -> c.isLetterOrDigit() } }
        if (fromPath != null) {
            return fromPath
        }
        val codec = track.codec.lowercase(Locale.ROOT)
        return when {
            "flac" in codec -> "flac"
            "aac" in codec || "m4a" in codec -> "m4a"
            "ogg" in codec || "opus" in codec -> "ogg"
            "wav" in codec -> "wav"
            else -> "mp3"
        }
    }

    private fun mimeTypeFor(track: Track): String =
        when (extensionFor(track)) {
            "flac" -> "audio/flac"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            "ogg", "opus" -> "audio/ogg"
            "wav" -> "audio/wav"
            else -> "audio/mpeg"
        }

    private fun artworkMimeTypeFor(artworkUri: Uri, mimeType: String?): String {
        val cleanMime = mimeType
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase(Locale.ROOT)
            .orEmpty()
        if (cleanMime.startsWith("image/")) {
            return cleanMime
        }
        return when (artworkExtensionFor(artworkUri, cleanMime)) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            else -> "image/jpeg"
        }
    }

    private fun artworkExtensionFor(artworkUri: Uri, mimeType: String): String {
        return when {
            "png" in mimeType -> "png"
            "webp" in mimeType -> "webp"
            "gif" in mimeType -> "gif"
            "jpeg" in mimeType || "jpg" in mimeType -> "jpg"
            else -> artworkUri.lastPathSegment
                ?.substringAfterLast('.', "")
                ?.lowercase(Locale.ROOT)
                ?.substringBefore('?')
                ?.takeIf { it in setOf("jpg", "jpeg", "png", "webp", "gif") }
                ?.let { if (it == "jpeg") "jpg" else it }
                ?: "jpg"
        }
    }

    private fun downloadStartedMessage(prefix: String, track: Track): String {
        val artworkSuffix = if (track.albumArtUri != null) "，封面会同步保存" else ""
        return "$prefix：${track.title}，保存到 ${downloadDirectoryLabel()}$artworkSuffix"
    }

    private fun publicDirectoryName(): String =
        when (downloadDirectory()) {
            DOWNLOAD_DIRECTORY_DOWNLOADS -> Environment.DIRECTORY_DOWNLOADS
            else -> Environment.DIRECTORY_MUSIC
        }

    private fun publicRelativePath(): String =
        when (downloadDirectory()) {
            DOWNLOAD_DIRECTORY_DOWNLOADS -> "${Environment.DIRECTORY_DOWNLOADS}/Yukine"
            else -> "${Environment.DIRECTORY_MUSIC}/Yukine"
        }

    private fun normalizeDirectory(directory: String?): String =
        when (directory?.trim()?.lowercase(Locale.ROOT)) {
            DOWNLOAD_DIRECTORY_CUSTOM -> DOWNLOAD_DIRECTORY_CUSTOM
            DOWNLOAD_DIRECTORY_DOWNLOADS -> DOWNLOAD_DIRECTORY_DOWNLOADS
            else -> DOWNLOAD_DIRECTORY_MUSIC
        }

    private fun mapStatus(status: Int): TrackDownloadStatus = when (status) {
        DownloadManager.STATUS_PENDING -> TrackDownloadStatus.Pending
        DownloadManager.STATUS_RUNNING -> TrackDownloadStatus.Running
        DownloadManager.STATUS_PAUSED -> TrackDownloadStatus.Paused
        DownloadManager.STATUS_SUCCESSFUL -> TrackDownloadStatus.Finished
        DownloadManager.STATUS_FAILED -> TrackDownloadStatus.Failed
        else -> TrackDownloadStatus.Unknown
    }

    private fun progressPercent(downloaded: Long, total: Long): Int {
        if (total <= 0L || downloaded <= 0L) {
            return 0
        }
        return ((downloaded * 100L) / total).toInt().coerceIn(0, 100)
    }

    private fun Cursor.longColumn(name: String): Long {
        val index = getColumnIndex(name)
        return if (index >= 0) getLong(index) else 0L
    }

    private fun Cursor.intColumn(name: String): Int {
        val index = getColumnIndex(name)
        return if (index >= 0) getInt(index) else 0
    }

    private fun Cursor.stringColumn(name: String): String {
        val index = getColumnIndex(name)
        return if (index >= 0) getString(index).orEmpty() else ""
    }

    private fun restoreRecords() {
        val raw = preferences.getString(KEY_RECORDS, "").orEmpty()
        if (raw.isBlank()) {
            return
        }
        synchronized(records) {
            records.clear()
            raw.split('\n')
                .filter { it.isNotBlank() }
                .forEach { line ->
                    val parts = line.split('|')
                    val id = parts.getOrNull(0)?.toLongOrNull() ?: return@forEach
                    records[id] = TrackDownloadRecord(
                        downloadId = id,
                        title = parts.getOrNull(1)?.decodeRecordPart().orEmpty().ifBlank { "Yukine Track" },
                        artist = parts.getOrNull(2)?.decodeRecordPart().orEmpty(),
                        quality = parts.getOrNull(3)?.decodeRecordPart().orEmpty().ifBlank { "high" },
                        status = parts.getOrNull(4)?.decodeRecordPart()
                            ?.let { runCatching { TrackDownloadStatus.valueOf(it) }.getOrNull() }
                            ?: TrackDownloadStatus.Unknown,
                        bytesDownloaded = parts.getOrNull(5)?.toLongOrNull() ?: 0L,
                        totalBytes = parts.getOrNull(6)?.toLongOrNull() ?: -1L,
                        localUri = parts.getOrNull(7)?.decodeRecordPart().orEmpty(),
                        album = parts.getOrNull(8)?.decodeRecordPart().orEmpty(),
                        durationMs = parts.getOrNull(9)?.toLongOrNull() ?: 0L,
                        sourceUri = parts.getOrNull(10)?.decodeRecordPart().orEmpty(),
                        treeUri = parts.getOrNull(11)?.decodeRecordPart().orEmpty(),
                        dataPath = parts.getOrNull(12)?.decodeRecordPart().orEmpty(),
                        codec = parts.getOrNull(13)?.decodeRecordPart().orEmpty(),
                        albumArtUri = parts.getOrNull(14)?.decodeRecordPart().orEmpty()
                    )
                }
        }
    }

    private fun persistRecordsLocked() {
        val raw = records.values.joinToString("\n") { record ->
            listOf(
                record.downloadId.toString(),
                record.title.encodeRecordPart(),
                record.artist.encodeRecordPart(),
                record.quality.encodeRecordPart(),
                record.status.name.encodeRecordPart(),
                record.bytesDownloaded.toString(),
                record.totalBytes.toString(),
                record.localUri.encodeRecordPart(),
                record.album.encodeRecordPart(),
                record.durationMs.toString(),
                record.sourceUri.encodeRecordPart(),
                record.treeUri.encodeRecordPart(),
                record.dataPath.encodeRecordPart(),
                record.codec.encodeRecordPart(),
                record.albumArtUri.encodeRecordPart()
            ).joinToString("|")
        }
        preferences.edit().putString(KEY_RECORDS, raw).apply()
    }

    private fun String.encodeRecordPart(): String =
        replace("%", "%25").replace("|", "%7C").replace("\n", "%0A").replace("\r", "%0D")

    private fun String.decodeRecordPart(): String =
        replace("%0D", "\r").replace("%0A", "\n").replace("%7C", "|").replace("%25", "%")

    private fun findOrCreateYukineDirectory(rootDocumentUri: Uri): Uri {
        findChildDirectory(rootDocumentUri, "Yukine")?.let { return it }
        return DocumentsContract.createDocument(
            context.contentResolver,
            rootDocumentUri,
            DocumentsContract.Document.MIME_TYPE_DIR,
            "Yukine"
        ) ?: rootDocumentUri
    }

    private fun findChildDirectory(parentDocumentUri: Uri, displayName: String): Uri? {
        val parentDocumentId = DocumentsContract.getDocumentId(parentDocumentUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(parentDocumentUri, parentDocumentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        return runCatching {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIndex).orEmpty()
                    val mimeType = cursor.getString(mimeIndex).orEmpty()
                    if (name == displayName && mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                        val documentId = cursor.getString(idIndex).orEmpty()
                        if (documentId.isNotBlank()) {
                            return@use DocumentsContract.buildDocumentUriUsingTree(parentDocumentUri, documentId)
                        }
                    }
                }
                null
            }
        }.getOrNull()
    }

    private data class TrackDownloadRecord(
        val downloadId: Long,
        val title: String,
        val artist: String,
        val quality: String,
        val status: TrackDownloadStatus = TrackDownloadStatus.Unknown,
        val bytesDownloaded: Long = 0L,
        val totalBytes: Long = -1L,
        val localUri: String = "",
        val album: String = "",
        val durationMs: Long = 0L,
        val sourceUri: String = "",
        val treeUri: String = "",
        val dataPath: String = "",
        val codec: String = "",
        val albumArtUri: String = ""
    ) {
        fun toTrack(): Track =
            Track(
                downloadId,
                title,
                artist,
                album,
                durationMs,
                Uri.parse(sourceUri),
                dataPath,
                0L,
                albumArtUri.takeIf { it.isNotBlank() }?.let(Uri::parse),
                codec,
                0,
                0,
                0,
                0
            )
    }

    private data class ArtworkDownloadSource(
        val bytes: ByteArray,
        val mimeType: String?
    )

    private data class DownloadProbe(
        val totalBytes: Long,
        val acceptRanges: Boolean
    )

    private data class DownloadSegment(
        val index: Int,
        val start: Long,
        val end: Long
    ) {
        val length: Long
            get() = end - start + 1L
    }

    companion object {
        private const val KEY_RECORDS = "records"
        private const val KEY_DIRECTORY = "directory"
        private const val KEY_CUSTOM_TREE_URI = "custom_tree_uri"
        private const val MAX_ARTWORK_BYTES = 10 * 1024 * 1024
        private const val APP_DOWNLOAD_CONCURRENCY = 2
        private const val SEGMENT_DOWNLOAD_CONCURRENCY = 4
        private const val MIN_SEGMENTED_DOWNLOAD_BYTES = 3L * 1024L * 1024L
        private const val SINGLE_PART_FILE_NAME = "track.part"
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 Yukine/1.0"
        const val DOWNLOAD_DIRECTORY_MUSIC = "music"
        const val DOWNLOAD_DIRECTORY_DOWNLOADS = "downloads"
        const val DOWNLOAD_DIRECTORY_CUSTOM = "custom"
    }
}

private class DownloadPausedException : IOException()
