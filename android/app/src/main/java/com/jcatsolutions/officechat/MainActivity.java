package com.jcatsolutions.officechat;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.WindowManager;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.getcapacitor.BridgeActivity;

/**
 * The app: shows the Jcat Connect website (server.url in capacitor.config.json), plus:
 * - opens it at a given address when started from a call or message notification;
 * - tells the website when it goes to the background, so the server sends notifications.
 */
public class MainActivity extends BridgeActivity {

    static final String EXTRA_URL = "officechat_url";
    /** Answered on the lock screen: show the call over it (no PIN/pattern) until the call ends. */
    static final String EXTRA_OVER_LOCK = "officechat_over_lock";
    /** A call answered from the lock screen must start within this time, or the lock screen returns. */
    private static final long OVER_LOCK_START_MS = 30_000;

    private static java.lang.ref.WeakReference<MainActivity> current = new java.lang.ref.WeakReference<>(null);
    private boolean overLock = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(OfficeChatPlugin.class);
        super.onCreate(savedInstanceState);
        current = new java.lang.ref.WeakReference<>(this);
        CallNotifier.createChannels(this);
        NativeActions.refreshPushToken(this);
        boolean openedFromNotification = getIntent() != null && getIntent().getStringExtra(EXTRA_URL) != null;
        if (bridge != null) {
            // Ringtone / call sounds start without a tap first.
            bridge.getWebView().getSettings().setMediaPlaybackRequiresUserGesture(false);
            // window.OfficeChatAndroid for the website. It's added to pages loaded from now on, so load
            // the site again (unless a notification is about to load its own page anyway).
            bridge.getWebView().addJavascriptInterface(new WebBridge(this), "OfficeChatAndroid");
            if (!openedFromNotification) bridge.getWebView().post(() -> bridge.getWebView().loadUrl(Config.WEB_URL + "/"));
        }
        askNotificationPermission();
        openFromIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        openFromIntent(intent);
    }

    @Override
    public void onResume() {
        super.onResume();
        AppState.foreground = true;
        tellWebsiteVisible(true);
    }

    @Override
    public void onPause() {
        AppState.foreground = false;
        tellWebsiteVisible(false);
        super.onPause();
    }

    private void tellWebsiteVisible(boolean visible) {
        if (bridge == null) return;
        bridge.eval("window.__officeChatNativeVisible && window.__officeChatNativeVisible(" + visible + ")", value -> {});
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    /** Show the app over the lock screen (answered call), or stop doing so (the lock screen comes back). */
    private void setOverLock(boolean on) {
        overLock = on;
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(on);
            setTurnScreenOn(on);
        } else {
            int flags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON;
            if (on) getWindow().addFlags(flags); else getWindow().clearFlags(flags);
        }
    }

    /** The call answered over the lock screen has ended: back behind the lock screen. */
    static void callEnded() {
        MainActivity a = current.get();
        if (a != null) a.runOnUiThread(() -> { if (a.overLock) a.setOverLock(false); });
    }

    /** Started from a notification: load the website at that address (e.g. /?answer=12&conv=3). */
    private void openFromIntent(Intent intent) {
        if (intent == null || bridge == null) return;
        if (intent.getBooleanExtra(EXTRA_OVER_LOCK, false)) {
            intent.removeExtra(EXTRA_OVER_LOCK);
            setOverLock(true);
            // Never leave the app open over the lock screen if the call didn't start.
            handler.postDelayed(() -> { if (overLock && !AppState.inCall) setOverLock(false); }, OVER_LOCK_START_MS);
        }
        String url = intent.getStringExtra(EXTRA_URL);
        long conv = intent.getLongExtra(CallNotifier.EXTRA_CONV, -1);
        if (conv >= 0 && url != null && url.contains("answer=")) CallNotifier.cancelIncoming(this, conv);
        intent.removeExtra(EXTRA_URL);
        if (url == null || !url.startsWith(Config.WEB_URL)) return;
        // Reloading would drop a call in progress: then just bring the app to the front.
        if (AppState.inCall) return;
        bridge.getWebView().post(() -> bridge.getWebView().loadUrl(url));
    }

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
        }
        askOnce("battery", this::askIgnoreBatteryOptimization);
        askOnce("fullscreen", this::askFullScreenCalls);
    }

    /**
     * Calls when the app is closed: many phones (Oppo, Realme, Vivo, Xiaomi, ...) block push to apps
     * that are "battery optimised", so a closed app never rings. Asks to be exempted, like calling apps do.
     */
    private void askIgnoreBatteryOptimization() {
        if (Build.VERSION.SDK_INT < 23) return;
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm == null || pm.isIgnoringBatteryOptimizations(getPackageName())) return;
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName())));
        } catch (Exception ignored) {}
    }

    /** Android 14+: the full-screen call screen needs this permission (else only a pop-up shows). */
    private void askFullScreenCalls() {
        if (Build.VERSION.SDK_INT < 34) return;
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null || nm.canUseFullScreenIntent()) return;
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + getPackageName())));
        } catch (Exception ignored) {}
    }

    /** Each settings screen is shown once (not on every launch), a moment after the app opens. */
    private void askOnce(String key, Runnable ask) {
        SharedPreferences prefs = getSharedPreferences("officechat", MODE_PRIVATE);
        if (prefs.getBoolean("asked_" + key, false)) return;
        prefs.edit().putBoolean("asked_" + key, true).apply();
        long delay = "battery".equals(key) ? 3_000 : 6_000;
        new Handler(Looper.getMainLooper()).postDelayed(ask, delay);
    }
}
