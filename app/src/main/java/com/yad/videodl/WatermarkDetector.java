package com.yad.videodl;

import android.graphics.Bitmap;
import android.util.Log;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

/**
 * Watermark detector pakai OpenCV.
 * Metode: edge detection + contour analysis + heuristic.
 */
public class WatermarkDetector {

    private static final String TAG = "WatermarkDetector";

    public static class WatermarkArea {
        public float xPercent;
        public float yPercent;
        public float widthPercent;
        public float heightPercent;
        public String label;
        public String platform;
        public float confidence; // 0-1

        public WatermarkArea(float x, float y, float w, float h,
                             String label, String platform, float confidence) {
            this.xPercent = x;
            this.yPercent = y;
            this.widthPercent = w;
            this.heightPercent = h;
            this.label = label;
            this.platform = platform;
            this.confidence = confidence;
        }
    }

    /**
     * Deteksi platform dari URL.
     */
    public static String detectPlatform(String url) {
        if (url == null) return "unknown";
        String u = url.toLowerCase();
        if (u.contains("youtube.com") || u.contains("youtu.be")) return "youtube";
        if (u.contains("facebook.com") || u.contains("fb.watch") || u.contains("fb.com")) return "facebook";
        if (u.contains("tiktok.com") || u.contains("vt.tiktok") || u.contains("vm.tiktok")) return "tiktok";
        if (u.contains("instagram.com") || u.contains("instagr.am")) return "instagram";
        if (u.contains("twitter.com") || u.contains("x.com") || u.contains("t.co")) return "twitter";
        if (u.contains("snapchat.com")) return "snapchat";
        if (u.contains("vimeo.com")) return "vimeo";
        return "unknown";
    }

    /**
     * Deteksi watermark dari Bitmap (frame video).
     * Return null kalau tidak ada watermark terdeteksi.
     */
    public static WatermarkArea detectFromBitmap(Bitmap bitmap, String url) {
        if (bitmap == null) return null;

        try {
            String platform = detectPlatform(url);

            // Convert Bitmap ke Mat
            Mat src = new Mat();
            Utils.bitmapToMat(bitmap, src);

            int width = src.cols();
            int height = src.rows();

            // Convert ke grayscale
            Mat gray = new Mat();
            Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY);

            // Edge detection
            Mat edges = new Mat();
            Imgproc.Canny(gray, edges, 50, 150);

            // Dilate untuk gabungkan edge yang dekat
            Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(5, 5));
            Imgproc.dilate(edges, edges, kernel);

            // Cari contours
            List<MatOfPoint> contours = new ArrayList<>();
            Mat hierarchy = new Mat();
            Imgproc.findContours(edges, contours, hierarchy,
                Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

            // Filter contour yang masuk kriteria watermark
            WatermarkArea best = findBestWatermarkArea(contours, width, height, platform);

            // Cleanup
            src.release();
            gray.release();
            edges.release();
            kernel.release();
            hierarchy.release();

            if (best != null) {
                Log.d(TAG, "Watermark detected: " + best.label +
                    " at (" + best.xPercent + ", " + best.yPercent + ") " +
                    "confidence: " + best.confidence);
                return best;
            }

            // Fallback: pakai heuristic based on platform
            return getHeuristicArea(platform);

        } catch (Exception e) {
            Log.e(TAG, "Detection error", e);
            return getHeuristicArea(detectPlatform(url));
        }
    }

    /**
     * Cari area watermark dari contours.
     */
    private static WatermarkArea findBestWatermarkArea(List<MatOfPoint> contours,
                                                       int width, int height,
                                                       String platform) {
        List<WatermarkArea> candidates = new ArrayList<>();

        for (MatOfPoint contour : contours) {
            Rect rect = Imgproc.boundingRect(contour);

            // Filter berdasarkan ukuran
            double areaPercent = (rect.width * rect.height) / (double)(width * height);

            // Watermark biasanya 0.5% - 15% dari area
            if (areaPercent < 0.005 || areaPercent > 0.15) continue;

            // Watermark biasanya aspect ratio antara 1:1 sampai 10:1
            double aspectRatio = (double)rect.width / rect.height;
            if (aspectRatio < 0.5 || aspectRatio > 10) continue;

            // Watermark biasanya di area pinggir (bukan tengah)
            double centerX = (rect.x + rect.width / 2.0) / width;
            double centerY = (rect.y + rect.height / 2.0) / height;

            boolean isEdge = centerX < 0.25 || centerX > 0.75 ||
                             centerY < 0.15 || centerY > 0.75;

            if (!isEdge) continue;

            // Hitung confidence
            float confidence = 0.5f;
            confidence += (float)(areaPercent * 3); // Area sedang = lebih confident
            if (isEdge) confidence += 0.2f;

            // Bonus confidence kalau posisi sesuai platform
            if (platformMatchesPosition(platform, centerX, centerY)) {
                confidence += 0.2f;
            }

            WatermarkArea candidate = new WatermarkArea(
                (float)rect.x / width,
                (float)rect.y / height,
                (float)rect.width / width,
                (float)rect.height / height,
                "Detected (" + platform + ")",
                platform,
                Math.min(confidence, 1.0f)
            );

            candidates.add(candidate);
        }

        // Ambil candidate dengan confidence tertinggi
        if (candidates.isEmpty()) return null;

        WatermarkArea best = candidates.get(0);
        for (WatermarkArea c : candidates) {
            if (c.confidence > best.confidence) best = c;
        }

        // Hanya return kalau confidence > 0.6
        return best.confidence > 0.6 ? best : null;
    }

    /**
     * Cek apakah posisi sesuai dengan karakteristik platform.
     */
    private static boolean platformMatchesPosition(String platform, double cx, double cy) {
        switch (platform) {
            case "tiktok":
                // TikTok: kiri bawah atau kanan bawah
                return (cx < 0.4 && cy > 0.7) || (cx > 0.6 && cy > 0.7);
            case "instagram":
                // IG: bawah tengah
                return cy > 0.7;
            case "facebook":
                // FB: kanan bawah
                return cx > 0.6 && cy > 0.7;
            case "youtube":
                // YT: kanan bawah
                return cx > 0.6 && cy > 0.7;
            case "twitter":
                // Twitter: kanan bawah
                return cx > 0.6 && cy > 0.7;
            default:
                return true;
        }
    }

    /**
     * Fallback heuristic berdasarkan platform (tanpa image recognition).
     */
    public static WatermarkArea getHeuristicArea(String platform) {
        switch (platform) {
            case "tiktok":
                return new WatermarkArea(0.10f, 0.85f, 0.40f, 0.12f,
                    "TikTok username (heuristic)", platform, 0.5f);
            case "instagram":
                return new WatermarkArea(0.10f, 0.88f, 0.35f, 0.10f,
                    "IG username (heuristic)", platform, 0.5f);
            case "facebook":
                return new WatermarkArea(0.88f, 0.92f, 0.10f, 0.06f,
                    "FB logo (heuristic)", platform, 0.5f);
            case "youtube":
                return new WatermarkArea(0.85f, 0.90f, 0.14f, 0.08f,
                    "YT watermark (heuristic)", platform, 0.5f);
            case "twitter":
                return new WatermarkArea(0.88f, 0.88f, 0.10f, 0.08f,
                    "Twitter logo (heuristic)", platform, 0.5f);
            default:
                return new WatermarkArea(0.85f, 0.88f, 0.13f, 0.10f,
                    "Unknown watermark (heuristic)", platform, 0.4f);
        }
    }

    /**
     * Detect dari URL tanpa bitmap (fallback).
     */
    public static WatermarkArea detect(String url) {
        return getHeuristicArea(detectPlatform(url));
    }
}
