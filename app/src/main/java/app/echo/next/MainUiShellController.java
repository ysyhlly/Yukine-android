package app.echo.next;

import android.graphics.Insets;
import android.graphics.Color;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewConfiguration;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.activity.ComponentActivity;

import java.util.ArrayList;
import java.util.List;

import app.echo.next.model.Track;
import app.echo.next.ui.AppTabUiState;
import app.echo.next.ui.ContentRouteHostController;
import app.echo.next.ui.ContentRouteSelectAction;
import app.echo.next.ui.EchoTheme;
import app.echo.next.ui.EchoViewBackground;
import app.echo.next.ui.HeaderController;
import app.echo.next.ui.NowBarController;
import app.echo.next.ui.NowBarState;
import app.echo.next.ui.NowPlayingOverlayController;
import app.echo.next.ui.SearchAction;
import app.echo.next.ui.SearchBarController;
import app.echo.next.ui.SeekAction;
import app.echo.next.ui.TabBarController;
import app.echo.next.ui.TabSelectAction;

final class MainUiShellController {
    interface Listener {
        void onSearchChanged(String query);

        void onTabSelected(String tabKey, boolean userInitiated);

        void onRouteSelected(String route);

        boolean onHorizontalSwipe(boolean next);

        String selectedTab();

        void onPrevious();

        void onPlayPause();

        void onNext();

        void onFavorite();

        void onShuffle();

        void onBottomPlaybackMode();

        void onRepeat();

        void onSeek(long positionMs);

        void onOpenNowPlayingOverlay();

        void onCloseNowPlayingOverlay();

        void onOpenQueueFromNowPlayingOverlay();
    }

    private final ComponentActivity activity;
    private final Listener listener;
    private FrameLayout rootFrame;
    private LinearLayout rootLayout;
    private ScrollDirectionFrameLayout contentHost;
    private ContentRouteHostController contentRouteHostController;
    private ContentHostController contentHostController;
    private LinearLayout contentContainer;
    private HeaderController headerController;
    private SearchBarController searchBarController;
    private TabBarController tabBarController;
    private NowBarController nowBarController;
    private NowPlayingOverlayController nowPlayingOverlayController;
    private ArrayList<String> tabRoutes = new ArrayList<>();
    private long lastHorizontalTabSwitchAtMs;

    MainUiShellController(ComponentActivity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;
    }

    void applyThemeSurface() {
        Window window = activity.getWindow();
        int background = EchoTheme.backgroundArgb(activity);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Edge-to-edge: let the window draw behind the system bars and rely on
            // applySafeAreaInsets() to add the bar padding exactly once. Using
            // setDecorFitsSystemWindows(true) here would make the framework inset the
            // content as well, double-padding top and bottom (over-large gaps above the
            // header and below the now-playing bar).
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
        if (rootLayout != null) {
            EchoViewBackground.applyPageBackground(rootLayout);
        }
        if (rootFrame != null) {
            EchoViewBackground.applyPageBackground(rootFrame);
        }
        if (contentHostController != null) {
            contentHostController.applyBackground();
        }
    }

    void build(String initialRoute, String languageMode) {
        FrameLayout frame = new FrameLayout(activity) {
            private final int touchSlop = ViewConfiguration.get(activity).getScaledTouchSlop();
            private float downX;
            private float downY;
            private boolean moved;
            private boolean downInsideNowBar;

            @Override
            public boolean dispatchTouchEvent(MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getRawX();
                        downY = event.getRawY();
                        moved = false;
                        downInsideNowBar = isPointInsideNowBar(downX, downY);
                        if (!downInsideNowBar) {
                            collapseNowBarWaveform();
                        }
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (Math.abs(event.getRawX() - downX) > touchSlop
                                || Math.abs(event.getRawY() - downY) > touchSlop) {
                            moved = true;
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                        if (!moved
                                && !downInsideNowBar
                                && !isPointInsideNowBar(event.getRawX(), event.getRawY())) {
                            collapseNowBarWaveform();
                        }
                        break;
                    case MotionEvent.ACTION_CANCEL:
                        moved = false;
                        downInsideNowBar = false;
                        break;
                    default:
                        break;
                }
                return super.dispatchTouchEvent(event);
            }
        };
        rootFrame = frame;
        frame.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        EchoViewBackground.applyPageBackground(frame);

        LinearLayout root = new LinearLayout(activity);
        rootLayout = root;
        root.setOrientation(LinearLayout.VERTICAL);
        EchoViewBackground.applyPageBackground(root);
        root.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        applySafeAreaInsets(root);
        frame.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(16), dp(14), dp(16), dp(8));
        installTapCollapseListener(header);
        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        headerController = new HeaderController(activity, activity.getString(R.string.app_name));
        header.addView(headerController.getView(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        tabBarController = new TabBarController(activity, localizedTabs(languageMode), initialRoute, new TabSelectAction() {
            @Override
            public void select(String tabKey, boolean userInitiated) {
                listener.onTabSelected(tabKey, userInitiated);
            }
        });
        header.addView(tabBarController.getView(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        searchBarController = new SearchBarController(activity, AppLanguage.text(languageMode, "search.music"), new SearchAction() {
            @Override
            public void onSearchChanged(String query) {
                listener.onSearchChanged(query);
            }
        });
        header.addView(searchBarController.getView(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        contentHost = new ScrollDirectionFrameLayout(activity);
        contentHost.setOnScrollDirectionListener(new ScrollDirectionFrameLayout.OnScrollDirectionListener() {
            @Override
            public void onScrollDirection(boolean scrollingDown) {
                if (searchBarController != null) {
                    searchBarController.setCollapsed(scrollingDown);
                }
            }
        });
        contentHost.setOnHorizontalSwipeListener(new ScrollDirectionFrameLayout.OnHorizontalSwipeListener() {
            @Override
            public void onHorizontalSwipe(boolean swipingLeft) {
                if (!listener.onHorizontalSwipe(swipingLeft)) {
                    selectAdjacentTab(swipingLeft);
                }
            }
        });
        contentHost.setOnTapListener(new ScrollDirectionFrameLayout.OnTapListener() {
            @Override
            public void onTap() {
                collapseNowBarWaveform();
            }
        });
        ArrayList<String> contentRoutes = tabRouteKeys();
        contentRouteHostController = new ContentRouteHostController(activity, contentRoutes, initialRoute, new ContentRouteSelectAction() {
            @Override
            public void select(String route) {
                listener.onRouteSelected(route);
            }
        });
        contentHostController = new ContentHostController(activity, contentHost, contentRouteHostController.getView());
        contentHostController.applyBackground();
        root.addView(contentHost, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));
        useScrollingContentContainer();

        nowBarController = new NowBarController(
                activity,
                new Runnable() {
                    @Override
                    public void run() {
                        listener.onPrevious();
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        listener.onPlayPause();
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        listener.onNext();
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        listener.onFavorite();
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        listener.onBottomPlaybackMode();
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        listener.onRepeat();
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        listener.onOpenNowPlayingOverlay();
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        listener.onOpenQueueFromNowPlayingOverlay();
                    }
                },
                new SeekAction() {
                    @Override
                    public void seekTo(long positionMs) {
                        listener.onSeek(positionMs);
                    }
                }
        );
        root.addView(nowBarController.getView(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        // Resident full-screen player: build the overlay ComposeView once and keep it in the frame
        // (starts hidden via View.GONE inside the controller). Opening it later is a cheap
        // visibility toggle + animation instead of a fresh Compose inflation.
        nowPlayingOverlayController = new NowPlayingOverlayController(
                activity,
                NowBarController.emptyState(),
                new Runnable() {
                    @Override
                    public void run() {
                        hideNowPlayingOverlay();
                        listener.onCloseNowPlayingOverlay();
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        listener.onPrevious();
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        listener.onPlayPause();
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        listener.onNext();
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        listener.onFavorite();
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        listener.onShuffle();
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        listener.onRepeat();
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        hideNowPlayingOverlay();
                        listener.onOpenQueueFromNowPlayingOverlay();
                    }
                },
                new SeekAction() {
                    @Override
                    public void seekTo(long positionMs) {
                        listener.onSeek(positionMs);
                    }
                }
        );
        frame.addView(nowPlayingOverlayController.getView(), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        activity.setContentView(frame);
    }

    private void applySafeAreaInsets(View root) {
        final int originalLeft = root.getPaddingLeft();
        final int originalTop = root.getPaddingTop();
        final int originalRight = root.getPaddingRight();
        final int originalBottom = root.getPaddingBottom();
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int top = 0;
            int bottom = 0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Insets systemBars = insets.getInsets(WindowInsets.Type.systemBars());
                top = systemBars.top;
                bottom = systemBars.bottom;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }
            view.setPadding(
                    originalLeft,
                    originalTop + top,
                    originalRight,
                    originalBottom + bottom
            );
            return insets;
        });
        root.requestApplyInsets();
    }

    boolean hasContentHost() {
        return contentHost != null;
    }

    boolean navigateContentRoute(String route) {
        return contentRouteHostController != null && contentRouteHostController.navigate(route);
    }

    void updateSelectedContentRoute(String route) {
        if (contentRouteHostController != null) {
            contentRouteHostController.updateSelected(route);
        }
    }

    void useScrollingContentContainer() {
        if (contentHostController != null) {
            contentContainer = contentHostController.useScrollingContainer();
        }
    }

    void useFixedContentContainer(List<View> existingChildren) {
        if (contentHostController != null) {
            contentContainer = contentHostController.useFixedContainer(existingChildren);
        }
    }

    void prepareHorizontalContentTransition(boolean next) {
        if (contentHostController != null) {
            contentHostController.prepareHorizontalTransition(next);
        }
    }

    ScrollView getScrollView() {
        return contentHostController == null ? null : contentHostController.getScrollView();
    }

    void addVirtualContent(View view) {
        if (contentContainer == null) {
            return;
        }
        if (contentHostController != null && contentHostController.getScrollView() != null) {
            ArrayList<View> existingChildren = new ArrayList<>();
            while (contentContainer.getChildCount() > 0) {
                View child = contentContainer.getChildAt(0);
                contentContainer.removeViewAt(0);
                existingChildren.add(child);
            }
            useFixedContentContainer(existingChildren);
        }
        contentContainer.addView(view, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));
        if (contentHostController != null) {
            contentHostController.animateContentIfPending(view);
        }
    }

    void updateTabBar(String selectedTab) {
        if (tabBarController != null) {
            tabBarController.updateSelected(selectedTab);
        }
    }

    void updateLanguage(String languageMode) {
        if (searchBarController != null) {
            searchBarController.updatePlaceholder(AppLanguage.text(languageMode, "search.music"));
        }
        if (tabBarController != null) {
            tabBarController.updateTabs(localizedTabs(languageMode));
        }
    }

    boolean hasTabBar() {
        return tabBarController != null;
    }

    private void selectAdjacentTab(boolean next) {
        long now = android.os.SystemClock.uptimeMillis();
        if (now - lastHorizontalTabSwitchAtMs < HORIZONTAL_TAB_SWITCH_COOLDOWN_MS) {
            return;
        }
        if (tabRoutes == null || tabRoutes.isEmpty()) {
            return;
        }
        String selected = listener.selectedTab();
        String nextTab = MainTabSwipePolicy.adjacentTab(tabRoutes, selected, next);
        if (nextTab == null) {
            return;
        }
        lastHorizontalTabSwitchAtMs = now;
        prepareHorizontalContentTransition(next);
        listener.onTabSelected(nextTab, false);
    }

    void updateStatus(String status) {
        if (headerController != null) {
            headerController.updateStatus(status);
        }
    }

    void setHeaderExpanded(boolean expanded) {
        if (headerController != null) {
            headerController.setExpanded(expanded);
        }
    }

    void setSearchBarVisible(boolean visible) {
        if (searchBarController != null) {
            searchBarController.setVisible(visible);
        }
    }

    boolean hasHeader() {
        return headerController != null;
    }

    void updateNowBar(NowBarState state) {
        if (nowBarController != null) {
            nowBarController.updateState(state);
        }
        if (nowPlayingOverlayController != null) {
            nowPlayingOverlayController.updateState(state);
        }
    }

    void collapseNowBarWaveform() {
        if (nowBarController != null) {
            nowBarController.collapseWaveform();
        }
    }

    private boolean isPointInsideNowBar(float rawX, float rawY) {
        if (nowBarController == null) {
            return false;
        }
        View nowBar = nowBarController.getView();
        if (nowBar == null || nowBar.getVisibility() != View.VISIBLE || !nowBar.isShown()) {
            return false;
        }
        int[] location = new int[2];
        nowBar.getLocationOnScreen(location);
        int left = location[0];
        int top = location[1];
        int right = left + nowBar.getWidth();
        int bottom = top + nowBar.getHeight();
        return rawX >= left && rawX <= right && rawY >= top && rawY <= bottom;
    }

    private void installTapCollapseListener(View view) {
        view.setOnTouchListener(new View.OnTouchListener() {
            private final int touchSlop = android.view.ViewConfiguration.get(activity).getScaledTouchSlop();
            private float downX;
            private float downY;
            private boolean moved;

            @Override
            public boolean onTouch(View target, android.view.MotionEvent event) {
                switch (event.getActionMasked()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        downX = event.getRawX();
                        downY = event.getRawY();
                        moved = false;
                        break;
                    case android.view.MotionEvent.ACTION_MOVE:
                        if (Math.abs(event.getRawX() - downX) > touchSlop
                                || Math.abs(event.getRawY() - downY) > touchSlop) {
                            moved = true;
                        }
                        break;
                    case android.view.MotionEvent.ACTION_UP:
                        if (!moved) {
                            collapseNowBarWaveform();
                        }
                        break;
                    default:
                        break;
                }
                return false;
            }
        });
    }

    void showNowPlayingOverlay(NowBarState state) {
        if (rootFrame == null || state == null || !state.getCanExpand() || nowPlayingOverlayController == null) {
            return;
        }
        Window window = activity.getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        nowPlayingOverlayController.updateState(state);
        nowPlayingOverlayController.show();
    }

    void hideNowPlayingOverlay() {
        if (nowPlayingOverlayController != null) {
            nowPlayingOverlayController.hide();
        }
        applyThemeSurface();
    }

    boolean hideNowPlayingOverlayIfVisible() {
        if (nowPlayingOverlayController == null || !nowPlayingOverlayController.isShowing()) {
            return false;
        }
        hideNowPlayingOverlay();
        listener.onCloseNowPlayingOverlay();
        return true;
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static final long HORIZONTAL_TAB_SWITCH_COOLDOWN_MS = 360L;

    private ArrayList<AppTabUiState> localizedTabs(String languageMode) {
        tabRoutes = tabRouteKeys();
        ArrayList<AppTabUiState> tabs = new ArrayList<>();
        for (String route : tabRoutes) {
            tabs.add(new AppTabUiState(AppLanguage.tabLabel(languageMode, route), route));
        }
        return tabs;
    }

    private ArrayList<String> tabRouteKeys() {
        ArrayList<String> routes = new ArrayList<>();
        routes.add(MainRoutes.TAB_HOME);
        routes.add(MainRoutes.TAB_LIBRARY);
        routes.add(MainRoutes.TAB_COLLECTIONS);
        routes.add(MainRoutes.TAB_QUEUE);
        routes.add(MainRoutes.TAB_NETWORK);
        routes.add(MainRoutes.TAB_SETTINGS);
        return routes;
    }
}
