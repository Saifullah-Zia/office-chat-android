package com.jcatsolutions.officechat;

import android.content.Context;
import android.content.Intent;

import androidx.core.content.ContextCompat;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.google.firebase.messaging.FirebaseMessaging;

/** What the website can ask the phone for (see lib/native.ts in the frontend). */
@CapacitorPlugin(name = "OfficeChat")
public class OfficeChatPlugin extends Plugin {

    /** The phone's Firebase token; the website sends it to the server after sign-in. */
    @PluginMethod
    public void getPushToken(PluginCall call) {
        try {
            FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
                if (!task.isSuccessful() || task.getResult() == null) {
                    call.reject("No push token", task.getException());
                    return;
                }
                JSObject result = new JSObject();
                result.put("token", task.getResult());
                call.resolve(result);
            });
        } catch (Exception e) {
            // google-services.json missing: Firebase isn't set up in this build.
            call.reject("Firebase is not set up", e);
        }
    }

    /** { inCall: true/false }. Keeps the call (and microphone) going while the app is minimised. */
    @PluginMethod
    public void setInCall(PluginCall call) {
        boolean inCall = Boolean.TRUE.equals(call.getBoolean("inCall", false));
        AppState.inCall = inCall;
        Context ctx = getContext();
        Intent service = new Intent(ctx, CallForegroundService.class);
        try {
            if (inCall) ContextCompat.startForegroundService(ctx, service);
            else ctx.stopService(service);
        } catch (Exception ignored) {
            // Not allowed right now (e.g. microphone permission not given yet): the call still works in front.
        }
        call.resolve();
    }
}
