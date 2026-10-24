package com.lifelog.app;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.Toast;

import org.json.JSONObject;

/**
 * 品牌识别 + 厂商权限页跳转。
 * 各 ROM 的「自启动 / 后台弹出」入口不同，这里只做一件事：
 * 按品牌给一串候选组件，能跳就跳，跳不了就退到应用详情页，绝不让 App 崩。
 */
public class DeviceBrand {

    /** xiaomi / huawei / oppo / vivo / samsung / meizu / generic */
    static String key() {
        String s = (Build.MANUFACTURER == null ? "" : Build.MANUFACTURER) + " " +
                (Build.BRAND == null ? "" : Build.BRAND);
        s = s.toLowerCase();
        if (s.contains("xiaomi") || s.contains("redmi") || s.contains("poco")) return "xiaomi";
        if (s.contains("huawei") || s.contains("honor")) return "huawei";
        if (s.contains("oppo") || s.contains("realme") || s.contains("oneplus")) return "oppo";
        if (s.contains("vivo") || s.contains("iqoo")) return "vivo";
        if (s.contains("samsung")) return "samsung";
        if (s.contains("meizu")) return "meizu";
        return "generic";
    }

    static String name() {
        String k = key();
        if ("xiaomi".equals(k)) return "小米（MIUI/HyperOS）";
        if ("huawei".equals(k)) return "华为/荣耀";
        if ("oppo".equals(k)) return "OPPO 系（ColorOS）";
        if ("vivo".equals(k)) return "vivo/iQOO";
        if ("samsung".equals(k)) return "三星（One UI）";
        if ("meizu".equals(k)) return "魅族（Flyme）";
        return "通用";
    }

    /** 各品牌里「后台弹出/后台运行」这一项的实际叫法 */
    static String popupLabel() {
        String k = key();
        if ("xiaomi".equals(k)) return "后台弹出界面";
        if ("huawei".equals(k)) return "关联启动";
        if ("vivo".equals(k)) return "后台高耗能";
        if ("samsung".equals(k)) return "后台使用限制";
        return "后台运行";
    }

    /** 给网页用的 JSON：{key,name,popup} */
    static String info() {
        try {
            JSONObject o = new JSONObject();
            o.put("key", key());
            o.put("name", name());
            o.put("popup", popupLabel());
            return o.toString();
        } catch (Exception e) {
            return "{\"key\":\"generic\",\"name\":\"通用\",\"popup\":\"后台运行\"}";
        }
    }

    static final String[][] CANDIDATES = {
            // xiaomi 自启动
            {"xiaomi", "autostart", "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"},
            // huawei 应用启动管理
            {"huawei", "autostart", "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"},
            {"huawei", "popup", "com.huawei.systemmanager",
                    "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"},
            // oppo 自启动
            {"oppo", "autostart", "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity"},
            {"oppo", "autostart", "com.oppo.safe",
                    "com.oppo.safe.permission.startup.StartupAppListActivity"},
            {"oppo", "popup", "com.coloros.safecenter",
                    "com.coloros.safecenter.startupapp.StartupAppListActivity"},
            // vivo 自启动
            {"vivo", "autostart", "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"},
            {"vivo", "autostart", "com.iqoo.secure",
                    "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"},
            {"vivo", "popup", "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.PurviewTabActivity"},
            // 魅族 自启动
            {"meizu", "autostart", "com.meizu.safe",
                    "com.meizu.safe.permission.SmartBGActivity"}
    };

    /** kind: autostart / popup / battery */
    static void open(Activity a, String kind) {
        if ("battery".equals(kind)) {
            if (openBattery(a)) return;
            openDetails(a);
            return;
        }
        String k = key();
        for (String[] c : CANDIDATES) {
            if (c[0].equals(k) && c[1].equals(kind)) {
                try {
                    Intent i = new Intent();
                    i.setComponent(new ComponentName(c[2], c[3]));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    a.startActivity(i);
                    return;
                } catch (Exception ignored) {
                }
            }
        }
        openDetails(a);
        toast(a, "没找到直达页，已打开应用详情，请看「权限」或「电池」附近");
    }

    private static boolean openBattery(Activity a) {
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + a.getPackageName()));
            a.startActivity(i);
            return true;
        } catch (Exception ignored) {
        }
        try {
            a.startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            return true;
        } catch (Exception ignored) {
        }
        return false;
    }

    static void openDetails(Activity a) {
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + a.getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            a.startActivity(i);
        } catch (Exception e) {
            toast(a, "打不开设置页，请手动到系统设置里找");
        }
    }

    /** 三项能查的状态；查不到的给 null，网页显示「去设置」 */
    static String status(Context c) {
        try {
            JSONObject o = new JSONObject();
            try {
                NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
                if (Build.VERSION.SDK_INT >= 24) o.put("notif", nm != null && nm.areNotificationsEnabled());
            } catch (Exception e) {
            }
            try {
                if (Build.VERSION.SDK_INT >= 31) {
                    AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
                    o.put("exact", am != null && am.canScheduleExactAlarms());
                } else {
                    o.put("exact", true);
                }
            } catch (Exception e) {
            }
            try {
                if (Build.VERSION.SDK_INT >= 23) {
                    PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
                    o.put("battery", pm != null && pm.isIgnoringBatteryOptimizations(c.getPackageName()));
                }
            } catch (Exception e) {
            }
            return o.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    private static void toast(final Activity a, final String m) {
        a.runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(a, m, Toast.LENGTH_LONG).show();
            }
        });
    }
}
