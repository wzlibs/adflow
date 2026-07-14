# Bỏ auto-load lúc init, thu hẹp nghĩa `preload`, consent gate toàn bộ init/load (1.0.0-alpha04)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Xóa auto-load ad lúc `AdFlow.initialize()`; thu hẹp nghĩa `preload` thành "tự load ad kế tiếp khi ad hiện tại mất đi" (sau show, sau release banner, và sau expiry drop — mọi loại kể cả Native); lib tự init MobileAds SDK có gate theo UMP consent (`canRequestAds()`) + first foreground, và tự nhả (flush) các lượt `load()` bị chặn tạm thời khi điều kiện đủ.

## Bối cảnh

`preload` hiện nhốt 2 hành vi độc lập vào 1 flag: (1) tự load lúc `AdFlow.initialize()` (qua `preloadActions` + `preloadOnFirstForeground`), (2) tự load ad kế tiếp sau khi show/release. Hệ quả: interstitial splash (`preload=true`) lãng phí 1 request sau show; interstitial toàn app (`preload=false`) mất khả năng nạp lại sau show. Nghiêm trọng hơn, luồng hiện tại **vi phạm tài liệu UMP của Google**: `MobileAds.initialize()` được gọi vô điều kiện ở first foreground, và `AdFlowRuntime.consentAllowsAdRequests` mặc định `true` nên gate `CONSENT_REQUIRED` trong `AdLoadEngine.passesGates()` gần như vô tác dụng — Google yêu cầu cả init SDK lẫn load ad phải chờ `canRequestAds()`.

**Quyết định đã chốt với user** (qua thảo luận + AskUserQuestion, không re-litigate):
- Bỏ hẳn auto-load lúc init. Lượt load đầu tiên là app-driven: `.load()` thủ công, view attach (banner/native đã tự load), hoặc show() self-heal.
- Giữ tên `preload`, thu hẹp nghĩa: "tự load ad KẾ TIẾP khi ad hiện tại mất đi" — sau show (full-screen), sau release (banner), **và sau expiry drop (mọi loại, gồm Native — đã chốt)**.
- Lib tự init MobileAds (gate: first foreground **VÀ** `canRequestAds()`, guard chạy 1 lần), tự nhả các lượt load đang chờ (pending demand) khi consent resolve. App không phải làm gì trong callback UMP.
- Consent mặc định **deny** (fail-safe); `consent.requestIfNeeded(activity)` thành bước tích hợp bắt buộc; seed `canRequestAds()` lúc tạo consent manager (fast path consent phiên trước).

Bug có sẵn sửa kèm: `AdFlow.onFirstForeground` public hiện là no-op chết — `ForegroundGate` chỉ nhận 1 action mà `initialize()` luôn chiếm trước.

Phát hiện bắt buộc xử lý: `AppOpenForegroundObserver` chỉ `show()` khi `canShow`, không bao giờ `load()` — bỏ init-preload mà không sửa thì placement `autoShowOnForeground` không bao giờ có ad đầu tiên.

**Kiến trúc:** Không đổi shape tổng thể (`:adflow-core` engine-agnostic, `:adflow-admob` implement UMP/AdMob thật) — chỉ thêm state "sẵn sàng nhận ad request" vào `AdFlowRuntime`, thêm cơ chế pending-demand vào `AdLoadEngine`, và rewiring `AdFlow.initialize()`.

## Global Constraints

- Toàn bộ mutation engine chạy trên `Dispatchers.Main.immediate` (bất biến sẵn có) — không cần lock ở bất kỳ state mới nào.
- Không đổi public API surface ngoài: xóa `AdFlowConfigScope.preloadOnFirstForeground` (breaking), `AdFlow.onFirstForeground` chạy thật thay vì no-op (breaking nhẹ, là bug fix).
- `com.google.android.ump.ConsentInformation` là interface — inject được vào `AdMobConsentManager` để test không cần Robolectric cho phần seed/fast-path.
- Mọi test helper dựng `AdFlowRuntime` cho fullscreen/banner/native/rewarded/AdLoadEngine phải cập nhật để giả lập trạng thái "đã sẵn sàng" (`updateConsent(true)`, `noteFirstForeground()`, `markNetworkInitialized()`), nếu không toàn bộ test cũ (assume load chạy ngay) sẽ đỏ.

---

### Task 1: `ForegroundGate` hỗ trợ nhiều action

**Files:**
- Modify: `adflow-core/src/main/java/com/adflow/core/engine/ForegroundGate.kt`
- Modify: `adflow-core/src/test/java/com/adflow/core/engine/ForegroundGateTest.kt`

**Vấn đề:** `runOnFirstForeground(action)` hiện chỉ giữ 1 `action: (() -> Unit)?` — `AdFlow.initialize()` luôn đăng ký trước nên `AdFlow.onFirstForeground()` (API public) là no-op chết.

- [ ] **Step 1:** Đổi field `action` thành `actions: MutableList<() -> Unit>`. `runOnFirstForeground` append vào list; nếu đã qua first-foreground thì chạy ngay action mới thêm; nếu chưa, đăng ký lifecycle observer (chỉ 1 lần, lần đăng ký đầu tiên).
- [ ] **Step 2:** `onForegroundStart()` (internal) drain toàn bộ `actions` và chạy hết đúng 1 lần.
- [ ] **Step 3:** Sửa `ForegroundGateTest.kt`: test "đăng ký lần 2 trước khi foreground là no-op" đổi thành "cả 2 action đều chạy"; thêm test "đăng ký sau khi đã qua first foreground thì chạy ngay lập tức"; giữ test "không chạy gì trước foreground".

---

### Task 2: `AdFlowRuntime` — trạng thái sẵn sàng nhận ad request

**Files:**
- Modify: `adflow-core/src/main/java/com/adflow/core/engine/AdFlowRuntime.kt`
- Create: `adflow-core/src/test/java/com/adflow/core/engine/AdFlowRuntimeTest.kt`

- [ ] **Step 1:** Thêm state:
  ```kotlin
  var consentAllowsAdRequests = false; private set   // ĐỔI mặc định true -> false
  var networkInitialized = false; private set        // MỚI
  val readyForAdRequests: Boolean get() = consentAllowsAdRequests && networkInitialized

  private var firstForegroundSeen = false
  private var networkInitStarted = false             // guard init 1 lần
  private val readyListeners = mutableListOf<() -> Unit>()
  var networkInitializer: (() -> Unit)? = null       // AdFlow.initialize gán
  ```
- [ ] **Step 2:** Thêm hàm `onReadyForAdRequests(listener: () -> Unit)` — engine tự đăng ký lúc khởi tạo, không bao giờ remove (sống cùng vòng đời runtime).
- [ ] **Step 3:** Thêm `updateConsent(allows: Boolean)`: set `consentAllowsAdRequests`, gọi `maybeStartNetworkInit()`, rồi nếu `readyForAdRequests` vừa chuyển false→true thì `notifyReady()`.
- [ ] **Step 4:** Thêm `noteFirstForeground()`: set `firstForegroundSeen = true`, gọi `maybeStartNetworkInit()`.
- [ ] **Step 5:** Thêm `markNetworkInitialized()`: idempotent (return sớm nếu đã `true`), set `networkInitialized = true`, nếu `readyForAdRequests` thì `notifyReady()`.
- [ ] **Step 6:** `maybeStartNetworkInit()` private: chạy `networkInitializer?.invoke()` đúng 1 lần khi `firstForegroundSeen && consentAllowsAdRequests && !networkInitStarted` (set `networkInitStarted = true` trước khi invoke).
- [ ] **Step 7:** `notifyReady()` private: `readyListeners.toList().forEach { it() }` (snapshot để tránh ConcurrentModification nếu listener tự đăng ký thêm).
- [ ] **Step 8:** Test `AdFlowRuntimeTest.kt`: network init chạy đúng 1 lần bất kể thứ tự gọi (consent→foreground, foreground→consent, consent nhấp nháy true/false/true trước khi foreground); ready listener bắn đúng lúc transition false→true; revoke rồi re-grant SAU KHI đã `markNetworkInitialized` vẫn re-notify listener; gọi `markNetworkInitialized()` 2 lần không notify 2 lần.

---

### Task 3: `AdLoadEngine` — pending demand + expiry reload

**Files:**
- Modify: `adflow-core/src/main/java/com/adflow/core/engine/AdLoadEngine.kt`
- Modify: `adflow-core/src/main/java/com/adflow/core/logging/AdFlowEvent.kt` (thêm `LOAD_DEFERRED`)
- Modify: `adflow-core/src/test/java/com/adflow/core/engine/AdLoadEngineTest.kt`
- Modify (test helper, để test cũ xanh trở lại): `AdLoadEngineTest.newRuntime()` (dòng ~66), `adflow-core/src/test/.../fullscreen/FullScreenTestFakes.kt` `newTestRuntime()` (dòng ~53 — dùng chung bởi fullscreen/banner/native/rewarded test), runtime inline trong `AppOpenForegroundObserverTest.kt` (dòng ~45)

- [ ] **Step 1:** Thêm `private var pendingDemand = false`. Trong `init` block của `AdLoadEngine`: `runtime.onReadyForAdRequests { if (pendingDemand) { pendingDemand = false; ensureLoaded() } }`.
- [ ] **Step 2:** Viết lại gate trong `ensureLoaded()` (thay lời gọi `passesGates()` hiện có), theo đúng thứ tự:
  1. `isReady` hoặc `loadJob?.isActive == true` → return (giữ nguyên).
  2. `!runtime.consentAllowsAdRequests` → `pendingDemand = true`; `reportBlocked(BlockReason.CONSENT_REQUIRED)` (giữ nguyên hành vi listener cũ); state giữ `Idle`; return.
  3. `config.loadRule?.isAllowed(...) == false` → `reportBlocked(BlockReason.RULE_REJECTED)`; **KHÔNG** set `pendingDemand` (chính sách app, không phải gate tạm thời); return.
  4. `!runtime.networkInitialized` → `pendingDemand = true`; **không gọi listener** (cửa sổ nội bộ tạm thời, không có `BlockReason` phù hợp); log `logger.log(..., AdFlowEvent.LOAD_DEFERRED, null)`; state giữ `Idle`; return.
  5. Chạy load cycle như cũ (`loadJob = scope.launch { runCycle(forcing = false) }`).
- [ ] **Step 3:** Áp dụng cùng logic gate cho `forceReload()` (bị gate thì cũng set `pendingDemand`; lúc flush chạy `ensureLoaded()` — force-refresh xếp hàng suy biến thành "đảm bảo có 1 ad", không phục hồi ý định "force" ban đầu).
- [ ] **Step 4:** Expiry reload — trong `scheduleExpiry(delayMs)` (KHÔNG sửa `dropIfExpired()` vì hàm này bị re-enter từ `isReady`/`peek`/`take`):
  ```kotlin
  private fun scheduleExpiry(delayMs: Long) {
      scope.launch {
          delay(delayMs)
          dropIfExpired()
          if (config.preload) ensureLoaded()   // no-op nếu ad mới đã thay thế / load đang chạy; nếu bị gate -> pendingDemand
      }
  }
  ```
- [ ] **Step 5:** Thêm `AdFlowEvent.LOAD_DEFERRED` (giá trị enum mới, additive, không phá thứ tự existing values nếu enum dùng theo tên chứ không phải ordinal — kiểm tra trước khi thêm).
- [ ] **Step 6:** Cập nhật 3 test helper runtime (`AdLoadEngineTest`, `FullScreenTestFakes`, `AppOpenForegroundObserverTest`) — mỗi cái gọi thêm `updateConsent(true); noteFirstForeground(); markNetworkInitialized()` ngay sau khi dựng `AdFlowRuntime`, để mọi test hiện có (giả định load chạy ngay) tiếp tục xanh.
- [ ] **Step 7:** Test mới trong `AdLoadEngineTest.kt`:
  - Consent chặn: `ensureLoaded()` không gọi `loadOne`, state giữ `Idle`, listener nhận `onAdBlocked(CONSENT_REQUIRED)`.
  - Pending demand flush đúng 1 lần khi runtime chuyển ready (test cả trường hợp gọi `load()` lặp lại trong lúc đang pending — không mở nhiều lượt).
  - Gate "network chưa init": consent `true` nhưng network chưa init → không gọi listener, load chạy khi `markNetworkInitialized()` được gọi.
  - `loadWhen { false }` → `RULE_REJECTED`, và khi runtime chuyển ready sau đó KHÔNG tự load (không set pendingDemand).
  - `forceReload()` bị gate → set pendingDemand, flush chạy load thường (không phải force).
  - Consent revoke sau khi đã ready → `load()` mới gọi lại re-queue đúng; re-grant → flush.
  - Expiry: `preload=true` → tự load lại lúc hết hạn; `preload=false` → về `Idle`, không tự load; hết hạn đúng lúc đang bị gate → set `pendingDemand`, không spam listener ngoài báo cáo đã định nghĩa.

---

### Task 4: `AdFlow` facade + DSL — bỏ init-preload, rewiring readiness

**Files:**
- Modify: `adflow-core/src/main/java/com/adflow/core/AdFlow.kt`
- Modify: `adflow-core/src/main/java/com/adflow/core/config/AdFlowConfigScope.kt`
- Modify: `adflow-core/src/main/java/com/adflow/core/config/AdFlowConfigScopeImpl.kt`
- Modify: `adflow-core/src/main/java/com/adflow/core/config/PlacementConfig.kt` (KDoc)
- Modify: `adflow-core/src/test/java/com/adflow/core/AdFlowTest.kt`
- Modify: `adflow-core/src/test/java/com/adflow/core/banner/AdFlowBannerViewTest.kt`
- Modify: `adflow-core/src/test/java/com/adflow/core/nativead/AdFlowNativeAdViewTest.kt`

- [ ] **Step 1:** `AdFlow.initialize()` (dòng 77-121 hiện tại): xóa hoàn toàn `preloadActions` (dòng 77, 83, 88, 98, 103, 108) và khối first-foreground cũ (116-120).
- [ ] **Step 2:** Wiring mới, đúng thứ tự (consent manager seed đồng bộ trong constructor; `runOnFirstForeground` chạy đồng bộ nếu app đang foreground):
  ```kotlin
  newRuntime.networkInitializer = {
      network.initialize(appContext) { coroutineScope.launch { newRuntime.markNetworkInitialized() } }
  }
  consentManager = network.createConsentManager(appContext, scope.consentDebugConfig) { allows ->
      newRuntime.updateConsent(allows)
  }
  newRuntime.foregroundGate.runOnFirstForeground { newRuntime.noteFirstForeground() }
  ```
- [ ] **Step 3:** Xóa `AdFlowConfigScope.preloadOnFirstForeground` (dòng 27) + implementation ở `AdFlowConfigScopeImpl.kt` (dòng 25).
- [ ] **Step 4:** Cập nhật KDoc `BasePlacementScope.preload` (đang ở `AdFlowConfigScope.kt`) và `PlacementConfig.preload`: nghĩa mới "tự load ad kế tiếp sau khi ad hiện tại bị tiêu thụ (show/release) hoặc hết hạn (expiry)".
- [ ] **Step 5:** `AdFlowTest.kt`: fake `AdNetwork.createConsentManager` phải seed `onConsentChanged(true)` ngay khi tạo (mô phỏng consent manager thật). Test mới:
  - `initialize()` không gây load nào cho bất kỳ placement nào dù `preload = true` (dùng fake source ghi lại số lần gọi).
  - `network.initialize` chỉ chạy sau khi cả consent lẫn first foreground đều đủ điều kiện, và đúng 1 lần — drive foreground bằng `(ProcessLifecycleOwner.get().lifecycle as LifecycleRegistry).handleLifecycleEvent(Lifecycle.Event.ON_START)` dưới Robolectric.
  - End-to-end pending demand: `AdFlow.interstitial(id).load()` gọi trước khi consent resolve → bị gate; sau đó consent `true` + foreground → fake `network.initialize` complete → fake source nhận đúng 1 request.
- [ ] **Step 6:** `AdFlowBannerViewTest.kt` / `AdFlowNativeAdViewTest.kt`: fake network's `createConsentManager` seed `onConsentChanged(true)`; đưa helper "drive foreground qua LifecycleRegistry" vào file test util dùng chung; mỗi test cần ad load được phải drive foreground trước khi assert.

---

### Task 5: `AppOpenForegroundObserver` — tự load khi không có ad

**Files:**
- Modify: `adflow-core/src/main/java/com/adflow/core/fullscreen/AppOpenForegroundObserver.kt`
- Modify: `adflow-core/src/test/java/com/adflow/core/fullscreen/AppOpenForegroundObserverTest.kt`

- [ ] **Step 1:** Trong `showIfPossible()`: khi `!appOpen.canShow`, gọi `appOpen.load()` (coalesce sẵn có trong `ensureLoaded`; pending demand từ Task 3 tự lo trường hợp foreground xảy ra trước khi consent resolve) rồi return, thay vì không làm gì.
- [ ] **Step 2:** `FakeAppOpenAd` trong test thêm bộ đếm `loadCalls`. Test mới: "foreground không có ad khả dụng → gọi load()". Test cũ `canShowOverride = false` cập nhật kỳ vọng: giờ cũng gọi `load()` (vô hại theo thiết kế, không phải regression). Test "show được thì không gọi load" giữ nguyên/thêm assertion `loadCalls == 0`.

---

### Task 6: `AdMobConsentManager` — seed + fast path

**Files:**
- Modify: `adflow-admob/src/main/java/com/adflow/admob/consent/AdMobConsentManager.kt`
- Modify: `adflow-core/src/main/java/com/adflow/core/network/AdNetwork.kt` (KDoc contract)
- Modify: `adflow-admob/src/test/java/com/adflow/admob/consent/AdMobConsentManagerTest.kt`

- [ ] **Step 1:** Thêm param constructor internal `consentInformation: ConsentInformation = UserMessagingPlatform.getConsentInformation(context.applicationContext)` để test inject fake (interface, không cần Robolectric).
- [ ] **Step 2:** Trong `init`: gọi `onConsentChanged(canRequestAds())` ngay — seed từ consent phiên trước / geography NOT_REQUIRED, mở khóa ads không cần chờ `requestIfNeeded()`.
- [ ] **Step 3:** Trong `requestIfNeeded()`: gọi `refreshStatus()` đồng bộ NGAY SAU khi khởi phát `consentInformation.requestConsentInfoUpdate(...)` (trước khi callback thành công/thất bại chạy) — fast path Google khuyến nghị dùng consent đã cache. Giữ nguyên `refreshStatus()` hiện có trong cả 2 callback (thành công + lỗi).
- [ ] **Step 4:** Cập nhật KDoc `AdNetwork.createConsentManager`: core mặc định deny; implementation PHẢI gọi `onConsentChanged(canRequestAds())` đồng bộ lúc tạo và mỗi lần trạng thái đổi.
- [ ] **Step 5:** Test với fake `ConsentInformation`: seed đúng cả 2 case (`canRequestAds()` true/false lúc tạo); `requestIfNeeded` gọi `onConsentChanged` ngay sau khi khởi phát update, trước khi callback chạy; callback lỗi vẫn refresh; giữ test `mapStatus` hiện có.

---

### Task 7: Demo app — minh họa pattern mới

**Files:**
- Modify: `app/src/main/java/com/dev/adflow/MainActivity.kt`
- Modify: `app/src/main/java/com/dev/adflow/AdFlowDemoApp.kt` (chỉ refresh comment, không set `preload` nên không đổi hành vi)

- [ ] **Step 1:** `MainActivity.kt` dòng ~19-23: xóa comment cũ (mô tả sai model "placement đã preload trước đó vô hại nếu consent chưa resolve"). Thay bằng: gọi `AdFlow.consent.requestIfNeeded(this) {}` rồi gọi thẳng các `.load()` tường minh (`AdFlow.interstitial("splash_interstitial").load()`, `AdFlow.appOpen("app_open").load()`, `AdFlow.rewarded(...).load()` nếu demo có) — pending demand tự xếp hàng tới khi consent + network init xong; banner/native vẫn tự load qua view attach, không cần đụng vào.

---

### Task 8: README gốc

**Files:**
- Modify: `README.md`

- [ ] **Step 1:** §4 (khoảng dòng 173-176): thay bullet mô tả auto-preload lúc init bằng: lib không load gì lúc init; `MobileAds.initialize()` hoãn tới khi first foreground **và** `canRequestAds()` đều đủ điều kiện; lượt load đầu tiên là app-driven; lượt bị gate tạm thời (consent/network chưa sẵn sàng) được tự động xếp hàng và flush khi điều kiện đủ.
- [ ] **Step 2:** Dòng ~186 + ~225: cập nhật mô tả field `preload` → "tự load ad kế tiếp sau khi show/release/hết hạn".
- [ ] **Step 3:** §9 (dòng 362-388): xóa câu "mặc định cho phép nếu app chưa gọi requestIfNeeded" (sai với thiết kế mới); mô tả rõ `requestIfNeeded()` giờ là bước tích hợp BẮT BUỘC để có ad ở phiên đầu; mô tả fail-safe deny mặc định + fast path dùng consent phiên trước.
- [ ] **Step 4:** Bảng migration (§12, ~dòng 422-428): thêm nhóm dòng cho alpha03 → alpha04 (xóa `preloadOnFirstForeground`; consent bắt buộc; load đầu app-driven; `preload` áp dụng cả cho Native qua expiry).

---

### Task 9: Flutter plugin

**Files:**
- Modify: `flutter/adflow_flutter/lib/src/placements.dart` (doc comment 5 field `preload` — chỉ đổi ngữ nghĩa, KHÔNG đổi schema/pigeon)
- Modify: `flutter/adflow_flutter/lib/src/ad_state.dart` (dòng ~38, comment cũ về foreground preload)
- Modify: `flutter/adflow_flutter/example/lib/main.dart`
- Modify: `flutter/adflow_flutter/README.md`
- Modify: `flutter/adflow_flutter/CHANGELOG.md`
- Modify: `flutter/adflow_flutter/pubspec.yaml`
- Modify: `flutter/adflow_flutter/android/build.gradle.kts` (dòng ~81-82, pin JitPack)

- [ ] **Step 1:** Cập nhật doc comment `preload` trên cả 5 placement class (`InterstitialPlacement`, `RewardedPlacement`, `AppOpenPlacement`, `BannerPlacement`, `NativePlacement`) — không đổi field/schema, chỉ đổi mô tả hành vi.
- [ ] **Step 2:** `example/lib/main.dart`: sau `AdFlow.requestConsentIfNeeded()` (dòng ~52), thêm gọi `load()` tường minh cho các placement full-screen (Dart đã có `load()` trên cả 5 handle sẵn, không cần API mới).
- [ ] **Step 3:** README + CHANGELOG cập nhật theo nội dung Task 8.
- [ ] **Step 4:** `pubspec.yaml`: **2.0.0 → 3.0.0** (breaking: mất init-preload, consent thành bắt buộc cho phiên đầu — 2.1.0 sẽ gây hiểu lầm về mức độ ảnh hưởng).
- [ ] **Step 5:** `android/build.gradle.kts` dòng ~81-82: bump pin JitPack → `v1.0.0-alpha04` (cần tag + publish native trước, theo `RELEASING.md`).

---

### Task 10: Version bump

**Files:**
- Modify: `gradle.properties` (dòng ~22: `adflowVersion=1.0.0-alpha03` → `1.0.0-alpha04`)

---

## Breaking changes

1. Xóa `AdFlowConfigScope.preloadOnFirstForeground` (source-breaking).
2. Không còn auto-load lúc init — app phải tự kích lượt load đầu (behavioral).
3. Consent mặc định deny; `requestIfNeeded()` bắt buộc cho phiên đầu (phiên sau mở khóa bằng seed consent phiên trước).
4. `MobileAds.initialize()` hoãn tới first foreground + `canRequestAds()`.
5. `AdFlow.onFirstForeground` giờ chạy thật (trước là no-op — bug fix nhưng observable).
6. `preload=true` giờ tự load lại khi ad hết hạn (behavioral, mới — áp dụng cả Native).
7. Flutter plugin → 3.0.0.

## Kiểm chứng

```bash
./gradlew :adflow-core:testDebugUnitTest :adflow-admob:testDebugUnitTest
./gradlew :adflow-compose:testDebugUnitTest :app:assembleDebug
cd flutter/adflow_flutter && flutter analyze && flutter test
cd example && flutter build apk --debug
```

Trên thiết bị thật (thông lệ dự án với AdMob — unit test/Robolectric không đủ để xác nhận SDK thật):
1. Cài mới với `consentDebug { geography = EEA }`: Logcat xác nhận KHÔNG có ad request nào và không có `MobileAds.initialize` trước khi form UMP hiện; các `.load()` gọi sớm log deferred/blocked.
2. Chấp nhận form → log init đúng 1 lần → các load xếp hàng tự bắn → ad load thành công.
3. `autoShowOnForeground`: foreground không có ad sẵn → tự load (đường mới Task 5); foreground kế tiếp show được.
4. Kill + mở lại app (fast path phiên trước): ad load được ngay, không cần form hiện lại, kể cả trước khi `requestIfNeeded()` chạy xong.
5. Privacy options form → revoke consent → `load()` báo `CONSENT_REQUIRED`; re-grant → pending demand tự flush, không cần code app xử lý.
6. Show interstitial xong (`preload=true`) → tự nạp lại ngay; để app idle quá `expiry` (mặc định 4h) → quan sát load refresh tự động kể cả không có show nào xảy ra.
