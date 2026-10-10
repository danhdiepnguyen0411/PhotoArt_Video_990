---
name: android-bundle-id-migration
description: Change, update, or refactor Android Bundle ID (applicationId) and package/namespace directory structure. Use when changing bundle ID for Google Play Store, rebranding, preparing white-label apps, or refactoring Kotlin/Java package hierarchy.
---

# Android Bundle ID & Package Migration

Tài liệu chuẩn hóa quy trình đổi Bundle ID (`applicationId`) và tái cấu trúc thư mục package (`namespace`) trong dự án Android.

---

## 1. Nguyên Tắc Cốt Lõi (Core Principle)

Trong Android Gradle Plugin (AGP 7.0+):
- **`applicationId` (Bundle ID)**: Định danh phân phối thực tế của app trên Google Play Store, Keystore, OS, Firebase, AdMob, In-App Purchase.
- **`namespace` (Code Package)**: Không gian tên nội bộ phục vụ trình biên dịch R/BuildConfig và cây thư mục vật lý.

> [!IMPORTANT]
> **Quy tắc ưu tiên**: Khi đổi tên app / rebrand / white-label, **luôn ưu tiên Cấp độ 1** (chỉ đổi `applicationId`). Chỉ dùng **Cấp độ 2** khi người dùng yêu cầu rõ ràng việc đổi cả thư mục nguồn.

---

## 2. Cấp Độ 1: Đổi Bundle ID Nhanh (Khuyên Dùng — 30s)

Chỉ cập nhật đúng **2 file cấu hình**, không di dời file, không gây xung đột git:

1. **`app/build.gradle.kts`**:
   ```kotlin
   defaultConfig {
       applicationId = "com.company.newappid" // Đổi tại đây
   }
   ```
2. **`app/src/main/res/xml/shortcuts.xml`**:
   ```xml
   <intent
       android:targetPackage="com.company.newappid"
       ... />
   ```

*Các thành phần khác tự động đồng bộ*:
- `AndroidManifest.xml`: `${applicationId}.fileprovider` và `${applicationId}.androidx-startup` tự động nhận ID mới.
- Code Kotlin: `BuildConfig.APPLICATION_ID` và `context.packageName` tự động trả về ID mới.

---

## 3. Cấp Độ 2: Đổi Toàn Diện Thư Mục & Namespace (Full Refactor)

Dùng khi cần đổi toàn bộ thư mục `java/com/...` và `package` trong code.

### 3.1. Chạy 1 Lệnh Tự Động Duy Nhất:
```bash
./.agents/skills/android-bundle-id-migration/scripts/migrate-bundle-id.sh <OLD_PKG> <NEW_PKG> --full-refactor
```

### 3.2. Bảng 7 Điểm Chạm Kỹ Thuật (Reference Checklist)
Nếu cần kiểm tra hoặc thao tác thủ công:

| # | Thành phần | File / Thư mục | Hành động |
|---|---|---|---|
| 1 | **Gradle Config** | `app/build.gradle.kts` | Cập nhật `applicationId` và `namespace`. |
| 2 | **Cây thư mục** | `app/src/{main,debug,test}/java/` | `git mv` từ `old/path` sang `new/path`. Xóa thư mục rỗng cũ. |
| 3 | **Source Code** | Toàn bộ file `.kt` / `.java` | Đổi `package <pkg>` và các `import <pkg>.*`. |
| 4 | **Layout XMLs** | `app/src/main/res/layout/*.xml` | Đổi package của các thẻ Custom View (VD: `<com.company.view.CustomView>`). |
| 5 | **App Shortcuts** | `app/src/main/res/xml/shortcuts.xml` | Đổi `android:targetPackage`, `targetClass`, `action`. |
| 6 | **Debug Manifest**| `app/src/debug/AndroidManifest.xml` | Đổi action receiver (dùng `${applicationId}.DEBUG_NOTIFICATION`). |
| 7 | **ProGuard** | `app/proguard-rules.pro` | Đổi package trong các rule `-keep class <pkg>.**`. |

---

## 4. Xác Minh Sau Khi Đổi (Verification)

Chạy tuần tự các lệnh sau:
```bash
./gradlew clean
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew assembleRelease
```
- Kiểm tra kết quả: `cat app/build/outputs/apk/release/output-metadata.json | grep applicationId`
- Kiểm tra chuỗi cũ: `git grep "<OLD_PKG>"` (kết quả phải là 0).
