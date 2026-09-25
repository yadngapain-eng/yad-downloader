package com.yad.videodl;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class BinaryInstaller {
    private static final String TAG = "BinaryInstaller";

    public static File getBinDir(Context ctx) {
        File dir = new File(ctx.getFilesDir(), "bin");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static File installBinary(Context ctx, String assetName, String outName) throws Exception {
        File binDir = getBinDir(ctx);
        File outFile = new File(binDir, outName);

        if (outFile.exists() && outFile.length() > 100000) {
            Log.d(TAG, outName + " sudah ada (" + outFile.length() + " bytes)");
            setExecutable(outFile);
            return outFile;
        }

        AssetManager am = ctx.getAssets();
        InputStream in = am.open(assetName);
        OutputStream out = new FileOutputStream(outFile);

        byte[] buf = new byte[8192];
        int n;
        long total = 0;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
            total += n;
        }
        in.close();
        out.close();

        Log.d(TAG, outName + " extracted (" + total + " bytes)");
        setExecutable(outFile);
        return outFile;
    }

    public static void setExecutable(File file) {
        try {
            file.setExecutable(true, false);
            file.setReadable(true, false);
            Runtime.getRuntime().exec("chmod 755 " + file.getAbsolutePath());
        } catch (Exception e) {
            Log.w(TAG, "chmod failed: " + e.getMessage());
        }
    }

    public static File getYtDlp(Context ctx) {
        return new File(getBinDir(ctx), "yt-dlp");
    }

    public static boolean isReady(Context ctx) {
        File f = getYtDlp(ctx);
        return f.exists() && f.length() > 100000;
    }
}
