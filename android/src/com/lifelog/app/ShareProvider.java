package com.lifelog.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;

/**
 * 极简 ContentProvider：只把 App 缓存目录里的文件按只读方式暴露给分享目标。
 * 没有 AndroidX，所以自己写一个，替代 FileProvider。
 */
public class ShareProvider extends ContentProvider {
    static Uri uriFor(Context c, File f) {
        return new Uri.Builder()
                .scheme("content")
                .authority(c.getPackageName() + ".share")
                .appendPath(f.getName())
                .build();
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws java.io.FileNotFoundException {
        try {
            String name = uri.getLastPathSegment();
            File f = new File(getContext().getCacheDir(), name);
            if (!f.getCanonicalPath().startsWith(getContext().getCacheDir().getCanonicalPath())) {
                throw new java.io.FileNotFoundException("bad path");
            }
            return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
        } catch (java.io.IOException e) {
            throw new java.io.FileNotFoundException(e.getMessage());
        }
    }

    @Override
    public String getType(Uri uri) {
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
