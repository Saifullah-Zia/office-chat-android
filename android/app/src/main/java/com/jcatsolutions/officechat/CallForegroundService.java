package com.jcatsolutions.officechat;

import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

/**
 * Runs during a call with a "call in progress" notification, so Android keeps the app (and the
 * microphone) going when you switch to another app or lock the phone. Started/stopped by the website.
 */
public class CallForegroundService extends Service {

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(CallNotifier.ONGOING_ID, CallNotifier.ongoing(this), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            } else {
                startForeground(CallNotifier.ONGOING_ID, CallNotifier.ongoing(this));
            }
        } catch (Exception e) {
            stopSelf(); // e.g. microphone permission not granted yet
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        stopForeground(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
