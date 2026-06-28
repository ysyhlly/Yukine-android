package app.yukine

import app.yukine.ui.HomeDashboardActions
import app.yukine.ui.HomeDashboardUiState

data class MainActivityHomeDashboardUiState(
    val content: HomeDashboardUiState = HomeDashboardUiState(),
    val actions: HomeDashboardActions = emptyHomeDashboardActions()
)

fun emptyHomeDashboardActions(): HomeDashboardActions = HomeDashboardActions(
    onOpenStat = emptyList(),
    onContinue = Runnable { },
    onOpenNowPlaying = Runnable { },
    onPlayRecent = emptyList(),
    onRefresh = Runnable { },
    onViewQueue = Runnable { },
    onShuffleAll = Runnable { },
    onRecentTabChanged = { }
)
