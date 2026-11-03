package com.jcatsolutions.officechat;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONException;

/**
 * Same actions through Capacitor's bridge, for pages where it's present. The website prefers
 * window.OfficeChatAndroid (WebBridge), which is always there.
 */
@CapacitorPlugin(name = "OfficeChat")
public class OfficeChatPlugin extends Plugin {

    @PluginMethod
    public void getPushToken(PluginCall call) {
        JSObject r = new JSObject();
        r.put("token", NativeActions.pushToken(getContext()));
        call.resolve(r);
    }

    @PluginMethod
    public void setInCall(PluginCall call) {
        NativeActions.setInCall(getContext(), Boolean.TRUE.equals(call.getBoolean("inCall", false)));
        call.resolve();
    }

    @PluginMethod
    public void getStatus(PluginCall call) {
        try {
            call.resolve(JSObject.fromJSONObject(NativeActions.status(getContext())));
        } catch (JSONException e) {
            call.reject("Couldn't read the phone status", e);
        }
    }

    @PluginMethod
    public void openAutoStartSettings(PluginCall call) {
        JSObject r = new JSObject();
        r.put("opened", NativeActions.openAutoStartSettings(getContext()));
        call.resolve(r);
    }

    @PluginMethod
    public void openBatterySettings(PluginCall call) {
        NativeActions.openBatterySettings(getContext());
        call.resolve();
    }
}
