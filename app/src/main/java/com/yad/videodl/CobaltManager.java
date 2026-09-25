package com.yad.videodl;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

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
 * Pure Cobalt.tools API downloader.
 * Support YouTube, Facebook, TikTok, IG, Twitter, dll.
 */
public class CobaltManager {

    private static final String TAG = "CobaltManager";

    // Instance publik cobalt (bisa diganti ke self-hosted)
    private static final String[] API_ENDPOINTS = {
        "https://api.cobalt.tools/api/json",
        "https://co.wuk.sh/api/json",       // legacy domain
        "https://cobalt-api.kwiatekmiki.com/api/json",  // mirror publik
    };

    private final OkHttpClient client;
    private final Handler ui = new Handler(Looper.getMainLooper());

    public CobaltManager(Context ctx) {
        this.client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build();
    }

    public void init() throws Exception {
        Log.d(TAG, "CobaltManager initialized");
    }

    public boolean isReady() {
        return true;
    }

    public String getVersion() {
        return "cobalt-api";
    }

    public interface Callback {
        void onProgress(int percent, String message);
        void onLog(String line);
        void onDone(boolean success, String filePath, String error);
    }

    /**
     * Info video via cobalt.
     * Cobalt tidak punya endpoint info, jadi kita pakai direct download + tampilkan judul dari response.
     */
    public JSONObject getInfo(String url) throws Exception {
        JSONObject resp = callCobalt(url, "720", false);
        return resp;
    }

    /**
     * Download file — pure cobalt API.
     */
    public void download(String url, String quality, File outputDir, Callback cb) {
        new Thread(() -> {
            try {
                if (cb != null) cb.onLog("> Memanggil cobalt API...");

                boolean isAudio = "audio".equals(quality);
                String vq = isAudio ? "720" : (quality == null || quality.isEmpty() ? "1080" : quality);

                JSONObject resp = callCobalt(url, vq, isAudio);

                String status = resp.optString("status");
                if (!"stream".equals(status) && !"redirect".equals(status) && !"success".equals(status)) {
                    throw new Exception("Cobalt error: " + resp.optString("text", status));
                }

                String directUrl = resp.optString("url", "");
                if (directUrl.isEmpty()) throw new Exception("No direct URL from cobalt");

                if (cb != null) cb.onLog("> Direct URL didapat, mulai download...");

                if (!outputDir.exists()) outputDir.mkdirs();

                String filename = resp.optString("filename", "");
                if (filename.isEmpty()) {
                    filename = "video_" + System.currentTimeMillis() + (isAudio ? ".m4a" : ".mp4");
                }
                // Sanitize filename
                filename = filename.replaceAll("[\\\\/:*?\"<>|]", "_");

                File outFile = new File(outputDir, filename);

                downloadFile(directUrl, outFile, cb);

                if (outFile.exists() && outFile.length() > 0) {
                    if (cb != null) cb.onDone(true, outFile.getAbsolutePath(), null);
                } else {
                    if (cb != null) cb.onDone(false, null, "File kosong");
                }

            } catch (Exception e) {
                Log.e(TAG, "Download error", e);
                if (cb != null) cb.onDone(false, null, e.getMessage());
            }
        }).start();
    }

    private JSONObject callCobalt(String url, String quality, boolean isAudio) throws Exception {
        JSONObject body = new JSONObject();
        body.put("url", url);
        body.put("vQuality", quality);
        body.put("aFormat", "mp3");
        body.put("isAudioOnly", isAudio);
        body.put("disableMetadata", false);
        body.put("filenamePattern", "classic");

        String jsonBody = body.toString();
        Exception lastError = null;

        for (String endpoint : API_ENDPOINTS) {
            try {
                Log.d(TAG, "Trying endpoint: " + endpoint);

                Request req = new Request.Builder()
                    .url(endpoint)
                    .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "YadDownloader/1.0")
                    .build();

                Response resp = client.newCall(req).execute();
                ResponseBody rb = resp.body();
                if (rb == null) throw new Exception("Empty response");

                String respStr = rb.string();
                Log.d(TAG, "Response: " + respStr);

                if (resp.code() != 200) {
                    throw new Exception("HTTP " + resp.code() + ": " + respStr);
                }

                return new JSONObject(respStr);

            } catch (Exception e) {
                Log.w(TAG, "Endpoint gagal: " + endpoint + " — " + e.getMessage());
                lastError = e;
            }
        }

        throw new Exception("Semua endpoint cobalt gagal: " + (lastError != null ? lastError.getMessage() : "unknown"));
    }

    private void downloadFile(String fileUrl, File outFile, Callback cb) throws Exception {
        URL url = new URL(fileUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        conn.setRequestProperty("Accept", "*/*");
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(120000);
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
            if (cb != null && now - lastUpdate > 300) {
                int pct = total > 0 ? (int)(downloaded * 100 / total) : 0;
                String msg = String.format("%.1f / %.1f MB",
                    downloaded / 1024.0 / 1024.0,
                    total > 0 ? total / 1024.0 / 1024.0 : 0);
                cb.onProgress(pct, msg);
                lastUpdate = now;
            }
        }

        in.close();
        out.close();
        conn.disconnect();

        if (cb != null) cb.onProgress(100, "Selesai");
    }
}
