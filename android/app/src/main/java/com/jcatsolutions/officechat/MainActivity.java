package com.jcatsolutions.officechat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.getcapacitor.BridgeActivity;

/**
 * The app: shows the Office Chat website (server.url in capacitor.config.json), plus:
 * - opens it at a given address when started from a call or message notification;
 * - tells the website when it goes to the background, so the server sends notifications.
 */
public class MainActivity extends BridgeActivity {

    static final String EXTRA_URL = "officechat_url";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(OfficeChatPlugin.class);
        super.onCreate(savedInstanceState);
        CallNotifier.createChannels(this);
        // Ringtone / call sounds start without a tap first.
        if (bridge != null) bridge.getWebView().getSettings().setMediaPlaybackRequiresUserGesture(false);
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

    /** Started from a notification: load the website at that address (e.g. /?answer=12&conv=3). */
    private void openFromIntent(Intent intent) {
        if (intent == null || bridge == null) return;
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
    }
}
