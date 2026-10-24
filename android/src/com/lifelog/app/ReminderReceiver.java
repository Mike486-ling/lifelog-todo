package com.lifelog.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class ReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent it) {
        String title = it.getStringExtra("title");
        String body = it.getStringExtra("body");
        RingPlayer.start(c.getApplicationContext());
        int id = (int) (System.currentTimeMillis() % 100000);
        show(c, id, title == null ? "待办提醒" : title, body == null ? "" : body,
                it.getStringExtra("id"));
    }

    static void show(Context c, int id, String title, String body, String taskId) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);

        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                    MainActivity.CHANNEL_ID, "待办闹钟", NotificationManager.IMPORTANCE_HIGH);
            ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            ch.enableVibration(true);
            ch.setVibrationPattern(new long[]{0, 260, 180, 260});
            nm.createNotificationChannel(ch);
            b = new Notification.Builder(c, MainActivity.CHANNEL_ID);
        } else {
            b = new Notification.Builder(c);
        }

        Intent open = new Intent(c, AlarmActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        open.putExtra("id", taskId);
        open.putExtra("title", title);
        open.putExtra("body", body);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(c, id, open, flags);

        // 直接尝试拉起（部分系统允许），失败则由全屏通知兜底
        try {
            c.startActivity(open);
        } catch (Exception e) {
        }

        b.setContentTitle(title)
                .setContentText(body)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setAutoCancel(false)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setFullScreenIntent(pi, true)
                .setContentIntent(pi)
                .setDefaults(Notification.DEFAULT_ALL)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .setStyle(new Notification.BigTextStyle().bigText(body));

        try {
            nm.notify(id, b.build());
        } catch (Exception e) {
        }
    }
}
