package app.echo.next.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;

public final class EchoViewBackground {
    private EchoViewBackground() {
    }

    public static void applyPageBackground(View view) {
        if (view == null) {
            return;
        }
        view.setBackground(pageBackground(view.getContext()));
    }

    public static GradientDrawable pageBackground(Context context) {
        int backgroundAlt = EchoTheme.backgroundAltArgb(context);
        int background = EchoTheme.backgroundArgb(context);
        int surfaceTail = withAlpha(EchoTheme.surfaceVariantArgb(context), 0x57);
        return new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{backgroundAlt, background, surfaceTail}
        );
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(
                alpha,
                Color.red(color),
                Color.green(color),
                Color.blue(color)
        );
    }
}
