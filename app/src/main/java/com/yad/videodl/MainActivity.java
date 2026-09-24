package com.yad.videodl;

import android.Manifest;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.Stream;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;

public class MainActivity extends AppCompatActivity {

    private EditText etUrl;
    private TextView tvStatus, tvLog;
    private ProgressBar progressBar;
    private Button btnDownload, btnInfo, btnPaste;
    private ScrollView logScroll;
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUI();
        requestStoragePermission();
        handleShareIntent(getIntent());
        initNewPipe();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleShareIntent(intent);
    }

    private void handleShareIntent(Intent intent) {
        if (intent != null && Intent.ACTION_SEND.equals(intent.getAction())) {
            String shared = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (shared != null) {
                etUrl.setText(shared.trim());
                log("Link dari share: " + shared);
            }
        }
    }

    private void buildUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(40, 60, 40, 40);
        root.setBackgroundColor(0xFF0A0E27);

        TextView title = new TextView(this);
        title.setText("🎬 YadDownloader");
        title.setTextSize(24);
        title.setTextColor(0xFF00E5FF);
        title.setPadding(0, 0, 0, 8);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("YouTube, SoundCloud, PeerTube, Bandcamp");
        subtitle.setTextSize(12);
        subtitle.setTextColor(0xFF8892B0);
        subtitle.setPadding(0, 0, 0, 24);
        root.addView(subtitle);

        etUrl = new EditText(this);
        etUrl.setHint("Paste link video di sini...");
        etUrl.setTextColor(0xFFE8ECFF);
        etUrl.setHintTextColor(0xFF8892B0);
        etUrl.setBackgroundColor(0xFF1A1F3A);
        etUrl.setPadding(24, 24, 24, 24);
        etUrl.setMinLines(2);
        etUrl.setMaxLines(4);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, 16);
        root.addView(etUrl, lp);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);

        btnPaste = mkBtn("📋 Paste", 0xFF1A1F3A);
        btnPaste.setOnClickListener(v -> pasteFromClipboard());
        row1.addView(btnPaste, btnLp());

        btnInfo = mkBtn("ℹ️ Info", 0xFF1A1F3A);
        btnInfo.setOnClickListener(v -> doInfo());
        row1.addView(btnInfo, btnLp());
        root.addView(row1);

        btnDownload = mkBtn("⬇️  Download Video", 0xFF00E5FF);
        btnDownload.setTextColor(0xFF0A0E27);
        btnDownload.setOnClickListener(v -> doDownload());
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 140);
        dlp.setMargins(0, 16, 0, 16);
        root.addView(btnDownload, dlp);

        progressBar = new ProgressBar(this, null,
            android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        root.addView(progressBar);

        tvStatus = new TextView(this);
        tvStatus.setText("Menyiapkan...");
        tvStatus.setTextColor(0xFF00E5FF);
        tvStatus.setPadding(0, 8, 0, 16);
        root.addView(tvStatus);

        logScroll = new ScrollView(this);
        logScroll.setBackgroundColor(0xFF060918);
        logScroll.setPadding(16, 16, 16, 16);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        logScroll.setLayoutParams(slp);

        tvLog = new TextView(this);
        tvLog.setTextColor(0xFF8892B0);
        tvLog.setTextSize(11);
        tvLog.setTypeface(android.graphics.Typeface.MONOSPACE);
        tvLog.setText("Log siap...\n");
        logScroll.addView(tvLog);
        root.addView(logScroll);

        setContentView(root);
    }

    private Button mkBtn(String text, int bg) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(0xFFFFFFFF);
        b.setBackgroundColor(bg);
        b.setAllCaps(false);
        b.setTextSize(14);
        return b;
    }

    private LinearLayout.LayoutParams btnLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(0, 0, 8, 0);
        return lp;
    }

    // ============================================
    // FIX: log() selalu pakai ui.post()
    // ============================================
    private void log(String msg) {
        ui.post(() -> {
            tvLog.append(msg + "\n");
            logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
        });
    }

    private void setStatus(String msg) {
        ui.post(() -> tvStatus.setText(msg));
    }

    private void pasteFromClipboard() {
        android.content.ClipboardManager cm = (android.content.ClipboardManager)
            getSystemService(CLIPBOARD_SERVICE);
        if (cm != null && cm.hasPrimaryClip()) {
            CharSequence text = cm.getPrimaryClip().getItemAt(0).getText();
            if (text != null) etUrl.setText(text.toString().trim());
        }
    }

    // ============================================
    // INIT NewPipe
    // ============================================
    private void initNewPipe() {
        try {
            NewPipe.init(new OkHttpDownloader());
            log("✅ NewPipe siap.");
            setStatus("Siap.");
        } catch (Exception e) {
            log("❌ Init error: " + e.getMessage());
            setStatus("❌ Init gagal");
        }
    }

    // Downloader untuk NewPipe pakai OkHttp
    static class OkHttpDownloader extends Downloader {
        private final OkHttpClient client = new OkHttpClient.Builder().build();

        @Override
        public Response execute(Request request) throws IOException {
            String method = request.httpMethod();
            String url = request.url();
            List<String> headers = new ArrayList<>();
            for (java.util.Map.Entry<String, List<String>> e : request.headers().entrySet()) {
                for (String v : e.getValue()) headers.add(e.getKey() + ": " + v);
            }

            okhttp3.Request.Builder b = new okhttp3.Request.Builder().url(url);
            for (java.util.Map.Entry<String, List<String>> e : request.headers().entrySet()) {
                for (String v : e.getValue()) b.addHeader(e.getKey(), v);
            }
            if ("POST".equals(method) && request.dataToSend() != null) {
                b.post(RequestBody.create(request.dataToSend()));
            }
            okhttp3.Response resp = client.newCall(b.build()).execute();
            ResponseBody body = resp.body();
            String bodyStr = body != null ? body.string() : "";
            int code = resp.code();
            String msg = resp.message();
            java.util.Map<String, List<String>> respHeaders = resp.headers().toMultimap();
            return new Response(code, msg, respHeaders, bodyStr, url);
        }
    }

    // ============================================
    // INFO
    // ============================================
    private void doInfo() {
        String url = etUrl.getText().toString().trim();
        if (url.isEmpty()) { toast("Masukkan URL dulu"); return; }

        setStatus("Ambil info...");
        log("\n> INFO: " + url);

        new Thread(() -> {
            try {
                StreamInfo info = StreamInfo.getInfo(ServiceList.YouTube, url);
                log("✅ Judul    : " + info.getName());
                log("   Uploader : " + info.getUploaderName());
                log("   Durasi   : " + info.getDuration() + " detik");
                log("   Views    : " + info.getViewCount());
                setStatus("✅ Info didapat");
            } catch (Exception e) {
                log("❌ " + e.getMessage());
                setStatus("❌ Error");
            }
        }).start();
    }

    // ============================================
    // DOWNLOAD pakai Android DownloadManager
    // ============================================
    private void doDownload() {
        String url = etUrl.getText().toString().trim();
        if (url.isEmpty()) { toast("Masukkan URL dulu"); return; }

        btnDownload.setEnabled(false);
        btnDownload.setText("⏳ Memproses...");
        setStatus("Mengambil info stream...");
        log("\n> DOWNLOAD: " + url);

        new Thread(() -> {
            try {
                StreamInfo info = StreamInfo.getInfo(ServiceList.YouTube, url);
                log("✅ Judul: " + info.getName());

                // Ambil video stream (mp4)
                List<VideoStream> videos = info.getVideoStreams();
                VideoStream best = null;
                for (VideoStream v : videos) {
                    if (v.getFormat() != null && v.getFormat().getName().contains("mp4")) {
                        if (best == null || v.getResolution().compareTo(best.getResolution()) > 0) {
                            best = v;
                        }
                    }
                }

                if (best == null && !videos.isEmpty()) best = videos.get(0);
                if (best == null) {
                    log("❌ Tidak ada video stream tersedia");
                    ui.post(() -> resetBtn());
                    return;
                }

                final String videoUrl = best.getUrl();
                final String title = info.getName().replaceAll("[^a-zA-Z0-9._-]", "_");
                log("   Resolusi: " + best.getResolution());
                log("   Format  : " + best.getFormat().getName());
                log("⬇️ Mulai download...");

                // Pakai Android DownloadManager (support redirect + resume)
                DownloadManager.Request req = new DownloadManager.Request(Uri.parse(videoUrl));
                req.setTitle(title);
                req.setDescription("YadDownloader");
                req.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                req.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    "YadDownloader/" + title + ".mp4");
                req.allowScanningByMediaScanner();

                DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                long id = dm.enqueue(req);

                log("✅ Download dimulai (ID: " + id + ")");
                log("📁 Cek: Downloads/YadDownloader/" + title + ".mp4");

                ui.post(() -> {
                    setStatus("✅ Download jalan di background");
                    progressBar.setProgress(100);
                    resetBtn();
                    toast("Cek notifikasi & folder Downloads/YadDownloader");
                });
            } catch (Exception e) {
                log("❌ " + e.getMessage());
                ui.post(() -> {
                    setStatus("❌ Error");
                    resetBtn();
                });
            }
        }).start();
    }

    private void resetBtn() {
        btnDownload.setEnabled(true);
        btnDownload.setText("⬇️  Download Video");
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    startActivity(new Intent(
                        android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        android.net.Uri.parse("package:" + getPackageName())));
                } catch (Exception e) {
                    try {
                        startActivity(new Intent(
                            android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                    } catch (Exception ignored) {}
                }
            }
        } else {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                }, 1001);
            }
        }
    }
}
