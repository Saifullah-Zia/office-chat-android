package com.jcatsolutions.officechat;

import android.content.Context;
import android.webkit.JavascriptInterface;

/**
 * Direct link from the website to the phone: window.OfficeChatAndroid in the page.
 * Added straight to the WebView, so it works however the page was loaded (Capacitor's own bridge
 * isn't there when the site's service worker serves the page). See lib/native.ts in the frontend.
 */
final class WebBridge {

    private final Context context;

    WebBridge(Context context) {
        this.context = context.getApplicationContext();
    }

    /** The phone's Firebase token ("" if not ready yet: ask again in a moment). */
    @JavascriptInterface
    public String getPushToken() {
        String token = NativeActions.pushToken(context);
        if (token.isEmpty()) NativeActions.refreshPushToken(context);
        return token;
    }

    @JavascriptInterface
    public void setInCall(boolean inCall) {
        NativeActions.setInCall(context, inCall);
    }

    /** JSON text: see NativeActions.status. */
    @JavascriptInterface
    public String getStatus() {
        return NativeActions.status(context).toString();
    }

    @JavascriptInterface
    public String openAutoStartSettings() {
        return NativeActions.openAutoStartSettings(context);
    }

    @JavascriptInterface
    public void openBatterySettings() {
        NativeActions.openBatterySettings(context);
    }

    @JavascriptInterface
    public void openAppInfo() {
        NativeActions.openAppInfo(context);
    }
}
