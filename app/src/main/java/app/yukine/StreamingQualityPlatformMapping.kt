package app.yukine

import app.yukine.streaming.StreamingAudioQuality

object StreamingQualityPlatformMapping {
    @JvmStatic
    fun explanation(quality: StreamingAudioQuality, languageMode: String): String {
        return if (AppLanguage.isChinese(languageMode)) {
            chineseExplanation(quality)
        } else {
            englishExplanation(quality)
        }
    }

    @JvmStatic
    fun summaryForSettings(quality: String, languageMode: String): String {
        val audioQuality = StreamingAudioQuality.fromWireName(StreamingQualityPreference.normalize(quality))
            ?: StreamingAudioQuality.HIGH
        return explanation(audioQuality, languageMode)
    }

    @JvmStatic
    fun downloadDialogMessage(languageMode: String): String {
        return if (AppLanguage.isChinese(languageMode)) {
            "Yukine 使用统一音质档位，再映射到各平台最接近的真实格式。若音源、账号、会员或地区不支持，平台可能返回失败或自动降级。\n\n" +
                "标准：网易云 standard / QQ M500 MP3 / LX 优先 AAC 或 MP3。\n" +
                "高音质：网易云 higher / QQ M800 320k MP3 / LX 优先 320k MP3。\n" +
                "无损：网易云 lossless / QQ F000 FLAC / LX 优先 FLAC。\n" +
                "Hi-Res：网易云 hires / QQ 有 Hi-Res 标记时尝试最高规格，否则回退 FLAC / LX 优先 Hi-Res 或 FLAC。"
        } else {
            "Yukine uses one quality level across sources, then maps it to the closest platform format. If the source, account, membership, or region does not support it, the provider may fail or fall back to a lower level.\n\n" +
                "Standard: NetEase standard / QQ M500 MP3 / LX AAC or MP3.\n" +
                "High: NetEase higher / QQ M800 320k MP3 / LX 320k MP3 when available.\n" +
                "Lossless: NetEase lossless / QQ F000 FLAC / LX FLAC first.\n" +
                "Hi-Res: NetEase hires / QQ Hi-Res when exposed, otherwise FLAC / LX Hi-Res or FLAC first."
        }
    }

    @JvmStatic
    fun optionLabel(quality: StreamingAudioQuality, languageMode: String): String {
        val label = SettingsPageRenderController.streamingQualityLabel(quality.wireName, languageMode)
        return "$label - ${shortExplanation(quality, languageMode)}"
    }

    private fun chineseExplanation(quality: StreamingAudioQuality): String {
        return when (quality) {
            StreamingAudioQuality.STANDARD ->
                "通用目标：约 128k。网易云请求 standard；QQ 使用 M500 MP3；LX/洛雪优先 AAC/MP3，适合弱网和省流。"
            StreamingAudioQuality.HIGH ->
                "通用目标：约 320k。网易云请求 higher；QQ 使用 M800 320k MP3；LX/洛雪优先 320k MP3，当前推荐档。"
            StreamingAudioQuality.LOSSLESS ->
                "通用目标：FLAC/高码率。网易云请求 lossless；QQ 使用 F000 FLAC；LX/洛雪优先 FLAC，失败时按音源能力降级。"
            StreamingAudioQuality.HIRES ->
                "通用目标：平台可用的最高规格。网易云请求 hires；QQ 检测到 Hi-Res 时优先最高规格，否则回退 FLAC；LX/洛雪优先 Hi-Res/FLAC。"
        }
    }

    private fun englishExplanation(quality: StreamingAudioQuality): String {
        return when (quality) {
            StreamingAudioQuality.STANDARD ->
                "Target: about 128k. NetEase uses standard; QQ uses M500 MP3; LX prefers AAC/MP3. Best for weak networks."
            StreamingAudioQuality.HIGH ->
                "Target: about 320k. NetEase uses higher; QQ uses M800 320k MP3; LX prefers 320k MP3. Recommended default."
            StreamingAudioQuality.LOSSLESS ->
                "Target: FLAC/high bitrate. NetEase uses lossless; QQ uses F000 FLAC; LX prefers FLAC, with provider fallback."
            StreamingAudioQuality.HIRES ->
                "Target: the highest available platform tier. NetEase uses hires; QQ tries Hi-Res when exposed or falls back to FLAC; LX prefers Hi-Res/FLAC."
        }
    }

    private fun shortExplanation(quality: StreamingAudioQuality, languageMode: String): String {
        val english = !AppLanguage.isChinese(languageMode)
        return when (quality) {
            StreamingAudioQuality.STANDARD -> if (english) "128k / small size" else "约 128k / 更省流"
            StreamingAudioQuality.HIGH -> if (english) "320k / recommended" else "约 320k / 推荐"
            StreamingAudioQuality.LOSSLESS -> if (english) "FLAC first" else "优先 FLAC"
            StreamingAudioQuality.HIRES -> if (english) "highest available" else "可用时最高规格"
        }
    }
}
