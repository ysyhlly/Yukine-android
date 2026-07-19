package app.yukine.streaming

enum class GoldenRecordingLabel {
    SAME_RECORDING,
    DIFFERENT_RECORDING,
    ALTERNATE_VERSION,
    UNCERTAIN
}

data class GoldenRecordingPair(
    val name: String,
    val left: StreamingTrackMatchPolicy.Reference,
    val right: StreamingTrackMatchPolicy.Reference,
    val expected: GoldenRecordingLabel
)

data class GoldenPlaybackCandidate(
    val id: String,
    val reference: StreamingTrackMatchPolicy.Reference
)

data class GoldenPlaybackCase(
    val name: String,
    val target: StreamingTrackMatchPolicy.Reference,
    val candidatesInProviderOrder: List<GoldenPlaybackCandidate>,
    val expectedTop1Id: String
)

object RecordingMatchGoldenFixtures {
    val recordingPairs = listOf(
        pair("same recording different encoding", expected = GoldenRecordingLabel.SAME_RECORDING),
        pair(
            "local and webdav duration drift",
            rightDurationMs = 182_500L,
            expected = GoldenRecordingLabel.SAME_RECORDING
        ),
        pair(
            "same title different artist",
            rightArtist = "Other Artist",
            rightCanonicalWorkId = "work-other",
            expected = GoldenRecordingLabel.DIFFERENT_RECORDING
        ),
        pair(
            "original and live",
            rightTitle = "Echo (Live at Tokyo)",
            expected = GoldenRecordingLabel.ALTERNATE_VERSION
        ),
        pair(
            "original and remix",
            rightTitle = "Echo - Club Remix",
            expected = GoldenRecordingLabel.ALTERNATE_VERSION
        ),
        pair(
            "original and cover",
            rightTitle = "Echo（翻唱）",
            expected = GoldenRecordingLabel.ALTERNATE_VERSION
        ),
        pair(
            "vocal and instrumental",
            rightTitle = "Echo (Instrumental)",
            expected = GoldenRecordingLabel.ALTERNATE_VERSION
        ),
        pair(
            "vocal and karaoke",
            rightTitle = "Echo (Karaoke)",
            expected = GoldenRecordingLabel.ALTERNATE_VERSION
        ),
        pair(
            "original and remaster",
            rightTitle = "Echo (2024 Remastered)",
            expected = GoldenRecordingLabel.SAME_RECORDING
        ),
        pair(
            "clean and explicit need confirmation",
            leftTitle = "Echo (Clean)",
            rightTitle = "Echo (Explicit)",
            expected = GoldenRecordingLabel.ALTERNATE_VERSION
        ),
        pair(
            "wrong isrc cannot override live",
            rightTitle = "Echo (Live)",
            leftIsrc = "JPABC1234567",
            rightIsrc = "JP-ABC-12-34567",
            expected = GoldenRecordingLabel.ALTERNATE_VERSION
        ),
        pair(
            "missing primary artist",
            rightArtist = "",
            rightCanonicalWorkId = "",
            rightCanonicalWorkConfirmed = false,
            expected = GoldenRecordingLabel.UNCERTAIN
        )
    )

    val playbackCases = listOf(
        GoldenPlaybackCase(
            name = "wrong first result correct second result",
            target = reference(),
            candidatesInProviderOrder = listOf(
                GoldenPlaybackCandidate("wrong", reference(title = "Other Song", artist = "Other Artist")),
                GoldenPlaybackCandidate("right", reference())
            ),
            expectedTop1Id = "right"
        ),
        GoldenPlaybackCase(
            name = "original ahead of earlier live",
            target = reference(),
            candidatesInProviderOrder = listOf(
                GoldenPlaybackCandidate("live", reference(title = "Echo (Live)")),
                GoldenPlaybackCandidate("original", reference())
            ),
            expectedTop1Id = "original"
        ),
        GoldenPlaybackCase(
            name = "correct artist ahead of same title wrong artist",
            target = reference(),
            candidatesInProviderOrder = listOf(
                GoldenPlaybackCandidate("wrong-artist", reference(artist = "Other Artist")),
                GoldenPlaybackCandidate("right-artist", reference())
            ),
            expectedTop1Id = "right-artist"
        ),
        GoldenPlaybackCase(
            name = "closer duration wins",
            target = reference(durationMs = 180_000L),
            candidatesInProviderOrder = listOf(
                GoldenPlaybackCandidate("far", reference(durationMs = 195_000L)),
                GoldenPlaybackCandidate("near", reference(durationMs = 181_000L))
            ),
            expectedTop1Id = "near"
        ),
        GoldenPlaybackCase(
            name = "unique low similarity remains top1",
            target = reference(),
            candidatesInProviderOrder = listOf(
                GoldenPlaybackCandidate("only", reference(title = "Unknown", artist = "Unknown"))
            ),
            expectedTop1Id = "only"
        )
    )

    fun reference(
        title: String = "Echo",
        artist: String = "Artist",
        album: String = "Album",
        durationMs: Long = 180_000L,
        isrc: String = "",
        canonicalWorkId: String = "",
        canonicalWorkConfirmed: Boolean = false
    ) = StreamingTrackMatchPolicy.Reference(
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        isrc = isrc,
        canonicalWorkId = canonicalWorkId,
        canonicalWorkConfirmed = canonicalWorkConfirmed
    )

    private fun pair(
        name: String,
        leftTitle: String = "Echo",
        rightTitle: String = "Echo",
        leftArtist: String = "Artist",
        rightArtist: String = "Artist",
        leftDurationMs: Long = 180_000L,
        rightDurationMs: Long = 180_000L,
        leftIsrc: String = "",
        rightIsrc: String = "",
        leftCanonicalWorkId: String = "work-echo",
        rightCanonicalWorkId: String = "work-echo",
        leftCanonicalWorkConfirmed: Boolean = true,
        rightCanonicalWorkConfirmed: Boolean = true,
        expected: GoldenRecordingLabel
    ) = GoldenRecordingPair(
        name = name,
        left = reference(
            leftTitle,
            leftArtist,
            durationMs = leftDurationMs,
            isrc = leftIsrc,
            canonicalWorkId = leftCanonicalWorkId,
            canonicalWorkConfirmed = leftCanonicalWorkConfirmed
        ),
        right = reference(
            rightTitle,
            rightArtist,
            durationMs = rightDurationMs,
            isrc = rightIsrc,
            canonicalWorkId = rightCanonicalWorkId,
            canonicalWorkConfirmed = rightCanonicalWorkConfirmed
        ),
        expected = expected
    )
}
