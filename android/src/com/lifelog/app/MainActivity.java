package com.lifelog.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

public class MainActivity extends Activity {
    public static final String CHANNEL_ID = "reminders";
    public static final String CH_PROGRESS = "progress";
    private static final int PROGRESS_ID = 9001;
    private WebView web;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        createChannel();

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setSupportZoom(false);
        s.setLoadWithOverviewMode(false);
        web.setBackgroundColor(Color.rgb(14, 15, 19));
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new Host(), "AndroidHost");
        setContentView(web);
        web.loadUrl("file:///android_asset/app.html");

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "待办提醒", NotificationManager.IMPORTANCE_HIGH);
            ch.enableVibration(true);
            ch.setVibrationPattern(new long[]{0, 260, 180, 260});
            nm.createNotificationChannel(ch);

            // 常驻进度条：低优先级、无声、不打扰
            NotificationChannel pg = new NotificationChannel(
                    CH_PROGRESS, "今日进度", NotificationManager.IMPORTANCE_LOW);
            pg.setShowBadge(false);
            pg.enableVibration(false);
            pg.setSound(null, null);
            nm.createNotificationChannel(pg);
        }
    }

    /** 通知栏常驻今日进度（同 id 覆盖更新，不响不震） */
    void showProgress(final String text) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            b = new Notification.Builder(this, CH_PROGRESS);
        } else {
            b = new Notification.Builder(this);
            b.setPriority(Notification.PRIORITY_LOW);
        }
        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(this, 9001, open, flags);
        b.setContentTitle("今天 " + headOf(text))
                .setContentText(tailOf(text))
                .setSmallIcon(android.R.drawable.ic_menu_agenda)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setContentIntent(pi);
        if (Build.VERSION.SDK_INT >= 26) b.setChannelId(CH_PROGRESS);
        try {
            nm.notify(PROGRESS_ID, b.build());
        } catch (Exception e) {
        }
    }

    /** text 形如 "4/8 完成 · 还有 4 项倒计时" —— 前半截进标题，后半截进正文 */
    static String headOf(String t) {
        if (t == null) return "";
        int i = t.indexOf('·');
        return (i < 0 ? t : t.substring(0, i)).trim();
    }

    static String tailOf(String t) {
        if (t == null) return "";
        int i = t.indexOf('·');
        return i < 0 ? "" : t.substring(i + 1).trim();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (code == 1) {
            boolean ok = res.length > 0 && res[0] == 0;
            toast(ok ? "通知权限已开启" : "通知权限被拒绝，提醒不会弹出");
        }
    }

    private void toast(final String m) {
        runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(MainActivity.this, m, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_BACK && web != null && web.canGoBack()) {
            web.goBack();
            return true;
        }
        return super.onKeyDown(code, e);
    }

    public class Host {
        @JavascriptInterface
        public boolean nativePush() {
            return true;
        }

        @JavascriptInterface
        public void sync(String json) {
            Sched.sync(MainActivity.this, json);
        }

        @JavascriptInterface
        public void notifyNow(String title, String body) {
            ReminderReceiver.show(MainActivity.this, (int) (System.currentTimeMillis() % 100000), title, body, null);
        }

        @JavascriptInterface
        public void askPermission() {
            runOnUiThread(new Runnable() {
                public void run() {
                    if (Build.VERSION.SDK_INT >= 33) {
                        requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
                    } else {
                        toast("此系统无需单独授权");
                    }
                }
            });
        }

        /** 精确闹钟权限（Android 12+ 可能默认不给），引导用户去系统设置 */
        @JavascriptInterface
        public void askExactAlarm() {
            runOnUiThread(new Runnable() {
                public void run() {
                    if (Build.VERSION.SDK_INT >= 31) {
                        try {
                            Intent i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                    Uri.parse("package:" + getPackageName()));
                            startActivity(i);
                        } catch (Exception e) {
                            toast("无法打开设置页");
                        }
                    }
                }
            });
        }

        @JavascriptInterface
        public void buzz() {
            try {
                android.os.Vibrator v = (android.os.Vibrator) getSystemService(VIBRATOR_SERVICE);
                if (Build.VERSION.SDK_INT >= 26) {
                    v.vibrate(android.os.VibrationEffect.createOneShot(20,
                            android.os.VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    v.vibrate(20);
                }
            } catch (Exception e) {
            }
        }

        @JavascriptInterface
        public void toastMsg(String m) {
            toast(m);
        }

        /** 更新通知栏常驻进度 */
        @JavascriptInterface
        public void progress(final String text) {
            runOnUiThread(new Runnable() {
                public void run() {
                    showProgress(text);
                }
            });
        }

        /** 自动备份到 下载/今日待办备份/，成功返回 true */
        @JavascriptInterface
        public boolean saveBackup(String json, String name) {
            return Backup.write(MainActivity.this, json, name);
        }

        /** 跟随网页主题切换 WebView 底色，避免内容不够高时露出深色 */
        @JavascriptInterface
        public void setBg(final String color) {
            runOnUiThread(new Runnable() {
                public void run() {
                    try {
                        int c = Color.parseColor(color);
                        if (web != null) web.setBackgroundColor(c);
                        getWindow().getDecorView().setBackgroundColor(c);
                    } catch (Exception e) {
                    }
                }
            });
        }

        /** 打开本应用的系统设置页（自启动 / 省电策略 / 后台弹出界面都在附近） */
        @JavascriptInterface
        public void openAppSettings() {
            runOnUiThread(new Runnable() {
                public void run() {
                    try {
                        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:" + getPackageName()));
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(i);
                    } catch (Exception e) {
                        toast("打不开应用详情页，请手动到系统设置里找");
                    }
                }
            });
        }
    }
}
