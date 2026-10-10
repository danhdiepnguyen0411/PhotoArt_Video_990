# Kế hoạch gỡ bỏ feature line Short-Drama / Movie Series (PHOTO-ART)

> Báo cáo điều tra READ-ONLY. Không có file nào bị chỉnh sửa ngoài báo cáo này. Tất cả dẫn chứng kèm `file:line`.
> Mục tiêu: xác định phần tính năng "short-drama / movie series" nào thực sự chết (dead) và lập kế hoạch gỡ bỏ an toàn, theo thứ tự, luôn compile được. KHÔNG đụng tới phần NEW-badge (TemplateBadgeState/Repository, NewBadgePolicy, bảng `template_badge_state`, `favorite_templates`, `saved_videos`).

---

## 1. TÓM TẮT KẾT LUẬN (đọc trước)

1. **Người dùng KHÔNG thể tới được feature drama/search/history**. `SearchActivity` và màn `ui/history` (`HistoryFragment`/`MyListViewModel`) hoàn toàn không có đường vào trong UI đang ship (bottom nav, menu, nav graph, click listener). `AppNavigator.openSearch()` tồn tại nhưng KHÔNG có nơi nào gọi. Tab "My List" của bottom nav trỏ tới `ui/library/LibraryFragment` (của photo-art), KHÔNG phải `ui/history`.
2. **Pipeline thông báo (notification) KHÔNG chết — nó vẫn chạy**. `LocalNotificationScheduler.schedule()` được gọi trong `FilmApplication.onCreate()` (`FilmApplication.kt:43`), nên `LocalNotificationWorker` + `NotificationCoordinator` chạy định kỳ (3h) và theo "golden slot" kể cả khi không có UI. Tuy nhiên toàn bộ nội dung notification lấy từ `DramaRepository` → `DramaRemoteDataSource` (endpoint VOD short-drama). Đây là code còn **sống về mặt thực thi nhưng vô nghĩa về nghiệp vụ** cho một app photo-art ⇒ cần gỡ, nhưng phải gỡ bằng cách **ngắt lịch (unschedule) trước**, không xoá mù.
3. **Có cross-link vào các thành phần KEEP** cần cắt cẩn thận (không xoá nguyên file):
   - `AuthRepositoryImpl` (KEEP) inject `MyListRepository`, `WatchHistoryDao`, `FollowingDao`, `ReminderDao` để sync lúc login và xoá Room lúc xoá tài khoản.
   - `ErrorMapper` (KEEP, dùng bởi `BaseDiscoverViewModel`) phụ thuộc kiểu `DramaApiException`.
   - `AppPreferenceManager` (KEEP) phụ thuộc `DramaPolicy` qua `addFreeWatchMinutes()` (hàm leftover, không ai gọi).
   - `FilmApplication.onCreate()` gọi `DramaReleaseNotificationHelper.createNotificationChannel()` và `LocalNotificationScheduler.schedule()`.
4. **Thành phần dùng chung tên "Film" nhưng thực chất PHOTO-ART** phải GIỮ: `FilmImageLoader` (object), `AiVideoNotificationHelper`, `NotificationHelper`, `CustomNotificationDialog`, `NotificationPrefManager`, `ErrorMapper`, hạ tầng network OkHttp/Gson. Riêng `FilmCoverImageView`, `CoverUrlHelper`, extension `ImageView.loadDramaCover` chỉ phục vụ drama ⇒ REMOVE.
5. **Room**: AppDatabase hiện ở version 10 (migration 8→9, 9→10). Để bỏ 3 bảng drama phải lên **version 11** + thêm `migration10To11` DROP 3 bảng `watch_history`, `followed_series`, `reminder_series`. Có cảnh báo về `fallbackToDestructiveMigration()` (xem Mục 5).

---

## 2. REACHABILITY (dẫn chứng)

### 2.1. Entry points khai báo trong Manifest
`app/src/main/AndroidManifest.xml`:
- Launcher duy nhất: `ui.splash.SplashActivity` (`exported=true`, intent LAUNCHER).
- `ui.main.MainActivity` là host bottom nav (`exported=false`).
- `ui.search.SearchActivity` ĐƯỢC khai báo (`exported=false`) nhưng không có `intent-filter` → chỉ mở được qua `startActivity` nội bộ.
- Các activity còn lại đều là photo-art (SelectImageTemplate, ViewVideoTemplate, CreatePhoto, SelectImage, PhotoResult, CreateVideo, VideoResult) hoặc hệ thống (Language/Intro/Paywall/Login/Uninstall/Setting).
- **Không có** activity drama player nào trong manifest (đã bị gỡ trước đó).

### 2.2. Bottom navigation KHÔNG trỏ tới drama
- `res/menu/bottom_nav_menu.xml`: 5 item `navigation_home`, `navigation_video`, `navigation_image`, `navigation_my_list`, `navigation_profile`.
- `res/navigation/nav_main.xml`: `navigation_my_list` → `ui.library.LibraryFragment`; `navigation_video`/`navigation_image`/`navigation_foryou` → `DiscoverVideosFragment`/`DiscoverPhotosFragment` (photo-art).
- `MainActivity.createFragmentForTab()` (`MainActivity.kt:196-204`): `navigation_my_list -> LibraryFragment()`. **KHÔNG hề tạo `HistoryFragment`**.
- `grep HistoryFragment|LibraryFragment`: `HistoryFragment` chỉ xuất hiện ở chính nó (`ui/history/HistoryFragment.kt:40`), không nơi nào khởi tạo. `LibraryFragment` được dùng ở `MainActivity.kt:20,200`.

**Kết luận**: màn `ui/history` (My List của drama) KHÔNG reachable.

### 2.3. SearchActivity KHÔNG reachable
- `grep openSearch\(|SearchActivity` toàn `app/src/main`: chỉ khớp định nghĩa `AppNavigator.openSearch()` (`AppNavigator.kt:157-161`) và nội tại `SearchActivity.kt`. **Không có caller** (không nút, không menu, không click listener, không XML `onClick`).
- `SearchActivity` dùng `HomeFilm3ColAdapter` (`SearchActivity.kt:29,43-45,72-93`) ⇒ `HomeFilm3ColAdapter` chỉ sống trong luồng search không reachable.

### 2.4. AppNavigator
- `AppNavigator.openDramaPlayer()` là no-op; `createDramaPlayerIntent()` trả Intent trỏ `MainActivity` (`AppNavigator.kt:187-213`) ⇒ tàn dư.
- `openSearch()` không ai gọi ⇒ tàn dư.

---

## 3. NOTIFICATION PIPELINE (QUAN TRỌNG — ĐANG CHẠY)

### 3.1. Có được schedule không? — CÓ
- `FilmApplication.onCreate()`:
  - `DramaReleaseNotificationHelper.createNotificationChannel(this)` (`FilmApplication.kt:39`).
  - `LocalNotificationScheduler.schedule(this@FilmApplication)` chạy trên coroutine (`FilmApplication.kt:42-44`).
- `LocalNotificationScheduler.schedule()` (`LocalNotificationScheduler.kt:24-37`): enqueue `PeriodicWorkRequest<LocalNotificationWorker>` mỗi 3h (`UNIQUE_PERIODIC_WORK_NAME`) + `scheduleNextGoldenSlot()` (one-time 12:00/18:30/20:30/22:30).
- `LocalNotificationWorker` (`LocalNotificationWorker.kt:11-16`) là `@HiltWorker`, inject `NotificationCoordinator`. WorkManager default initializer bị tắt trong manifest; `FilmApplication` cấp `HiltWorkerFactory` qua `Configuration.Provider`.

**Vì được schedule, worker chạy không cần UI ⇒ pipeline KHÔNG dead. Phải unschedule (gọi `LocalNotificationScheduler.cancel()` một lần, hoặc gỡ hẳn lời gọi schedule + cho worker tự tắt) TRƯỚC khi xoá code.**

### 3.2. Nó làm gì? — chỉ phục vụ drama
`NotificationCoordinator.run()` (`NotificationCoordinator.kt:28-85`) lấy "catalog" qua `dramaRepository.getSeriesPage()/getCachedSeriesPage()` (`NotificationCoordinator.kt:90-96`), đọc `watchHistoryDao`, `followingDao`, `reminderDao` để sinh notification "continue watching", "release", "golden slot", "re-engagement"... Tất cả đều là nghiệp vụ short-drama. `DramaRepository` gọi endpoint VOD (`DramaRemoteDataSource` + `VodEndpoints`, BuildConfig `VOD_EXTERNAL_*`). Với backend photo-art, dữ liệu này rỗng/không liên quan.

**Kết luận**: pipeline sống nhưng là dead-weight nghiệp vụ ⇒ REMOVE toàn bộ sau khi unschedule. `scheduleContinueWatchingCheck()` không có caller.

---

## 4. DEPENDENCY GRAPH (ai inject/tham chiếu)

| Type | Người tham chiếu | Phán quyết |
| :-- | :-- | :-- |
| `DramaRepository` / `DramaRepositoryImpl` | Bind ở `RepositoryModule.kt:22`; inject vào `NotificationCoordinator.kt:21`, `SearchViewModel.kt:45` | REMOVE (sau khi gỡ coordinator + search) |
| `MyListRepository` / `MyListRepositoryImpl` | Bind ở `MyListModule.kt:20`; inject vào `MyListViewModel.kt:26` (dead UI) và **`AuthRepositoryImpl.kt:36` (KEEP)** | REMOVE repo; **cắt** reference khỏi `AuthRepositoryImpl` |
| `NotificationCoordinator` | Inject vào `LocalNotificationWorker.kt:14` | REMOVE |
| `WatchHistoryDao` | `AppDatabase.kt:34`, `DatabaseModule.kt:97`, `NotificationCoordinator.kt:22`, `MyListRepositoryImpl.kt:40`, **`AuthRepositoryImpl.kt:38` (KEEP)** | REMOVE accessor + provides; **cắt** khỏi AuthRepositoryImpl |
| `FollowingDao` | `AppDatabase.kt:35`, `DatabaseModule.kt:102`, `NotificationCoordinator.kt:23`, `MyListRepositoryImpl.kt:41`, **`AuthRepositoryImpl.kt:39` (KEEP)** | REMOVE accessor + provides; **cắt** khỏi AuthRepositoryImpl |
| `ReminderDao` | `AppDatabase.kt:36`, `DatabaseModule.kt:107`, `NotificationCoordinator.kt:22`, `MyListRepositoryImpl.kt:42`, **`AuthRepositoryImpl.kt` (clearAllReminders)** | REMOVE accessor + provides; **cắt** khỏi AuthRepositoryImpl |
| `WatchHistoryEntity` / `FollowedSeriesEntity` / `ReminderSeriesEntity` | `AppDatabase.kt:11-14, 20-23` | REMOVE khỏi `@Database.entities` + xoá file |
| `DramaSeries` / `DramaModels` / `DramaEpisode` | DTO/mapper/search/coordinator/`SurveyRanker`/`FilmImageLoader.loadDramaCover`/`FilmCoverImageView` | REMOVE |
| `DramaPolicy` | `AppPreferenceManager.kt:500` (addFreeWatchMinutes, **không ai gọi**), `DramaModels`, `SeriesDto`/`SeriesDetailDto`/`SeriesListResponseDto`, `DramaMapper`, `SearchViewModel` | REMOVE; **cắt** `addFreeWatchMinutes`/`canClaimFreeWatchToday` khỏi `AppPreferenceManager` |
| `DramaApiException` | `DramaRemoteDataSource` (ném), **`ErrorMapper.kt:19` (KEEP)**, `NetworkModule` (chỉ dùng BuildConfig, không dùng class) | Xem Mục 6 — rủi ro cao, cần quyết định giữ class hay bỏ nhánh ErrorMapper |

### Cross-link KEEP phụ thuộc drama (phải cắt khéo, không xoá file):
- `AuthRepositoryImpl.kt:36-39,61,120-124`: `syncSavedSeriesWithRemote()` lúc login + `watchHistoryDao.clearAllHistory()/followingDao.clearAllFollowing()/reminderDao.clearAllReminders()` trong `performLocalAccountCleanup()`.
- `AppPreferenceManager.kt:496-504`: hàm free-watch leftover tham chiếu `DramaPolicy`.
- `ErrorMapper.kt:19-28`: nhánh `is DramaApiException`.
- `FilmApplication.kt:39,42-44`: tạo channel + schedule.

---

## 5. SHARED / AMBIGUOUS — KEEP vs REMOVE

| Thành phần | Ai dùng (dẫn chứng) | Phán quyết |
| :-- | :-- | :-- |
| `DramaSeries` (`DramaModels.kt`) | Chỉ luồng drama/search/coordinator/mapper; **Home dùng `ArtTemplate`** (`HomeViewModel.kt`), Discover dùng `ArtTemplate` (`BaseDiscoverViewModel.kt`); `grep DramaSeries` trong `ui/discover` = 0 | **REMOVE** |
| `FilmCoverImageView` | Chỉ 3 layout drama: `item_home_film_2col.xml`, `item_home_film_3col.xml`, `item_foryou_movie_card.xml`; code `FilmImageLoader.loadDramaCover`/`FilmCoverImageView.kt` | **REMOVE** |
| `CoverUrlHelper` | Chỉ `FilmCoverImageView.kt:144` (+ test `CoverUrlHelperTest`) | **REMOVE** (cùng FilmCoverImageView) |
| `HomeFilm3ColAdapter` | Chỉ `SearchActivity.kt:43-45` | **REMOVE** |
| `DramaGridAdapter` | Không caller (chỉ `item_drama_grid.xml`) | **REMOVE** |
| `DramaSeriesAdapter` | Không caller (chỉ `item_drama_series.xml`) | **REMOVE** |
| `SeriesBadgeHelper` (`ui/home/util`) | Chỉ `HomeFilm3ColAdapter.kt:14,75-83` | **REMOVE** (lưu ý: KHÁC `NewBadgePolicy`/`TemplateBadgeState` — những cái đó GIỮ) |
| `SurveyRanker` | Không caller trong main (chỉ test `SurveyRankerTest`) | **REMOVE** |
| `FilmImageLoader` (object) | KEEP — dùng bởi `ArtTemplateRepositoryImpl`, `CreatePhotoActivity`, `PhotoResultActivity`, `CreateVideoActivity`, `HomeViewModel` | **KEEP** (chỉ xoá extension `loadDramaCover` trong cùng file) |
| `AiVideoNotificationHelper` | KEEP — `VideoGenerationWorker`, `VideoResultActivity`, `VideoCompletedDialogFragment`, `FilmApplication` | **KEEP** |
| `NotificationHelper` / `CustomNotificationDialog` / `NotificationPrefManager` | KEEP — `MainActivity`, publisher | **KEEP** (lưu ý `LocalNotificationPublisher`/`NotificationCandidate`/`NotificationLedger` là drama ⇒ REMOVE) |
| `ErrorMapper` | KEEP — `BaseDiscoverViewModel.kt:179` | **KEEP** |
| `DramaReleaseNotificationHelper` | Chỉ `FilmApplication.kt:39` (tạo channel) | **REMOVE** (cắt khỏi FilmApplication) |

> ⚠️ Lưu ý đặt tên gây nhầm: `FilmImageLoader`, `AiVideoNotificationHelper`, `NotificationHelper` mang chữ "Film"/"notification" nhưng là hạ tầng photo-art — GIỮ. Chỉ các lớp gắn `Drama*`/`Series*`/`WatchHistory*`/`Following*`/`Reminder*`/`LocalNotification*` mới thuộc feature line bị gỡ.

---

## 6. ROOM IMPACT

- Hiện trạng: `AppDatabase.kt` `version = 10`, entities gồm 3 bảng drama (`WatchHistoryEntity`, `FollowedSeriesEntity`, `ReminderSeriesEntity`) + `SavedVideoEntity`, `FavoriteTemplateEntity`, `TemplateBadgeStateEntity`.
- `DatabaseModule.provideAppDatabase()` (`DatabaseModule.kt:84-96`): `addMigrations(migration3To4, migration4To5, migration8To9, migration9To10)` + `.fallbackToDestructiveMigration()`.

### Hành động Room bắt buộc:
1. Nâng `AppDatabase` lên **`version = 11`**.
2. Thêm `migration10To11` trong `DatabaseModule`:
   ```kotlin
   private val migration10To11 = object : Migration(10, 11) {
       override fun migrate(db: SupportSQLiteDatabase) {
           db.execSQL("DROP TABLE IF EXISTS `watch_history`")
           db.execSQL("DROP TABLE IF EXISTS `followed_series`")
           db.execSQL("DROP TABLE IF EXISTS `reminder_series`")
       }
   }
   ```
   và đăng ký vào `addMigrations(... migration9To10, migration10To11)`.
3. Bỏ 3 entity khỏi `@Database(entities=[...])` và 3 abstract DAO accessor (`watchHistoryDao()/followingDao()/reminderDao()`).
4. Bỏ 3 `@Provides` DAO trong `DatabaseModule`.

### ⚠️ Cảnh báo `fallbackToDestructiveMigration()`:
- Vì fallback đang BẬT, nếu KHÔNG viết `migration10To11` mà chỉ đổi schema, Room sẽ **xoá sạch DB** (mất cả `saved_videos`, `favorite_templates`, `template_badge_state`) ⇒ hỏng NEW-badge và thư viện video đã lưu. **Bắt buộc phải có migration 10→11 tường minh** để chỉ DROP 3 bảng drama, giữ nguyên phần còn lại.
- Nên giữ `.fallbackToDestructiveMigration()` như hiện tại (an toàn cho các máy nhảy version bất thường), nhưng migration tường minh vẫn phải có để bảo toàn dữ liệu KEEP ở đường nâng cấp bình thường.

---

## 7. KẾ HOẠCH GỠ BỎ (thứ tự để luôn compile được)

> Nguyên tắc: cắt caller/scheduler trước → cắt cross-link KEEP → gỡ DI → xoá UI/data → cập nhật Room → dọn resource. Build sau mỗi nhóm.

### Bước 0 — Ngắt lịch notification (an toàn runtime)
- Sửa `FilmApplication.kt`: bỏ `DramaReleaseNotificationHelper.createNotificationChannel(this)` (dòng 39) và khối `CoroutineScope{ LocalNotificationScheduler.schedule(...) }` (dòng 42-44), bỏ import tương ứng (dòng 8-10). (Tuỳ chọn: tạm gọi `LocalNotificationScheduler.cancel(this)` một lần để huỷ work đã enqueue trên máy cũ, rồi mới xoá class ở bước sau.)

### Bước 1 — Cắt cross-link trong các thành phần KEEP
1. `AuthRepositoryImpl.kt`: bỏ 4 param inject `myListRepository`, `watchHistoryDao`, `followingDao`, `reminderDao`; bỏ khối `syncSavedSeriesWithRemote()` (dòng 60-63) và 3 lệnh `clearAll*` trong `performLocalAccountCleanup()` (dòng ~120-124); bỏ import drama DAO + `MyListRepository`.
2. `AppPreferenceManager.kt`: xoá `addFreeWatchMinutes()` + `canClaimFreeWatchToday()` (và key free-watch liên quan nếu không dùng chỗ khác), bỏ import `DramaPolicy`.
3. `ErrorMapper.kt`: xử lý `DramaApiException` (xem Bước 6, quyết định đổi tên exception thành tên trung lập hoặc giữ class).

Verify: `./gradlew assembleDebug` compile OK (các file KEEP không còn tham chiếu drama).

### Bước 2 — Gỡ UI không reachable
Xoá:
- `ui/search/`: `SearchActivity.kt`, `SearchViewModel.kt`, `SearchMatcher.kt`
- `ui/history/`: `HistoryFragment.kt`, `MyListViewModel.kt`, `MyListUiState.kt`, `adapter/FollowingFilmAdapter.kt`, `adapter/HistorySectionAdapter.kt`, `adapter/ReminderFilmAdapter.kt`
- `ui/home/adapter/HomeFilm3ColAdapter.kt`, `ui/home/util/SeriesBadgeHelper.kt`
- `ui/common/adapter/DramaGridAdapter.kt`, `ui/common/adapter/DramaSeriesAdapter.kt`
- `ui/common/view/FilmCoverImageView.kt`
- `AppNavigator.kt`: xoá `openSearch()`, `openDramaPlayer()`, `createDramaPlayerIntent()` + import `SearchActivity` + các hằng `EXTRA_SERIES_ID`/`EXTRA_INITIAL_EPISODE_*`/`SHARED_ELEMENT_DRAMA_CONTAINER` nếu không dùng.
- Manifest: xoá khối khai báo `ui.search.SearchActivity`.

Verify: `./gradlew assembleDebug`.

### Bước 3 — Gỡ pipeline notification drama
Xoá: `common/notification/NotificationCoordinator.kt`, `LocalNotificationWorker.kt`, `LocalNotificationScheduler.kt`, `LocalNotificationPublisher.kt`, `NotificationLedger.kt`, `NotificationCandidate.kt`, `DramaReleaseNotificationHelper.kt`.
GIỮ: `AiVideoNotificationHelper.kt`, `NotificationHelper.kt`, `CustomNotificationDialog.kt`, `NotificationPrefManager.kt`.

Verify: `./gradlew assembleDebug`.

### Bước 4 — Gỡ repository + data layer drama
Xoá:
- `data/repository/DramaRepositoryImpl.kt`, `data/repository/MyListRepositoryImpl.kt`
- `domain/repository/DramaRepository.kt`, `domain/repository/MyListRepository.kt`
- `data/datasource/DramaRemoteDataSource.kt`
- `data/mapper/DramaMapper.kt`
- `data/network/VodEndpoints.kt`, `data/network/model/SeriesDto`+`SeriesListResponseDto.kt`, `SeriesDetailDto.kt`, `EpisodeDto`/`EpisodeListResponseDto`, `PlaybackGrantDto`/`PlaybackRequestDto`, `InteractionApiService.kt` (xác nhận không KEEP dùng — grep đã cho thấy chỉ MyListRepositoryImpl dùng)
- `domain/model/DramaModels.kt`, `domain/policy/DramaPolicy.kt`
- `common/util/SurveyRanker.kt`, `common/image/CoverUrlHelper.kt`
- Trong `common/image/FilmImageLoader.kt`: chỉ xoá extension `ImageView.loadDramaCover(...)` (dòng ~148+) — GIỮ nguyên object `FilmImageLoader`.

DI:
- `di/RepositoryModule.kt`: xoá `bindDramaRepository` (giữ các bind ArtTemplate/TemplateBadge).
- `di/MyListModule.kt`: xoá cả file (hoặc nội dung) — chỉ bind MyListRepository.

> Lưu ý: nếu `VOD_EXTERNAL_*` BuildConfig / `NetworkModule` x-api-key chỉ phục vụ drama thì có thể dọn thêm, nhưng `NetworkModule` cấp OkHttp/Gson cho cả photo-art ⇒ GIỮ module, chỉ cân nhắc bỏ header VOD nếu backend art không cần (cần xác nhận riêng — xem Mục 8).

Verify: `./gradlew assembleDebug`.

### Bước 5 — Cập nhật Room (sau khi không còn ai dùng DAO/entity)
- `AppDatabase.kt`: bỏ 3 import entity + 3 import DAO, bỏ 3 entity khỏi `@Database`, bỏ 3 accessor, nâng `version = 11`.
- `DatabaseModule.kt`: bỏ 3 import DAO + 3 `@Provides` DAO; thêm `migration10To11` (DROP 3 bảng) và đăng ký.
- Xoá file: `data/local/dao/WatchHistoryDao.kt`, `FollowingDao.kt`, `ReminderDao.kt`; `data/local/entity/WatchHistoryEntity.kt`, `FollowedSeriesEntity.kt`, `ReminderSeriesEntity.kt`.

Verify: `./gradlew assembleDebug` + chạy app, kiểm tra DB nâng từ v10→v11 không mất `saved_videos`/`favorite_templates`/`template_badge_state`.

### Bước 6 — Quyết định `DramaApiException` (RỦI RO CAO)
Hai lựa chọn (không được để treo):
- (a) **Giữ lại** class, đổi tên trung lập (ví dụ `ApiException`) + cập nhật `ErrorMapper` và `NetworkModule`/`AppEventBus` reference. Ít rủi ro hành vi (giữ nguyên mapping 401→ForceUpdate cho API art).
- (b) **Bỏ** class: cần thay nhánh `is DramaApiException` trong `ErrorMapper` bằng kiểu exception mà hạ tầng network art thực sự ném. Rủi ro mất mapping lỗi HTTP cho luồng art.
> Khuyến nghị (a) — rename, giữ hành vi error-handling cho photo-art.

### Bước 7 — Dọn resource drama-only
- Layout (xoá): `activity_search.xml`, `fragment_history.xml`, `item_drama_grid.xml`, `item_drama_series.xml`, `item_foryou_movie_card.xml`, `item_home_film_2col.xml`, `item_home_film_3col.xml`, `item_my_list_following.xml`, `item_my_list_horizontal.xml`, `item_my_list_section_header.xml`, `layout_foryou_movie_list.xml`, `layout_my_list_empty.xml`.
- `res/values/attrs.xml`: xoá `declare-styleable name="FilmCoverImageView"`.
- `res/menu/`: `bottom_nav_menu.xml` KHÔNG đổi (không có item drama). Không có menu drama riêng.
- `res/navigation/nav_main.xml`: **KHÔNG xoá `navigation_foryou`** — nó được dùng như alias của `navigation_video` trong `MainActivity.kt:189,198` và `PhotoArtBottomNavBar.kt:106-135` (map về DiscoverVideos). GIỮ.
- `res/values/strings.xml`: ~104 chuỗi khớp `local_notification*`/`badge_hot`/`drama`/`episode`/`series`. Xoá theo nhóm `local_notification_*`, `badge_hot_text`, các chuỗi My List/history/search/episode drama. **Rà soát từng chuỗi** để tránh xoá nhầm chuỗi dùng chung; đọc skill `app-localization` + glossary trước khi đụng strings (AGENTS.md Mục 8). Áp dụng cho mọi `values-*/strings.xml`.

Verify cuối: `./gradlew lintDebug` (bắt unused resource còn sót) + `./gradlew assembleDebug`.

### Bước 8 — Dọn test drama
Xoá test chỉ phục vụ drama: `common/util/SurveyRankerTest.kt`, `common/notification/NotificationCoordinatorCalculationTest.kt`, `ui/search/SearchMatcherContentFilterTest.kt`, `domain/policy/DramaPolicyTest.kt`, `common/image/CoverUrlHelperTest.kt`.
Verify: `./gradlew test`.

### Các severance rủi ro cao nhất (ưu tiên review kỹ):
1. **`AuthRepositoryImpl`** — xoá sai làm hỏng login/xoá-tài-khoản. Phải giữ nguyên logic auth, chỉ bỏ nhánh drama.
2. **Room `migration10To11` + version 11** — sai sẽ kích hoạt destructive fallback, mất dữ liệu KEEP.
3. **`DramaApiException` ↔ `ErrorMapper`** — ảnh hưởng xử lý lỗi của luồng photo-art Discover.
4. **`FilmImageLoader`** — chỉ xoá extension `loadDramaCover`, tuyệt đối không xoá object (photo-art phụ thuộc).
5. **Unschedule notification** — nếu xoá class trước khi gỡ `schedule()` call sẽ không compile; nếu không cancel work cũ, máy đã cài sẽ còn job mồ côi (vô hại nhưng nên cancel).

---

## 8. CÂU HỎI MỞ (cần quyết định của người)

1. `DramaApiException`: chọn phương án (a) rename hay (b) bỏ (Mục 6)? Ảnh hưởng mapping lỗi photo-art.
2. BuildConfig `VOD_EXTERNAL_BASE_URL`/`VOD_EXTERNAL_API_KEY`/`USE_DIRECT_VOD_EXTERNAL_API` và header `x-api-key` trong `NetworkModule`: backend photo-art có dùng chung endpoint/API key này không? Nếu không, dọn luôn; nếu có, GIỮ. Cần xác nhận trước khi đụng `NetworkModule`/`build.gradle.kts`.
3. Nhóm free-watch trong `AppPreferenceManager` (`addFreeWatchMinutes`, `canClaimFreeWatchToday`, `isFreeWatchActive`, `getRemainingFreeWatchSeconds`, `getTodayFreeWatchClaimCount`, các key `KEY_FREE_WATCH_*`, `MAX_FREE_WATCH_CLAIMS_PER_DAY`): các hàm này tham chiếu lẫn nhau nhưng cả cụm **không có caller bên ngoài** (chỉ `addFreeWatchMinutes` dùng `DramaPolicy`). Chỉ cần bỏ import `DramaPolicy` + hard-code lại default của `addFreeWatchMinutes`, hoặc xoá cả cụm. Xác nhận không UI nào gọi `isFreeWatchActive`/`getRemainingFreeWatchSeconds` trước khi xoá cả cụm (đây là tàn dư coin-economy — AGENTS.md Mục 1 cấm coin economy ⇒ ưu tiên xoá cả cụm).
4. `InteractionApiService` (comment/like server film): grep xác nhận **chỉ `MyListRepositoryImpl` dùng** (REMOVE) ⇒ an toàn xoá cùng data layer drama. Nếu còn nghi ngờ dùng chung, xác nhận lại.

---

*Nội dung đã được viết lại/diễn giải để tuân thủ; mọi dẫn chứng tham chiếu trực tiếp mã nguồn trong repo tại thời điểm điều tra.*
