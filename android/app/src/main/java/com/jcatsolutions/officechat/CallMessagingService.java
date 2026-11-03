package com.jcatsolutions.officechat;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

/**
 * Receives the server's Firebase messages, even when the app is closed or the phone is asleep:
 * "call" (ring), "call-cancel" (stop ringing / missed call) and "message" (new chat message).
 */
public class CallMessagingService extends FirebaseMessagingService {

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        Map<String, String> data = message.getData();
        String type = data.get("type");
        if ("call".equals(type)) {
            // App open on screen: the website is already ringing.
            if (!AppState.foreground) CallNotifier.showIncoming(this, data);
        } else if ("call-cancel".equals(type)) {
            CallNotifier.cancelIncoming(this, CallNotifier.parseLong(data.get("conversationId")));
            if ("true".equals(data.get("missed")) && !AppState.foreground) CallNotifier.showMissed(this, data);
        } else if ("message".equals(type)) {
            if (!AppState.foreground) CallNotifier.showMessage(this, data);
        }
    }

    @Override
    public void onNewToken(@NonNull String token) {
        // The website registers the current token with the server every time the app opens.
    }
}
