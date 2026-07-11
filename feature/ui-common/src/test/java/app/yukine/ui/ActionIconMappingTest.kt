package app.yukine.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ActionIconMappingTest {
    private val noOp = Runnable { }

    @Test
    fun trackHeaderActionsUseSemanticIconsInChineseAndEnglish() {
        assertEquals(EchoIconKind.Shuffle, iconForTrackHeaderAction("随机"))
        assertEquals(EchoIconKind.Shuffle, iconForTrackHeaderAction("Shuffle"))
        assertEquals(EchoIconKind.Download, iconForTrackHeaderAction("下载当前列表"))
        assertEquals(EchoIconKind.Download, iconForTrackHeaderAction("Download current list"))
    }

    @Test
    fun settingsActionsReservePlusForRealCreation() {
        assertEquals(EchoIconKind.Palette, iconForSettingsAction(action("外观")))
        assertEquals(EchoIconKind.Download, iconForSettingsAction(action("下载管理")))
        assertEquals(EchoIconKind.Permission, iconForSettingsAction(action("授予音乐访问权限")))
        assertEquals(EchoIconKind.Upload, iconForSettingsAction(action("导出备份")))
        assertEquals(EchoIconKind.Info, iconForSettingsAction(action("关于")))
        assertEquals(EchoIconKind.Network, iconForSettingsAction(action("Yukine QQ 群")))
        assertEquals(EchoIconKind.Refresh, iconForSettingsAction(action("恢复已隐藏歌曲")))
        assertEquals(EchoIconKind.Settings, iconForSettingsAction(action("其他选项")))
        assertEquals(EchoIconKind.Check, iconForSettingsAction(action("开启功能", SettingsActionStyle.Toggle)))
        assertEquals(EchoIconKind.Action, iconForSettingsAction(action("新建项目")))
    }

    private fun action(label: String, style: SettingsActionStyle = SettingsActionStyle.Navigation) =
        SettingsAction(label = label, onClick = noOp, style = style)
}
