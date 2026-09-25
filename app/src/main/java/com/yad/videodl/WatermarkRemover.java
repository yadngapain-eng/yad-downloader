package com.yad.videodl;

import android.content.Context;
import android.util.Log;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFmpegSession;
import com.arthenica.ffmpegkit.FFprobeKit;
import com.arthenica.ffmpegkit.MediaInformation;
import com.arthenica.ffmpegkit.ReturnCode;
import com.arthenica.ffmpegkit.StreamInformation;

import java.io.File;

public class WatermarkRemover {
    private static final String TAG = "WatermarkRemover";

    public interface ProgressCallback {
        void onLog(String line);
        void onDone(boolean success, File output, String error);
    }

    public static void remove(Context ctx, File input, File output, String mode, ProgressCallback cb) {
        if (!input.exists()) {
            cb.onDone(false, null, "Input not found: " + input.getAbsolutePath());
            return;
        }

        MediaInformation info = FFprobeKit.getMediaInformation(input.getAbsolutePath())
            .getMediaInformation();

        if (info == null || info.getStreams() == null || info.getStreams().isEmpty()) {
            cb.onDone(false, null, "Gagal probe video");
            return;
        }

        int width = 0, height = 0;
        for (StreamInformation s : info.getStreams()) {
            if ("video".equals(s.getType())) {
                String w = s.getProperties().get("width");
                String h = s.getProperties().get("height");
                if (w != null && h != null) {
                    width = Integer.parseInt(w);
                    height = Integer.parseInt(h);
                    break;
                }
            }
        }

        if (width == 0 || height == 0) {
            cb.onDone(false, null, "Resolusi tidak terdeteksi");
            return;
        }

        Log.d(TAG, "Resolusi: " + width + "x" + height);
        cb.onLog("Resolusi: " + width + "x" + height);

        String filter = buildFilter(mode, width, height);
        cb.onLog("Mode: " + mode);
        cb.onLog("Filter: " + filter);

        String cmd = "-y -i \"" + input.getAbsolutePath() + "\" " +
                     "-vf \"" + filter + "\" " +
                     "-c:a copy " +
                     "\"" + output.getAbsolutePath() + "\"";

        FFmpegKit.executeAsync(cmd,
            s -> {
                long rc = s.getReturnCode();
                if (ReturnCode.isSuccess(rc)) {
                    cb.onLog("Selesai!");
                    cb.onDone(true, output, null);
                } else {
                    String err = s.getFailStackTrace() != null
                        ? s.getFailStackTrace() : "FFmpeg error";
                    cb.onLog("Error: " + err);
                    cb.onDone(false, null, err);
                }
            },
            log -> cb.onLog(log.getMessage()),
            statistics -> {}
        );
    }

    private static String buildFilter(String mode, int width, int height) {
        int wmW = Math.max(80, (int)(width * 0.25));
        int wmH = Math.max(40, (int)(height * 0.10));
        int wmX = width - wmW - 10;
        int wmY = height - wmH - 10;

        if ("crop".equals(mode)) {
            int cropW = (int)(width * 0.98) & ~1;
            int cropH = (int)(height * 0.82) & ~1;
            int cropX = (width - cropW) / 2;
            int cropY = (int)(height * 0.07);
            return "crop=" + cropW + ":" + cropH + ":" + cropX + ":" + cropY;
        } else if ("blur".equals(mode)) {
            return "boxblur=15:3";
        } else {
            return "delogo=x=" + wmX + ":y=" + wmY + ":w=" + wmW + ":h=" + wmH;
        }
    }
}
