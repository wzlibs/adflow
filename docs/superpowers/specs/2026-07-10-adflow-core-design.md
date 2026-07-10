# AdFlow — Tài liệu chức năng

Ngày: 2026-07-10
Phạm vi: `adflow-core` + `adflow-admob` (phần lõi và phần triển khai AdMob của thư viện quảng
cáo AdFlow). Tài liệu này mô tả **thư viện làm được gì** cho app dùng nó, không đi vào chi
tiết code/class bên trong.

## 1. Tổng quan

AdFlow là thư viện quản lý quảng cáo cho app Android, hỗ trợ đầy đủ 5 loại quảng cáo phổ biến:
Interstitial (toàn màn hình xen giữa), App Open (toàn màn hình lúc mở app), Rewarded (xem để
nhận thưởng), Native (quảng cáo lồng vào giao diện app) và Banner (dải quảng cáo cố định).

Mỗi vị trí đặt quảng cáo trong app (gọi là 1 "placement", ví dụ "Interstitial ở màn Splash",
"Banner ở màn Home") được cấu hình độc lập - có thể có nhiều placement cùng loại trong 1 app,
mỗi cái dùng ID quảng cáo và quy tắc riêng.

## 2. Từng loại quảng cáo hỗ trợ

- **Interstitial** - quảng cáo toàn màn hình, app tự quyết định lúc nào gọi hiển thị (ví dụ
  sau khi hoàn thành 1 màn chơi, khi chuyển màn hình).
- **App Open** - quảng cáo toàn màn hình dành riêng cho lúc user mở lại app (quay từ
  background). Có thể tự động hiển thị mà app không cần tự viết logic bắt sự kiện này (xem
  mục 5).
- **Rewarded** - quảng cáo user chủ động bấm xem để nhận phần thưởng trong app (thêm lượt
  chơi, mở khóa nội dung...). Báo về app chính xác khi nào user thực sự xem đủ để được
  thưởng.
- **Native** - quảng cáo không có hình dạng cố định, được "lồng" vào giao diện app theo layout
  do app tự thiết kế (xem mục 9).
- **Banner** - dải quảng cáo nhỏ, cố định vị trí trên màn hình, tự làm mới định kỳ.

## 3. Tải quảng cáo (Load)

- **Nhiều ID quảng cáo dự phòng theo thứ tự ưu tiên (waterfall)** - mỗi placement khai báo 1
  danh sách ID quảng cáo, thử ID đầu tiên trước, hết quảng cáo (no-fill) thì tự động rơi
  xuống thử ID kế tiếp, không cần app tự viết logic thử lại theo danh sách.
- **Tự động thử lại khi tải thất bại** - nếu toàn bộ danh sách ID đều hết quảng cáo, thư viện
  tự chờ 1 khoảng thời gian rồi thử lại cả danh sách, khoảng chờ tăng dần sau mỗi lần thất
  bại (để không dội liên tục vào server quảng cáo), và mặc định **thử lại vô thời hạn** cho
  tới khi có quảng cáo (thời gian chờ giữa các lần thử có giới hạn trên, không tăng vô hạn).
  Khoảng chờ ban đầu, hệ số tăng, giới hạn trên và số lần thử tối đa đều chỉnh được riêng cho
  từng placement nếu cần.
- **Tự động tải trước (preload)** - sau khi 1 quảng cáo toàn màn hình/Rewarded được hiển thị
  xong (user đóng lại hoặc hiển thị lỗi), thư viện tự tải sẵn quảng cáo tiếp theo, tùy chọn
  bật/tắt theo từng placement.
- **Bật/tắt 1 placement** - mỗi placement có thể được cấu hình tắt hẳn (không tải, không hiển
  thị) ngay từ đầu.
- **Điều kiện tải theo quy tắc riêng của app (`loadRule`)** - app tự định nghĩa 1 điều kiện
  (ví dụ: user chưa mua gói premium) để quyết định placement có được phép tải quảng cáo mới
  hay không, mà không cần sửa code thư viện.

## 4. Hiển thị quảng cáo (Show)

- **Giữ sẵn 1 quảng cáo trong bộ nhớ để hiển thị ngay lập tức** - không phải đợi tải mới mỗi
  lần cần hiển thị; quảng cáo được giữ tối đa 4 giờ theo mặc định (chỉnh được), quá thời gian
  đó tự bỏ đi và không dùng quảng cáo "cũ" nữa (Banner là ngoại lệ - không bao giờ bị coi là
  cũ, vì Banner tự làm mới liên tục chứ không giữ sẵn rồi hiển thị sau).
- **Điều kiện hiển thị theo quy tắc riêng của app (`showRule`)** - tương tự `loadRule` nhưng
  áp dụng ngay lúc hiển thị (ví dụ: chặn hiển thị Interstitial khi user đang ở màn thanh
  toán). Áp dụng cho **cả 5 loại quảng cáo**, kể cả Native/Banner.
- **Giới hạn tần suất giữa Interstitial và App Open** - tự động đảm bảo 1 khoảng nghỉ tối
  thiểu giữa 2 lần hiển thị Interstitial liên tiếp, giữa 2 lần App Open liên tiếp, và giữa 1
  lần Interstitial với 1 lần App Open ngay sau đó (theo cả 2 chiều) - tránh làm phiền user
  bằng quảng cáo dồn dập. Khoảng nghỉ này chỉnh được, không áp dụng cho Rewarded/Native/
  Banner.
- **Không bao giờ có 2 quảng cáo toàn màn hình chồng lên nhau** - nếu 1 Interstitial/App
  Open/Rewarded đang hiển thị, mọi lệnh hiển thị quảng cáo toàn màn hình khác (kể cả từ
  placement khác) sẽ bị chặn cho tới khi cái đang hiển thị đóng lại.
- **Không bao giờ làm crash app khi bị chặn hiển thị** - nếu quảng cáo chưa sẵn sàng, bị quy
  tắc `showRule` từ chối, chưa đủ thời gian nghỉ, hoặc đang có quảng cáo khác hiển thị, thư
  viện báo lại **lý do cụ thể** cho app qua callback (không throw lỗi, không crash). Với
  Native/Banner, nếu bị chặn thì trả về 1 khoảng trống (không chiếm layout) thay vì hiển thị
  gì đó sai lệch.
- **Tự "chữa lành" khi bị kẹt** - nếu 1 placement báo "chưa sẵn sàng" lúc app cần hiển thị,
  thư viện tự kích hoạt lại việc tải phía sau, để lần sau app gọi hiển thị có cơ hội thành
  công hơn, không cần app tự nhớ gọi tải lại.
- **Đổi sang quảng cáo Native mới theo yêu cầu** - Native ad được giữ và dùng lại cho tới khi
  hết hạn hoặc bị thay; app có thể chủ động yêu cầu đổi sang 1 quảng cáo Native mới (dù cái
  cũ vẫn còn hạn) - ví dụ khi user quay lại 1 màn hình đang hiển thị Native ad và muốn thấy
  nội dung khác.

## 5. Tự động hóa vòng đời app

- **Tự động hiển thị App Open mỗi khi user quay lại app** - app chỉ cần bật 1 tính năng có
  sẵn, không cần tự viết logic bắt sự kiện "app quay lại foreground". Thư viện tự đảm bảo
  không hiển thị App Open đè lên 1 quảng cáo khác đang hiển thị, và chỉ hiển thị khi đã có
  quảng cáo sẵn sàng.
- **Trì hoãn khởi tạo/tải quảng cáo tới khi app thực sự được user mở** - tránh lãng phí yêu
  cầu quảng cáo cho những lần process bị hệ điều hành đánh thức chỉ để xử lý việc ở nền (ví
  dụ nhận thông báo đẩy) mà không hề có màn hình nào sắp hiển thị cho user.

## 6. Quyền riêng tư / GDPR

- **Tích hợp sẵn cơ chế xin sự đồng ý (consent) theo chuẩn Google (UMP)** - tự phát hiện user
  có ở khu vực cần xin consent hay không (EEA/UK), tự hiện form xin phép nếu cần, app không
  phải tự viết logic phát hiện vùng địa lý.
- **Tự động tôn trọng lựa chọn của user** - một khi user chưa đồng ý hoặc từ chối, việc tải
  quảng cáo tự động bị chặn ở tầng thư viện, app không cần tự viết điều kiện kiểm tra riêng
  trước mỗi lần tải.
- **Có sẵn màn hình cho user xem lại/đổi lựa chọn đã chọn** - đáp ứng yêu cầu chính sách của
  Google/App Store về việc phải có lối vào để đổi ý sau này.
- **Hỗ trợ giả lập vùng EEA để test** - dev có thể test luồng xin consent ngay cả khi máy test
  không thực sự ở khu vực EEA.
- Đây là tính năng độc lập, tùy chọn tích hợp - app không dùng cũng không ảnh hưởng gì tới
  hành vi tải/hiển thị quảng cáo hiện có.

## 7. Theo dõi doanh thu quảng cáo

- Mỗi khi có sự kiện quảng cáo trả tiền (paid event) từ mạng quảng cáo, thư viện tự thu thập
  đầy đủ thông tin (placement, loại quảng cáo, ID quảng cáo, số tiền, đơn vị tiền tệ, độ
  chính xác, tên mạng quảng cáo) và forward cho app.
- App tự quyết định forward đi đâu (Adjust, AppsFlyer, Firebase, hệ thống nội bộ...) - thư
  viện không tự gắn cứng với 1 công cụ đo lường cụ thể nào, không phải cài thêm SDK nào khác
  nếu không dùng tính năng này.

## 8. Ghi log

- Mặc định mọi hoạt động quan trọng (đang tải, tải xong, tải lỗi, đang thử lại, hiển thị, bị
  chặn hiển thị, hiển thị lỗi, hết quảng cáo...) được ghi log ra Logcat để dev theo dõi lúc
  phát triển.
- App có thể thay thế bằng cách ghi log riêng của mình (ví dụ gửi vào hệ thống giám sát nội
  bộ) mà không mất đi log mặc định nếu không muốn.

## 9. Cách hiển thị Native/Banner trong giao diện app

- **Dùng được với cả giao diện View truyền thống (XML) lẫn Jetpack Compose** - có sẵn cách
  dùng cho cả 2 kiểu, app không phải tự viết cầu nối.
- **2 mẫu giao diện Native dựng sẵn** - 1 mẫu đầy đủ (tiêu đề + ảnh + mô tả + nút hành động,
  bố cục dọc) và 1 mẫu gọn (tiêu đề + icon + mô tả, không có ảnh lớn, dùng khi không đủ chỗ -
  ví dụ trong danh sách).
- **Cho phép app tự thiết kế giao diện Native hoàn toàn riêng** - nếu 2 mẫu có sẵn không phù
  hợp, app có thể tự viết layout riêng, thư viện lo phần gắn dữ liệu quảng cáo thật (tiêu đề,
  ảnh, nút bấm...) vào layout đó và đảm bảo việc đo lường click/hiển thị của mạng quảng cáo
  vẫn hoạt động đúng.

## 10. Dễ dàng đổi mạng quảng cáo

- Toàn bộ phần "lõi" (cấu hình placement, quy tắc, cache, retry, chống chồng lấn, giới hạn
  tần suất...) không phụ thuộc vào bất kỳ mạng quảng cáo cụ thể nào.
- Hiện tại thư viện dùng Google AdMob làm mạng quảng cáo thật, nhưng kiến trúc cho phép sau
  này đổi sang 1 mạng quảng cáo khác (hoặc dùng song song) mà **không cần sửa code phía app**
  - chỉ cần đổi 1 thành phần triển khai duy nhất.

## 11. Hỗ trợ Flutter

Ngoài app Android thuần (Kotlin), AdFlow còn có 1 plugin riêng cho Flutter
(`adflow_flutter`), cho phép app viết bằng Flutter dùng lại toàn bộ các tính năng ở trên từ
phía Dart, bao gồm cả khả năng chọn giao diện Native tùy biến viết bằng Kotlin. Chi tiết
phần Flutter không nằm trong tài liệu này.
