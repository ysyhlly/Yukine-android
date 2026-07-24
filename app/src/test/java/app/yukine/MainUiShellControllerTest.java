package app.yukine;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class MainUiShellControllerTest {
    @Test
    public void suppressesMissingStreamingInterfaceToast() {
        assertTrue(MainUiShellController.shouldSuppressToast(
                "Gateway request failed (404): 接口未找到"
        ));
    }

    @Test
    public void keepsOtherErrorsVisible() {
        assertFalse(MainUiShellController.shouldSuppressToast(
                "Gateway request failed (500): 服务暂时不可用"
        ));
    }
}
