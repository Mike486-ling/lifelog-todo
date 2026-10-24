package com.lifelog.app;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/** 把 JSON 备份写到用户看得见的「下载/今日待办备份/」 */
public class Backup {
    static final String FOLDER = "今日待办备份";

    static boolean write(Context c, String json, String name) {
        if (json == null || json.length() == 0) return false;
        if (name == null || name.length() == 0) name = "任务备份.json";
        if (Build.VERSION.SDK_INT >= 29) {
            return writeMediaStore(c, json, name);
        }
        return writeFilesDir(c, json, name);
    }

    /** Android 10+：MediaStore，不需要任何存储权限 */
    private static boolean writeMediaStore(Context c, String json, String name) {
        Uri uri = null;
        try {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            cv.put(MediaStore.MediaColumns.MIME_TYPE, "application/json");
            cv.put(MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + File.separator + FOLDER);
            cv.put(MediaStore.MediaColumns.IS_PENDING, 1);
            uri = c.getContentResolver().insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
            if (uri == null) return false;
            OutputStream os = c.getContentResolver().openOutputStream(uri);
            if (os == null) return false;
            os.write(json.getBytes("UTF-8"));
            os.flush();
            os.close();
            cv.clear();
            cv.put(MediaStore.MediaColumns.IS_PENDING, 0);
            c.getContentResolver().update(uri, cv, null, null);
            return true;
        } catch (Exception e) {
            if (uri != null) {
                try {
                    c.getContentResolver().delete(uri, null, null);
                } catch (Exception ignored) {
                }
            }
            return false;
        }
    }

    /** Android 9 及以下：没有免权限的公共目录，退回 App 专属目录 */
    private static boolean writeFilesDir(Context c, String json, String name) {
        try {
            File dir = new File(c.getExternalFilesDir(null), FOLDER);
            if (!dir.exists() && !dir.mkdirs()) return false;
            FileOutputStream fo = new FileOutputStream(new File(dir, name));
            fo.write(json.getBytes("UTF-8"));
            fo.flush();
            fo.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
