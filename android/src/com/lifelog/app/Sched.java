package com.lifelog.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 把网页里设置的提醒同步成系统闹钟。
 * 网页每次保存都会调用 sync()，这里先取消旧的再按新列表重建，保证不重复。
 */
public class Sched {
    static final String PREFS = "sched";
    static final String KEY_ITEMS = "items";
    static final String KEY_IDS = "ids";

    static synchronized void sync(Context c, String json) {
        SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        try {
            JSONArray arr = new JSONArray(json);

            // 先取消上一轮登记过的闹钟
            JSONArray old = new JSONArray(sp.getString(KEY_IDS, "[]"));
            for (int i = 0; i < old.length(); i++) {
                cancel(c, old.optString(i));
            }

            JSONArray ids = new JSONArray();
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            long now = System.currentTimeMillis();

            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String id = o.optString("id");
                long at = o.optLong("at");
                if (id.length() == 0 || at <= 0) continue;
                ids.put(id);
                if (at <= now + 1500) continue;   // 已经过去的就不设了

                Intent it = new Intent(c, ReminderReceiver.class);
                it.putExtra("id", id);
                it.putExtra("title", o.optString("title", "待办提醒"));
                it.putExtra("body", o.optString("body", ""));
                it.putExtra("sound", o.optString("sound", ""));
                int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
                PendingIntent pi = PendingIntent.getBroadcast(c, id.hashCode(), it, flags);

                try {
                    if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
                    } else if (Build.VERSION.SDK_INT >= 23) {
                        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
                    } else {
                        am.setExact(AlarmManager.RTC_WAKEUP, at, pi);
                    }
                } catch (SecurityException e) {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
                }
            }

            sp.edit().putString(KEY_IDS, ids.toString()).putString(KEY_ITEMS, json).apply();
        } catch (Exception e) {
            // 解析失败就当作没有提醒，不崩溃
        }
    }

    static void cancel(Context c, String id) {
        try {
            Intent it = new Intent(c, ReminderReceiver.class);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
            PendingIntent pi = PendingIntent.getBroadcast(c, id.hashCode(), it, flags);
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            am.cancel(pi);
            pi.cancel();
        } catch (Exception e) {
        }
    }

    /** 重启后按上次保存的清单重建闹钟 */
    static void restore(Context c) {
        SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String json = sp.getString(KEY_ITEMS, "[]");
        sp.edit().putString(KEY_IDS, "[]").apply();
        sync(c, json);
    }
}
