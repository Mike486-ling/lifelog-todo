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
import android.media.Ringtone;
import android.media.RingtoneManager;
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

import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
    public static final String CHANNEL_ID = "reminders";
    public static final String CH_PROGRESS = "progress";
    private static final int PROGRESS_ID = 9001;
    private static final int REQ_RING = 101;
    private WebView web;
    private android.widget.FrameLayout root;
    private android.widget.LinearLayout splash;
    private android.widget.TextView splashCount;
    private android.view.View splashLine;
    /** 0 关闭 / 1 简洁（只有 A）/ 2 完整（A + 交接 B） */
    private int splashMode = 2;
    private boolean splashGone = false;

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
        root = new android.widget.FrameLayout(this);
        root.addView(web, new android.widget.FrameLayout.LayoutParams(-1, -1));

        splashMode = getSharedPreferences("ui", MODE_PRIVATE).getInt("splash", 2);
        if (splashMode > 0) buildSplash();
        setContentView(root);
        web.loadUrl("file:///android_asset/app.html");

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }
    }

    /** 开屏：黑底 + 图标呼吸 + 一条细线，加载完淡出 */
    private void buildSplash() {
        splashGone = false;
        splash = new android.widget.LinearLayout(this);
        splash.setOrientation(android.widget.LinearLayout.VERTICAL);
        splash.setGravity(android.view.Gravity.CENTER);
        splash.setBackgroundColor(Color.rgb(11, 18, 32));

        android.widget.ImageView icon = new android.widget.ImageView(this);
        try {
            icon.setImageResource(getApplicationInfo().icon);
        } catch (Exception e) {
        }
        int sz = (int) (84 * getResources().getDisplayMetrics().density);
        android.widget.LinearLayout.LayoutParams ip =
                new android.widget.LinearLayout.LayoutParams(sz, sz);
        ip.bottomMargin = (int) (18 * getResources().getDisplayMetrics().density);
        splash.addView(icon, ip);

        android.widget.TextView name = new android.widget.TextView(this);
        name.setText("今日待办");
        name.setTextColor(Color.rgb(230, 234, 242));
        name.setTextSize(15);
        name.setGravity(android.view.Gravity.CENTER);
        splash.addView(name);

        splashCount = new android.widget.TextView(this);
        splashCount.setTextColor(Color.rgb(120, 160, 230));
        splashCount.setTextSize(13);
        splashCount.setGravity(android.view.Gravity.CENTER);
        splashCount.setAlpha(0f);
        android.widget.LinearLayout.LayoutParams cp =
                new android.widget.LinearLayout.LayoutParams(-2, -2);
        cp.topMargin = (int) (10 * getResources().getDisplayMetrics().density);
        splash.addView(splashCount, cp);

        splashLine = new android.view.View(this);
        splashLine.setBackgroundColor(Color.rgb(90, 162, 255));
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                (int) (60 * getResources().getDisplayMetrics().density),
                (int) (2 * getResources().getDisplayMetrics().density));
        lp.topMargin = (int) (22 * getResources().getDisplayMetrics().density);
        splash.addView(splashLine, lp);

        root.addView(splash, new android.widget.FrameLayout.LayoutParams(-1, -1));

        icon.setScaleX(0.92f);
        icon.setScaleY(0.92f);
        icon.animate().scaleX(1f).scaleY(1f).setDuration(520)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        splashLine.setScaleX(0.05f);
        splashLine.setPivotX(0f);
        splashLine.animate().scaleX(0.62f).setDuration(620).start();

        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView v, String url) {
                // 简洁模式：加载完直接淡出；完整模式等网页把「今天 x/y」发过来
                if (splashMode == 1) splashFade();
                else splashTimeout();
            }
        });
    }

    private void splashTimeout() {
        if (splashGone) return;
        runOnUiThread(new Runnable() {
            public void run() {
                new android.os.Handler(android.os.Looper.getMainLooper())
                        .postDelayed(new Runnable() {
                            public void run() {
                                splashFade();
                            }
                        }, 1200);
            }
        });
    }

    /** 网页数据就绪后调用：完成细线 + 显示今天进度，然后淡出 */
    void splashHandoff(final String text) {
        if (splashMode < 2) {
            splashFade();
            return;
        }
        runOnUiThread(new Runnable() {
            public void run() {
                if (splashGone || splash == null) return;
                splashCount.setText(text == null ? "" : text);
                splashCount.animate().alpha(1f).setDuration(180).start();
                splashLine.animate().scaleX(1f).setDuration(220).start();
                new android.os.Handler(android.os.Looper.getMainLooper())
                        .postDelayed(new Runnable() {
                            public void run() {
                                splashFade();
                            }
                        }, 300);
            }
        });
    }

    private void splashFade() {
        if (splashGone || splash == null) return;
        splashGone = true;
        runOnUiThread(new Runnable() {
            public void run() {
                final android.view.View v = splash;
                v.animate().alpha(0f).setDuration(220).withEndAction(new Runnable() {
                    public void run() {
                        try {
                            root.removeView(v);
                        } catch (Exception e) {
                        }
                        splash = null;
                    }
                }).start();
            }
        });
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
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_RING) return;
        Uri u = null;
        if (res == RESULT_OK && data != null) {
            try {
                u = (Uri) data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            } catch (Exception e) {
            }
        }
        String uri = u == null ? "" : u.toString();
        String title = ringTitle(u);
        String js = "window.onRingPicked&&onRingPicked(" + JSONObject.quote(uri) + ","
                + JSONObject.quote(title) + ")";
        final String code = js;
        runOnUiThread(new Runnable() {
            public void run() {
                try {
                    if (web != null) web.evaluateJavascript(code, null);
                } catch (Exception e) {
                }
            }
        });
    }

    private String ringTitle(Uri u) {
        if (u == null) return "";
        try {
            Ringtone r = RingtoneManager.getRingtone(this, u);
            if (r != null) return r.getTitle(this);
        } catch (Exception e) {
        }
        return "";
    }

    private String ringInfoJson(Uri u) {
        try {
            JSONObject o = new JSONObject();
            o.put("uri", u == null ? "" : u.toString());
            o.put("title", ringTitle(u));
            return o.toString();
        } catch (Exception e) {
            return "{}";
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

        /** 品牌标识 + 后台弹窗的本地叫法，给网页渲染用 */
        @JavascriptInterface
        public String deviceBrand() {
            return DeviceBrand.info();
        }

        /** 跳厂商权限页：autostart / popup / battery */
        @JavascriptInterface
        public void openVendorPerm(final String kind) {
            runOnUiThread(new Runnable() {
                public void run() {
                    DeviceBrand.open(MainActivity.this, kind);
                }
            });
        }

        /** 通知 / 精确闹钟 / 电池白名单 的当前状态，查不到的不填 */
        @JavascriptInterface
        public String permStatus() {
            return DeviceBrand.status(MainActivity.this);
        }

        /** 开屏动画三档：0 关 / 1 简洁 / 2 完整（写进 SharedPreferences，下次启动生效） */
        @JavascriptInterface
        public void setSplashMode(int mode) {
            try {
                splashMode = mode;
                getSharedPreferences("ui", MODE_PRIVATE).edit().putInt("splash", mode).apply();
            } catch (Exception e) {
            }
        }

        /** 数据就绪：完整模式下把「今天 x/y」交给开屏 */
        @JavascriptInterface
        public void splashDone(final String text) {
            splashHandoff(text);
        }

        /** 读剪贴板纯文本，给「批量导入」用 */
        @JavascriptInterface
        public String readClipboard() {
            try {
                android.content.ClipboardManager cm = (android.content.ClipboardManager)
                        getSystemService(CLIPBOARD_SERVICE);
                if (cm == null || !cm.hasPrimaryClip()) return "";
                android.content.ClipData cd = cm.getPrimaryClip();
                if (cd == null || cd.getItemCount() == 0) return "";
                CharSequence cs = cd.getItemAt(0).coerceToText(MainActivity.this);
                return cs == null ? "" : cs.toString();
            } catch (Exception e) {
                return "";
            }
        }

        /** 完成音效：一声轻响，不申请音频焦点 */
        @JavascriptInterface
        public void clickSound() {
            try {
                android.media.ToneGenerator tg =
                        new android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 60);
                tg.startTone(android.media.ToneGenerator.TONE_PROP_ACK, 110);
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
                    public void run() {
                        try {
                            tg.release();
                        } catch (Exception e) {
                        }
                    }
                }, 400);
            } catch (Exception e) {
            }
        }

        /** 打开系统铃声选择器（仅闹钟类），选完回调 window.onRingPicked(uri,title) */
        @JavascriptInterface
        public void pickRingtone(final String current) {
            runOnUiThread(new Runnable() {
                public void run() {
                    try {
                        Uri cur = null;
                        if (current != null && current.length() > 0) cur = Uri.parse(current);
                        if (cur == null) cur = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                        Intent i = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
                        i.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM);
                        i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
                        i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);
                        i.putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, cur);
                        i.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, cur);
                        startActivityForResult(i, REQ_RING);
                    } catch (Exception e) {
                        toast("这台手机没有铃声选择器，用内置的几个吧");
                    }
                }
            });
        }

        /** 试听 3 秒 */
        @JavascriptInterface
        public void previewRing(final String uri) {
            runOnUiThread(new Runnable() {
                public void run() {
                    try {
                        RingPlayer.preview(MainActivity.this, uri);
                        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
                            public void run() {
                                RingPlayer.stopPreview();
                            }
                        }, 3200);
                    } catch (Exception e) {
                    }
                }
            });
        }

        /** 当前铃声的名字，传空串取系统默认闹铃 */
        @JavascriptInterface
        public String ringInfo(String uri) {
            Uri u = null;
            try {
                if (uri != null && uri.length() > 0) u = Uri.parse(uri);
                else u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            } catch (Exception e) {
            }
            return ringInfoJson(u);
        }

        /** 系统闹钟类铃声清单，给「快捷选择」用 */
        @JavascriptInterface
        public String ringtoneList() {
            try {
                RingtoneManager rm = new RingtoneManager(MainActivity.this);
                rm.setType(RingtoneManager.TYPE_ALARM);
                android.database.Cursor cur = rm.getCursor();
                JSONArray arr = new JSONArray();
                while (cur != null && cur.moveToNext() && arr.length() < 20) {
                    Uri u = rm.getRingtoneUri(cur.getPosition());
                    if (u == null) continue;
                    JSONObject o = new JSONObject();
                    o.put("uri", u.toString());
                    o.put("title", ringTitle(u));
                    arr.put(o);
                }
                if (cur != null) cur.close();
                return arr.toString();
            } catch (Exception e) {
                return "[]";
            }
        }

        /** 字体缩放：100 = 标准，130 = 更大 */
        @JavascriptInterface
        public void setTextZoom(final int pct) {
            runOnUiThread(new Runnable() {
                public void run() {
                    try {
                        if (web != null) {
                            int v = pct < 80 ? 80 : (pct > 200 ? 200 : pct);
                            web.getSettings().setTextZoom(v);
                        }
                    } catch (Exception e) {
                    }
                }
            });
        }

        /** 5 秒后弹一条测试通知，验证提醒链路通不通 */
        @JavascriptInterface
        public void testRemind() {
            runOnUiThread(new Runnable() {
                public void run() {
                    Toast.makeText(MainActivity.this, "5 秒后弹一条测试通知", Toast.LENGTH_SHORT).show();
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
                        public void run() {
                            try {
                                ReminderReceiver.show(MainActivity.this,
                                        (int) (System.currentTimeMillis() % 100000),
                                        "测试提醒", "能弹出来，说明通知和全屏闹钟都正常", null);
                            } catch (Exception e) {
                            }
                        }
                    }, 5000);
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
