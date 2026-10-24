package com.lifelog.app;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** 闹钟式提醒：亮屏、覆盖锁屏、循环响铃、震动，必须手动关掉 */
public class AlarmActivity extends Activity {
    private MediaPlayer mp;
    private Vibrator vb;
    private String title = "";
    private String body = "";
    private String taskId = "";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        title = getIntent().getStringExtra("title");
        body = getIntent().getStringExtra("body");
        taskId = getIntent().getStringExtra("id");
        if (title == null) title = "待办提醒";
        if (body == null) body = "";

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }

        setContentView(buildUi());
        startRing();
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.rgb(12, 13, 17));
        int pad = dp(26);
        root.setPadding(pad, pad, pad, pad);

        TextView small = new TextView(this);
        small.setText("待办提醒");
        small.setTextColor(Color.rgb(120, 130, 150));
        small.setTextSize(14);
        root.addView(small);

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(Color.WHITE);
        t.setTextSize(26);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(18), 0, dp(10));
        root.addView(t);

        if (body.length() > 0) {
            TextView s = new TextView(this);
            s.setText(body);
            s.setTextColor(Color.rgb(160, 168, 182));
            s.setTextSize(15);
            s.setGravity(Gravity.CENTER);
            root.addView(s);
        }

        Button close = new Button(this);
        close.setText("关掉闹钟");
        close.setTextSize(17);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(58));
        lp.topMargin = dp(40);
        close.setLayoutParams(lp);
        close.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                stopRing();
                finish();
            }
        });
        root.addView(close);

        Button snooze = new Button(this);
        snooze.setText("10 分钟后再提醒");
        snooze.setTextSize(17);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        lp2.topMargin = dp(12);
        snooze.setLayoutParams(lp2);
        snooze.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                snoozeTen();
            }
        });
        root.addView(snooze);

        Button open = new Button(this);
        open.setText("去 App 里勾掉");
        open.setTextSize(15);
        LinearLayout.LayoutParams lp3 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        lp3.topMargin = dp(12);
        open.setLayoutParams(lp3);
        open.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                stopRing();
                startActivity(new Intent(AlarmActivity.this, MainActivity.class));
                finish();
            }
        });
        root.addView(open);
        return root;
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }

    private void startRing() {
        RingPlayer.start(getApplicationContext());
    }

    private void stopRing() {
        RingPlayer.stop();
        try {
            ((android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancelAll();
        } catch (Exception e) {
        }
    }

    @Override
    protected void onDestroy() {
        stopRing();
        super.onDestroy();
    }

    private void snoozeTen() {
        try {
            long at = System.currentTimeMillis() + 10 * 60 * 1000;
            Intent it = new Intent(this, ReminderReceiver.class);
            it.putExtra("id", taskId);
            it.putExtra("title", title);
            it.putExtra("body", body);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
            PendingIntent pi = PendingIntent.getBroadcast(
                    this, (taskId == null ? "snooze" : taskId).hashCode() + 7, it, flags);
            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            }
            Toast.makeText(this, "10 分钟后再提醒你", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
        }
        stopRing();
        finish();
    }
}
