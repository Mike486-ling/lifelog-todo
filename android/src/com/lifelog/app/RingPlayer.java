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
 *
 * 支持按 URI 播放（任务里存的铃声），URI 失效时逐级退回系统默认音，
 * 绝不允许出现「闹钟不响」。
 */
public class RingPlayer {
    private static MediaPlayer mp;
    private static Vibrator vb;
    /** 试听用，跟闹钟播放分开，避免互相打断 */
    private static MediaPlayer preview;

    static synchronized boolean playing() {
        return mp != null;
    }

    static synchronized void start(Context c) {
        start(c, null);
    }

    static synchronized void start(Context c, String uriStr) {
        if (mp != null) {
            try {
                if (mp.isPlaying()) return;
            } catch (Exception e) {
            }
            stop();
        }
        boolean ok = false;
        if (uriStr != null && uriStr.length() > 0) {
            ok = play(c, Uri.parse(uriStr));
        }
        if (!ok) ok = play(c, defaultUri());
        if (!ok) ok = play(c, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
        if (!ok) ok = fallback(c);

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

    private static Uri defaultUri() {
        Uri u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (u == null) u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        return u;
    }

    private static boolean play(Context c, Uri u) {
        if (u == null) return false;
        try {
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
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean fallback(Context c) {
        try {
            Uri u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (u == null) return false;
            mp = MediaPlayer.create(c.getApplicationContext(), u);
            if (mp != null) {
                mp.setLooping(true);
                mp.start();
                return true;
            }
        } catch (Exception e) {
        }
        return false;
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

    /** 试听 3 秒，不进闹钟主链路 */
    static synchronized void preview(Context c, String uriStr) {
        stopPreview();
        Uri u = (uriStr == null || uriStr.length() == 0) ? defaultUri() : Uri.parse(uriStr);
        try {
            MediaPlayer p = new MediaPlayer();
            AudioAttributes aa = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            p.setAudioAttributes(aa);
            p.setDataSource(c.getApplicationContext(), u);
            p.setLooping(false);
            p.prepare();
            p.start();
            preview = p;
        } catch (Exception e) {
            try {
                if (u != null) {
                    preview = MediaPlayer.create(c.getApplicationContext(), u);
                    if (preview != null) preview.start();
                }
            } catch (Exception e2) {
            }
        }
    }

    static synchronized void stopPreview() {
        try {
            if (preview != null) {
                if (preview.isPlaying()) preview.stop();
                preview.release();
            }
        } catch (Exception e) {
        }
        preview = null;
    }
}
