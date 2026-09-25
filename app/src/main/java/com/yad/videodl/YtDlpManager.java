package com.yad.videodl;

import android.content.Context;
import android.util.Log;

import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import com.yausername.youtubedl_android.YoutubeDLResponse;
import com.yausername.ffmpeg.FFmpeg;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class YtDlpManager {

    private static final String TAG = "YtDlpManager";
    private static final String COOKIES_FILENAME = "cookies.txt";

    private final Context ctx;
    private final File cookiesFile;
    private boolean initialized = false;

    public YtDlpManager(Context ctx) {
        this.ctx = ctx;
        this.cookiesFile = new File(ctx.getFilesDir(), COOKIES_FILENAME);
    }

    public void install() throws Exception {
        YoutubeDL.getInstance().init(ctx);
        FFmpeg.getInstance().init(ctx);

        // Extract cookies.txt dari assets kalau ada
        extractCookiesFromAssets();

        // Auto-update yt-dlp
        try {
            Log.d(TAG, "Updating yt-dlp...");
            YoutubeDL.getInstance().updateYoutubeDL(ctx, YoutubeDL.UpdateChannel._STABLE);
            Log.d(TAG, "yt-dlp updated");
        } catch (Exception e) {
            Log.w(TAG, "Update gagal, pakai bundled: " + e.getMessage());
        }

        initialized = true;
    }

    private void extractCookiesFromAssets() {
        try {
            InputStream in = ctx.getAssets().open("cookies.txt");
            FileOutputStream out = new FileOutputStream(cookiesFile);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close();
            out.close();
            Log.d(TAG, "Cookies loaded: " + cookiesFile.length() + " bytes");
        } catch (Exception e) {
            Log.d(TAG, "No cookies.txt in assets");
        }
    }

    public boolean isReady() {
        return initialized;
    }

    public boolean hasCookies() {
        return cookiesFile.exists() && cookiesFile.length() > 0;
    }

    public String getVersion() {
        try {
            String v = YoutubeDL.getInstance().version(ctx);
            return (v == null || v.isEmpty()) ? "bundled" : v;
        } catch (Exception e) {
            return "bundled";
        }
    }

    public JSONObject getInfo(String url) throws Exception {
        YoutubeDLRequest req = new YoutubeDLRequest(url);
        req.addOption("--dump-json");
        req.addOption("--no-warnings");
        req.addOption("--no-playlist");
        req.addOption("--no-check-certificate");

        if (hasCookies()) {
            req.addOption("--cookies", cookiesFile.getAbsolutePath());
        }

        YoutubeDLResponse resp = YoutubeDL.getInstance().execute(req);
        String out = resp.getOut();

        if (out == null || out.trim().isEmpty()) {
            throw new Exception("No output from yt-dlp");
        }

        String[] lines = out.trim().split("\n");
        return new JSONObject(lines[lines.length - 1]);
    }

    public String download(String url, String quality, File outputDir, DownloadCallback cb) throws Exception {
        if (!outputDir.exists()) outputDir.mkdirs();

        String format;
        switch (quality == null ? "" : quality) {
            case "audio": format = "bestaudio[ext=m4a]/bestaudio"; break;
            case "1080":  format = "best[height<=1080][ext=mp4]/best"; break;
            case "720":   format = "best[height<=720][ext=mp4]/best"; break;
            case "480":   format = "best[height<=480][ext=mp4]/best"; break;
            default:      format = "best[ext=mp4]/best";
        }

        String outputTemplate = new File(outputDir, "%(title).80s.%(ext)s").getAbsolutePath();

        YoutubeDLRequest req = new YoutubeDLRequest(url);
        req.addOption("-f", format);
        req.addOption("-o", outputTemplate);
        req.addOption("--no-warnings");
        req.addOption("--no-playlist");
        req.addOption("--no-check-certificate");
        req.addOption("--user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

        // Pakai cookies kalau ada
        if (hasCookies()) {
            req.addOption("--cookies", cookiesFile.getAbsolutePath());
            if (cb != null) cb.onLog("Using cookies: " + cookiesFile.getAbsolutePath());
        } else {
            if (cb != null) cb.onLog("No cookies - some videos may require login");
        }

        if (cb != null) cb.onLog("Starting download...");

        YoutubeDLResponse resp = YoutubeDL.getInstance().execute(req);

        String out = resp.getOut();
        String err = resp.getErr();

        if (cb != null) {
            if (out != null) for (String line : out.split("\n")) cb.onLog(line);
            if (err != null) for (String line : err.split("\n")) cb.onLog("ERR: " + line);
        }

        File[] files = outputDir.listFiles();
        if (files != null && files.length > 0) {
            File newest = files[0];
            for (File f : files) {
                if (f.lastModified() > newest.lastModified()) newest = f;
            }
            return newest.getAbsolutePath();
        }

        return null;
    }

    public interface DownloadCallback {
        void onProgress(float percent, String rawLine);
        void onLog(String line);
    }
}
