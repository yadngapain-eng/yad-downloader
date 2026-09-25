package com.yad.videodl;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Watermark detector v2 — pakai image recognition pada multiple frames.
 * Lebih akurat dari single-frame detection.
 */
public class WatermarkDetectorV2 {

    private static final String TAG = "WatermarkDetectorV2";

    /**
     * Deteksi watermark dari video file.
     * Extract multiple frames, deteksi di tiap frame, vote untuk hasil akhir.
     */
    public static WatermarkDetector.WatermarkArea detectFromVideo(File videoFile, String url) {
        File tempDir = new File(videoFile.getParent(), "temp_frames");
        if (!tempDir.exists()) tempDir.mkdirs();

        try {
            Log.d(TAG, "Extracting frames...");
            List<File> frames = VideoFrameExtractor.extractFrames(videoFile, tempDir);

            if (frames.isEmpty()) {
                Log.w(TAG, "No frames extracted, using heuristic");
                return WatermarkDetector.detect(url);
            }

            Log.d(TAG, "Analyzing " + frames.size() + " frames...");

            List<WatermarkDetector.WatermarkArea> detections = new ArrayList<>();

            for (File frameFile : frames) {
                Bitmap bmp = BitmapFactory.decodeFile(frameFile.getAbsolutePath());
                if (bmp == null) continue;

                WatermarkDetector.WatermarkArea area =
                    WatermarkDetector.detectFromBitmap(bmp, url);

                if (area != null) {
                    detections.add(area);
                }

                bmp.recycle();
            }

            // Cleanup frame files
            for (File f : frames) {
                f.delete();
            }
            tempDir.delete();

            if (detections.isEmpty()) {
                Log.d(TAG, "No watermark detected in frames, using heuristic");
                return WatermarkDetector.detect(url);
            }

            // Vote: cari area yang paling sering muncul
            WatermarkDetector.WatermarkArea best = voteBestArea(detections);

            Log.d(TAG, "Best area: " + best.label +
                " confidence: " + best.confidence);

            return best;

        } catch (Exception e) {
            Log.e(TAG, "Detection error", e);
            return WatermarkDetector.detect(url);
        }
    }

    /**
     * Vote area yang paling sering muncul di multiple frames.
     */
    private static WatermarkDetector.WatermarkArea voteBestArea(
            List<WatermarkDetector.WatermarkArea> detections) {

        if (detections.size() == 1) return detections.get(0);

        // Group by approximate position
        List<List<WatermarkDetector.WatermarkArea>> groups = new ArrayList<>();

        for (WatermarkDetector.WatermarkArea area : detections) {
            boolean added = false;
            for (List<WatermarkDetector.WatermarkArea> group : groups) {
                WatermarkDetector.WatermarkArea ref = group.get(0);
                // Kalau posisi mirip (dalam 10%), masuk group yang sama
                if (Math.abs(area.xPercent - ref.xPercent) < 0.10 &&
                    Math.abs(area.yPercent - ref.yPercent) < 0.10) {
                    group.add(area);
                    added = true;
                    break;
                }
            }
            if (!added) {
                List<WatermarkDetector.WatermarkArea> newGroup = new ArrayList<>();
                newGroup.add(area);
                groups.add(newGroup);
            }
        }

        // Ambil group dengan anggota terbanyak
        List<WatermarkDetector.WatermarkArea> bestGroup = groups.get(0);
        for (List<WatermarkDetector.WatermarkArea> group : groups) {
            if (group.size() > bestGroup.size()) bestGroup = group;
        }

        // Average dari group terbaik
        float avgX = 0, avgY = 0, avgW = 0, avgH = 0, avgConf = 0;
        for (WatermarkDetector.WatermarkArea a : bestGroup) {
            avgX += a.xPercent;
            avgY += a.yPercent;
            avgW += a.widthPercent;
            avgH += a.heightPercent;
            avgConf += a.confidence;
        }
        int n = bestGroup.size();

        return new WatermarkDetector.WatermarkArea(
            avgX / n, avgY / n, avgW / n, avgH / n,
            bestGroup.get(0).label + " (voted " + n + "/" + detections.size() + ")",
            bestGroup.get(0).platform,
            avgConf / n
        );
    }
}
