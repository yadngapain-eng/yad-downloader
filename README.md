# YadDownloader

Video downloader Android — download YouTube, Facebook, TikTok, Instagram, dll.

**Build otomatis via GitHub Actions.**

## Cara Download APK

1. Buka tab **Actions** di repo ini
2. Klik workflow **Build APK** yang hijau (paling atas)
3. Scroll ke bawah ke bagian **Artifacts**
4. Klik **YadDownloader-debug** untuk download ZIP
5. Extract ZIP → install `app-debug.apk` di HP

## Cara Pakai

1. Buka app → tunggu yt-dlp selesai download (~30 detik pertama)
2. Paste link video (atau share dari YouTube/FB → pilih YadDownloader)
3. Klik **Download Video**
4. Cek folder **Downloads/YadDownloader** di HP

## Fitur
- ✅ Offline (yt-dlp binary bundled otomatis di runtime)
- ✅ Support YouTube, Facebook, TikTok, Instagram, Twitter/X, dll
- ✅ Share intent (share dari app lain langsung masuk)
- ✅ Cek info video dulu (judul, uploader, durasi)
- ✅ Progress log real-time

## Build Sendiri
- Buka repo di Android Studio
- Build → Build APK

Atau push ke `main` → GitHub Actions build otomatis.

## ⚠️ Peringatan
- Gunakan untuk konten yang kamu punya hak atau fair use
- YouTube sering update algoritma — kalau gagal, app otomatis download yt-dlp versi terbaru
- Bukan untuk distribusi Play Store

© 2026 YadDownloader — Dibuat oleh Ysdev
