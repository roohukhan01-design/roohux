# RoohuX — Android Music & Video App

**Version 1 source project (prototype).** Native Kotlin Android app, no paid API key required.

## Features implemented
- Online search of Internet Archive public media catalog
- Browse available audio/video files and stream using Android Media3 ExoPlayer
- Open local audio/video via Android document picker
- Download selected media through Android DownloadManager (only where permitted)
- Local favorites via SharedPreferences
- AMOLED-inspired dark interface and mini player
- GitHub Actions debug APK workflow

## Build APK on GitHub using your phone
1. Create a new GitHub repository named `RoohuX`.
2. Extract the ZIP. Upload **the files and directories** to the repository root. GitHub web upload can accept selected files; the `.github/workflows/android.yml` directory structure must be preserved. Alternatively use GitHub's web editor to create workflow file.
3. Open **Actions** > **Build RoohuX APK** > **Run workflow** (or push to main).
4. When the job finishes, download the `RoohuX-debug-apk` artifact and extract `app-debug.apk`.
5. Install the debug APK on your Android phone. You may need to allow installation from that source.

## Honest limitations
- This is a starting version, **not a fully tested production app**. APK compilation and device playback have not been verified here.
- Search is limited to Internet Archive; it is not a universal commercial song/video search.
- Downloads are only lawful when media rights permit; personal use alone does not guarantee permission.
- No background playback service, lock-screen media controls, subtitles UI, playlist editor, or equalizer yet.
- Playback stops when the activity is destroyed; a proper MediaSessionService is a future milestone.
- Not every Internet Archive file is compatible or streamable.
- Google Play publication would require additional testing, permissions review, and policy work.

Developer: Roohu | Support: roohukhan01@gmail.com
