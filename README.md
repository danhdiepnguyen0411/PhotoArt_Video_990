# PhotoArt_Video_990

AI Photo & Video Restoration Android Application.

## Tech Stack & Architecture
- Language: Kotlin
- UI Framework: XML Views & ViewBinding
- Navigation: Android Jetpack Navigation Component
- Networking & Data: Retrofit2, OkHttp, Gson
- Image Loading: Coil
- SDK Integration: ArtMagic SDK (lib-release.aar)

## Features & Navigation Flow
- **Splash Screen**: Animated loading progress with branded visuals.
- **First Language Screen**: Multi-language selection (English, Vietnamese, Spanish, French, Hindi, Japanese, Korean).
- **Intro (1-4)**: 4-step onboarding slider with ViewPager2.
- **Main Dashboard**:
  - Bottom Navigation with 3 tabs: **Home**, **History**, **Setting**.
  - **Home Screen** featuring 5 AI tools:
    1. Gom kỷ niệm (Memories)
    2. Khôi phục ảnh (Restore)
    3. Làm nét (Enhance)
    4. Ghép nhạc (Add Music)
    5. Nâng cấp ảnh (Upscale)
