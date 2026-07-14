# Init SDK quảng cáo chỉ theo consent (bỏ điều kiện foreground)

> Bản ghi quyết định (contract giữa người và AI) - giải thích WHY, để đọc lại sau này hiểu vì sao code trông như vậy chứ không phải giải thích WHAT (code đã tự nói qua tên hàm/biến).

## Bối cảnh

`AdFlow.initialize()` trước đây gọi `network.initialize(appContext) { ... }` (tức `MobileAds.initialize()` thật) vô điều kiện ngay khi app vào foreground lần đầu - không quan tâm consent GDPR (`canRequestAds()`) đã resolve hay chưa. Đây là vi phạm khuyến nghị UMP của Google: cả init SDK lẫn load ad đều phải chờ có consent.

## Quyết định

SDK init (`network.initialize()`) giờ chỉ phụ thuộc **duy nhất** vào `canRequestAds()`, không còn cần thêm điều kiện "đã foreground":
- Nếu `canRequestAds()` đã `true` ngay từ đầu (user cũ, consent phiên trước còn hợp lệ, hoặc geography không cần hỏi) → init ngay lập tức, có thể chạy ngay trong `Application.onCreate()`, trước khi có Activity nào kịp resume.
- Nếu chưa (`false`, lần đầu cài đặt) → đợi tới khi consent callback báo `true` (sau khi app gọi `AdFlow.consent.requestIfNeeded(activity)` và user tương tác form UMP) → init đúng lúc đó.

Cơ chế: `AdMobConsentManager` seed `onConsentChanged(canRequestAds())` đồng bộ ngay trong `init` (đọc consent đã lưu ở phiên trước, không cần mạng). `AdFlowRuntime.updateConsent(allows)` nhận giá trị này, và nếu `allows == true` và chưa init lần nào, gọi `networkInitializer` đúng 1 lần (guard `networkInitStarted`).

## Lý do bỏ điều kiện foreground

Thiết kế nháp ban đầu định bắt SDK init phải chờ cả `firstForegroundSeen && canRequestAds()` - lý do "chờ foreground" này thực ra copy nhầm từ 1 bài toán khác:

- **Load ad** (gọi API load thật của mạng quảng cáo cho 1 placement cụ thể) tốn 1 request thật, có thể **lặp lại nhiều lần** (đặc biệt với `preload=true` tự nạp lại liên tục) - nếu process chỉ bị OS đánh thức ngầm (vd FCM push) mà không có màn hình nào sắp hiện, load ad ở đó là lãng phí thật và có thể lặp lại ở mỗi lần thức dậy tiếp theo. Điều kiện "chờ foreground" có ý nghĩa thật ở đây.
- **Init SDK** thì khác hẳn: chạy **đúng 1 lần duy nhất** trong cả vòng đời process (có guard `networkInitStarted` chặn gọi lại), không phát sinh ad request nào, và không có yêu cầu pháp lý nào của Google buộc phải chờ thêm foreground ngoài consent. Có xảy ra lúc app bị đánh thức ngầm hay lúc app đang mở cũng chỉ tốn đúng 1 lần y hệt.

Kết luận: điều kiện foreground chỉ có ý nghĩa cho việc *load ad*, bị áp nhầm sang bước *init SDK* trong thiết kế gốc. Gỡ bỏ nó khỏi bước init làm bài toán đơn giản hơn hẳn mà không đánh đổi gì thật.

## `preload` không còn liên quan gì tới init nữa

Trước đây `preload` nhốt 2 hành vi độc lập: (1) tự `load()` mọi placement `preload=true` lúc `AdFlow.initialize()` (qua `preloadActions` + `scope.preloadOnFirstForeground`), và (2) tự load ad kế tiếp sau khi ad hiện tại bị tiêu thụ (show/release). Hành vi (1) đã bị xóa hoàn toàn trong thay đổi này. Từ giờ `preload` chỉ còn đúng 1 nghĩa: **sau khi ad hiện tại bị tiêu thụ (show xong với full-screen, `release()` với banner), tự load ngay 1 ad kế tiếp** - hành vi này vốn đã có sẵn (`FullScreenShowGate.afterShowEnds()`, `BannerAdControllerImpl.release()`), không đổi gì.

Hệ quả: **lượt load ĐẦU TIÊN của mọi placement giờ luôn phải do app chủ động gọi** (`.load()` cho full-screen/rewarded; banner/native tự load khi gắn view, không cần app gọi).

## Ngoại lệ: `autoShowOnForeground` (App Open) vẫn phải tự lo cả load

Hệ quả ở trên đúng cho Interstitial/Rewarded (app vốn đã tự viết code gọi `.show()`, thêm 1 dòng `.load()` không lạ), nhưng **không đúng tinh thần** cho placement App Open bật `autoShowOnForeground = true`: lý do tính năng này tồn tại là để app **không phải viết gì cả**, thư viện tự lo toàn bộ vòng đời "khi nào show". Nếu bắt app tự nhớ gọi `.load()` 1 lần thì lời hứa "auto" chỉ còn đúng nửa vế (tự show, không tự load) - và khi app quên, không có gì báo lỗi, ad chỉ đơn giản không bao giờ hiện, âm thầm.

Sửa: `AppOpenForegroundObserver.showIfPossible()` giờ tự gọi `appOpen.load()` khi `!canShow` (bất kể lý do gì - showRule/interval/slot bận đều vô hại vì `ensureLoaded()` tự no-op nếu đã có ad sẵn sàng), thay vì chỉ đứng nhìn. Placement `autoShowOnForeground` tự lo trọn cả load lẫn show, đúng nghĩa "auto".

## Phạm vi KHÔNG đụng tới (để dành cho tăng dần sau)

- Gate từng lượt `.load()` theo "network đã init xong chưa" (cơ chế pending-demand/`readyForAdRequests`, xếp hàng lượt load bị chặn tạm thời rồi tự flush khi điều kiện đủ). Theo tài liệu AdMob, gọi `loadAd()` trước khi `initialize()` hoàn tất vẫn an toàn (SDK tự xếp hàng nội bộ), nên đây không phải lỗ hổng nghiêm trọng, chỉ là chưa tối ưu.
- `preload` áp dụng thêm cho expiry-drop (tự load lại khi ad hết hạn, kể cả Native) - không nằm trong thay đổi này.

Cả 2 điểm trên vẫn thuộc `docs/superpowers/plans/2026-07-13-preload-consent-redesign.md` (giữ nguyên, không sửa) - Task 2/4 của file đó (giả định init cần cả `firstForegroundSeen`) coi như đã bị thay thế trên thực tế bởi quyết định ở đây; các task còn lại (1,3,5-10) của file đó vẫn là việc chưa làm, tách biệt.

## Breaking changes

1. Xóa `AdFlowConfigScope.preloadOnFirstForeground` (source-breaking).
2. Không còn auto-load lúc `initialize()` cho bất kỳ placement nào dù `preload = true` (behavioral, mặc định `preload` vẫn là `true`) - app đang dựa vào việc "mở app lên là đã có sẵn ad" sẽ thấy placement full-screen/rewarded rỗng cho tới khi tự gọi `.load()`. Banner/Native không bị ảnh hưởng (vẫn tự load khi gắn view).
3. `MobileAds.initialize()` không còn đợi first-foreground - có thể chạy ngay trong `Application.onCreate()` nếu consent đã có sẵn từ phiên trước.

## File liên quan

- `adflow-admob/src/main/java/com/adflow/admob/consent/AdMobConsentManager.kt` - seed `onConsentChanged` trong `init`.
- `adflow-core/src/main/java/com/adflow/core/engine/AdFlowRuntime.kt` - `updateConsent()`, `networkInitializer`, guard `networkInitStarted`.
- `adflow-core/src/main/java/com/adflow/core/AdFlow.kt` - wiring trong `initialize()`.
- `adflow-core/src/main/java/com/adflow/core/config/AdFlowConfigScope.kt` - KDoc `preload` mới, xóa `preloadOnFirstForeground`.
- `adflow-core/src/main/java/com/adflow/core/fullscreen/AppOpenForegroundObserver.kt` - `showIfPossible()` tự `load()` khi `!canShow`.
