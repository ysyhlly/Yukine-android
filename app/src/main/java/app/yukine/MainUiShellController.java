package app.yukine;

import android.os.Build;
import android.view.View;
import android.view.Window;
import android.widget.ScrollView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;

import app.yukine.ui.EchoTheme;

final class MainUiShellController {
    private final ComponentActivity activity;
    private String lastToastStatus = "";
    private long lastToastAtMs = 0L;

    MainUiShellController(ComponentActivity activity) {
        this.activity = activity;
    }

    void applyThemeSurface() {
        Window window = activity.getWindow();
        int background = EchoTheme.backgroundArgb(activity);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
        }
        window.setStatusBarColor(background);
        window.setNavigationBarColor(background);
        int flags = window.getDecorView().getSystemUiVisibility();
        flags &= ~View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        flags &= ~View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        flags &= ~View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        flags &= ~View.SYSTEM_UI_FLAG_FULLSCREEN;
        flags &= ~View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        flags &= ~View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        if (EchoTheme.isLight(activity)) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
        } else {
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
        }
        window.getDecorView().setSystemUiVisibility(flags);
    }

    boolean hasContentHost() {
        return false;
    }

    boolean navigateContentRoute(String route) {
        return false;
    }

    void updateSelectedContentRoute(String route) {
    }

    void useScrollingContentContainer() {
    }

    void useFixedContentContainer(java.util.List<View> existingChildren) {
    }

    void prepareHorizontalContentTransition(boolean next) {
    }

    ScrollView getScrollView() {
        return null;
    }

    void updateTabBar(String selectedTab) {
    }

    void updateLanguage(String languageMode) {
    }

    boolean hasTabBar() {
        return false;
    }

    void updateStatus(String status) {
        if (status == null) {
            return;
        }
        String message = status.trim();
        if (message.isEmpty() || shouldSuppressToast(message)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (message.equals(lastToastStatus) && now - lastToastAtMs < 1800L) {
            return;
        }
        lastToastStatus = message;
        lastToastAtMs = now;
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
    }

    private boolean shouldSuppressToast(String message) {
        return "正在加载曲库".equals(message)
                || "Loading library".equals(message);
    }

    void setHeaderExpanded(boolean expanded) {
    }

    void setSearchBarVisible(boolean visible) {
    }

    boolean hasHeader() {
        return false;
    }

}
