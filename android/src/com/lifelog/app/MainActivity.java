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
import android.webkit.ValueCallback;
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
    private static final int REQ_IMAGE = 102;
    private static final int REQ_BACKUP = 103;
    private WebView web;
    /** 返回键正在问网页「这一层要不要我关」，防止连按重复触发 */
    private boolean backBusy = false;
    private android.widget.FrameLayout root;
    private SplashView splashView;
    /** 0 关闭 / 1 简洁（只有 A）/ 2 完整（A + 交接 B） */
    private int splashMode = 2;
    /** 桌面快捷方式带来的动作：new / stats */
    private String launchAct = "";
    /** 本进程是否已经放过开屏 */
    private static boolean sSplashShown = false;
    /** 距上次开屏不足这个间隔就不再放（从后台/最近任务回来） */
    private static final long SPLASH_GAP_MS = 30L * 60L * 1000L;


    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        installCrashLog();
        try {
            Intent in = getIntent();
            if (in != null && in.getStringExtra("act") != null) launchAct = in.getStringExtra("act");
        } catch (Exception e) {
        }
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
        /* 只在真正的冷启动放：b==null 才可能是新开（重建时 bundle 非空）；
         * 同进程不重复放；30 分钟内刚放过也不再放（被系统杀掉再从最近任务回来不算新开）。 */
        long nowMs = System.currentTimeMillis();
        long lastSplash = getSharedPreferences("ui", MODE_PRIVATE).getLong("splash_at", 0L);
        if (splashMode > 0 && b == null && !sSplashShown && nowMs - lastSplash > SPLASH_GAP_MS) {
            sSplashShown = true;
            getSharedPreferences("ui", MODE_PRIVATE).edit().putLong("splash_at", nowMs).apply();
            buildSplash();
        }
        setContentView(root);
        web.loadUrl("file:///android_asset/app.html");
        showLastCrash();

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }
    }

    /** 崩溃时把栈存到内部文件；下次启动弹出来给人看（没 ADB 也能排查） */
    private void installCrashLog() {
        try {
            final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                public void uncaughtException(Thread th, Throwable ex) {
                    dumpCrash(ex);
                    if (prev != null) prev.uncaughtException(th, ex);
                }
            });
        } catch (Throwable ignore) {
        }
    }

    private java.io.File crashFile() {
        return new java.io.File(getFilesDir(), "crash.txt");
    }

    private void dumpCrash(Throwable ex) {
        try {
            java.io.StringWriter sw = new java.io.StringWriter();
            java.io.PrintWriter pw = new java.io.PrintWriter(sw);
            sw.write("time=" + new java.util.Date().toString() + "\n");
            sw.write("sdk=" + Build.VERSION.SDK_INT + " ver=2.3.2\n");
            ex.printStackTrace(pw);
            pw.flush();
            java.io.FileOutputStream fos = new java.io.FileOutputStream(crashFile());
            fos.write(sw.toString().getBytes("UTF-8"));
            fos.close();
        } catch (Throwable ignore) {
        }
    }

    /** 上次崩溃过：把栈打到屏幕上，方便截图反馈 */
    private void showLastCrash() {
        try {
            final java.io.File f = crashFile();
            if (!f.exists()) return;
            java.io.FileInputStream fis = new java.io.FileInputStream(f);
            byte[] buf = new byte[(int) Math.min(f.length(), 20000L)];
            int n = fis.read(buf);
            fis.close();
            f.delete();
            if (n <= 0) return;
            final String text = new String(buf, 0, n, "UTF-8");
            root.postDelayed(new Runnable() {
                public void run() {
                    try {
                        android.widget.TextView tv = new android.widget.TextView(MainActivity.this);
                        tv.setText(text);
                        tv.setTextSize(9);
                        tv.setTextColor(0xFFE2E8F4);
                        tv.setBackgroundColor(0xFF05080E);
                        tv.setPadding(24, 40, 24, 24);
                        tv.setTextIsSelectable(true);
                        android.widget.ScrollView sv = new android.widget.ScrollView(MainActivity.this);
                        sv.addView(tv);
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle("上次启动崩溃了（截图给开发者）")
                                .setView(sv)
                                .setPositiveButton("好", null)
                                .show();
                    } catch (Throwable ignore) {
                    }
                }
            }, 1200);
        } catch (Throwable ignore) {
        }
    }

    /** 开屏：SplashView 负责所有动画；这里只管挂载和收尾 */
    private void buildSplash() {

        try {
            SplashView sv = new SplashView(this, splashMode, getApplicationInfo().icon);
            splashView = sv;
            root.addView(sv, new android.widget.FrameLayout.LayoutParams(-1, -1));
            web.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView v, String url) {
                    if (splashMode == 1 && splashView != null) splashView.handoff(null);
                }
            });
        } catch (Exception e) {
        }
    }

    private static byte[] decodeDataUrl(String dataUrl) {
        try {
            if (dataUrl == null) return null;
            int i = dataUrl.indexOf(',');
            if (i < 0) return null;
            return android.util.Base64.decode(dataUrl.substring(i + 1), android.util.Base64.DEFAULT);
        } catch (Exception e) {
            return null;
        }
    }

    private static String safeName(String name) {
        String n = (name == null || name.length() == 0) ? "report.png" : name;
        n = n.replaceAll("[\\/:*?\"<>|]", "_");
        return n.length() > 60 ? n.substring(n.length() - 60) : n;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        try {
            setIntent(intent);
            if (intent != null && intent.getStringExtra("act") != null) {
                launchAct = intent.getStringExtra("act");
            }
        } catch (Exception e) {
        }
    }

    /** 数据就绪后把「今天 x/y」交给开屏 */
    void splashHandoff(final String text) {
        try {
            if (splashView != null) splashView.handoff(text);
        } catch (Exception e) {
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
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_IMAGE) {
            if (res != RESULT_OK || data == null) {
                runJs("window.onImagePicked&&onImagePicked({err:'cancel'})");
                return;
            }
            Uri src = null;
            try {
                src = data.getData();
                if (src == null) src = (Uri) data.getParcelableExtra(Intent.EXTRA_STREAM);
            } catch (Exception e) {
            }
            if (src == null) {
                runJs("window.onImagePicked&&onImagePicked({err:'cancel'})");
                return;
            }
            final Uri fsrc = src;
            new Thread(new Runnable() {
                public void run() {
                    String json = ImageStore.importImage(MainActivity.this, fsrc);
                    runJs("window.onImagePicked&&onImagePicked(" + json + ")");
                }
            }).start();
            return;
        }
        if (req == REQ_BACKUP) {
            if (res != RESULT_OK || data == null || data.getData() == null) {
                runJs("window.onBackupPicked&&onBackupPicked({err:'cancel'})");
                return;
            }
            final Uri src = data.getData();
            new Thread(new Runnable() {
                public void run() {
                    String r = Backup.read(MainActivity.this, src);
                    runJs("window.onBackupPicked&&onBackupPicked(" + JSONObject.quote(r) + ")");
                }
            }).start();
            return;
        }
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
        runJs(js);
    }

    /** 在 UI 线程执行一段 JS */
    private void runJs(final String code) {
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
        if (code == KeyEvent.KEYCODE_BACK) {
            if (web == null) return super.onKeyDown(code, e);
            if (backBusy) return true;
            backBusy = true;
            try {
                web.evaluateJavascript(
                    "(function(){try{return (window.__back&&window.__back())?'1':'0'}catch(e){return '0'}})()",
                    new ValueCallback<String>() {
                        @Override
                        public void onReceiveValue(String v) {
                            backBusy = false;
                            if (v != null && v.indexOf('1') >= 0) return;   /* 网页自己关掉了一层 */
                            if (web != null && web.canGoBack()) web.goBack();
                            else finish();
                        }
                    });
            } catch (Throwable t) {
                backBusy = false;
                return super.onKeyDown(code, e);
            }
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

        /** 打包备份：tasks.json + 引用的图片，成功返回 true */
        @JavascriptInterface
        public boolean saveBackupZip(String json, String name, String filesJson) {
            try {
                JSONArray a = new JSONArray(filesJson == null ? "[]" : filesJson);
                String[] files = new String[a.length()];
                for (int i = 0; i < a.length(); i++) files[i] = a.optString(i);
                return Backup.writeZip(MainActivity.this, json, name, files);
            } catch (Exception e) {
                return false;
            }
        }

        /** 打开系统图片选择器（Android 13+ 用 Photo Picker，旧版走 OPEN_DOCUMENT，都不需要权限） */
        @JavascriptInterface
        public void pickImage() {
            runOnUiThread(new Runnable() {
                public void run() {
                    try {
                        Intent i;
                        if (Build.VERSION.SDK_INT >= 33) {
                            i = new Intent("android.provider.action.PICK_IMAGES");
                            i.setType("image/*");
                        } else {
                            i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                            i.addCategory(Intent.CATEGORY_OPENABLE);
                            i.setType("image/*");
                        }
                        startActivityForResult(i, REQ_IMAGE);
                    } catch (Exception e) {
                        try {
                            Intent i2 = new Intent(Intent.ACTION_GET_CONTENT);
                            i2.setType("image/*");
                            startActivityForResult(i2, REQ_IMAGE);
                        } catch (Exception e2) {
                            toast("这台手机没有图片选择器");
                        }
                    }
                }
            });
        }

        /** 删掉一张配图（原图 + 缩略图） */
        @JavascriptInterface
        public void deleteImage(String id) {
            ImageStore.delete(MainActivity.this, id);
        }

        /** 图片占用情况 {"count":n,"bytes":x} */
        @JavascriptInterface
        public String imgStats() {
            return ImageStore.stats(MainActivity.this);
        }

        /** 配图的 content:// 前缀，网页侧拿它自己拼 <img src> */
        @JavascriptInterface
        public String imgBase() {
            return "content://" + getPackageName() + ".share/att/";
        }

        /** 兜底：直接把图片读成 data URL（content:// 万一加载失败时用） */
        @JavascriptInterface
        public String imgDataUrl(String id, String ext, boolean thumb) {
            try {
                java.io.File f = thumb
                        ? ImageStore.thumbFor(MainActivity.this, id, ext)
                        : ImageStore.fileFor(MainActivity.this, id, ext);
                if (!f.isFile()) return "";
                byte[] b = new byte[(int) f.length()];
                java.io.FileInputStream in = new java.io.FileInputStream(f);
                try {
                    int off = 0;
                    int n;
                    while (off < b.length && (n = in.read(b, off, b.length - off)) > 0) off += n;
                } finally {
                    try {
                        in.close();
                    } catch (Exception ignored) {
                    }
                }
                String mime = "image/jpeg";
                if ("png".equals(ext)) mime = "image/png";
                else if ("gif".equals(ext)) mime = "image/gif";
                else if ("webp".equals(ext)) mime = "image/webp";
                if (thumb && !"png".equals(ext)) mime = "image/jpeg";
                return "data:" + mime + ";base64,"
                        + android.util.Base64.encodeToString(b, android.util.Base64.NO_WRAP);
            } catch (Throwable t) {
                return "";
            }
        }

        /** 分享某张配图 */
        @JavascriptInterface
        public void shareImageFile(final String id, final String ext, final String name) {
            runOnUiThread(new Runnable() {
                public void run() {
                    try {
                        String fn = "att-" + id + "." + ext;
                        Uri u = ShareProvider.uriForAtt(MainActivity.this, fn);
                        String mime = "image/jpeg";
                        if ("png".equals(ext)) mime = "image/png";
                        else if ("gif".equals(ext)) mime = "image/gif";
                        else if ("webp".equals(ext)) mime = "image/webp";
                        Intent send = new Intent(Intent.ACTION_SEND);
                        send.setType(mime);
                        send.putExtra(Intent.EXTRA_STREAM, u);
                        send.putExtra(Intent.EXTRA_SUBJECT, name == null ? "图片" : name);
                        send.setClipData(android.content.ClipData.newRawUri("img", u));
                        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(Intent.createChooser(send, "分享图片"));
                    } catch (Exception e) {
                    }
                }
            });
        }

        /** 选一个备份文件（json 或含图片的 zip） */
        @JavascriptInterface
        public void pickBackup() {
            runOnUiThread(new Runnable() {
                public void run() {
                    try {
                        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        i.addCategory(Intent.CATEGORY_OPENABLE);
                        i.setType("*/*");
                        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                                "application/zip", "application/x-zip-compressed",
                                "application/json", "text/plain", "application/octet-stream"});
                        startActivityForResult(i, REQ_BACKUP);
                    } catch (Exception e) {
                        toast("这台手机没有文件选择器");
                    }
                }
            });
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

        /** 通知栏/收尾通知按下的操作，取一次就清空 */
        @JavascriptInterface
        public String takeOps() {
            return PendingOps.take(MainActivity.this);
        }

        /** 桌面快捷方式带进来的动作（new / stats），取一次就清空 */
        @JavascriptInterface
        public String takeLaunchAct() {
            String a = launchAct == null ? "" : launchAct;
            launchAct = "";
            return a;
        }

        /** 每天 22:00 的收尾提醒：on=1 开，text 是通知正文 */
        @JavascriptInterface
        public void setWrapup(int on, String text) {
            WrapupReceiver.schedule(MainActivity.this, on == 1, text);
        }

        /** 年度报告长图：dataUrl 是 canvas 导出的 PNG，存缓存后走系统分享 */
        @JavascriptInterface
        public void shareImage(final String dataUrl, final String name) {
            try {
                final byte[] png = decodeDataUrl(dataUrl);
                if (png == null || png.length == 0) return;
                final String fn = safeName(name);
                final java.io.File f = new java.io.File(getCacheDir(), fn);
                java.io.FileOutputStream fos = new java.io.FileOutputStream(f);
                fos.write(png);
                fos.close();
                runOnUiThread(new Runnable() {
                    public void run() {
                        try {
                            Uri u = ShareProvider.uriFor(MainActivity.this, f);
                            Intent send = new Intent(Intent.ACTION_SEND);
                            send.setType("image/png");
                            send.putExtra(Intent.EXTRA_STREAM, u);
                            send.putExtra(Intent.EXTRA_SUBJECT, fn);
                            send.setClipData(android.content.ClipData.newRawUri("report", u));
                            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(Intent.createChooser(send, "分享年度报告"));
                        } catch (Exception e) {
                        }
                    }
                });
            } catch (Exception e) {
            }
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

        /** 系统闹钟类铃声清单，给「快捷选择」用。
         *  注意：必须传 ApplicationContext。传 Activity 的话 RingtoneManager 会
         *  startManagingCursor，游标被 close 后 Activity 每次 restart 都会 requery 它，
         *  直接 StaleDataException 崩溃（v2.3.1 之前的秒退就是这个）。 */
        @JavascriptInterface
        public String ringtoneList() {
            android.database.Cursor cur = null;
            try {
                RingtoneManager rm = new RingtoneManager(getApplicationContext());
                rm.setType(RingtoneManager.TYPE_ALARM);
                cur = rm.getCursor();
                JSONArray arr = new JSONArray();
                while (cur != null && cur.moveToNext() && arr.length() < 20) {
                    Uri u = rm.getRingtoneUri(cur.getPosition());
                    if (u == null) continue;
                    JSONObject o = new JSONObject();
                    o.put("uri", u.toString());
                    o.put("title", ringTitle(u));
                    arr.put(o);
                }
                return arr.toString();
            } catch (Exception e) {
                return "[]";
            } finally {
                try {
                    if (cur != null) cur.close();
                } catch (Exception ignore) {
                }
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
