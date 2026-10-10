# Báo cáo triển khai: badge NEW (first-seen phía client) + icon premium. Bản cuối

## Quyết định user (vòng 3)
- API KHÔNG trả `createdAt`. Tôi đã kiểm raw JSON 242 item trong disk cache: các key chỉ gồm `categoryCode, code, description, imageUrl, maxInputImages, name, premium, rank, templateType, videoThumbnailUrl, videoUrl`. Vì vậy NEW dùng cơ chế first-seen phía client (plan §3-4).
- Luật theo `createdAt` đã bỏ hẳn:
  - Đã xoá `TemplateCreatedAtParser.kt`.
  - `ArtTemplateRepositoryImpl` trở lại `optLong("createdAt", 0L)` như HEAD.
  - Field `ArtTemplate.createdAt` vẫn giữ vì các chỗ khác còn dùng (test, `buildHeroBanners`).
- Icon video: giữ theo commit `cd0ce5b`, tức là ĐÃ XOÁ khỏi card Home/Discover và không thêm lại. Premium hiển thị bằng crown vàng (`ic_premium_crown` + `bg_badge_premium_glass`) ở góc trên trái. Bind đặt `isVisible = template.premium` theo cả hai chiều. Featured (`HomeTrendingAdapter`) hiện EXCLUSIVE chỉ khi `premium`.

## Luật hiển thị
- Chỉ dùng MỘT bảng `template_badge_state(template_code TEXT PK, first_seen_at INTEGER NOT NULL, opened_at INTEGER NULL)`. Bảng `opened_template` đã bỏ.
- `syncCatalog(codes)` (gọi sau mỗi lần nạp feed ở Home và trong `BaseDiscoverViewModel.loadData`):
  - Danh sách rỗng thì bỏ qua.
  - Bảng rỗng: ghi tất cả code với BASELINE = 0.
  - Ngược lại: `INSERT OR IGNORE` code mới với `now`.
- `isNewCandidate`: `firstSeen > 0` và tuổi `(now - firstSeen)` nằm trong `[0, 7 ngày]`, tuổi âm kẹp về 0. Hằng số `NEW_BADGE_WINDOW_MS`.
- `isNew` = `isNewCandidate` và chưa mở. Mở template thì NEW mất vĩnh viễn.
- Mỗi section Home tối đa 1 NEW, chọn template hợp lệ ĐẦU TIÊN theo thứ tự backend trong 3 card đang hiện. Không sort ở client.
- `markOpened`:
  - Gọi từ `HomeViewModel.onTemplateClick` (`HomeFragment` gọi trước khi điều hướng) và `BaseDiscoverFragment`.
  - Cập nhật StateFlow ngay, rồi ghi Room trên `CoroutineScope(SupervisorJob() + Dispatchers.IO)` dạng singleton.

## File (phần của task)
Mới:
- `data/local/entity/TemplateBadgeStateEntity.kt`
- `data/local/dao/TemplateBadgeStateDao.kt`
- `domain/model/TemplateBadgeState.kt`
- `domain/repository/TemplateBadgeRepository.kt`, `data/repository/TemplateBadgeRepositoryImpl.kt`
- `domain/policy/NewBadgePolicy.kt`
- `ui/home/model/HomeSectionUi.kt`

Sửa:
- `AppDatabase.kt` (v9), `di/DatabaseModule.kt` (`migration8To9`), `di/RepositoryModule.kt`
- `HomeViewModel.kt`, `HomeUiState.kt`, `HomeFragment.kt`
- `HomeSectionAdapter.kt` (`ListAdapter` + `PAYLOAD_BADGES`), `HomeTrendingAdapter.kt`
- Discover:
  - `BaseDiscoverViewModel`, `DiscoverViewModel`, `DiscoverPhotosViewModel`, `DiscoverVideosViewModel`
  - `DiscoverUiState`, `DiscoverTemplateAdapter`, `BaseDiscoverFragment`
  - `SelectImageTemplateViewModel`
- Layout `item_home_template_card.xml` (sửa comment header), `item_discover_template_card.xml`
- 16 file `strings.xml`, glossary
- Test có sẵn (chỉ sửa constructor/fake, không thêm test): `HomeViewModelTest`, `DiscoverViewModelTest`, `SelectImageTemplateViewModelTest`

## Discover
- Đã bỏ `rank` và hack `categoryCode == "NEW"`.
- Badge dùng `NewBadgePolicy.isNew`, không giới hạn 1 vì Discover là lưới.
- Tab New = `isNewCandidate` (first-seen trong 7 ngày), chỉ lọc, giữ thứ tự backend.
  - Template đã mở VẪN nằm trong tab, chỉ mất badge (finding #4).
  - Khi badge state đổi thì lọc lại trên `Dispatchers.Default`.
- `SelectImageTemplateViewModel` collect `state` và lọc lại tab New khi state load xong (finding #5).
- Card Discover không có icon video. Crown premium hiển thị theo `template.premium`.

## Kiểm chứng
- `./gradlew assembleDebug`: PASS.
- `./gradlew test --rerun-tasks`: PASS.
- `./gradlew lintDebug`: PASS. Chỉ còn 5 Error có sẵn ở file không đụng tới (`NotificationHelper`, `styles.xml`, `themes.xml`, `SettingFragment`). File của task chỉ có cảnh báo `SmallSp` có từ trước.
- Câu CREATE trong `migration8To9` khớp từng ký tự với `AppDatabase_Impl` do Room sinh.
- Thiết bị HONOR `A7LQCP4B05412266`. Tôi dựng DB v8 (hash `ad598d5a…`, 2 favorite, không có bảng badge) rồi `adb install -r` đè lên bản cũ:
  1. Sau migration: `user_version` = 9, hash `d579711c…` khớp bản build, 2 favorite vẫn còn, `template_badge_state` đúng schema. 239 dòng đều `first_seen_at = 0`. Home không có NEW nào. Không crash.
  2. Xoá 3 dòng (Neon Vector, Standing In The Sea, Blue Space Dancing) rồi mở lại app. Cả 3 được insert lại với `now`.
     - `uiautomator dump`: section Trending photos chỉ có 1 NEW ở Neon Vector (đầu tiên theo thứ tự backend), Standing In The Sea không có badge.
     - Video funny: NEW ở Blue Space Dancing.
  3. Chạm Neon Vector mở CreatePhotoActivity rồi Back.
     - `opened_at` được ghi.
     - Ảnh chụp 300 ms sau Back: Neon Vector mất NEW, ảnh vẫn hiện, không nháy hay về placeholder.
     - Slot NEW của section chuyển sang Standing In The Sea, vì nó là template hợp lệ kế tiếp. Đúng luật "tối đa 1 NEW, chọn template đầu tiên hợp lệ".
  4. Không còn icon video trên card nào.
- Lưu ý: trong lúc kiểm, thiết bị có input từ session khác (tự chuyển sang tab Video, mở Beanie Headphones). Vì vậy kết quả badge tôi lấy từ `uiautomator dump` và DB, không chỉ dựa vào ảnh chụp.
- File tạm trong /tmp và /sdcard đã xoá.

## Chưa kiểm chứng
- Crown premium trên máy: dữ liệu thật không có template premium nào (0/242).
- Video không khởi động lại sau khi badge đổi: chỉ suy ra từ code (payload bind không gọi Glide hay autoplay).
- RTL và độ dài chuỗi theo từng locale trên máy.
- Hạn chế của cơ chế first-seen:
  - Cài mới thì lần đầu không có NEW nào.
  - Template bị gỡ rồi thêm lại sẽ không NEW lại.
  - Mỗi thiết bị thấy NEW khác nhau.
- Thay đổi song song không thuộc task này (cần tách trước khi commit):
  - `ArtTemplateRepositoryImpl`: loại trừ `AI_TOOL`, `isFeaturedCategory`.
  - `trendingThumbnailUrl`.
  - Khoảng 40 file dialog, `CinemaTopAppBar`, `PhotoResult`.
- Tôi KHÔNG commit. Trong lúc tôi kiểm chứng, một session khác đã commit toàn bộ working tree vào `4dbb346` ("feat: redesign popup dialogs … and implement template new badge policy"). Commit đó gộp cả code badge (đúng bản cuối ở trên) lẫn các thay đổi song song. Hiện chỉ còn file báo cáo này chưa commit.
