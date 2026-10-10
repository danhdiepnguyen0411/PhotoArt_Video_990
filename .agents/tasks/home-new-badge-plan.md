# Kế hoạch: Logic hiển thị badge "NEW" trên Home

## 1. Tóm tắt

- Nguyên nhân gốc: badge NEW được bật theo điều kiện `template.rank <= 1` (`HomeSectionAdapter.bindCard`). Dữ liệu API thật có `rank = 0` cho 225/241 template (rank 0 là giá trị "chưa xếp hạng"), và không template nào `premium`, nên gần như mọi card đều hiện NEW. XML không có lỗi (badge mặc định `gone`), adapter cũng set visibility cả hai chiều, nên không phải lỗi recycle.
- API template không có trường nào nói về độ mới: không có `isNew`, `createdAt`, `updatedAt`, `tags`. `ArtTemplate.createdAt` luôn là `0L`.
- Đề xuất: bỏ hoàn toàn heuristic `rank`. Xác định "mới" ở client bằng cách so catalog: lưu `firstSeenAt` của từng template code trong Room. Template xuất hiện sau baseline lần đầu được coi là mới trong N ngày (đề xuất 7), ẩn ngay khi user mở template đó, và giới hạn 1 badge NEW mỗi section. Nếu sau này server trả `isNew`/`createdAt` thì dùng nguồn đó (có ưu tiên), parse một lần lúc đọc JSON.
- Hiệu năng: tính badge một lần trong ViewModel trên `Dispatchers.Default`, đưa vào UI model; trạng thái badge nằm trong RAM dưới dạng `StateFlow`; DiffUtil trả payload riêng cho thay đổi badge để không reload Glide và không chạy lại autoplay video.

## 2. Bằng chứng

### 2.1 Nơi quyết định badge (cả ba đều dùng `rank`)

| Vị trí | Logic hiện tại | Ảnh hưởng |
|---|---|---|
| `app/src/main/java/com/aiart/photo/video/generator/ui/home/adapter/HomeSectionAdapter.kt` `bindCard()` (~dòng 158-171) | `premium -> PRO`, `rank <= 1 -> NEW`, ngược lại `GONE` | Các section 3 cột trên Home. Đây là lỗi user báo |
| `ui/home/adapter/HomeTrendingAdapter.kt` `bind()` (~dòng 103) | `tvExclusiveBadge.isVisible = item.premium \|\| item.rank <= 1` | Badge "EXCLUSIVE" của hàng Featured cũng hiện ở mọi card, cùng nguyên nhân |
| `ui/home/adapter/HomeTemplateAdapter.kt` (~dòng 93-105) | Giống `HomeSectionAdapter` | Không được khởi tạo ở đâu (dead code). Ngoài phạm vi |
| `ui/discover/DiscoverTemplateAdapter.kt` (~dòng 112-126) | NEW chỉ khi `categoryCode == "NEW" && rank <= 1` | Không có category `NEW` trong dữ liệu nên NEW không bao giờ hiện. Ngoài phạm vi, chỉ ghi nhận |
| `ui/discover/BaseDiscoverViewModel.kt` ~264, `SelectImageTemplateViewModel.kt` ~152 | Tab "New" lọc theo `rank <= 2` | Tab New ở Discover chứa gần hết template. Ngoài phạm vi, chỉ ghi nhận |

### 2.2 Layout

`app/src/main/res/layout/item_home_template_card.xml`: `tvTemplateBadge` đã có `android:visibility="gone"`, chữ chỉ ở `tools:text`. Không cần sửa XML. Cùng một view dùng chung cho cả NEW và PRO, với nền `@drawable/bg_badge_template_pill`.

### 2.3 Nguồn dữ liệu và schema

- Luồng dữ liệu: `ArtSdkManager.getTemplateApps(page=0, size=2000)` → `ArtTemplateRepositoryImpl.fetchRemoteCatalog()` → `parseTemplatesFromElement()` → cache RAM (L0) cùng `ArtCacheManager` (disk `cache/art_sdk_cache/art_templates_all.json`) → `buildSectionsFromTemplates()` → `HomeFeedData` → `HomeViewModel` → `HomeUiState.Success(sections: List<HomeSection>)` → `HomeSectionAdapter`. Preload chạy từ `FilmApplication` (dòng ~75) và `ArtSdkWarmupTask` (splash).
- Parser: `rank = obj.optInt("rank", i)` (dòng ~584), `createdAt = obj.optLong("createdAt", 0L)` (dòng ~547, cố ý default 0 để DiffUtil ổn định).
- Dữ liệu thật: tôi đọc file cache trên emulator đang kết nối (`adb exec-out run-as … cat cache/art_sdk_cache/art_templates_all.json`, chỉ đọc). Kết quả:
  - 241 template, `code` duy nhất (241/241), dùng làm khóa ổn định được.
  - Các key của item: `categoryCode, code, description, imageUrl, maxInputImages, name, premium, rank, templateType, videoThumbnailUrl, videoUrl`. Không có `createdAt`, `updatedAt`, `isNew`, `active`, `tags`.
  - Phân bố `rank`: 0 → 225 item; 1..8 → mỗi giá trị 2 item, chỉ ở `PHOTOS_BEAUTY` và `TRENDING_PHOTOS`. 27 category còn lại có toàn bộ `rank = 0`.
  - `premium = false` cho cả 241 item, nên nhánh PRO không bao giờ chạy và nhánh `rank <= 1` bắt hết.
- `HomeSection(categoryCode, categoryName, templates)` hiển thị tối đa 3 card (`section.templates.take(3)` trong `SectionViewHolder.bind`).

### 2.4 Hạ tầng lưu trữ có sẵn

- Room 2.6.1 (`gradle/libs.versions.toml` dòng 27, 81-83; `app/build.gradle.kts` dòng 236-239, dùng KSP và Hilt).
- `data/local/AppDatabase.kt`: `version = 8`, `exportSchema = false`, 5 entity (có `FavoriteTemplateEntity` / `FavoriteTemplateDao` khóa theo `template_code`, là mẫu tốt để làm theo).
- `di/DatabaseModule.kt`: chỉ có `migration3To4`, `migration4To5`, cộng với `.fallbackToDestructiveMigration()`. Rủi ro: tăng version mà không có Migration sẽ xóa favorites, watch history, saved videos của user.
- DataStore chưa có trong dependencies. `AppPreferenceManager` (SharedPreferences) đang lưu nhiều Set<String>.

### 2.5 Hiệu năng của bind hiện tại

- `HomeSectionAdapter.DIFF.areContentsTheSame` dùng `oldItem == newItem`, không có `getChangePayload`. Mọi thay đổi trong section đều gọi `bind()` đầy đủ: `loadPoster` (Glide) cho 3 card, `setOnClickListener`, rồi `binding.root.post { checkAutoPlay() }` (phát lại video). Nếu thay đổi badge đi theo đường này thì sẽ thấy nháy ảnh và video bị khởi động lại.
- `HomeViewModel` dựng state ban đầu đồng bộ từ `getCachedHomeFeed()` trong constructor, nên Home hiện ngay mà chưa có dữ liệu badge.

## 3. Thiết kế luật hiển thị

### 3.1 Đánh giá các phương án

| Phương án | Kết luận |
|---|---|
| (a) Flag từ server | Chính xác nhất nhưng hiện không có. Chuẩn bị parse sẵn `isNew`/`is_new` và `createdAt`/`created_at` (epoch hoặc ISO-8601) để tự bật khi backend bổ sung. Không phụ thuộc vào nó |
| (b) Cửa sổ thời gian theo `createdAt` | Không có `createdAt`, nên phải dùng "thời điểm client thấy lần đầu" (`firstSeenAt`) làm mốc. Đây là cơ chế chính |
| (c) Theo dõi đã mở/chưa mở | Cần thiết để badge có ý nghĩa: mở template rồi thì hết NEW |
| (d) Giới hạn số badge | Giữ badge có giá trị khi server thêm nhiều template một lúc. Giới hạn 1 badge mỗi section (section hiện 3 card) |

### 3.2 Luật đề xuất (hàm thuần, tính một lần)

```
badge(t) =
  PRO  nếu t.premium                                  // giữ thứ tự ưu tiên hiện tại
  NEW  nếu isNewCandidate(t) và t.code ∉ openedCodes  // chưa từng mở
       và t nằm trong top-K ứng viên NEW của section  // K = 1
  NONE ngược lại

isNewCandidate(t) =
     t.serverIsNew == true                                            // (a) nếu server có
  || (t.createdAtMillis > 0 && now - t.createdAtMillis ∈ [0, WINDOW]) // (a) nếu server có
  || (firstSeenAt(t.code) > BASELINE && now - firstSeenAt ∈ [0, WINDOW]) // (b) client-side

WINDOW = 7 ngày (hằng số NEW_BADGE_WINDOW_MS)
Thứ tự ưu tiên trong top-K: thời điểm mới nhất (createdAt hoặc firstSeenAt) giảm dần, sau đó thứ tự hiển thị.
```

### 3.3 Edge case

- Cài mới hoặc nâng cấp app (bảng trống): ghi toàn bộ catalog hiện tại với `first_seen_at = 0` (BASELINE), nên không có card NEW nào. Badge chỉ xuất hiện khi server thêm template thật. Đây là hành vi "chuẩn", nhưng màn Home lần đầu sẽ không có NEW (mục 6, quyết định 1).
- Catalog rỗng hoặc lỗi mạng: không sync, để tránh trường hợp baseline rỗng khiến lần sau mọi thứ đều thành NEW.
- Phục vụ từ cache cũ: các code đã có, `INSERT OR IGNORE` không làm gì.
- Template bị gỡ rồi thêm lại: row cũ còn nên không NEW lại. Chấp nhận được.
- Đổi đồng hồ: nếu `now < firstSeenAt` (lùi giờ) thì coi tuổi là 0 (vẫn NEW, không âm). Tiến giờ thì badge hết sớm, vô hại. Không cần đồng hồ monotonic.
- Thiếu timestamp từ server: `createdAtMillis = 0` thì bỏ qua nhánh (a).
- Template được cập nhật: API không có `updatedAt`, không xử lý "UPDATED".
- PRO và NEW cùng lúc: PRO thắng (giữ nguyên hành vi hiện tại, một view chung).
- Kích thước bảng: tối đa khoảng 2000 row (pageSize), không cần prune.

## 4. Hiệu năng

- Tính badge trong `HomeViewModel` bằng `combine(feedFlow, badgeStateFlow)` + `.flowOn(Dispatchers.Default)`. Chỉ tính cho `take(3)` mỗi section (O(sections × 3)), không tính trong `onBindViewHolder`.
- Trạng thái badge (`firstSeen`, `opened`) đọc từ Room một lần vào `MutableStateFlow<TemplateBadgeState>` trong repository `@Singleton`. Tra cứu O(1) bằng `HashMap`/`HashSet`. Ghi Room trên `Dispatchers.IO`, cập nhật RAM ngay để UI phản hồi tức thì.
- Timestamp server parse sang epoch millis một lần trong `parseTemplatesFromElement`, không parse ngày khi bind.
- DiffUtil: `getChangePayload` trả `PAYLOAD_BADGES` khi chỉ badge thay đổi. `onBindViewHolder(holder, position, payloads)` chỉ set text và visibility của 3 `tvTemplateBadge`, không gọi Glide, không gọi `checkAutoPlay`.
- Trong bind luôn set cả `VISIBLE` lẫn `GONE` (đã đúng hiện nay, giữ nguyên trong `when` exhaustive trên enum).
- `HomeViewModel` đang so sánh `currentState.sections == feed.sections` để tránh rebuild. So sánh này vẫn đúng với UI model mới vì là data class.

## 5. Kế hoạch triển khai (theo thứ tự phụ thuộc)

Lệnh kiểm tra dùng chung: `./gradlew assembleDebug` và `./gradlew lintDebug`. `./gradlew test` chỉ để đảm bảo các test có sẵn (`ArtTemplateRepositoryTest`) vẫn compile và pass. Không viết test mới (AGENTS.md §6).

- [ ] 1. Mở rộng model và parser cho tín hiệu từ server (không đổi hành vi).
  Thêm vào `ArtTemplate` hai field có default: `serverIsNew: Boolean = false`, `createdAtMillis` (dùng lại `createdAt: Long` sẵn có). Trong `parseTemplatesFromElement`: đọc `isNew` hoặc `is_new` bằng `optBoolean`. Đọc `createdAt` hoặc `created_at` theo thứ tự: number → `asLong`, string số → `toLongOrNull()`, string ISO-8601 → `SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX", Locale.US)` với UTC, thử thêm mẫu không có millis (minSdk 24 nên không dùng `java.time` khi chưa bật desugaring). Lỗi parse thì trả `0L`. Formatter tạo một lần cho mỗi lần parse catalog, không tạo cho từng item.
  Files: `domain/model/PhotoArtModels.kt`, `data/repository/ArtTemplateRepositoryImpl.kt`.
  Verify: `./gradlew assembleDebug test` pass.

- [ ] 2. Thêm persistence Room cho trạng thái badge, kèm migration an toàn.
  Tạo `TemplateBadgeStateEntity` (bảng `template_badge_state`: `template_code TEXT PK`, `first_seen_at INTEGER NOT NULL`, `opened_at INTEGER` nullable). Tạo `TemplateBadgeStateDao`: `getAll(): List<…>`, `count(): Int`, `insertIgnore(list)` (`OnConflictStrategy.IGNORE`), `markOpened(code, at)` (dạng upsert: `INSERT OR IGNORE` với first_seen_at = 0, sau đó `UPDATE … SET opened_at = :at WHERE template_code = :code AND opened_at IS NULL`). Đăng ký entity, tăng `AppDatabase.version` 8 → 9, thêm `migration8To9` với `CREATE TABLE IF NOT EXISTS template_badge_state(...)` khớp đúng schema Room sinh ra, vào `.addMigrations(...)`, và provider DAO trong `DatabaseModule`. Import đầu file, không dùng FQN (AGENTS.md §5).
  Files: tạo `data/local/entity/TemplateBadgeStateEntity.kt`, `data/local/dao/TemplateBadgeStateDao.kt`; sửa `data/local/AppDatabase.kt`, `di/DatabaseModule.kt`.
  Verify: `./gradlew assembleDebug` (KSP compile Room). Cài đè lên bản đang có trên emulator, mở app, kiểm tra favorites cũ vẫn còn (migration không destructive).

- [ ] 3. Tạo `TemplateBadgeRepository` (`@Singleton`, inject DAO) cùng luật thuần.
  - `domain/model/TemplateBadge.kt`: `enum class TemplateBadge { NONE, NEW, PRO }`.
  - `domain/badge/NewBadgePolicy.kt` (object thuần, không phụ thuộc Android): hằng `NEW_BADGE_WINDOW_MS = 7 ngày`, `MAX_NEW_PER_SECTION = 1`, `BASELINE = 0L`. Hàm `isNewCandidate(template, firstSeenAt, now)` và `resolveSectionBadges(templates, state, now): List<TemplateBadge>` áp dụng luật §3.2 (PRO ưu tiên, loại `opened`, top-K theo độ mới).
  - `data/repository/TemplateBadgeRepository.kt`: `state: StateFlow<TemplateBadgeState>` (`firstSeen: Map<String, Long>`, `opened: Set<String>`), nạp lười một lần từ Room trên IO (`Mutex` để chỉ load một lần). `suspend fun syncCatalog(codes: Collection<String>)`: bỏ qua nếu rỗng; nếu `count() == 0` thì insert tất cả với `BASELINE`, ngược lại insert-ignore code mới với `now`; sau đó cập nhật StateFlow. `fun markOpened(code)`: cập nhật StateFlow ngay, rồi ghi Room trên một `CoroutineScope(SupervisorJob() + Dispatchers.IO)` của singleton để không bị hủy khi rời màn hình.
  - Bind Hilt theo pattern trong `di/RepositoryModule.kt` (constructor `@Inject` đủ nếu không cần interface).
  Files: 3 file mới ở trên; có thể sửa `di/RepositoryModule.kt`.
  Verify: `./gradlew assembleDebug`.

- [ ] 4. Tạo UI model cho Home và tính badge trong ViewModel.
  - Tạo `ui/home/model/HomeSectionUi.kt`: `data class HomeTemplateCardUi(val template: ArtTemplate, val badge: TemplateBadge)` và `data class HomeSectionUi(val section: HomeSection, val cards: List<HomeTemplateCardUi>)` (cards là `take(3)`). Giữ `section` để `onSeeAllClick` không đổi.
  - `HomeUiState.Success.sections` đổi sang `List<HomeSectionUi>`. `featuredSection` giữ nguyên kiểu (EXCLUSIVE xử lý ở bước 7 nếu được duyệt).
  - `HomeViewModel`: inject `TemplateBadgeRepository`. Giữ biến feed hiện tại (`HomeFeedData`) trong một `MutableStateFlow<HomeFeedData?>`. Thêm một collector `combine(feedFlow.filterNotNull(), badgeRepo.state) { feed, s -> feed.sections.map { toUi(it, s, now) } }.flowOn(Dispatchers.Default).distinctUntilChanged()` để cập nhật `sections` trong `Success` hiện tại. Sau mỗi lần nạp feed thành công thì gọi `badgeRepo.syncCatalog(feed.templates.map { it.imageEditTemplateCode })`, kể cả nhánh return sớm khi cache còn hiệu lực. State khởi tạo đồng bộ trong constructor map với `TemplateBadge` tính từ `badgeRepo.state.value` hiện có (có thể rỗng, nên chưa có badge rồi cập nhật bằng payload).
  - `onTemplateClick(template)` của ViewModel (hiện rỗng) gọi `badgeRepo.markOpened(template.imageEditTemplateCode)`.
  - `HomeFragment.renderState`: `sectionAdapter.submitList(state.sections)`. Đoạn "Smart Preload" dùng `state.sections.flatMap { it.section.templates }`. `onTemplateClick(template)` của Fragment gọi `viewModel.onTemplateClick(template)` trước khi điều hướng. Hàm này đã dùng chung cho click card section, featured và banner có template.
  Files: tạo `ui/home/model/HomeSectionUi.kt`; sửa `ui/home/HomeUiState.kt`, `ui/home/HomeViewModel.kt`, `ui/home/HomeFragment.kt`.
  Verify: `./gradlew assembleDebug` (bước 4 và 5 phải xong cùng nhau mới compile được, nên gộp chung một commit).

- [ ] 5. Chuyển `HomeSectionAdapter` sang UI model và thêm payload cho badge.
  - `ListAdapter<HomeSectionUi, SectionViewHolder>`. `areItemsTheSame` so `section.categoryCode`. `areContentsTheSame` dùng `==`. `getChangePayload`: nếu `old.section == new.section` và chỉ `cards.map { it.badge }` khác nhau thì trả `PAYLOAD_BADGES`, ngược lại `null`.
  - Override `onBindViewHolder(holder, position, payloads)`: nếu payloads chứa `PAYLOAD_BADGES` thì gọi `holder.bindBadges(item.cards)`, ngược lại `super`.
  - `bind(sectionUi)` dùng `sectionUi.cards`. `bindCard(cardBinding, cardUi?)` thay khối `when` theo `rank` bằng `applyBadge(cardBinding.tvTemplateBadge, cardUi.badge)`: `NEW` → `R.string.home_badge_new` + VISIBLE, `PRO` → `R.string.home_badge_pro` + VISIBLE, `NONE` → GONE. Dùng `when` exhaustive. `bindBadges` chỉ gọi `applyBadge` cho 3 card. `currentTemplates` lấy từ `cards.map { it.template }`.
  - Xóa hoàn toàn điều kiện `rank <= 1` ở file này.
  Files: `ui/home/adapter/HomeSectionAdapter.kt`.
  Verify: `./gradlew assembleDebug lintDebug`. Thủ công trên emulator: (1) cài đè, mở Home: không card nào có NEW (baseline). (2) Mô phỏng template mới: `adb shell run-as com.aiart.photo.video.generator sqlite3 databases/photo_art_local.db "UPDATE template_badge_state SET first_seen_at=strftime('%s','now')*1000 WHERE template_code IN ('<code1>','<code2>')"` (nếu image emulator không có `sqlite3` thì dùng App Inspection > Database Inspector của Android Studio), force-stop rồi mở lại: chỉ tối đa 1 NEW mỗi section, đúng card đó. (3) Bấm card NEW, quay lại: badge biến mất, ảnh không nháy, video không khởi động lại. (4) Cuộn nhanh lên xuống: không có NEW "dính" sang card khác.

- [ ] 6. Bản địa hóa chuỗi badge.
  `home_badge_new` (và `home_badge_pro`) hiện chỉ có trong `values/strings.xml`. Các locale khác đang fallback về "NEW". Theo skill `.agents/skills/app-localization/SKILL.md` và `docs/localization/glossary.md`, thêm bản dịch vào các `values-*/strings.xml` hiện có (ví dụ vi: "MỚI", đối chiếu với `badge_new_text` đã có sẵn: vi "Mới", es "Nuevo", pt-PT "Novo", in "Baru", zh-TW "新劇" (cần xem lại cho ngữ cảnh template ảnh)). View có `textAllCaps="true"`, kiểm tra độ dài trong pill 9sp.
  Files: `app/src/main/res/values-*/strings.xml`.
  Verify: `./gradlew lintDebug` (không có MissingTranslation mới). Đổi ngôn ngữ máy sang vi rồi xem badge.

- [ ] 7. (Chờ duyệt, quyết định 3) Sửa badge EXCLUSIVE của hàng Featured.
  `HomeTrendingAdapter.bind`: `tvExclusiveBadge.isVisible = item.premium` (bỏ `rank <= 1`). Với dữ liệu hiện tại thì EXCLUSIVE sẽ biến mất khỏi mọi card Featured.
  Files: `ui/home/adapter/HomeTrendingAdapter.kt`.
  Verify: `./gradlew assembleDebug`, xem hàng Featured trên emulator.

## 6. Rủi ro và quyết định cần user chốt

1. Lần mở đầu tiên (cài mới hoặc vừa nâng cấp) sẽ không có NEW nào. Đây là hành vi đúng nghĩa "mới", nhưng có thể khác kỳ vọng marketing. Phương án thay thế nếu muốn có NEW ngay: đánh dấu một danh sách code cố định qua remote config, hoặc nhờ backend thêm `isNew`/`createdAt`. Khuyến nghị backend thêm `createdAt` (code đã sẵn sàng đọc ở bước 1).
2. Độ dài cửa sổ N: đề xuất 7 ngày. Có cho phép mở template xóa badge ngay không: đề xuất có. Giới hạn K: đề xuất 1 mỗi section.
3. Badge EXCLUSIVE ở Featured đang hiện trên mọi card vì cùng nguyên nhân `rank <= 1`. Sửa (bước 7) sẽ làm EXCLUSIVE biến mất vì không có template premium nào. Cần user xác nhận vì đây là thay đổi nhìn thấy được ngoài phạm vi NEW.
4. Discover (`DiscoverTemplateAdapter`, `BaseDiscoverViewModel`, `SelectImageTemplateViewModel`) cũng dùng `rank` và `categoryCode == "NEW"` sai tương tự. Không nằm trong yêu cầu Home. Nên làm một task riêng, dùng lại `TemplateBadgeRepository` và `NewBadgePolicy`.
5. Migration Room: bắt buộc thêm `migration8To9`. Nếu quên, `fallbackToDestructiveMigration()` sẽ âm thầm xóa favorites, lịch sử và video đã lưu của user.
6. `markOpened` chỉ gọi từ Home. Nếu user mở template đó từ Discover hay tìm kiếm, badge trên Home vẫn còn cho tới khi hết cửa sổ, trừ khi các màn đó cũng gọi `markOpened` (dễ bổ sung sau, cùng repository).
7. Dữ liệu thật được kiểm chứng từ cache trên một emulator (241 template). Tôi không kiểm tra endpoint có hỗ trợ field khác cho app/account khác hay không.
