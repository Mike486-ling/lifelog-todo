package com.lifelog.app;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** 通知栏上的「完成」「稍后 10 分钟」两个按钮 */
public class ReminderActions extends BroadcastReceiver {
    static final String A_DONE = "com.lifelog.app.NOTIF_DONE";
    static final String A_SNOOZE = "com.lifelog.app.NOTIF_SNOOZE";

    static PendingIntent pi(Context c, String action, int notifId, Intent src) {
        Intent it = new Intent(c, ReminderActions.class);
        it.setAction(action);
        it.putExtra("nid", notifId);
        it.putExtra("id", src.getStringExtra("id"));
        it.putExtra("title", src.getStringExtra("title"));
        it.putExtra("body", src.getStringExtra("body"));
        it.putExtra("sound", src.getStringExtra("sound"));
        it.putExtra("mode", src.getStringExtra("mode"));
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(c, notifId * 7 + action.hashCode(), it, flags);
    }

    @Override
    public void onReceive(Context c, Intent it) {
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            int nid = it.getIntExtra("nid", 0);
            if (nid != 0) nm.cancel(nid);
            RingPlayer.stop();

            String taskId = it.getStringExtra("id");
            if (A_DONE.equals(it.getAction())) {
                if (taskId != null && taskId.length() > 0) {
                    PendingOps.add(c, "{\"t\":\"done\",\"id\":\"" + esc(taskId) + "\"}");
                    Sched.cancel(c, taskId);
                }
                return;
            }
            /* 稍后 10 分钟：照原来的内容再排一次 */
            Intent again = new Intent(c, ReminderReceiver.class);
            again.putExtra("id", taskId);
            again.putExtra("title", it.getStringExtra("title"));
            again.putExtra("body", it.getStringExtra("body"));
            again.putExtra("sound", it.getStringExtra("sound"));
            again.putExtra("mode", it.getStringExtra("mode"));
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
            PendingIntent pi = PendingIntent.getBroadcast(c,
                    (int) (System.currentTimeMillis() % 100000), again, flags);
            long at = System.currentTimeMillis() + 10 * 60 * 1000L;
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
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

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
