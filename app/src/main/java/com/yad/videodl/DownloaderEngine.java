package com.yad.videodl;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Multi-engine downloader.
 * Coba yt-dlp dulu, kalau gagal pakai cobalt.tools API.
 * Support semua platform termasuk share link.
 */
public class DownloaderEngine {

    private static final String TAG = "DownloaderEngine";
    private static final String COBALT_API = "https://api.cobalt.tools/api/json";

    private final Context ctx;
    private final YtDlpManager ytDlp;
    private final OkHttpClient client;

    public DownloaderEngine(Context ctx) {
        this.ctx = ctx;
        this.ytDlp = new YtDlpManager(ctx);
        this.client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .followRedirects(true)
            .build();
    }

    public void init() throws Exception {
        ytDlp.install();
    }

    public boolean isReady() {
        return ytDlp.isReady();
    }

    public String getVersion() {
        return ytDlp.getVersion();
    }

    public JSONObject getInfo(String url) throws Exception {
        return ytDlp.getInfo(url);
    }

    public interface Callback {
        void onProgress(float percent, String message);
        void onLog(String line);
        void onDone(boolean success, String filePath, String error);
    }

    /**
     * Smart download — coba yt-dlp dulu, fallback ke cobalt.
     */
    public void download(String url, String quality, File outputDir, Callback cb) {
        new Thread(() -> {
            // Engine 1: yt-dlp
            try {
                if (cb != null) cb.onLog("> Engine 1: yt-dlp");
                String result = ytDlp.download(url, quality, outputDir, new YtDlpManager.DownloadCallback() {
                    @Override
                    public void onProgress(float percent, String rawLine) {
                        if (cb != null) cb.onProgress(percent, rawLine);
                    }
                    @Override
                    public void onLog(String line) {
                        if (cb != null) cb.onLog(line);
                    }
                });

                if (result != null && new File(result).exists()) {
                    if (cb != null) cb.onLog("OK yt-dlp berhasil");
                    if (cb != null) cb.onDone(true, result, null);
                    return;
                }
            } catch (Exception e) {
                if (cb != null) cb.onLog("yt-dlp gagal: " + e.getMessage());
            }

            // Engine 2: cobalt.tools API
            try {
                if (cb != null) cb.onLog("> Engine 2: cobalt.tools API");
                String result = downloadViaCobalt(url, quality, outputDir, cb);
                if (result != null && new File(result).exists()) {
                    if (cb != null) cb.onLog("OK cobalt berhasil");
                    if (cb != null) cb.onDone(true, result, null);
                    return;
                }
            } catch (Exception e) {
                if (cb != null) cb.onLog("cobalt gagal: " + e.getMessage());
            }

            // Semua gagal
            if (cb != null) cb.onDone(false, null, "Semua engine gagal. Coba link lain atau login dulu.");
        }).start();
    }

    /**
     * Download via cobalt.tools API.
     * POST {url: "...", vQuality: "720"} -> dapat direct URL
     */
    private String downloadViaCobalt(String url, String quality, File outputDir, Callback cb) throws Exception {
        String q = (quality == null || quality.isEmpty()) ? "720" : quality;
        if (q.equals("1080")) q = "1080";
        else if (q.equals("720")) q = "720";
        else if (q.equals("480")) q = "480";
        else q = "1080";

        JSONObject body = new JSONObject();
        body.put("url", url);
        body.put("vQuality", q);
        body.put("aFormat", "mp4");
        body.put("isAudioOnly", quality != null && quality.equals("audio"));

        Request req = new Request.Builder()
            .url(COBALT_API)
            .post(RequestBody.create(body.toString(), MediaType.parse("application/json")))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .build();

        Response resp = client.newCall(req).execute();
        ResponseBody rb = resp.body();
        if (rb == null) throw new Exception("Empty response");

        String json = rb.string();
        if (cb != null) cb.onLog("cobalt response: " + json);

        JSONObject obj = new JSONObject(json);
        String status = obj.optString("status");

        if (status.equals("error")) {
            throw new Exception("cobalt error: " + obj.optString("text"));
        }

        String downloadUrl = obj.optString("url", "");
        if (downloadUrl.isEmpty()) throw new Exception("No URL in response");

        // Download direct file
        return downloadFile(downloadUrl, outputDir, cb);
    }

    private String downloadFile(String fileUrl, File outputDir, Callback cb) throws Exception {
        String filename = "video_" + System.currentTimeMillis() + ".mp4";
        File outFile = new File(outputDir, filename);

        URL url = new URL(fileUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
        conn.connect();

        int total = conn.getContentLength();
        InputStream in = conn.getInputStream();
        FileOutputStream out = new FileOutputStream(outFile);

        byte[] buf = new byte[8192];
        int n;
        long downloaded = 0;
        long lastUpdate = 0;

        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
            downloaded += n;

            long now = System.currentTimeMillis();
            if (cb != null && now - lastUpdate > 500) {
                int pct = total > 0 ? (int)(downloaded * 100 / total) : 0;
                cb.onProgress(pct, "Downloaded " + (downloaded/1024) + " KB");
                lastUpdate = now;
            }
        }

        in.close();
        out.close();
        conn.disconnect();

        return outFile.getAbsolutePath();
    }
}
