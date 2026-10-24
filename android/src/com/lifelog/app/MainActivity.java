package com.lifelog.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
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
        }
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
        public void toastMsg(String m) {
            toast(m);
        }
    }
}
