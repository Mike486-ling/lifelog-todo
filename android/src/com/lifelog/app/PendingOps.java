package com.lifelog.app;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 原生侧（通知按钮）记下的待处理操作，等网页下次启动/回前台时取走执行。
 * 格式：[{"t":"done","id":"xxx"},{"t":"deferall"}]
 */
final class PendingOps {
    private static final String PREF = "ops";
    private static final String KEY = "q";

    static synchronized void add(Context c, String json) {
        try {
            SharedPreferences sp = c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
            String cur = sp.getString(KEY, "[]");
            String head = cur.substring(0, Math.max(0, cur.length() - 1));
            String merged = "[]".equals(cur) ? "[" + json + "]" : head + "," + json + "]";
            sp.edit().putString(KEY, merged).apply();
        } catch (Exception e) {
        }
    }

    static synchronized String take(Context c) {
        try {
            SharedPreferences sp = c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
            String cur = sp.getString(KEY, "[]");
            sp.edit().putString(KEY, "[]").apply();
            return cur;
        } catch (Exception e) {
            return "[]";
        }
    }
}
