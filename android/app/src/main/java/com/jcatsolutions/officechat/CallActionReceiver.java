package com.jcatsolutions.officechat;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** "Decline" on the ringing notification or screen: stop ringing and tell the server (the caller stops too). */
public class CallActionReceiver extends BroadcastReceiver {

    static final String ACTION_DECLINE = "com.jcatsolutions.officechat.DECLINE";

    static Intent declineIntent(Context c, Map<String, String> d) {
        return new Intent(c, CallActionReceiver.class)
                .setAction(ACTION_DECLINE)
                .putExtra(CallNotifier.EXTRA_CONV, CallNotifier.parseLong(d.get("conversationId")))
                .putExtra("callId", CallNotifier.nz(d.get("callId")))
                .putExtra("toUserId", CallNotifier.nz(d.get("toUserId")))
                .putExtra("declineToken", CallNotifier.nz(d.get("declineToken")));
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION_DECLINE.equals(intent.getAction())) return;
        CallNotifier.cancelIncoming(context, intent.getLongExtra(CallNotifier.EXTRA_CONV, -1));

        String callId = intent.getStringExtra("callId");
        String userId = intent.getStringExtra("toUserId");
        String token = intent.getStringExtra("declineToken");
        if (callId == null || callId.isEmpty() || userId == null || userId.isEmpty() || token == null || token.isEmpty()) return;

        PendingResult pending = goAsync();
        new Thread(() -> {
            HttpURLConnection con = null;
            try {
                // The push carried a token for just this call, so no sign-in is needed here.
                String body = "{\"userId\":" + Long.parseLong(userId) + ",\"token\":\"" + token + "\"}";
                con = (HttpURLConnection) new URL(Config.API_URL + "/api/calls/" + callId + "/push-decline").openConnection();
                con.setRequestMethod("POST");
                con.setConnectTimeout(10_000);
                con.setReadTimeout(10_000);
                con.setDoOutput(true);
                con.setRequestProperty("Content-Type", "application/json");
                try (OutputStream out = con.getOutputStream()) {
                    out.write(body.getBytes(StandardCharsets.UTF_8));
                }
                con.getResponseCode();
            } catch (Exception ignored) {
                // Offline: the caller's ringing simply times out.
            } finally {
                if (con != null) con.disconnect();
                pending.finish();
            }
        }).start();
    }
}
