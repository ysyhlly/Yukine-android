package app.yukine.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ActionIconMappingTest {
    private val noOp = Runnable { }

    @Test
    fun trackHeaderActionsCarrySemanticIconsWithoutLabelParsing() {
        assertEquals(
            EchoIconKind.Shuffle,
            TrackListHeaderAction("任意文案", noOp, icon = EchoIconKind.Shuffle).icon
        )
        assertEquals(
            EchoIconKind.Download,
            TrackListHeaderAction("同一文案", noOp, icon = EchoIconKind.Download).icon
        )
        assertEquals(
            EchoIconKind.Back,
            TrackListHeaderAction("任意返回文案", noOp, icon = EchoIconKind.Back, isBack = true).icon
        )
    }

    @Test
    fun settingsActionsUseExplicitIconsBeforeStyleFallback() {
        assertEquals(EchoIconKind.Palette, iconForSettingsAction(action("任意本地化文案", icon = EchoIconKind.Palette)))
        assertEquals(EchoIconKind.Network, iconForSettingsAction(action("同一文案", icon = EchoIconKind.Network)))
        assertEquals(EchoIconKind.Check, iconForSettingsAction(action("任意开关", SettingsActionStyle.Toggle)))
        assertEquals(EchoIconKind.Gauge, iconForSettingsAction(action("任意滑杆", SettingsActionStyle.Slider)))
        assertEquals(EchoIconKind.Delete, iconForSettingsAction(action("任意危险操作", SettingsActionStyle.Destructive)))
        assertEquals(EchoIconKind.Settings, iconForSettingsAction(action("任意默认操作")))
    }

    private fun action(
        label: String,
        style: SettingsActionStyle = SettingsActionStyle.Navigation,
        icon: EchoIconKind? = null
    ) = SettingsAction(label = label, onClick = noOp, style = style, icon = icon)
}
