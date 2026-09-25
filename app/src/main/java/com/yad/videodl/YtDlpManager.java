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

/**
 * Wrapper yt-dlp pakai youtubedl-android (bundled Python).
 * Support YouTube, Facebook, TikTok, IG, dll.
 */
public class YtDlpManager {

    private static final String TAG = "YtDlpManager";
    private final Context ctx;
    private boolean initialized = false;

    public YtDlpManager(Context ctx) {
        this.ctx = ctx;
    }

    /**
     * Init library. WAJIB dipanggil di background thread.
     */
    public void install() throws Exception {
        YoutubeDL.getInstance().init(ctx);
        FFmpeg.getInstance().init(ctx);
        initialized = true;
        Log.d(TAG, "youtubedl-android initialized");
    }

    public boolean isReady() {
        return initialized;
    }

    public String getVersion() {
        try {
            return YoutubeDL.getInstance().version(ctx);
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * Ambil info video (format JSON).
     */
    public JSONObject getInfo(String url) throws Exception {
        YoutubeDLRequest req = new YoutubeDLRequest(url);
        req.addOption("--dump-json");
        req.addOption("--no-warnings");
        req.addOption("--no-playlist");

        YoutubeDLResponse resp = YoutubeDL.getInstance().execute(req, null, null);
        String out = resp.getOut();

        if (out == null || out.trim().isEmpty()) {
            throw new Exception("No output from yt-dlp");
        }

        // Ambil baris JSON terakhir
        String[] lines = out.trim().split("\n");
        return new JSONObject(lines[lines.length - 1]);
    }

    /**
     * Download video. Return path file hasil.
     */
    public String download(String url, String quality, File outputDir, DownloadCallback cb) throws Exception {
        if (!outputDir.exists()) outputDir.mkdirs();

        // Format selector
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
        req.addOption("--newline");

        YoutubeDLResponse resp = YoutubeDL.getInstance().execute(req, null, (progress, etaInSeconds, line) -> {
            if (cb != null) {
                cb.onProgress((float) progress, line);
                cb.onLog(line);
            }
        });

        String out = resp.getOut();
        if (cb != null && out != null) {
            for (String line : out.split("\n")) {
                cb.onLog(line);
            }
        }

        // Cari file hasil download
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
