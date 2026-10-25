package com.lifelog.app;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

/** 每天 22:00 的收尾提醒：今天还剩多少 / 一键全部顺延 */
public class WrapupReceiver extends BroadcastReceiver {
    static final String A_DEFER = "com.lifelog.app.WRAPUP_DEFER";
    static final int NID = 9100;
    static final int REQ = 9110;
    private static final String CH = "wrapup";

    static void schedule(Context c, boolean on, String text) {
        try {
            SharedPreferences sp = c.getSharedPreferences("ui", Context.MODE_PRIVATE);
            sp.edit().putBoolean("wrapup_on", on).putString("wrapup_text", text == null ? "" : text).apply();

            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
            PendingIntent pi = PendingIntent.getBroadcast(c, REQ, new Intent(c, WrapupReceiver.class), flags);
            if (!on) {
                am.cancel(pi);
                return;
            }
            long at = nextAt();
            try {
                if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
                } else {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
                }
            } catch (Exception e) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            }
        } catch (Exception e) {
        }
    }

    /** 恢复上次的设置（开机 / 应用更新后） */
    static void restore(Context c) {
        try {
            SharedPreferences sp = c.getSharedPreferences("ui", Context.MODE_PRIVATE);
            boolean on = sp.getBoolean("wrapup_on", true);
            schedule(c, on, sp.getString("wrapup_text", ""));
        } catch (Exception e) {
        }
    }

    private static long nextAt() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.HOUR_OF_DAY, 22);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis() + 60000L) {
            cal.add(java.util.Calendar.DAY_OF_MONTH, 1);
        }
        return cal.getTimeInMillis();
    }

    @Override
    public void onReceive(Context c, Intent it) {
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (A_DEFER.equals(it.getAction())) {
                PendingOps.add(c, "{\"t\":\"deferall\"}");
                nm.cancel(NID);
                return;
            }

            SharedPreferences sp = c.getSharedPreferences("ui", Context.MODE_PRIVATE);
            String text = sp.getString("wrapup_text", "");
            if (text == null || text.length() == 0) text = "把今天收个尾，剩下的顺延到明天";

            Notification.Builder b;
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel ch = new NotificationChannel(CH, "每日收尾", NotificationManager.IMPORTANCE_DEFAULT);
                ch.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
                nm.createNotificationChannel(ch);
                b = new Notification.Builder(c, CH);
            } else {
                b = new Notification.Builder(c);
            }

            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;

            Intent open = new Intent(c, MainActivity.class);
            open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent openPi = PendingIntent.getActivity(c, REQ + 1, open, flags);

            Intent defer = new Intent(c, WrapupReceiver.class);
            defer.setAction(A_DEFER);
            PendingIntent deferPi = PendingIntent.getBroadcast(c, REQ + 2, defer, flags);

            b.setContentTitle("今天的收尾时间到了")
                    .setContentText(text)
                    .setSmallIcon(android.R.drawable.ic_popup_reminder)
                    .setAutoCancel(true)
                    .setContentIntent(openPi)
                    .addAction(new Notification.Action.Builder(null, "全部顺延到明天", deferPi).build())
                    .addAction(new Notification.Action.Builder(null, "打开看看", openPi).build())
                    .setStyle(new Notification.BigTextStyle().bigText(text));

            nm.notify(NID, b.build());
            schedule(c, true, text);      /* 排下一天 */
        } catch (Exception e) {
        }
    }
}
