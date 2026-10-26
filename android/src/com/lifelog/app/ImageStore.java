package com.lifelog.app;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.provider.OpenableColumns;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.UUID;

/**
 * 任务配图：导入（校验 / 按 EXIF 转正 / 压缩 / 缩略图）、删除、统计。
 * 图片只写 App 私有目录 filesDir/attachments/，网页侧只拿到 id，不碰 base64。
 */
public class ImageStore {
    static final String DIR = "attachments";
    static final long MAX_SRC = 10L * 1024 * 1024;   // 源文件上限 10MB
    static final int MAX_EDGE = 1600;                 // 存下来的长边
    static final int THUMB_EDGE = 300;                // 列表缩略图长边
    static final int MAX_AREA = 60 * 1000 * 1000;     // 6000 万像素，防解压炸弹

    static File dir(Context c) {
        File d = new File(c.getFilesDir(), DIR);
        if (!d.exists()) d.mkdirs();
        return d;
    }

    static File fileFor(Context c, String id, String ext) {
        return new File(dir(c), "att-" + id + "." + ext);
    }

    static File thumbFor(Context c, String id, String ext) {
        return new File(dir(c), "att-" + id + ".thumb." + ("png".equals(ext) ? "png" : "jpg"));
    }

    static String newId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /** 导入一张图，返回 JSON；失败时带 err 字段（large / decode / empty / write） */
    static String importImage(Context c, Uri uri) {
        JSONObject out = new JSONObject();
        File tmp = null;
        try {
            String mime = "";
            try {
                String m = c.getContentResolver().getType(uri);
                if (m != null) mime = m.toLowerCase();
            } catch (Exception ignored) {
            }
            String name = queryName(c, uri);
            String lower = name.toLowerCase();

            tmp = File.createTempFile("llimg", ".tmp", c.getCacheDir());
            long total = copy(c, uri, tmp);
            if (total < 0) {
                out.put("err", "large");
                out.put("bytes", -total);
                return out.toString();
            }
            if (total == 0) {
                out.put("err", "empty");
                return out.toString();
            }

            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(tmp.getAbsolutePath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                out.put("err", "decode");
                return out.toString();
            }
            long area = (long) bounds.outWidth * bounds.outHeight;
            if (area > MAX_AREA) {
                out.put("err", "decode");
                return out.toString();
            }

            String id = newId();
            int w, h;
            long bytes;
            String ext;

            boolean gif = mime.contains("gif") || lower.endsWith(".gif");
            if (gif) {
                /* 动图不重编码，原样存，浏览器只是按 content:// 放 */
                File f = fileFor(c, id, "gif");
                if (!tmp.renameTo(f)) {
                    copyFile(tmp, f);
                    tmp.delete();
                }
                ext = "gif";
                w = bounds.outWidth;
                h = bounds.outHeight;
                bytes = f.length();
                Bitmap first = decodeSampled(f.getAbsolutePath(), 1200);
                saveThumb(c, id, ext, first);
                if (first != null) first.recycle();
            } else {
                Bitmap bmp = decodeSampled(tmp.getAbsolutePath(), MAX_EDGE * 2);
                if (bmp == null) {
                    out.put("err", "decode");
                    return out.toString();
                }
                bmp = rotateByExif(tmp.getAbsolutePath(), bmp);
                bmp = scaleTo(bmp, MAX_EDGE);
                boolean alpha = bmp.hasAlpha()
                        && (mime.contains("png") || mime.contains("webp")
                        || lower.endsWith(".png") || lower.endsWith(".webp"));
                ext = alpha ? "png" : "jpg";
                File f = fileFor(c, id, ext);
                FileOutputStream fo = new FileOutputStream(f);
                bmp.compress(alpha ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG,
                        alpha ? 100 : 85, fo);
                fo.flush();
                fo.close();
                w = bmp.getWidth();
                h = bmp.getHeight();
                bytes = f.length();
                saveThumb(c, id, ext, bmp);
                bmp.recycle();
            }

            /* 压完还超 10MB（极少见，比如超大 PNG）—— 直接拒，别让备份以后爆掉 */
            if (bytes > MAX_SRC) {
                delete(c, id);
                out.put("err", "large");
                out.put("bytes", bytes);
                return out.toString();
            }

            out.put("id", id);
            out.put("ext", ext);
            out.put("w", w);
            out.put("h", h);
            out.put("bytes", bytes);
            out.put("name", name.length() > 80 ? name.substring(0, 80) : name);
            return out.toString();
        } catch (Throwable t) {
            try {
                out.put("err", "write");
            } catch (Exception ignored) {
            }
            return out.toString();
        } finally {
            if (tmp != null) tmp.delete();
        }
    }

    static void delete(Context c, String id) {
        if (id == null || id.length() == 0) return;
        File[] fs = dir(c).listFiles();
        if (fs == null) return;
        String prefix = "att-" + id + ".";
        for (File f : fs) {
            if (f.getName().startsWith(prefix)) f.delete();
        }
    }

    static String stats(Context c) {
        JSONObject o = new JSONObject();
        int n = 0;
        long bytes = 0;
        File[] fs = dir(c).listFiles();
        if (fs != null) {
            for (File f : fs) {
                if (f.getName().indexOf(".thumb.") >= 0) continue;
                n++;
                bytes += f.length();
            }
        }
        try {
            o.put("count", n);
            o.put("bytes", bytes);
        } catch (Exception ignored) {
        }
        return o.toString();
    }

    static String queryName(Context c, Uri uri) {
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(uri,
                    new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cur != null && cur.moveToFirst()) {
                String n = cur.getString(0);
                if (n != null && n.length() > 0) return n;
            }
        } catch (Exception ignored) {
        } finally {
            if (cur != null) try {
                cur.close();
            } catch (Exception ignored) {
            }
        }
        return "图片";
    }

    /** 拷到临时文件，返回字节数；超过上限返回负数 */
    static long copy(Context c, Uri uri, File out) throws Exception {
        InputStream in = c.getContentResolver().openInputStream(uri);
        if (in == null) throw new Exception("open failed");
        FileOutputStream fo = new FileOutputStream(out);
        byte[] buf = new byte[64 * 1024];
        long total = 0;
        try {
            int n;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > MAX_SRC) return -total;
                fo.write(buf, 0, n);
            }
        } finally {
            try {
                in.close();
            } catch (Exception ignored) {
            }
            try {
                fo.flush();
                fo.close();
            } catch (Exception ignored) {
            }
        }
        return total;
    }

    static void copyFile(File src, File dst) throws Exception {
        InputStream in = new java.io.FileInputStream(src);
        FileOutputStream out = new FileOutputStream(dst);
        byte[] buf = new byte[64 * 1024];
        try {
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } finally {
            try {
                in.close();
            } catch (Exception ignored) {
            }
            try {
                out.flush();
                out.close();
            } catch (Exception ignored) {
            }
        }
    }

    static Bitmap decodeSampled(String path, int maxEdge) {
        try {
            BitmapFactory.Options b = new BitmapFactory.Options();
            b.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, b);
            if (b.outWidth <= 0 || b.outHeight <= 0) return null;
            int sample = 1;
            if (maxEdge > 0) {
                int m = Math.max(b.outWidth, b.outHeight);
                while (m / sample > maxEdge * 2) sample *= 2;
            }
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            o.inPreferredConfig = Bitmap.Config.ARGB_8888;
            return BitmapFactory.decodeFile(path, o);
        } catch (Throwable t) {
            return null;
        }
    }

    static Bitmap rotateByExif(String path, Bitmap b) {
        if (Build.VERSION.SDK_INT < 24) return b;
        try {
            ExifInterface ex = new ExifInterface(path);
            int o = ex.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);
            int deg = 0;
            if (o == ExifInterface.ORIENTATION_ROTATE_90) deg = 90;
            else if (o == ExifInterface.ORIENTATION_ROTATE_180) deg = 180;
            else if (o == ExifInterface.ORIENTATION_ROTATE_270) deg = 270;
            if (deg == 0) return b;
            Matrix m = new Matrix();
            m.postRotate(deg);
            Bitmap r = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
            if (r != b) b.recycle();
            return r;
        } catch (Throwable t) {
            return b;
        }
    }

    static Bitmap scaleTo(Bitmap b, int maxEdge) {
        int m = Math.max(b.getWidth(), b.getHeight());
        if (m <= maxEdge) return b;
        float s = (float) maxEdge / m;
        int w = Math.max(1, Math.round(b.getWidth() * s));
        int h = Math.max(1, Math.round(b.getHeight() * s));
        try {
            Bitmap r = Bitmap.createScaledBitmap(b, w, h, true);
            if (r != b) b.recycle();
            return r;
        } catch (Throwable t) {
            return b;
        }
    }

    static void saveThumb(Context c, String id, String ext, Bitmap src) {
        if (src == null) return;
        try {
            int m = Math.max(src.getWidth(), src.getHeight());
            float s = m > THUMB_EDGE ? (float) THUMB_EDGE / m : 1f;
            int tw = Math.max(1, Math.round(src.getWidth() * s));
            int th = Math.max(1, Math.round(src.getHeight() * s));
            Bitmap t = Bitmap.createScaledBitmap(src, tw, th, true);
            File f = thumbFor(c, id, ext);
            FileOutputStream fo = new FileOutputStream(f);
            if ("png".equals(ext)) t.compress(Bitmap.CompressFormat.PNG, 100, fo);
            else t.compress(Bitmap.CompressFormat.JPEG, 80, fo);
            fo.flush();
            fo.close();
            if (t != src) t.recycle();
        } catch (Throwable ignored) {
        }
    }
}
