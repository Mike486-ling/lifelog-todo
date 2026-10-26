package com.lifelog.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;

/**
 * 极简 ContentProvider：把 App 缓存目录里的文件、以及任务配图（私有 attachments 目录）
 * 按只读方式暴露出去（分享 / WebView 里 <img src="content://...">）。
 * 没有 AndroidX，所以自己写一个，替代 FileProvider。
 *
 * content://<pkg>.share/<name>       → cacheDir/<name>                 （分享长图）
 * content://<pkg>.share/att/<name>   → filesDir/attachments/<name>     （任务配图）
 */
public class ShareProvider extends ContentProvider {
    static Uri uriFor(Context c, File f) {
        return new Uri.Builder()
                .scheme("content")
                .authority(c.getPackageName() + ".share")
                .appendPath(f.getName())
                .build();
    }

    /** 任务配图的 content:// 地址，网页侧直接用来做 <img src> */
    static Uri uriForAtt(Context c, String fileName) {
        return new Uri.Builder()
                .scheme("content")
                .authority(c.getPackageName() + ".share")
                .appendPath("att")
                .appendPath(fileName)
                .build();
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws java.io.FileNotFoundException {
        try {
            java.util.List<String> segs = uri.getPathSegments();
            File f;
            if (segs != null && segs.size() == 2 && "att".equals(segs.get(0))) {
                String name = segs.get(1);
                if (!isAttName(name)) throw new java.io.FileNotFoundException("bad name");
                File dir = new File(getContext().getFilesDir(), ImageStore.DIR);
                f = new File(dir, name);
                if (!f.getCanonicalPath().startsWith(dir.getCanonicalPath() + File.separator)) {
                    throw new java.io.FileNotFoundException("bad path");
                }
            } else {
                String name = uri.getLastPathSegment();
                f = new File(getContext().getCacheDir(), name);
                if (!f.getCanonicalPath().startsWith(getContext().getCacheDir().getCanonicalPath())) {
                    throw new java.io.FileNotFoundException("bad path");
                }
            }
            return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
        } catch (java.io.IOException e) {
            throw new java.io.FileNotFoundException(e.getMessage());
        }
    }

    /** 只认 att-<16 位 hex> 或 att-<16 位>.thumb + 白名单扩展名 */
    private static boolean isAttName(String name) {
        if (name == null) return false;
        return name.matches("att-[a-f0-9]{16}(\\.thumb)?\\.(jpg|png|gif|webp)$");
    }

    @Override
    public String getType(Uri uri) {
        String p = uri.getLastPathSegment();
        if (p != null) {
            String l = p.toLowerCase();
            if (l.endsWith(".jpg") || l.endsWith(".jpeg")) return "image/jpeg";
            if (l.endsWith(".png")) return "image/png";
            if (l.endsWith(".gif")) return "image/gif";
            if (l.endsWith(".webp")) return "image/webp";
        }
        return "image/png";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] args) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] args) {
        return 0;
    }
}
