package com.github.axet.audiorecorder.services;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;
import android.view.View;

import com.github.axet.androidlibrary.widgets.ProximityShader;
import com.github.axet.androidlibrary.widgets.RemoteNotificationCompat;
import com.github.axet.audiolibrary.app.MainApplication;
import com.github.axet.audiolibrary.app.Storage;
import com.github.axet.audiorecorder.R;
import com.github.axet.audiorecorder.activities.MainActivity;
import com.github.axet.audiorecorder.activities.RecordingActivity;
import com.github.axet.audiorecorder.app.AudioApplication;

import java.io.File;

/**
 * Sometimes RecordingActivity started twice when launched from lockscreen. We need service and move recording into Application object.
 */
public class RecordingService extends Service {
    public static final String TAG = RecordingService.class.getSimpleName();

    public static final int NOTIFICATION_RECORDING_ICON = 1;

    public static String SHOW_ACTIVITY = RecordingService.class.getCanonicalName() + ".SHOW_ACTIVITY";
    public static String PAUSE_BUTTON = RecordingService.class.getCanonicalName() + ".PAUSE_BUTTON";
    public static String RECORD_BUTTON = RecordingService.class.getCanonicalName() + ".RECORD_BUTTON";

    Storage storage; // for storage path
    Notification notification;

    public static void startIfEnabled(Context context) {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        if (!shared.getBoolean(AudioApplication.PREFERENCE_CONTROLS, false))
            return;
        start(context);
    }

    public static void startIfPending(Context context) {
        Storage st = new Storage(context);
        if (st.recordingPending()) {
            final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
            String f = shared.getString(AudioApplication.PREFERENCE_TARGET, "");
            String d;
            if (f.startsWith(ContentResolver.SCHEME_CONTENT)) {
                Uri u = Uri.parse(f);
                d = Storage.getDocumentName(u);
            } else if (f.startsWith(ContentResolver.SCHEME_FILE)) {
                Uri u = Uri.parse(f);
                File file = Storage.getFile(u);
                d = file.getName();
            } else {
                File file = new File(f);
                d = file.getName();
            }
            startService(context, d, false, false, null);
            return;
        }
        startIfEnabled(context);
    }

    public static void start(Context context) {
        MainApplication.startService(context, new Intent(context, RecordingService.class));
    }

    public static void startService(Context context, String targetFile, boolean recording, boolean encoding, String duration) {
        MainApplication.startService(context, new Intent(context, RecordingService.class)
                .putExtra("targetFile", targetFile)
                .putExtra("recording", recording)
                .putExtra("encoding", encoding)
                .putExtra("duration", duration)
        );
    }

    public static void stopRecording(Context context) {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        if (shared.getBoolean(AudioApplication.PREFERENCE_CONTROLS, false)) {
            start(context);
            return;
        }
        stopService(context);
    }

    public static void stopService(Context context) {
        context.stopService(new Intent(context, RecordingService.class));
    }

    public RecordingService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");

        storage = new Storage(this);

        showNotification(true, new Intent());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");

        if (intent != null) {
            String a = intent.getAction();
            if (a == null) {
                showNotification(true, intent);
            } else if (a.equals(PAUSE_BUTTON)) {
                Intent i = new Intent(RecordingActivity.PAUSE_BUTTON);
                sendBroadcast(i);
            } else if (a.equals(RECORD_BUTTON)) {
                RecordingActivity.startActivity(this, false);
            } else if (a.equals(SHOW_ACTIVITY)) {
                ProximityShader.closeSystemDialogs(this);
                if (intent.getStringExtra("targetFile") == null)
                    MainActivity.startActivity(this);
                else
                    RecordingActivity.startActivity(this, !intent.getBooleanExtra("recording", false));
            }
        }

        return super.onStartCommand(intent, flags, startId);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public class Binder extends android.os.Binder {
        public RecordingService getService() {
            return RecordingService.this;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestory");
        showNotification(false, null);
    }

    @SuppressLint("RestrictedApi")
    public Notification build(Intent intent) {
        String targetFile = intent.getStringExtra("targetFile");
        boolean recording = intent.getBooleanExtra("recording", false);
        boolean encoding = intent.getBooleanExtra("encoding", false);
        String duration = intent.getStringExtra("duration");

        PendingIntent main;

        PendingIntent pe = PendingIntent.getService(this, 0,
                new Intent(this, RecordingService.class).setAction(PAUSE_BUTTON),
                PendingIntent.FLAG_UPDATE_CURRENT);

        PendingIntent re = PendingIntent.getService(this, 0,
                new Intent(this, RecordingService.class).setAction(RECORD_BUTTON),
                PendingIntent.FLAG_UPDATE_CURRENT);

        RemoteNotificationCompat.Builder builder;

        String title;
        String text;
        if (targetFile == null) {
            builder = new RemoteNotificationCompat.Low(this, R.layout.notifictaion);
            title = getString(R.string.app_name);
            Uri f = storage.getStoragePath();
            long free = storage.getFree(f);
            long sec = Storage.average(this, free);
            text = AudioApplication.formatFree(this, free, sec);
            builder.setViewVisibility(R.id.notification_record, View.VISIBLE);
            builder.setViewVisibility(R.id.notification_pause, View.GONE);
            main = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);
        } else {
            builder = new RemoteNotificationCompat.Builder(this, R.layout.notifictaion);
            if (recording)
                title = getString(R.string.recording_title);
            else
                title = getString(R.string.pause_title);
            if (duration != null)
                title += " (" + duration + ")";
            text = ".../" + targetFile;
            builder.setViewVisibility(R.id.notification_record, View.GONE);
            builder.setViewVisibility(R.id.notification_pause, View.VISIBLE);
            main = PendingIntent.getService(this, 0, new Intent(this, RecordingService.class).setAction(SHOW_ACTIVITY)
                    .putExtra("targetFile", targetFile).putExtra("recording", recording), PendingIntent.FLAG_UPDATE_CURRENT);
        }

        if (encoding) {
            builder.setViewVisibility(R.id.notification_pause, View.GONE);
            title = getString(R.string.encoding_title);
        }

        builder.setOnClickPendingIntent(R.id.notification_pause, pe);
        builder.setOnClickPendingIntent(R.id.notification_record, re);
        builder.setImageViewResource(R.id.notification_pause, !recording ? R.drawable.ic_play_arrow_black_24dp : R.drawable.ic_pause_black_24dp);
        builder.setContentDescription(R.id.notification_pause, getString(!recording ? R.string.record_button : R.string.pause_button));

        builder.setTheme(AudioApplication.getTheme(this, R.style.RecThemeLight, R.style.RecThemeDark))
                .setChannel(AudioApplication.from(this).channelStatus)
                .setImageViewTint(R.id.icon_circle, R.attr.colorButtonNormal)
                .setTitle(title)
                .setText(text)
                .setWhen(notification)
                .setMainIntent(main)
                .setOngoing(true)
                .setSmallIcon(R.drawable.ic_mic);

        return builder.build();
    }

    public void showNotification(boolean show, Intent intent) {
        NotificationManagerCompat nm = NotificationManagerCompat.from(this);
        if (!show) {
            stopForeground(false);
            nm.cancel(NOTIFICATION_RECORDING_ICON);
            notification = null;
        } else {
            Notification n = build(intent);
            if (notification == null)
                startForeground(NOTIFICATION_RECORDING_ICON, n);
            else
                nm.notify(NOTIFICATION_RECORDING_ICON, n);
            notification = n;
        }
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        Log.d(TAG, "onTaskRemoved");
    }
}
