package com.jcatsolutions.officechat;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

/**
 * Receives the server's Firebase messages, even when the app is closed or the phone is asleep:
 * "call" (ring), "call-cancel" (stop ringing / missed call), "message" (new chat message) and
 * "test" (the phone check in Settings).
 */
public class CallMessagingService extends FirebaseMessagingService {

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        Map<String, String> data = message.getData();
        String type = data.get("type");
        // Remembered for the phone check: proves a push reached this phone, and when.
        getSharedPreferences("officechat", MODE_PRIVATE).edit()
                .putLong("last_push_at", System.currentTimeMillis())
                .putString("last_push_type", type)
                .putBoolean("last_push_app_closed", !AppState.foreground)
                .apply();

        if ("call".equals(type)) {
            // The server sends each ring up to 3 times (a sleepy phone can miss one): ring once per call,
            // and never for a call that's already over.
            if (!firstTimeFor(data.get("callId"))) return;
            // App open on screen: the website is already ringing.
            if (!AppState.foreground) CallNotifier.showIncoming(this, data);
        } else if ("call-cancel".equals(type)) {
            firstTimeFor(data.get("callId")); // a repeat ring arriving late is ignored
            CallNotifier.cancelIncoming(this, CallNotifier.parseLong(data.get("conversationId")));
            if ("true".equals(data.get("missed")) && !AppState.foreground) CallNotifier.showMissed(this, data);
        } else if ("message".equals(type)) {
            if (!AppState.foreground) CallNotifier.showMessage(this, data);
        } else if ("test".equals(type)) {
            CallNotifier.showTest(this, data);
        }
    }

    /** True the first time a call id is seen (the last few are remembered, even if the app is closed). */
    private boolean firstTimeFor(String callId) {
        if (callId == null || callId.isEmpty() || "null".equals(callId)) return true;
        android.content.SharedPreferences p = getSharedPreferences("officechat", MODE_PRIVATE);
        String seen = p.getString("seen_calls", "");
        if (("," + seen + ",").contains("," + callId + ",")) return false;
        String[] ids = seen.isEmpty() ? new String[0] : seen.split(",");
        StringBuilder keep = new StringBuilder(callId);
        for (int i = 0; i < Math.min(ids.length, 19); i++) keep.append(',').append(ids[i]);
        p.edit().putString("seen_calls", keep.toString()).apply();
        return true;
    }

    @Override
    public void onNewToken(@NonNull String token) {
        // Kept for the website, which registers it with the server every time the app opens.
        NativeActions.savePushToken(this, token);
    }
}
