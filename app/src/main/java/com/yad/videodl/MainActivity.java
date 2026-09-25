package com.yad.videodl;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

import java.io.File;

public class MainActivity extends AppCompatActivity {

    private EditText etUrl;
    private TextView tvStatus, tvLog;
    private ProgressBar progressBar;
    private Button btnDownload, btnInfo, btnPaste;
    private ScrollView logScroll;
    private Spinner qualitySpinner, modeSpinner;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private YtDlpManager ytDlp;
    private File downloadDir;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUI();
        requestStoragePermission();
        handleShareIntent(getIntent());

        downloadDir = new File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "YadDownloader"
        );
        if (!downloadDir.exists()) downloadDir.mkdirs();

        new Thread(() -> {
            try {
                log("Menyiapkan yt-dlp...");
                ytDlp = new YtDlpManager(this);
                ytDlp.install();
                String version = ytDlp.getVersion();
                log("yt-dlp siap: v" + version);
                setStatus("Siap!");
            } catch (Exception e) {
                log("Gagal init yt-dlp: " + e.getMessage());
                setStatus("Init gagal");
            }
        }).start();
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
                etUrl.setText(extractUrl(shared));
                log("Link dari share: " + shared);
            }
        }
    }

    private String extractUrl(String text) {
        String[] parts = text.split("\\s+");
        for (String p : parts) {
            if (p.startsWith("http://") || p.startsWith("https://")) return p;
        }
        return text.trim();
    }

    private void buildUI() {
        ScrollView mainScroll = new ScrollView(this);
        mainScroll.setBackgroundColor(0xFF0A0E27);
        mainScroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(40, 60, 40, 40);
        mainScroll.addView(root);

        TextView title = new TextView(this);
        title.setText("YadDownloader");
        title.setTextSize(26);
        title.setTextColor(0xFF00E5FF);
        title.setPadding(0, 0, 0, 8);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("YouTube, Facebook, TikTok, Instagram, dll\n100% offline, tanpa server");
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

        btnPaste = mkBtn("Paste", 0xFF1A1F3A);
        btnPaste.setOnClickListener(v -> pasteFromClipboard());
        row1.addView(btnPaste, btnLp());

        btnInfo = mkBtn("Info", 0xFF1A1F3A);
        btnInfo.setOnClickListener(v -> doInfo());
        row1.addView(btnInfo, btnLp());
        root.addView(row1);

        qualitySpinner = new Spinner(this);
        String[] qualities = {"Best Quality", "1080p", "720p", "480p", "Audio Only"};
        ArrayAdapter<String> qAdapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_dropdown_item, qualities);
        qualitySpinner.setAdapter(qAdapter);
        root.addView(qualitySpinner);

        modeSpinner = new Spinner(this);
        String[] modes = {"Tanpa Hapus Watermark", "Hapus Watermark (Auto)", "Hapus Watermark (Blur)", "Hapus Watermark (Crop)"};
        ArrayAdapter<String> mAdapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_dropdown_item, modes);
        modeSpinner.setAdapter(mAdapter);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        sp.setMargins(0, 8, 0, 16);
        root.addView(modeSpinner, sp);

        btnDownload = mkBtn("Download", 0xFF00E5FF);
        btnDownload.setTextColor(0xFF0A0E27);
        btnDownload.setOnClickListener(v -> doDownload());
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 140);
        dlp.setMargins(0, 0, 0, 16);
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
            LinearLayout.LayoutParams.MATCH_PARENT, 400);
        logScroll.setLayoutParams(slp);

        tvLog = new TextView(this);
        tvLog.setTextColor(0xFF8892B0);
        tvLog.setTextSize(11);
        tvLog.setTypeface(android.graphics.Typeface.MONOSPACE);
        tvLog.setText("Log siap...\n");
        logScroll.addView(tvLog);
        root.addView(logScroll);

        setContentView(mainScroll);
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

    private void log(String msg) {
        ui.post(() -> {
            tvLog.append(msg + "\n");
            logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
        });
    }

    private void setStatus(String msg) { ui.post(() -> tvStatus.setText(msg)); }

    // FIX: rename dari setProgress ke updateProgressBar
    private void updateProgressBar(int pct) { ui.post(() -> progressBar.setProgress(pct)); }

    private void pasteFromClipboard() {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null && cm.hasPrimaryClip()) {
            ClipData clip = cm.getPrimaryClip();
            if (clip != null && clip.getItemCount() > 0) {
                CharSequence text = clip.getItemAt(0).getText();
                if (text != null) etUrl.setText(extractUrl(text.toString()));
            }
        }
    }

    private void doInfo() {
        if (ytDlp == null || !ytDlp.isReady()) { toast("yt-dlp belum siap"); return; }
        String url = etUrl.getText().toString().trim();
        if (url.isEmpty()) { toast("Masukkan URL"); return; }

        setStatus("Ambil info...");
        log("\n> INFO: " + url);

        new Thread(() -> {
            try {
                JSONObject info = ytDlp.getInfo(url);
                log("Judul: " + info.optString("title"));
                log("Uploader: " + info.optString("uploader", info.optString("channel", "?")));
                log("Durasi: " + info.optInt("duration") + " detik");
                log("Views: " + info.optLong("view_count"));
                setStatus("Info didapat");
            } catch (Exception e) {
                log("Error: " + e.getMessage());
                setStatus("Error");
            }
        }).start();
    }

    private void doDownload() {
        if (ytDlp == null || !ytDlp.isReady()) { toast("yt-dlp belum siap"); return; }
        String url = etUrl.getText().toString().trim();
        if (url.isEmpty()) { toast("Masukkan URL"); return; }

        String[] qualities = {"", "1080", "720", "480", "audio"};
        String quality = qualities[qualitySpinner.getSelectedItemPosition()];

        int modePos = modeSpinner.getSelectedItemPosition();
        String wmMode = null;
        if (modePos == 1) wmMode = "auto";
        else if (modePos == 2) wmMode = "blur";
        else if (modePos == 3) wmMode = "crop";

        btnDownload.setEnabled(false);
        btnDownload.setText("Downloading...");
        setStatus("Download...");
        updateProgressBar(0);
        log("\n> DOWNLOAD: " + url);
        if (wmMode != null) log("Mode watermark: " + wmMode);

        final String fWmMode = wmMode;

        new Thread(() -> {
            try {
                String resultPath = ytDlp.download(url, quality, downloadDir,
                    new YtDlpManager.DownloadCallback() {
                        @Override
                        public void onProgress(float percent, String rawLine) {
                            updateProgressBar((int) percent);
                            setStatus("Download: " + (int) percent + "%");
                        }
                        @Override
                        public void onLog(String line) { log(line); }
                    });

                log("Download selesai: " + resultPath);

                if (fWmMode != null && resultPath != null) {
                    File input = new File(resultPath);
                    File output = new File(input.getParent(), "clean_" + input.getName());

                    log("\n> HAPUS WATERMARK...");
                    setStatus("Hapus watermark...");

                    final Object lock = new Object();
                    final boolean[] done = {false};
                    final boolean[] success = {false};
                    final File[] outFile = {null};

                    WatermarkRemover.remove(this, input, output, fWmMode,
                        new WatermarkRemover.ProgressCallback() {
                            @Override
                            public void onLog(String line) { log("[FFmpeg] " + line); }
                            @Override
                            public void onDone(boolean ok, File out, String err) {
                                synchronized (lock) {
                                    done[0] = true;
                                    success[0] = ok;
                                    outFile[0] = out;
                                    if (ok) log("Watermark dihapus: " + out.getAbsolutePath());
                                    else log("Gagal: " + err);
                                    lock.notify();
                                }
                            }
                        });

                    synchronized (lock) {
                        while (!done[0]) lock.wait();
                    }

                    if (success[0] && outFile[0] != null) {
                        setStatus("Selesai (watermark removed)");
                        toast("Bersih: " + outFile[0].getAbsolutePath());
                    } else {
                        setStatus("Video asli saja");
                    }
                } else {
                    setStatus("Selesai");
                    toast("Tersimpan: " + resultPath);
                }

                ui.post(this::resetBtn);
            } catch (Exception e) {
                log("ERROR: " + e.getMessage());
                ui.post(() -> { setStatus("Error"); resetBtn(); });
            }
        }).start();
    }

    private void resetBtn() {
        btnDownload.setEnabled(true);
        btnDownload.setText("Download");
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    startActivity(new Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
                } catch (Exception e) {
                    try {
                        startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                    } catch (Exception ignored) {}
                }
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                }, 1001);
            }
        }
    }
}
