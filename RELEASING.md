# Hướng dẫn publish `adflow-core`/`adflow-admob`

Tài liệu dành cho người maintain repo này - các bước để đưa 1 bản build mới của `adflow-core`/`adflow-admob` lên JitPack.

Flutter plugin `flutter/adflow_flutter` phụ thuộc trực tiếp coordinate JitPack (`com.github.wzlibs.adflow:core`/`admob:<tag>`) trong `android/build.gradle.kts` của chính nó - không còn local Maven repo trung gian nữa. Sau khi ra tag mới ở đây, nếu muốn `adflow_flutter` dùng version mới, cập nhật tag đó trong `flutter/adflow_flutter/android/build.gradle.kts` (xem `flutter/adflow_flutter/RELEASING.md`) như 1 bước riêng, không tự động kéo theo.

## 1. Bump version

Sửa `adflowVersion` trong `gradle.properties` (chỉ có ý nghĩa nội bộ cho `publishToMavenLocal` - JitPack **không** đọc giá trị này, xem mục 3):

```properties
adflowVersion=0.2.0
```

## 2. Tag + push

JitPack build theo git tag, **không** đọc `adflowVersion` - tên tag chính là version mà consumer sẽ dùng trong dependency (`implementation("com.github.wzlibs.adflow:core:<tag>")`). Nên đặt tag khớp `adflowVersion` cho dễ nhớ, có tiền tố `v`:

```bash
git tag -a v0.2.0 -m "Mô tả ngắn thay đổi trong bản này"
git push origin master
git push origin v0.2.0
```

## 3. Trigger + verify build trên JitPack

JitPack chỉ build khi có request đầu tiên tới 1 tag (build theo yêu cầu, không tự động chạy khi tag được push). Trigger bằng cách tải trực tiếp file `.pom` của từng module (request đầu tiên sẽ block vài chục giây tới vài phút để JitPack build xong rồi mới trả về):

```bash
curl -s "https://jitpack.io/com/github/wzlibs/adflow/core/v0.2.0/core-v0.2.0.pom"
curl -s "https://jitpack.io/com/github/wzlibs/adflow/admob/v0.2.0/admob-v0.2.0.pom"
```

HTTP 200 kèm nội dung POM thật (không phải trang lỗi) nghĩa là build thành công. Có thể xem log build đầy đủ tại `https://jitpack.io/com/github/wzlibs/adflow/core/v0.2.0/build.log` nếu cần debug lỗi build.

`jitpack.yml` ở root đã pin sẵn JDK 21 khớp AGP/Gradle đang dùng - nếu sau này nâng cấp AGP/Gradle cần JDK khác, nhớ cập nhật `jitpack.yml` theo, nếu không JitPack sẽ build lỗi bằng JDK cũ.

## 4. Cập nhật README.md (tuỳ chọn)

`README.md` mục 1 hiện hard-code ví dụ `v0.1.0` - cân nhắc cập nhật lên tag mới nhất để ví dụ trong tài liệu luôn phản ánh bản ổn định gần nhất, dù về mặt kỹ thuật consumer vẫn có thể tự chọn bất kỳ tag nào.

## Ghi chú

- `groupId` khi build qua JitPack luôn là `com.github.wzlibs.adflow` (theo owner/repo GitHub), **khác** với `groupId = "com.adflow"` cấu hình trong `build.gradle.kts` (giá trị đó chỉ có hiệu lực cho `publishToMavenLocal`/`mavenLocal()` - JitPack tự ghi đè groupId theo scheme riêng của nó khi publish, chỉ giữ nguyên `artifactId`/`version`).
- Không cần đổi gì trong `adflow-core/build.gradle.kts`/`adflow-admob/build.gradle.kts` khi ra tag mới - cấu hình `maven-publish` hiện tại đã cung cấp sẵn task `publishToMavenLocal` mà JitPack cần.
