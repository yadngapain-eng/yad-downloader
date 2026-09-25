package com.yad.videodl;

import android.graphics.Bitmap;
import android.util.Log;

import com.antonkarpenko.ffmpegkit.FFmpegKit;
import com.antonkarpenko.ffmpegkit.ReturnCode;

import java.io.File;

/**
 * Extract frame dari video untuk image recognition.
 * Ambil 3 frame di detik berbeda untuk deteksi lebih akurat.
 */
public class VideoFrameExtractor {

    private static final String TAG = "VideoFrameExtractor";

    public interface FrameCallback {
        void onFrame(Bitmap bitmap, int frameIndex);
        void onDone();
        void onError(String error);
    }

    /**
     * Extract frame dari video (sync).
     * Return path ke file gambar.
     */
    public static File extractFrame(File videoFile, int second, File outputDir) {
        try {
            File outputFile = new File(outputDir, "frame_" + second + ".png");

            String cmd = "-y -i \"" + videoFile.getAbsolutePath() + "\" " +
                         "-ss " + second + " -vframes 1 " +
                         "\"" + outputFile.getAbsolutePath() + "\"";

            long rc = FFmpegKit.execute(cmd).getReturnCode();

            if (ReturnCode.isSuccess(rc) && outputFile.exists()) {
                return outputFile;
            }
        } catch (Exception e) {
            Log.e(TAG, "Extract frame error", e);
        }
        return null;
    }

    /**
     * Extract 3 frame dari detik berbeda untuk deteksi watermark.
     */
    public static java.util.List<File> extractFrames(File videoFile, File outputDir) {
        java.util.List<File> frames = new java.util.ArrayList<>();

        // Ambil di detik 2, 5, dan 8 (representatif)
        int[] seconds = {2, 5, 8};

        for (int sec : seconds) {
            File frame = extractFrame(videoFile, sec, outputDir);
            if (frame != null && frame.exists()) {
                frames.add(frame);
            }
        }

        return frames;
    }
}
