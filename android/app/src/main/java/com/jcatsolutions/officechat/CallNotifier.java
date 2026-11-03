package com.jcatsolutions.officechat;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.Person;
import androidx.core.content.ContextCompat;

import java.util.Map;

/** All of the app's notifications: the ringing call, missed calls, chat messages, "call in progress". */
final class CallNotifier {

    // Channel settings (sound, importance) can't change after creation: bump the suffix to change them.
    static final String CH_CALLS = "calls_v1";
    static final String CH_MISSED = "missed_v1";
    static final String CH_MESSAGES = "messages_v1";
    static final String CH_ONGOING = "ongoing_v1";

    static final String EXTRA_CONV = "officechat_conv";
    static final String ACTION_CALL_ENDED = "com.jcatsolutions.officechat.CALL_ENDED";
    static final int ONGOING_ID = 42;
    private static final long[] VIBRATE = {0, 1000, 800, 1000, 800};
    private static final int FLAGS = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;

    private CallNotifier() {}

    static void createChannels(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;

        NotificationChannel calls = new NotificationChannel(CH_CALLS, "Incoming calls", NotificationManager.IMPORTANCE_HIGH);
        calls.setDescription("Rings when someone calls you");
        calls.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE), new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build());
        calls.enableVibration(true);
        calls.setVibrationPattern(VIBRATE);
        calls.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(calls);

        NotificationChannel missed = new NotificationChannel(CH_MISSED, "Missed calls", NotificationManager.IMPORTANCE_DEFAULT);
        nm.createNotificationChannel(missed);

        NotificationChannel messages = new NotificationChannel(CH_MESSAGES, "Messages", NotificationManager.IMPORTANCE_HIGH);
        messages.setDescription("New chat messages");
        nm.createNotificationChannel(messages);

        NotificationChannel ongoing = new NotificationChannel(CH_ONGOING, "Call in progress", NotificationManager.IMPORTANCE_LOW);
        nm.createNotificationChannel(ongoing);
    }

    /** Ringing: full-screen on the lock screen, heads-up otherwise, ringtone repeating until answered. */
    static void showIncoming(Context c, Map<String, String> d) {
        if (!canNotify(c)) return;
        createChannels(c);
        long conv = parseLong(d.get("conversationId"));
        String callId = nz(d.get("callId"));
        String name = d.get("fromUserName") != null ? d.get("fromUserName") : "Office Chat";

        PendingIntent answer = PendingIntent.getActivity(c, (int) (conv * 10 + 1),
                openApp(c, Config.WEB_URL + "/?answer=" + callId + "&conv=" + conv, conv), FLAGS);
        PendingIntent fullScreen = PendingIntent.getActivity(c, (int) (conv * 10 + 2),
                IncomingCallActivity.intent(c, d), FLAGS);
        PendingIntent decline = PendingIntent.getBroadcast(c, (int) (conv * 10 + 3),
                CallActionReceiver.declineIntent(c, d), FLAGS);

        Person caller = new Person.Builder().setName(name).setImportant(true).build();
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CH_CALLS)
                .setSmallIcon(android.R.drawable.sym_call_incoming)
                .setContentTitle(d.get("title"))
                .setContentText(d.get("body"))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(true)
                .setContentIntent(fullScreen)
                .setFullScreenIntent(fullScreen, true)
                .setTimeoutAfter(45_000)
                // For Android 7 and older (no channels):
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE), AudioManager.STREAM_RING)
                .setVibrate(VIBRATE)
                .setStyle(NotificationCompat.CallStyle.forIncomingCall(caller, decline, answer));
        Notification n = b.build();
        n.flags |= Notification.FLAG_INSISTENT; // keep ringing until answered, declined or cleared
        notify(c, callNotificationId(conv), n);
    }

    /** Stop ringing (answered, declined, cancelled or timed out), and close the ringing screen. */
    static void cancelIncoming(Context c, long conv) {
        if (conv < 0) return;
        NotificationManagerCompat.from(c).cancel(callNotificationId(conv));
        Intent ended = new Intent(ACTION_CALL_ENDED).setPackage(c.getPackageName()).putExtra(EXTRA_CONV, conv);
        c.sendBroadcast(ended);
    }

    static void showMissed(Context c, Map<String, String> d) {
        if (!canNotify(c)) return;
        long conv = parseLong(d.get("conversationId"));
        PendingIntent open = PendingIntent.getActivity(c, (int) (conv * 10 + 4),
                openApp(c, Config.WEB_URL + "/?chat=" + conv, conv), FLAGS);
        notify(c, (int) (200_000 + conv), new NotificationCompat.Builder(c, CH_MISSED)
                .setSmallIcon(android.R.drawable.sym_call_missed)
                .setContentTitle(d.get("title"))
                .setContentText(d.get("body"))
                .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
                .setAutoCancel(true)
                .setContentIntent(open)
                .build());
    }

    static void showMessage(Context c, Map<String, String> d) {
        if (!canNotify(c)) return;
        long conv = parseLong(d.get("conversationId"));
        String messageId = nz(d.get("messageId"));
        PendingIntent open = PendingIntent.getActivity(c, (int) (conv * 10 + 5),
                openApp(c, Config.WEB_URL + "/?chat=" + conv + (messageId.isEmpty() ? "" : "&message=" + messageId), conv), FLAGS);
        notify(c, (int) (300_000 + conv), new NotificationCompat.Builder(c, CH_MESSAGES)
                .setSmallIcon(android.R.drawable.sym_action_chat)
                .setContentTitle(d.get("title"))
                .setContentText(d.get("body"))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(d.get("body")))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setAutoCancel(true)
                .setContentIntent(open)
                .build());
    }

    /** The phone check: rings once on the calls channel, so it also proves the ringtone and pop-up work. */
    static void showTest(Context c, Map<String, String> d) {
        if (!canNotify(c)) return;
        createChannels(c);
        PendingIntent open = PendingIntent.getActivity(c, 9, openApp(c, null, -1), FLAGS);
        notify(c, 400_000, new NotificationCompat.Builder(c, CH_CALLS)
                .setSmallIcon(android.R.drawable.sym_call_incoming)
                .setContentTitle(d.get("title"))
                .setContentText(d.get("body"))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE), AudioManager.STREAM_RING)
                .setVibrate(VIBRATE)
                .setAutoCancel(true)
                .setTimeoutAfter(20_000)
                .setContentIntent(open)
                .build());
    }

    /** Shown while a call is going on (keeps it alive in the background). Tapping returns to the call. */
    static Notification ongoing(Context c) {
        createChannels(c);
        PendingIntent open = PendingIntent.getActivity(c, 7, openApp(c, null, -1), FLAGS);
        return new NotificationCompat.Builder(c, CH_ONGOING)
                .setSmallIcon(android.R.drawable.sym_action_call)
                .setContentTitle("Office Chat call in progress")
                .setContentText("Tap to return to the call")
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setContentIntent(open)
                .build();
    }

    /** Opens the app; with a url, at that page of the website. */
    static Intent openApp(Context c, String url, long conv) {
        Intent i = new Intent(c, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (url != null) {
            i.putExtra(MainActivity.EXTRA_URL, url);
            i.setData(Uri.parse(url)); // keeps each notification's intent distinct
        }
        if (conv >= 0) i.putExtra(EXTRA_CONV, conv);
        return i;
    }

    static int callNotificationId(long conv) {
        return (int) (100_000 + conv);
    }

    static long parseLong(String v) {
        try {
            return v == null ? -1 : Long.parseLong(v);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    static String nz(String v) {
        return v == null || "null".equals(v) ? "" : v;
    }

    private static boolean canNotify(Context c) {
        return Build.VERSION.SDK_INT < 33
                || ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressWarnings("MissingPermission") // checked in canNotify
    private static void notify(Context c, int id, Notification n) {
        if (canNotify(c)) NotificationManagerCompat.from(c).notify(id, n);
    }
}
