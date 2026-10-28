package pl.rentmor.protokol;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

/** Compact, accessible upper-right inbox entry on the RentMor home screen. */
public final class HomeInboxShortcut extends FrameLayout {
    private final TextView badge;

    public HomeInboxShortcut(Context context, Runnable openInbox) {
        super(context);
        setClickable(true);
        setFocusable(true);
        setOnClickListener(v -> openInbox.run());
        setBackground(roundBackground(0xffffe8a2, 13));

        ImageView envelope = new ImageView(context);
        envelope.setImageResource(android.R.drawable.ic_dialog_email);
        envelope.setColorFilter(0xff172235);
        envelope.setContentDescription(null);
        LayoutParams iconParams = new LayoutParams(dp(25), dp(25), Gravity.CENTER);
        addView(envelope, iconParams);

        badge = new TextView(context);
        badge.setTag("rentmor_unread_messages");
        badge.setTextSize(11);
        badge.setTypeface(null, 1);
        badge.setTextColor(Color.WHITE);
        badge.setGravity(Gravity.CENTER);
        badge.setMinWidth(dp(20));
        badge.setPadding(dp(4), 0, dp(4), 0);
        badge.setBackground(roundBackground(0xffbf312b, 14));
        LayoutParams badgeParams = new LayoutParams(-2, dp(20), Gravity.TOP | Gravity.RIGHT);
        badgeParams.topMargin = dp(1);
        badgeParams.rightMargin = dp(1);
        addView(badge, badgeParams);
        setUnread(0);
    }

    public void setUnread(int unread) {
        int count = Math.max(0, unread);
        badge.setText(count > 99 ? "99+" : String.valueOf(count));
        badge.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        setContentDescription(count == 0
            ? "Wiadomości klientów. Brak nieprzeczytanych wiadomości."
            : "Wiadomości klientów. Nieprzeczytane: " + count + ".");
    }

    private int dp(int size) {
        return Math.round(getResources().getDisplayMetrics().density * size);
    }

    private static GradientDrawable roundBackground(int color, int radius) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(radius * 2.5f);
        return bg;
    }
}
