package com.jcatsolutions.officechat;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

import androidx.core.app.NotificationManagerCompat;
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

    /**
     * Phone makers' own "auto-start" screens (no standard Android one). An app the phone isn't allowed to
     * auto-start can't be woken by a push while it's closed, so calls don't ring.
     */
    private static final String[][] AUTO_START_SCREENS = {
            // Infinix / Tecno / itel (Transsion)
            {"com.transsion.phonemaster", "com.cyin.himgr.autostart.AutoStartActivity"},
            {"com.transsion.phonemanager", "com.itel.autobootmanager.activity.AutoBootMgrActivity"},
            // Xiaomi / Redmi / POCO
            {"com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"},
            // Oppo / Realme
            {"com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"},
            {"com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"},
            // Vivo
            {"com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"},
            {"com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"},
            // Huawei / Honor
            {"com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"},
    };
    /** If no auto-start screen exists, open the maker's phone-manager app itself. */
    private static final String[] PHONE_MANAGER_APPS = {"com.transsion.phonemaster", "com.miui.securitycenter", "com.coloros.safecenter"};

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

    /** For the phone check in Settings: what could stop calls from ringing, and when a push last arrived. */
    @PluginMethod
    public void getStatus(PluginCall call) {
        Context ctx = getContext();
        SharedPreferences prefs = ctx.getSharedPreferences("officechat", Context.MODE_PRIVATE);
        JSObject r = new JSObject();
        r.put("notificationsEnabled", NotificationManagerCompat.from(ctx).areNotificationsEnabled());
        PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        r.put("batteryUnrestricted", Build.VERSION.SDK_INT < 23 || (pm != null && pm.isIgnoringBatteryOptimizations(ctx.getPackageName())));
        r.put("lastPushAt", prefs.getLong("last_push_at", 0));
        r.put("lastPushType", prefs.getString("last_push_type", null));
        r.put("lastPushAppClosed", prefs.getBoolean("last_push_app_closed", false));
        r.put("manufacturer", Build.MANUFACTURER);
        r.put("model", Build.MODEL);
        r.put("android", Build.VERSION.RELEASE);
        call.resolve(r);
    }

    /** Opens the phone maker's auto-start screen (or the closest settings screen we can find). */
    @PluginMethod
    public void openAutoStartSettings(PluginCall call) {
        Context ctx = getContext();
        JSObject r = new JSObject();
        for (String[] screen : AUTO_START_SCREENS) {
            if (tryStart(ctx, new Intent().setComponent(new ComponentName(screen[0], screen[1])))) {
                r.put("opened", "autostart");
                call.resolve(r);
                return;
            }
        }
        for (String pkg : PHONE_MANAGER_APPS) {
            Intent launch = ctx.getPackageManager().getLaunchIntentForPackage(pkg);
            if (launch != null && tryStart(ctx, launch)) {
                r.put("opened", "phone-manager");
                call.resolve(r);
                return;
            }
        }
        tryStart(ctx, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.getPackageName())));
        r.put("opened", "app-info");
        call.resolve(r);
    }

    /** Opens the "don't optimise battery" prompt for this app. */
    @PluginMethod
    public void openBatterySettings(PluginCall call) {
        Context ctx = getContext();
        boolean ok = Build.VERSION.SDK_INT >= 23 && tryStart(ctx,
                new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + ctx.getPackageName())));
        if (!ok) tryStart(ctx, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.getPackageName())));
        call.resolve();
    }

    private static boolean tryStart(Context ctx, Intent intent) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (intent.resolveActivity(ctx.getPackageManager()) == null) return false;
            ctx.startActivity(intent);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
