package com.lifelog.app;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Vibrator;
import android.os.VibrationEffect;

/**
 * 闹钟铃声播放器。
 * 关键点：铃声在「广播接收器」里就要开始播，不能等界面起来——
 * 因为 App 被杀后小米会拦截后台拉起界面，但拦不住音频播放。
 */
public class RingPlayer {
    private static MediaPlayer mp;
    private static Vibrator vb;

    static synchronized boolean playing() {
        return mp != null;
    }

    static synchronized void start(Context c) {
        if (mp != null) {
            try {
                if (mp.isPlaying()) return;
            } catch (Exception e) {
            }
            stop();
        }
        try {
            Uri u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (u == null) u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            if (u == null) u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            MediaPlayer p = new MediaPlayer();
            AudioAttributes aa = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            p.setAudioAttributes(aa);
            p.setDataSource(c.getApplicationContext(), u);
            p.setLooping(true);
            p.prepare();
            p.start();
            mp = p;
        } catch (Exception e) {
            try {
                Uri u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                mp = MediaPlayer.create(c.getApplicationContext(), u);
                if (mp != null) {
                    mp.setLooping(true);
                    mp.start();
                }
            } catch (Exception e2) {
            }
        }
        try {
            vb = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
            long[] pat = new long[]{0, 400, 250, 400, 250, 700};
            if (Build.VERSION.SDK_INT >= 26) {
                vb.vibrate(VibrationEffect.createWaveform(pat, 0));
            } else {
                vb.vibrate(pat, 0);
            }
        } catch (Exception e) {
        }
    }

    static synchronized void stop() {
        try {
            if (mp != null) {
                if (mp.isPlaying()) mp.stop();
                mp.release();
            }
        } catch (Exception e) {
        }
        mp = null;
        try {
            if (vb != null) vb.cancel();
        } catch (Exception e) {
        }
        vb = null;
    }
}
