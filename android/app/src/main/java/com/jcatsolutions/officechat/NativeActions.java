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

import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONObject;

/** What the website can ask the phone to do (used by WebBridge and OfficeChatPlugin). */
final class NativeActions {

    static final String PREFS = "officechat";
    private static final String KEY_TOKEN = "fcm_token";

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

    private NativeActions() {}

    // ── Firebase token ──────────────────────────────────────────────────

    /** Fetches the phone's Firebase token and keeps it, so the website can read it any time. */
    static void refreshPushToken(Context c) {
        try {
            FirebaseMessaging.getInstance().getToken().addOnSuccessListener(t -> savePushToken(c, t));
        } catch (Exception ignored) {
            // google-services.json missing from the build: Firebase isn't set up.
        }
    }

    static void savePushToken(Context c, String token) {
        if (token != null && !token.isEmpty()) prefs(c).edit().putString(KEY_TOKEN, token).apply();
    }

    static String pushToken(Context c) {
        return prefs(c).getString(KEY_TOKEN, "");
    }

    // ── Calls ───────────────────────────────────────────────────────────

    /** Keeps a call (and the microphone) going while the app is in the background. */
    static void setInCall(Context c, boolean inCall) {
        AppState.inCall = inCall;
        Intent service = new Intent(c, CallForegroundService.class);
        try {
            if (inCall) ContextCompat.startForegroundService(c, service);
            else c.stopService(service);
        } catch (Exception ignored) {
            // Not allowed right now (e.g. microphone permission not given yet): the call still works in front.
        }
    }

    // ── Phone check ─────────────────────────────────────────────────────

    static JSONObject status(Context c) {
        JSONObject r = new JSONObject();
        try {
            SharedPreferences p = prefs(c);
            r.put("notificationsEnabled", NotificationManagerCompat.from(c).areNotificationsEnabled());
            PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
            r.put("batteryUnrestricted", Build.VERSION.SDK_INT < 23 || (pm != null && pm.isIgnoringBatteryOptimizations(c.getPackageName())));
            r.put("hasPushToken", !pushToken(c).isEmpty());
            r.put("lastPushAt", p.getLong("last_push_at", 0));
            r.put("lastPushType", p.getString("last_push_type", null));
            r.put("lastPushAppClosed", p.getBoolean("last_push_app_closed", false));
            r.put("manufacturer", Build.MANUFACTURER);
            r.put("model", Build.MODEL);
            r.put("android", Build.VERSION.RELEASE);
            r.put("appVersion", c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName);
        } catch (Exception ignored) {}
        return r;
    }

    /** Opens the phone maker's auto-start screen, or the closest settings screen there is. */
    static String openAutoStartSettings(Context c) {
        for (String[] screen : AUTO_START_SCREENS) {
            if (tryStart(c, new Intent().setComponent(new ComponentName(screen[0], screen[1])))) return "autostart";
        }
        for (String pkg : PHONE_MANAGER_APPS) {
            Intent launch = c.getPackageManager().getLaunchIntentForPackage(pkg);
            if (launch != null && tryStart(c, launch)) return "phone-manager";
        }
        tryStart(c, appInfo(c));
        return "app-info";
    }

    static void openBatterySettings(Context c) {
        boolean ok = Build.VERSION.SDK_INT >= 23 && tryStart(c,
                new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + c.getPackageName())));
        if (!ok) tryStart(c, appInfo(c));
    }

    static void openAppInfo(Context c) {
        tryStart(c, appInfo(c));
    }

    private static Intent appInfo(Context c) {
        return new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + c.getPackageName()));
    }

    private static boolean tryStart(Context c, Intent intent) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (intent.resolveActivity(c.getPackageManager()) == null) return false;
            c.startActivity(intent);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
