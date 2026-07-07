# Hướng dẫn publish `adflow-core`/`adflow-admob`

Tài liệu dành cho người maintain repo này - các bước để đưa 1 bản build mới của `adflow-core`/`adflow-admob` lên JitPack, và đồng bộ lại local Maven repo mà Flutter plugin (`flutter/adflow_flutter`) đang dùng.

Có 2 nơi cùng tiêu thụ 2 module này, và **cả 2 đều phải cập nhật mỗi khi API đổi** - không cái nào tự động kéo theo cái kia:

1. **JitPack** - cho app Android khác dùng qua Gradle dependency bình thường (xem `README.md` mục 1).
2. **Local Maven repo tĩnh** checked-in tại `flutter/adflow_flutter/android/local-maven/` - cho Flutter plugin `adflow_flutter` dùng (xem `flutter/adflow_flutter/RELEASING.md`... hiện chưa có, ghi chú luôn ở đây).

## 1. Bump version

Sửa `adflowVersion` trong `gradle.properties` (dùng cho local Maven repo - JitPack **không** đọc giá trị này, xem mục 3):

```properties
adflowVersion=0.2.0
```

## 2. Publish lại local Maven repo cho Flutter plugin

```bash
./gradlew :adflow-core:publishReleasePublicationToLocalFlutterRepoRepository \
           :adflow-admob:publishReleasePublicationToLocalFlutterRepoRepository
```

Kiểm tra `flutter/adflow_flutter/android/local-maven/com/adflow/{core,admob}/<version mới>/` xuất hiện đúng file (`.aar`, `.pom`, `.module`, sources jar). Commit toàn bộ thư mục `local-maven/` cùng với thay đổi API (thư mục này checked-in git, không gitignore).

Nếu chỉ API AdFlow đổi mà quên bước này, `adflow_flutter` sẽ tiếp tục build với version cũ một cách âm thầm (không lỗi biên dịch).

## 3. Tag + push

JitPack build theo git tag, **không** đọc `adflowVersion` - tên tag chính là version mà consumer sẽ dùng trong dependency (`implementation("com.github.wzlibs.adflow:core:<tag>")`). Nên đặt tag khớp `adflowVersion` cho dễ nhớ, có tiền tố `v`:

```bash
git tag -a v0.2.0 -m "Mô tả ngắn thay đổi trong bản này"
git push origin master
git push origin v0.2.0
```

## 4. Trigger + verify build trên JitPack

JitPack chỉ build khi có request đầu tiên tới 1 tag (build theo yêu cầu, không tự động chạy khi tag được push). Trigger bằng cách tải trực tiếp file `.pom` của từng module (request đầu tiên sẽ block vài chục giây tới vài phút để JitPack build xong rồi mới trả về):

```bash
curl -s "https://jitpack.io/com/github/wzlibs/adflow/core/v0.2.0/core-v0.2.0.pom"
curl -s "https://jitpack.io/com/github/wzlibs/adflow/admob/v0.2.0/admob-v0.2.0.pom"
```

HTTP 200 kèm nội dung POM thật (không phải trang lỗi) nghĩa là build thành công. Có thể xem log build đầy đủ tại `https://jitpack.io/com/github/wzlibs/adflow/core/v0.2.0/build.log` nếu cần debug lỗi build.

`jitpack.yml` ở root đã pin sẵn JDK 21 khớp AGP/Gradle đang dùng - nếu sau này nâng cấp AGP/Gradle cần JDK khác, nhớ cập nhật `jitpack.yml` theo, nếu không JitPack sẽ build lỗi bằng JDK cũ.

## 5. Cập nhật README.md (tuỳ chọn)

`README.md` mục 1 hiện hard-code ví dụ `v0.1.0` - cân nhắc cập nhật lên tag mới nhất để ví dụ trong tài liệu luôn phản ánh bản ổn định gần nhất, dù về mặt kỹ thuật consumer vẫn có thể tự chọn bất kỳ tag nào.

## Ghi chú

- `groupId` khi build qua JitPack luôn là `com.github.wzlibs.adflow` (theo owner/repo GitHub), **khác** với `groupId = "com.adflow"` cấu hình trong `build.gradle.kts` (giá trị đó chỉ có hiệu lực cho local Maven repo/`mavenLocal()` - JitPack tự ghi đè groupId theo scheme riêng của nó khi publish, chỉ giữ nguyên `artifactId`/`version`).
- Không cần đổi gì trong `adflow-core/build.gradle.kts`/`adflow-admob/build.gradle.kts` khi thêm/đổi kênh publish - cấu hình `maven-publish` hiện tại phục vụ được cả `publishToMavenLocal` (JitPack dùng) lẫn `publishReleasePublicationToLocalFlutterRepoRepository` (Flutter dùng) cùng lúc.
