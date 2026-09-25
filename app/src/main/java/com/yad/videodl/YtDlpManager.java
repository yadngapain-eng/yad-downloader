package com.yad.videodl;

import android.content.Context;
import android.util.Log;

import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import com.yausername.youtubedl_android.YoutubeDLResponse;
import com.yausername.ffmpeg.FFmpeg;

import org.json.JSONObject;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YtDlpManager {

    private static final String TAG = "YtDlpManager";
    private final Context ctx;
    private boolean initialized = false;

    public YtDlpManager(Context ctx) {
        this.ctx = ctx;
    }

    public void install() throws Exception {
        YoutubeDL.getInstance().init(ctx);
        FFmpeg.getInstance().init(ctx);
        initialized = true;
        Log.d(TAG, "youtubedl-android initialized");

        // Auto-update yt-dlp ke versi terbaru (biar support FB/TikTok/IG)
        try {
            updateYtDlp();
        } catch (Exception e) {
            Log.w(TAG, "Auto-update gagal: " + e.getMessage());
            // Tidak fatal — pakai versi bundled
        }
    }

    /**
     * Update yt-dlp ke versi terbaru dari GitHub.
     */
    public void updateYtDlp() throws Exception {
        Log.d(TAG, "Update yt-dlp...");

        YoutubeDLRequest req = new YoutubeDLRequest("https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp");
        // Library ini punya method updateYoutubeDL()
        YoutubeDL.getInstance().updateYoutubeDL(ctx, YoutubeDL.UpdateChannel.STABLE);

        Log.d(TAG, "yt-dlp updated");
    }

    public boolean isReady() {
        return initialized;
    }

    public String getVersion() {
        try {
            String version = YoutubeDL.getInstance().version(ctx);
            if (version == null || version.trim().isEmpty()) {
                return "bundled";
            }
            return version;
        } catch (Exception e) {
            return "bundled";
        }
    }

    public JSONObject getInfo(String url) throws Exception {
        YoutubeDLRequest req = new YoutubeDLRequest(url);
        req.addOption("--dump-json");
        req.addOption("--no-warnings");
        req.addOption("--no-playlist");

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

        if (cb != null) cb.onLog("Starting download...");

        YoutubeDLResponse resp = YoutubeDL.getInstance().execute(req);

        String out = resp.getOut();
        String err = resp.getErr();

        if (cb != null) {
            if (out != null) {
                for (String line : out.split("\n")) {
                    cb.onLog(line);
                }
            }
            if (err != null) {
                for (String line : err.split("\n")) {
                    cb.onLog("ERR: " + line);
                }
            }
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
