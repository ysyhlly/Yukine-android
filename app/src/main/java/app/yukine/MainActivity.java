package app.yukine;

import android.content.Intent;
import android.os.Bundle;
import dagger.hilt.android.AndroidEntryPoint;

/**
 * Android manifest entry point. Runtime assembly is delegated to
 * MainActivityBase while the coordinator migration continues.
 */
@AndroidEntryPoint
public final class MainActivity extends MainActivityBase {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
    }
}
