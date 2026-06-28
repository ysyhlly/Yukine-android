package app.yukine.ui;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * A fully self-drawn dialog whose colors come entirely from the live {@link EchoTheme} palette.
 *
 * <p>Unlike the platform {@code AlertDialog}, this does not rely on the OS dialog theme or any
 * framework-internal resource ids, so it renders consistently across vendor ROMs and always
 * matches the in-app theme (light/dark + accent).</p>
 *
 * <p>The builder API mirrors the subset of {@code AlertDialog.Builder} the app uses, so call sites
 * read the same way.</p>
 */
public final class EchoDialog {

    private EchoDialog() {
    }

    public static Builder builder(Context context) {
        return new Builder(context);
    }

    public static final class Builder {
        private final Context context;
        private CharSequence title;
        private CharSequence message;
        private View customView;
        private CharSequence[] items;
        private DialogInterface.OnClickListener itemListener;
        private CharSequence positiveText;
        private DialogInterface.OnClickListener positiveListener;
        private CharSequence negativeText;
        private DialogInterface.OnClickListener negativeListener;
        private boolean cancelable = true;

        Builder(Context context) {
            this.context = context;
        }

        public Builder setTitle(CharSequence title) {
            this.title = title;
            return this;
        }

        public Builder setMessage(CharSequence message) {
            this.message = message;
            return this;
        }

        public Builder setView(View view) {
            this.customView = view;
            return this;
        }

        public Builder setItems(CharSequence[] items, DialogInterface.OnClickListener listener) {
            this.items = items;
            this.itemListener = listener;
            return this;
        }

        public Builder setPositiveButton(CharSequence text, DialogInterface.OnClickListener listener) {
            this.positiveText = text;
            this.positiveListener = listener;
            return this;
        }

        public Builder setNegativeButton(CharSequence text, DialogInterface.OnClickListener listener) {
            this.negativeText = text;
            this.negativeListener = listener;
            return this;
        }

        public Builder setCancelable(boolean cancelable) {
            this.cancelable = cancelable;
            return this;
        }

        public Dialog show() {
            int surface = EchoTheme.surfaceArgb(context);
            int surfaceVariant = EchoTheme.surfaceVariantArgb(context);
            int backgroundAlt = EchoTheme.backgroundAltArgb(context);
            int text = EchoTheme.textArgb(context);
            int muted = EchoTheme.mutedArgb(context);
            int accent = EchoTheme.accentArgb(context);
            int accentStrong = EchoTheme.accentStrongArgb(context);
            int onAccent = EchoTheme.onAccentArgb(context);
            int border = EchoTheme.borderArgb(context);

            final Dialog dialog = new Dialog(context);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            dialog.setCancelable(cancelable);

            LinearLayout root = new LinearLayout(context);
            root.setOrientation(LinearLayout.VERTICAL);
            int padH = dp(24);
            int padV = dp(20);
            root.setPadding(padH, padV, padH, dp(12));

            // Yukine signature: a soft vertical gradient (surface → surfaceVariant →
            // backgroundAlt) instead of a flat fill, with a subtle border.
            GradientDrawable bg = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{surface, surfaceVariant, backgroundAlt});
            bg.setCornerRadius(dp(24));
            bg.setStroke(Math.max(1, dp(1)), border);
            root.setBackground(bg);

            if (title != null && title.length() > 0) {
                TextView titleView = new TextView(context);
                titleView.setText(title);
                titleView.setTextColor(text);
                titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
                titleView.setTypeface(titleView.getTypeface(), android.graphics.Typeface.BOLD);
                LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                tlp.bottomMargin = dp(12);
                root.addView(titleView, tlp);
            }

            // Scrollable content region (message / custom view / item list).
            ScrollView scroll = new ScrollView(context);
            LinearLayout content = new LinearLayout(context);
            content.setOrientation(LinearLayout.VERTICAL);
            scroll.addView(content, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            boolean hasContent = false;

            if (message != null && message.length() > 0) {
                TextView messageView = new TextView(context);
                messageView.setText(message);
                messageView.setTextColor(muted);
                messageView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                messageView.setLineSpacing(dp(2), 1f);
                content.addView(messageView, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                hasContent = true;
            }

            if (customView != null) {
                ViewGroup parent = (ViewGroup) customView.getParent();
                if (parent != null) {
                    parent.removeView(customView);
                }
                content.addView(customView, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                hasContent = true;
            }

            if (items != null && items.length > 0) {
                for (int i = 0; i < items.length; i++) {
                    final int index = i;
                    TextView row = new TextView(context);
                    row.setText(items[i]);
                    row.setTextColor(text);
                    row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                    row.setPadding(dp(4), dp(14), dp(4), dp(14));
                    applySelectableBackground(row);
                    row.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            dialog.dismiss();
                            if (itemListener != null) {
                                itemListener.onClick(dialog, index);
                            }
                        }
                    });
                    content.addView(row, new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                }
                hasContent = true;
            }

            if (hasContent) {
                LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                root.addView(scroll, slp);
            }

            // Button row.
            if (positiveText != null || negativeText != null) {
                LinearLayout buttons = new LinearLayout(context);
                buttons.setOrientation(LinearLayout.HORIZONTAL);
                buttons.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
                LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                blp.topMargin = dp(8);
                root.addView(buttons, blp);

                if (negativeText != null) {
                    buttons.addView(makeTextButton(negativeText, muted, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            dialog.dismiss();
                            if (negativeListener != null) {
                                negativeListener.onClick(dialog, DialogInterface.BUTTON_NEGATIVE);
                            }
                        }
                    }));
                }

                if (positiveText != null) {
                    buttons.addView(makeAccentButton(positiveText, accent, accentStrong, onAccent,
                            new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    dialog.dismiss();
                                    if (positiveListener != null) {
                                        positiveListener.onClick(dialog, DialogInterface.BUTTON_POSITIVE);
                                    }
                                }
                            }));
                }
            }

            dialog.setContentView(root);

            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
                WindowManager.LayoutParams lp = window.getAttributes();
                int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
                lp.width = Math.min(dp(360), Math.round(screenWidth * 0.9f));
                lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
                window.setAttributes(lp);
            }

            dialog.show();
            return dialog;
        }

        /** Plain text button (used for the negative / dismiss action). */
        private TextView makeTextButton(CharSequence label, int color, View.OnClickListener onClick) {
            TextView button = baseButton(label, color);
            applySelectableBackground(button);
            button.setOnClickListener(onClick);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginStart(dp(8));
            button.setLayoutParams(lp);
            return button;
        }

        /** Accent button with a Yukine gradient fill pill (used for the positive action). */
        private TextView makeAccentButton(
                CharSequence label, int accent, int accentStrong, int onAccent, View.OnClickListener onClick) {
            TextView button = baseButton(label, onAccent);

            GradientDrawable pill = new GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{accent, accentStrong});
            pill.setCornerRadius(dp(22));

            // Ripple overlay on top of the gradient pill for press feedback.
            TypedValue tv = new TypedValue();
            if (context.getTheme().resolveAttribute(
                    android.R.attr.selectableItemBackground, tv, true) && tv.resourceId != 0) {
                android.graphics.drawable.Drawable mask =
                        context.getResources().getDrawable(tv.resourceId, context.getTheme());
                button.setBackground(new android.graphics.drawable.LayerDrawable(
                        new android.graphics.drawable.Drawable[]{pill, mask}));
            } else {
                button.setBackground(pill);
            }

            button.setOnClickListener(onClick);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginStart(dp(8));
            button.setLayoutParams(lp);
            return button;
        }

        private TextView baseButton(CharSequence label, int textColor) {
            TextView button = new TextView(context);
            button.setText(label);
            button.setAllCaps(true);
            button.setTextColor(textColor);
            button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            button.setTypeface(button.getTypeface(), android.graphics.Typeface.BOLD);
            button.setPadding(dp(20), dp(10), dp(20), dp(10));
            button.setGravity(Gravity.CENTER);
            return button;
        }

        private void applySelectableBackground(View view) {
            TypedValue tv = new TypedValue();
            if (context.getTheme().resolveAttribute(
                    android.R.attr.selectableItemBackground, tv, true) && tv.resourceId != 0) {
                view.setBackgroundResource(tv.resourceId);
            }
        }

        private int dp(int value) {
            return Math.round(value * context.getResources().getDisplayMetrics().density);
        }
    }
}
