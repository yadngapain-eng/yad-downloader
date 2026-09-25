package com.yad.videodl;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class YtDlpManager {
    private static final String TAG = "YtDlpManager";
    private final Context ctx;
    private final File ytDlp;

    public YtDlpManager(Context ctx) {
        this.ctx = ctx;
        this.ytDlp = BinaryInstaller.getYtDlp(ctx);
    }

    public boolean isReady() {
        return ytDlp.exists() && ytDlp.canExecute();
    }

    public void install() throws Exception {
        BinaryInstaller.installBinary(ctx, "yt-dlp", "yt-dlp");
    }

    public String getVersion() throws Exception {
        List<String> out = run(new String[]{ytDlp.getAbsolutePath(), "--version"}, 10000);
        return out.isEmpty() ? "unknown" : out.get(0).trim();
    }

    public JSONObject getInfo(String url) throws Exception {
        List<String> out = run(new String[]{
            ytDlp.getAbsolutePath(),
            "-j", "--no-warnings", "--no-playlist",
            url
        }, 60000);
        if (out.isEmpty()) throw new Exception("yt-dlp no output");
        return new JSONObject(out.get(0));
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

        List<String> cmd = new ArrayList<>();
        cmd.add(ytDlp.getAbsolutePath());
        cmd.add("-f"); cmd.add(format);
        cmd.add("-o"); cmd.add(outputTemplate);
        cmd.add("--no-warnings");
        cmd.add("--no-playlist");
        cmd.add("--newline");
        cmd.add(url);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process proc = pb.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
        String line;
        String lastFile = null;

        while ((line = reader.readLine()) != null) {
            Log.d(TAG, "yt-dlp: " + line);

            if (line.contains("[download]") && line.contains("%")) {
                try {
                    int pctStart = line.indexOf(']') + 1;
                    String rest = line.substring(pctStart).trim();
                    int pctEnd = rest.indexOf('%');
                    if (pctEnd > 0) {
                        float pct = Float.parseFloat(rest.substring(0, pctEnd).trim());
                        if (cb != null) cb.onProgress(pct, line);
                    }
                } catch (Exception ignored) {}
            }

            if (line.contains("[download] Destination:")) {
                lastFile = line.substring(line.indexOf("Destination:") + 12).trim();
            }
            if (line.contains("has already been downloaded")) {
                lastFile = line.split(" has already")[0].replaceAll("^\\[download\\]\\s*", "").trim();
            }
            if (line.contains("[ExtractAudio] Destination:")) {
                lastFile = line.substring(line.indexOf("Destination:") + 12).trim();
            }

            if (cb != null) cb.onLog(line);
        }

        int exit = proc.waitFor();
        if (exit != 0) throw new Exception("yt-dlp exit code: " + exit);

        if (lastFile == null || !new File(lastFile).exists()) {
            File[] files = outputDir.listFiles();
            if (files != null && files.length > 0) {
                File newest = files[0];
                for (File f : files) {
                    if (f.lastModified() > newest.lastModified()) newest = f;
                }
                lastFile = newest.getAbsolutePath();
            }
        }

        return lastFile;
    }

    private List<String> run(String[] cmd, long timeoutMs) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process proc = pb.start();

        List<String> lines = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) lines.add(line);

        boolean done = proc.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        if (!done) {
            proc.destroyForcibly();
            throw new Exception("Timeout");
        }
        return lines;
    }

    public interface DownloadCallback {
        void onProgress(float percent, String rawLine);
        void onLog(String line);
    }
}
