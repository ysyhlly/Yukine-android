package app.yukine.playback

import app.yukine.model.Track
import kotlinx.coroutines.flow.StateFlow

/**
 * 播放控制器统一接口，封装播放服务细节。
 *
 * 这是播放能力的统一入口，屏蔽 EchoPlaybackService 的实现细节，
 * 让 ViewModel 和 UI 只依赖接口而非具体服务。
 *
 * 设计原则：
 * - Service 是实现细节，不应被 ViewModel 直接依赖
 * - 状态通过 StateFlow 暴露，支持响应式 UI
 * - 命令方法返回结果或通过状态流通知
 * - 单元测试可使用 Fake 实现
 */
interface PlaybackController {

    /**
     * 播放状态流，包含当前曲目、播放/暂停、进度、时长等。
     */
    val state: StateFlow<PlaybackStateSnapshot>

    /**
     * 播放队列流。
     */
    val queue: StateFlow<List<Track>>

    /**
     * 服务是否已连接。
     */
    fun isServiceConnected(): Boolean

    /**
     * 播放指定队列。
     *
     * @param tracks 曲目列表
     * @param index 起始索引
     */
    suspend fun playQueue(tracks: List<Track>, index: Int)

    /**
     * 播放/暂停切换。
     */
    suspend fun togglePlayPause()

    /**
     * 暂停播放。
     */
    suspend fun pause()

    /**
     * 继续播放。
     */
    suspend fun play()

    /**
     * 下一首。
     */
    suspend fun skipToNext()

    /**
     * 上一首。
     */
    suspend fun skipToPrevious()

    /**
     * 跳转到指定位置。
     *
     * @param positionMs 位置（毫秒）
     */
    suspend fun seekTo(positionMs: Long)

    /**
     * 设置随机播放。
     *
     * @param enabled 是否启用
     */
    suspend fun setShuffleEnabled(enabled: Boolean)

    /**
     * 切换循环模式。
     */
    suspend fun cycleRepeatMode()

    /**
     * 设置循环模式。
     *
     * @param mode 循环模式（Player.REPEAT_MODE_*）
     */
    suspend fun setRepeatMode(mode: Int)

    /**
     * 移动队列中的曲目。
     *
     * @param fromIndex 源索引
     * @param toIndex 目标索引
     */
    suspend fun moveQueueItem(fromIndex: Int, toIndex: Int)

    /**
     * 从队列中移除曲目。
     *
     * @param trackIds 要移除的曲目 ID 集合
     */
    suspend fun removeQueueItems(trackIds: Set<Long>)

    /**
     * 清空队列。
     */
    suspend fun clearQueue()

    /**
     * 追加曲目到队列末尾。
     *
     * @param tracks 要追加的曲目列表
     */
    suspend fun appendToQueue(tracks: List<Track>)

    /**
     * 替换当前播放曲目并恢复播放位置。
     *
     * @param track 新曲目
     * @param positionMs 播放位置
     */
    suspend fun replaceCurrentTrackAndResume(track: Track, positionMs: Long)

    /**
     * 启动睡眠定时器。
     *
     * @param minutes 分钟数
     */
    suspend fun startSleepTimer(minutes: Int)

    /**
     * 取消睡眠定时器。
     */
    suspend fun cancelSleepTimer()

    /**
     * 预缓存曲目。
     *
     * @param track 要预缓存的曲目
     */
    suspend fun precacheTrack(track: Track)

    /**
     * 更新队列中的曲目信息。
     *
     * @param track 更新后的曲目
     */
    suspend fun replaceQueuedTrack(track: Track)

    /**
     * 根据 ID 更新队列中的曲目。
     *
     * @param oldTrackId 旧曲目 ID
     * @param updatedTrack 更新后的曲目
     */
    suspend fun replaceQueuedTrackById(oldTrackId: Long, updatedTrack: Track)
}
