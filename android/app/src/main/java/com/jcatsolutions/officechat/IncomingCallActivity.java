package com.jcatsolutions.officechat;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import java.util.HashMap;
import java.util.Map;

/** Full-screen "X is calling" screen, shown even on the lock screen (like WhatsApp). */
public class IncomingCallActivity extends Activity {

    private static final String[] KEYS = {"conversationId", "callId", "callType", "fromUserName", "toUserId", "declineToken", "isGroup", "groupName", "title"};

    private Map<String, String> data;
    private long conv;
    /** Answer was tapped: ignore our own "stop ringing" broadcast. */
    private boolean answering = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final BroadcastReceiver ended = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!answering && intent.getLongExtra(CallNotifier.EXTRA_CONV, -2) == conv) finish();
        }
    };

    static Intent intent(Context c, Map<String, String> d) {
        Intent i = new Intent(c, IncomingCallActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION)
                .setData(Uri.parse("officechat://call/" + d.get("conversationId")));
        for (String k : KEYS) i.putExtra(k, d.get(k));
        return i;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        data = new HashMap<>();
        for (String k : KEYS) data.put(k, getIntent().getStringExtra(k));
        conv = CallNotifier.parseLong(data.get("conversationId"));
        setContentView(buildView());

        ContextCompat.registerReceiver(this, ended, new IntentFilter(CallNotifier.ACTION_CALL_ENDED), ContextCompat.RECEIVER_NOT_EXPORTED);
        handler.postDelayed(this::finish, 45_000); // the call stops ringing by then anyway
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        try { unregisterReceiver(ended); } catch (Exception ignored) {}
        super.onDestroy();
    }

    /**
     * Like WhatsApp: the call opens straight away, over the lock screen (no PIN/pattern first).
     * The app hides chats while locked, and the lock screen comes back when the call ends.
     */
    private void answer() {
        answering = true;
        // Stop ringing without the "call ended" broadcast: that would close this screen mid-answer
        // (which is what made answering from the lock screen do nothing).
        NotificationManagerCompat.from(this).cancel(CallNotifier.callNotificationId(conv));
        String url = Config.WEB_URL + "/?answer=" + CallNotifier.nz(data.get("callId")) + "&conv=" + conv;
        Intent open = CallNotifier.openApp(this, url, conv);
        KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (km != null && km.isKeyguardLocked()) open.putExtra(MainActivity.EXTRA_OVER_LOCK, true);
        startActivity(open);
        finish();
    }

    private void decline() {
        sendBroadcast(CallActionReceiver.declineIntent(this, data));
        finish();
    }

    private LinearLayout buildView() {
        int dp = (int) getResources().getDisplayMetrics().density;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(Color.parseColor("#0f172a"));
        root.setPadding(24 * dp, 96 * dp, 24 * dp, 64 * dp);

        boolean video = "VIDEO".equals(data.get("callType"));
        TextView kind = text(video ? "Incoming video call" : "Incoming voice call", 16, "#94a3b8", false);
        String caller = data.get("fromUserName") != null ? data.get("fromUserName") : "JCAT Connect";
        TextView name = text(caller, 32, "#ffffff", true);
        name.setPadding(0, 16 * dp, 0, 0);
        root.addView(kind);
        root.addView(name);
        if ("true".equals(data.get("isGroup")) && data.get("groupName") != null) {
            root.addView(text("in " + data.get("groupName"), 16, "#cbd5e1", false));
        }

        LinearLayout.LayoutParams grow = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(new LinearLayout(this), grow); // pushes the buttons to the bottom

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);
        buttons.addView(roundButton("Decline", "#dc2626", v -> decline(), dp));
        LinearLayout gap = new LinearLayout(this);
        buttons.addView(gap, new LinearLayout.LayoutParams(64 * dp, 1));
        buttons.addView(roundButton("Answer", "#16a34a", v -> answer(), dp));
        root.addView(buttons);
        return root;
    }

    private TextView text(String s, int sp, String color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(Color.parseColor(color));
        t.setGravity(Gravity.CENTER);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private Button roundButton(String label, String color, android.view.View.OnClickListener onClick, int dp) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setTextSize(16);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.parseColor(color));
        b.setBackground(bg);
        b.setLayoutParams(new LinearLayout.LayoutParams(88 * dp, 88 * dp));
        b.setOnClickListener(onClick);
        return b;
    }
}
