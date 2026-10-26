package com.lifelog.app;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** 备份：普通 JSON，或「任务 + 配图」的 zip，都写到用户看得见的「下载/今日待办备份/」 */
public class Backup {
    static final String FOLDER = "今日待办备份";

    static boolean write(Context c, String json, String name) {
        if (json == null || json.length() == 0) return false;
        if (name == null || name.length() == 0) name = "任务备份.json";
        try {
            byte[] raw = json.getBytes("UTF-8");
            return put(c, new ByteArrayInputStream(raw), name, "application/json");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 打包 zip：tasks.json + images/<att-xxx.jpg>（含缩略图）
     *
     * @param files attachments 目录下的文件名数组，null / 空表示只打包 JSON
     */
    static boolean writeZip(Context c, String json, String name, String[] files) {
        if (json == null || json.length() == 0) return false;
        if (name == null || name.length() == 0) name = "任务备份.zip";
        File tmp = null;
        try {
            tmp = File.createTempFile("llbak", ".zip", c.getCacheDir());
            File dir = new File(c.getFilesDir(), ImageStore.DIR);
            ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(tmp));
            zos.putNextEntry(new ZipEntry("tasks.json"));
            zos.write(json.getBytes("UTF-8"));
            zos.closeEntry();
            if (files != null) {
                for (int i = 0; i < files.length; i++) {
                    String fn = files[i];
                    if (fn == null || fn.length() == 0) continue;
                    File f = new File(dir, fn);
                    if (!f.isFile()) continue;
                    zos.putNextEntry(new ZipEntry("images/" + fn));
                    FileInputStream in = new FileInputStream(f);
                    byte[] buf = new byte[64 * 1024];
                    try {
                        int n;
                        while ((n = in.read(buf)) > 0) zos.write(buf, 0, n);
                    } finally {
                        try {
                            in.close();
                        } catch (Exception ignored) {
                        }
                    }
                    zos.closeEntry();
                }
            }
            zos.finish();
            zos.close();
            FileInputStream fin = new FileInputStream(tmp);
            try {
                return put(c, fin, name, "application/zip");
            } finally {
                try {
                    fin.close();
                } catch (Exception ignored) {
                }
            }
        } catch (Throwable t) {
            return false;
        } finally {
            if (tmp != null) tmp.delete();
        }
    }

    /**
     * 读一个备份文件（zip 或 json），zip 里的图片直接落到 attachments 目录。
     * 返回 {"json":"…","images":3} 或 {"err":"…"}
     */
    static String read(Context c, Uri uri) {
        JSONObject out = new JSONObject();
        InputStream raw = null;
        try {
            raw = c.getContentResolver().openInputStream(uri);
            if (raw == null) {
                out.put("err", "open");
                return out.toString();
            }
            BufferedInputStream in = new BufferedInputStream(raw);
            in.mark(4);
            int b0 = in.read();
            int b1 = in.read();
            in.reset();
            boolean zip = (b0 == 'P' && b1 == 'K');
            if (!zip) {
                out.put("json", readText(in));
                out.put("images", 0);
                return out.toString();
            }
            File dir = new File(c.getFilesDir(), ImageStore.DIR);
            if (!dir.exists()) dir.mkdirs();
            String json = null;
            int images = 0;
            ZipInputStream zis = new ZipInputStream(in);
            try {
                ZipEntry e;
                byte[] buf = new byte[64 * 1024];
                while ((e = zis.getNextEntry()) != null) {
                    String entry = e.getName();
                    File target = null;
                    if ("tasks.json".equals(entry)) {
                        json = readText(zis);
                    } else if (entry.startsWith("images/")) {
                        String fn = entry.substring("images/".length());
                        if (!fn.matches("att-[a-f0-9]{16}(\\.thumb)?\\.(jpg|png|gif|webp)")) continue;
                        target = new File(dir, fn);
                        FileOutputStream fo = new FileOutputStream(target);
                        try {
                            int n;
                            while ((n = zis.read(buf)) > 0) fo.write(buf, 0, n);
                        } finally {
                            try {
                                fo.flush();
                                fo.close();
                            } catch (Exception ignored) {
                            }
                        }
                        if (fn.indexOf(".thumb.") < 0) images++;
                    }
                }
            } finally {
                try {
                    zis.close();
                } catch (Exception ignored) {
                }
            }
            if (json == null) {
                out.put("err", "nojson");
                return out.toString();
            }
            out.put("json", json);
            out.put("images", images);
            return out.toString();
        } catch (Throwable t) {
            try {
                out.put("err", "read");
            } catch (Exception ignored) {
            }
            return out.toString();
        } finally {
            if (raw != null) try {
                raw.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static String readText(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[64 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        return bo.toString("UTF-8");
    }

    private static boolean put(Context c, InputStream src, String name, String mime) {
        if (Build.VERSION.SDK_INT >= 29) return putMediaStore(c, src, name, mime);
        return putFilesDir(c, src, name);
    }

    /** Android 10+：MediaStore，不需要任何存储权限 */
    private static boolean putMediaStore(Context c, InputStream src, String name, String mime) {
        Uri uri = null;
        try {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            cv.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            cv.put(MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + File.separator + FOLDER);
            cv.put(MediaStore.MediaColumns.IS_PENDING, 1);
            uri = c.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
            if (uri == null) return false;
            OutputStream os = c.getContentResolver().openOutputStream(uri);
            if (os == null) return false;
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = src.read(buf)) > 0) os.write(buf, 0, n);
            os.flush();
            os.close();
            cv.clear();
            cv.put(MediaStore.MediaColumns.IS_PENDING, 0);
            c.getContentResolver().update(uri, cv, null, null);
            return true;
        } catch (Throwable t) {
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
    private static boolean putFilesDir(Context c, InputStream src, String name) {
        try {
            File dir = new File(c.getExternalFilesDir(null), FOLDER);
            if (!dir.exists() && !dir.mkdirs()) return false;
            OutputStream os = new FileOutputStream(new File(dir, name));
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = src.read(buf)) > 0) os.write(buf, 0, n);
            os.flush();
            os.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
