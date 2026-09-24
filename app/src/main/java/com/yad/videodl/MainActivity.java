package com.yad.videodl;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private EditText etUrl;
    private TextView tvStatus, tvLog;
    private ProgressBar progressBar;
    private Button btnDownload, btnInfo, btnPaste;
    private ScrollView logScroll;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private File ytDlpBin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUI();
        requestStoragePermission();
        handleShareIntent(getIntent());

        // Download yt-dlp binary di background saat pertama buka
        new Thread(this::ensureYtDlp).start();
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
        subtitle.setText("YouTube, Facebook, TikTok, Instagram, dll");
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
        tvStatus.setText("Menyiapkan yt-dlp...");
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
        tvLog.setText("Log siap...\\n");
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

    private void log(String msg) {
        tvLog.append(msg + "\\n");
        logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
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

    // ============ YT-DLP BINARY MANAGEMENT ============
    private void ensureYtDlp() {
        ytDlpBin = new File(getFilesDir(), "yt-dlp");
        if (ytDlpBin.exists() && ytDlpBin.length() > 100000) {
            log("✅ yt-dlp sudah ada: " + ytDlpBin.length() + " bytes");
            ui.post(() -> setStatus("Siap."));
            return;
        }

        log("⬇️ Download yt-dlp binary...");
        ui.post(() -> setStatus("Download yt-dlp..."));

        String abi = Build.SUPPORTED_ABIS[0];
        String url;
        if (abi.contains("arm64")) {
            url = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_linux_aarch64";
        } else if (abi.contains("arm")) {
            url = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_linux_armv7l";
        } else if (abi.contains("x86_64")) {
            url = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_linux";
        } else {
            url = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_linux";
        }

        try {
            downloadFile(url, ytDlpBin);
            ytDlpBin.setExecutable(true);
            log("✅ yt-dlp siap: " + ytDlpBin.length() + " bytes");
            ui.post(() -> setStatus("Siap."));
        } catch (Exception e) {
            log("❌ Gagal download yt-dlp: " + e.getMessage());
            ui.post(() -> setStatus("❌ Gagal download yt-dlp"));
        }
    }

    private void downloadFile(String urlStr, File dest) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "YadDownloader/1.0");
        conn.connect();

        int code = conn.getResponseCode();
        if (code >= 300 && code < 400) {
            String loc = conn.getHeaderField("Location");
            conn.disconnect();
            downloadFile(loc, dest);
            return;
        }
        if (code != 200) throw new IOException("HTTP " + code);

        try (InputStream in = conn.getInputStream();
             FileOutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
        conn.disconnect();
    }

    // ============ RUN YT-DLP ============
    private String runYtDlp(List<String> args, File workDir) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add(ytDlpBin.getAbsolutePath());
        cmd.addAll(args);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(workDir);
        pb.redirectErrorStream(true);

        Process p = pb.start();

        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\\n");
                final String l = line;
                ui.post(() -> log(l));
            }
        }
        int code = p.waitFor();
        if (code != 0) {
            throw new Exception("yt-dlp exit " + code + "\\n" + sb.toString());
        }
        return sb.toString();
    }

    // ============ INFO ============
    private void doInfo() {
        String url = etUrl.getText().toString().trim();
        if (url.isEmpty()) { toast("Masukkan URL dulu"); return; }
        if (ytDlpBin == null || !ytDlpBin.exists()) {
            toast("yt-dlp belum siap, tunggu sebentar"); return;
        }

        setStatus("Mengambil info...");
        log("\\n> INFO: " + url);

        new Thread(() -> {
            try {
                List<String> args = new ArrayList<>();
                args.add("--no-warnings");
                args.add("--skip-download");
                args.add("--print");
                args.add("%(title)s | %(uploader)s | %(duration)s detik");
                args.add(url);

                String out = runYtDlp(args, getCacheDir());
                ui.post(() -> setStatus("✅ Info didapat"));
            } catch (Exception e) {
                ui.post(() -> {
                    setStatus("❌ Error");
                    log("ERROR: " + e.getMessage());
                });
            }
        }).start();
    }

    // ============ DOWNLOAD ============
    private void doDownload() {
        String url = etUrl.getText().toString().trim();
        if (url.isEmpty()) { toast("Masukkan URL dulu"); return; }
        if (ytDlpBin == null || !ytDlpBin.exists()) {
            toast("yt-dlp belum siap"); return;
        }

        btnDownload.setEnabled(false);
        btnDownload.setText("⏳ Memproses...");
        progressBar.setProgress(0);
        setStatus("Downloading...");
        log("\\n> DOWNLOAD: " + url);

        new Thread(() -> {
            try {
                File outDir = new File(
                    Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS),
                    "YadDownloader");
                if (!outDir.exists()) outDir.mkdirs();

                log("Output: " + outDir.getAbsolutePath());

                List<String> args = new ArrayList<>();
                args.add("-o");
                args.add(outDir.getAbsolutePath() + "/%(title).80s.%(ext)s");
                args.add("-f");
                args.add("best[ext=mp4]/best");
                args.add("--no-warnings");
                args.add("--no-playlist");
                args.add("--restrict-filenames");
                args.add(url);

                runYtDlp(args, getCacheDir());

                ui.post(() -> {
                    setStatus("✅ Selesai!");
                    progressBar.setProgress(100);
                    btnDownload.setEnabled(true);
                    btnDownload.setText("⬇️  Download Video");
                    toast("Cek folder Downloads/YadDownloader");
                });
            } catch (Exception e) {
                ui.post(() -> {
                    setStatus("❌ Error");
                    log("ERROR: " + e.getMessage());
                    btnDownload.setEnabled(true);
                    btnDownload.setText("⬇️  Download Video");
                });
            }
        }).start();
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
