# YadDownloader

Video downloader Android — pakai NewPipe Extractor.

## Site yang Didukung

NewPipe Extractor hanya support:

- YouTube
- SoundCloud
- PeerTube
- Bandcamp
- MediaCCC

Tidak support Facebook, TikTok, Instagram, Twitter/X.

## Cara Download APK

1. Buka tab **Actions** di repo ini
2. Klik workflow **Build APK** paling atas
3. Scroll ke **Artifacts** di bawah
4. Klik **YadDownloader-debug** untuk download ZIP
5. Extract ZIP, install `app-debug.apk` di HP

## Cara Pakai

1. Buka app
2. Paste link video (atau share dari YouTube)
3. Klik **Info** untuk cek judul/uploader
4. Klik **Download Video**
5. Tunggu selesai, cek folder `Downloads/YadDownloader`

## Fitur

- Download video mp4
- Cek info video
- Progress bar real-time
- Share intent support
- Log detail

## Known Issues

- Download file bisa besar (100MB+ untuk video panjang)
- Butuh izin storage manual di Android 11+
- Beberapa video YouTube mungkin butuh login (belum support)

## Build Sendiri

Via GitHub Actions: push ke `main` otomatis build.

Atau via Android Studio:
1. Buka repo
2. Build -> Build APK

## Peringatan

- Gunakan untuk konten yang kamu punya hak
- Bukan untuk distribusi Play Store
- YouTube sering update algoritma, app mungkin perlu di-update berkala

MIT License - Copyright 2026 YadDownloader


<!-- Build trigger: 2026-09-25T09:08:11.543521 -->


<!-- Build: 2026-09-25T09:14:40.069819 -->
