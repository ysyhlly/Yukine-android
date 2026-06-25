package app.yukine;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public final class StatusMessageControllerTest {
    @Test
    public void localizesLegacySystemStatusMessages() {
        assertEquals(
                AppLanguage.text(AppLanguage.MODE_CHINESE, "loading.library"),
                StatusMessageController.localize("Loading library", AppLanguage.MODE_CHINESE)
        );
        assertEquals(
                AppLanguage.text(AppLanguage.MODE_CHINESE, "audio.permission.required"),
                StatusMessageController.localize("Audio permission required", AppLanguage.MODE_CHINESE)
        );
        assertEquals(
                AppLanguage.text(AppLanguage.MODE_CHINESE, "queue.not.connected"),
                StatusMessageController.localize("Queue is not connected", AppLanguage.MODE_CHINESE)
        );
        assertEquals(
                AppLanguage.text(AppLanguage.MODE_CHINESE, "playback.service.not.connected"),
                StatusMessageController.localize("Playback service is not connected", AppLanguage.MODE_CHINESE)
        );
    }

    @Test
    public void localizesManualStreamingCookieStatusMessages() {
        assertEquals("账号信息为空", AppLanguage.text(AppLanguage.MODE_CHINESE, "streaming.cookie.empty"));
        assertEquals("账号信息已保存", AppLanguage.text(AppLanguage.MODE_CHINESE, "streaming.cookie.saved"));
        assertEquals(
                AppLanguage.text(AppLanguage.MODE_CHINESE, "streaming.cookie.empty"),
                StatusMessageController.localize("Cookie is empty", AppLanguage.MODE_CHINESE)
        );
        assertEquals(
                AppLanguage.text(AppLanguage.MODE_CHINESE, "streaming.cookie.saved"),
                StatusMessageController.localize("Cookie saved", AppLanguage.MODE_CHINESE)
        );
        assertEquals(
                AppLanguage.text(AppLanguage.MODE_CHINESE, "streaming.choose.login.provider"),
                StatusMessageController.localize("Choose a streaming provider to sign in", AppLanguage.MODE_CHINESE)
        );
    }

    @Test
    public void preservesUnknownMessages() {
        assertEquals("Custom status", StatusMessageController.localize("Custom status", AppLanguage.MODE_CHINESE));
    }

    @Test
    public void feedbackIgnoresBlankMessagesAndPublishesText() {
        StatusMessageViewModel viewModel = new StatusMessageViewModel();
        List<String> updates = new ArrayList<>();
        StatusMessageController controller = new StatusMessageController(
                viewModel,
                new StatusMessageHostBindings(
                        () -> AppLanguage.MODE_CHINESE,
                        updates::add
                )
        );

        controller.showFeedback(null);
        controller.showFeedback("   ");
        controller.showFeedback("Loading library");

        assertEquals(1, updates.size());
        assertEquals(AppLanguage.text(AppLanguage.MODE_CHINESE, "loading.library"), updates.get(0));
        assertEquals(updates.get(0), viewModel.getState().getValue().getMessage());
    }

    @Test
    public void statusKeyResolvesThroughMessageTextResolver() {
        StatusMessageViewModel viewModel = new StatusMessageViewModel();
        List<String> updates = new ArrayList<>();
        StatusMessageController controller = new StatusMessageController(
                viewModel,
                new StatusMessageHostBindings(
                        () -> AppLanguage.MODE_CHINESE,
                        updates::add
                )
        );

        controller.setStatusKey("backup.export.success");

        assertEquals(1, updates.size());
        assertEquals(AppLanguage.text(AppLanguage.MODE_CHINESE, "backup.export.success"), updates.get(0));
        assertEquals(updates.get(0), viewModel.getState().getValue().getMessage());
    }

    @Test
    public void ignoresStatusUpdatesWhenHostIsMissing() {
        StatusMessageViewModel viewModel = new StatusMessageViewModel();
        StatusMessageController controller = new StatusMessageController(
                viewModel,
                null,
                new MessageTextResolver(() -> AppLanguage.MODE_CHINESE)
        );

        controller.setStatus("Loading library");

        assertEquals("", viewModel.getState().getValue().getMessage());
    }

    @Test
    public void localizesPlaybackServiceErrorMessages() {
        assertEquals(
                AppLanguage.text(AppLanguage.MODE_ENGLISH, "playback.error.unable.to.open"),
                PlaybackErrorMessageLocalizer.localize("\u65e0\u6cd5\u6253\u5f00\u8fd9\u9996\u6b4c\u66f2\u3002", AppLanguage.MODE_ENGLISH)
        );
        assertEquals(
                AppLanguage.text(AppLanguage.MODE_CHINESE, "playback.error.streaming.not.resolved"),
                StatusMessageController.localize(
                        "\u6d41\u5a92\u4f53\u6b4c\u66f2\u5c1a\u672a\u89e3\u6790\uff0c\u8bf7\u91cd\u65b0\u70b9\u51fb\u6b4c\u66f2\u64ad\u653e\u3002",
                        AppLanguage.MODE_CHINESE
                )
        );
    }
}
