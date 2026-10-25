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
    /** 提醒方式：alarm 全屏闹钟 / silent 静音弹窗 / vibrate 震动弹窗 */
    static final String CH_POPUP_SILENT = "reminders_silent";
    static final String CH_POPUP_VIB = "reminders_vibrate";

    @Override
    public void onReceive(Context c, Intent it) {
        String title = it.getStringExtra("title");
        String body = it.getStringExtra("body");
        String sound = it.getStringExtra("sound");
        String taskId = it.getStringExtra("id");
        String mode = it.getStringExtra("mode");
        if (mode == null || mode.length() == 0) mode = "alarm";
        int id = (int) (System.currentTimeMillis() % 100000);

        if ("silent".equals(mode) || "vibrate".equals(mode)) {
            /* 弹窗档：不拉全屏、不响铃，只出横幅；震动档额外震两下 */
            popup(c, id, title == null ? "待办提醒" : title, body == null ? "" : body,
                    taskId, "vibrate".equals(mode));
            return;
        }

        RingPlayer.start(c.getApplicationContext(), sound);
        show(c, id, title == null ? "待办提醒" : title, body == null ? "" : body, taskId, sound, mode);
    }

    static void show(Context c, int id, String title, String body, String taskId) {
        show(c, id, title, body, taskId, null, "alarm");
    }

    static void show(Context c, int id, String title, String body, String taskId, String sound, String mode) {
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
        open.putExtra("sound", sound);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(c, id, open, flags);

        // 直接尝试拉起（部分系统允许），失败则由全屏通知兜底
        try {
            c.startActivity(open);
        } catch (Exception e) {
        }

        Intent src = new Intent();
        src.putExtra("id", taskId);
        src.putExtra("title", title);
        src.putExtra("body", body);
        src.putExtra("sound", sound);
        src.putExtra("mode", mode);
        b.addAction(new Notification.Action.Builder(null, "完成",
                ReminderActions.pi(c, ReminderActions.A_DONE, id, src)).build())
                .addAction(new Notification.Action.Builder(null, "稍后 10 分钟",
                        ReminderActions.pi(c, ReminderActions.A_SNOOZE, id, src)).build())
                .setContentTitle(title)
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

    /** 静音 / 震动弹窗：横幅通知，点一下回到 App */
    static void popup(Context c, int id, String title, String body, String taskId, boolean vibrate) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        String chId = vibrate ? CH_POPUP_VIB : CH_POPUP_SILENT;

        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                    chId, vibrate ? "待办弹窗（震动）" : "待办弹窗（静音）",
                    NotificationManager.IMPORTANCE_HIGH);
            ch.setSound(null, null);
            ch.enableLights(false);
            ch.enableVibration(vibrate);
            if (vibrate) ch.setVibrationPattern(new long[]{0, 220, 140, 220});
            ch.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            nm.createNotificationChannel(ch);
            b = new Notification.Builder(c, chId);
        } else {
            b = new Notification.Builder(c);
        }

        Intent open = new Intent(c, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(c, id, open, flags);

        Intent src = new Intent();
        src.putExtra("id", taskId);
        src.putExtra("title", title);
        src.putExtra("body", body);
        src.putExtra("mode", vibrate ? "vibrate" : "silent");
        b.addAction(new Notification.Action.Builder(null, "完成",
                ReminderActions.pi(c, ReminderActions.A_DONE, id, src)).build())
                .addAction(new Notification.Action.Builder(null, "稍后 10 分钟",
                        ReminderActions.pi(c, ReminderActions.A_SNOOZE, id, src)).build())
                .setContentTitle(title)
                .setContentText(body)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setAutoCancel(true)
                .setOngoing(false)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setContentIntent(pi)
                .setOnlyAlertOnce(false)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .setStyle(new Notification.BigTextStyle().bigText(body));

        if (Build.VERSION.SDK_INT < 26) {
            // 8.0 以下没有通道，只能靠默认值控制：静音就清掉声音/震动
            b.setDefaults(vibrate ? Notification.DEFAULT_VIBRATE : 0);
            if (vibrate) b.setVibrate(new long[]{0, 220, 140, 220});
            b.setSound(null);
        }

        try {
            nm.notify(id, b.build());
        } catch (Exception e) {
        }
    }
}
