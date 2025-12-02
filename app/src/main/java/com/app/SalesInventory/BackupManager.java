package com.app.SalesInventory;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class BackupManager {

    private static final String DB_NAME = "sales_inventory_db";

    public static File getDatabaseFile(Context context) {
        return context.getDatabasePath(DB_NAME);
    }

    public static boolean exportDatabase(Context context, Uri destUri) {
        File dbFile = getDatabaseFile(context);
        if (dbFile == null || !dbFile.exists()) return false;
        ContentResolver resolver = context.getContentResolver();
        try (ParcelFileDescriptor pfd = resolver.openFileDescriptor(destUri, "w");
             FileInputStream in = new FileInputStream(dbFile);
             FileOutputStream out = new FileOutputStream(pfd.getFileDescriptor())) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
            out.flush();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean importDatabase(Context context, Uri srcUri) {
        File dbFile = getDatabaseFile(context);
        if (dbFile == null) return false;
        File parent = dbFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        ContentResolver resolver = context.getContentResolver();
        try (ParcelFileDescriptor pfd = resolver.openFileDescriptor(srcUri, "r");
             InputStream in = new FileInputStream(pfd.getFileDescriptor());
             OutputStream out = new FileOutputStream(dbFile, false)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
            out.flush();
            AppDatabase.resetInstance();
            AppDatabase.getInstance(context.getApplicationContext());
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}