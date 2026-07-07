# Hướng dẫn publish `adflow_flutter` lên pub.dev

Xem `RELEASING.md` ở root repo trước (cách publish `adflow-core`/`adflow-admob` lên JitPack) - `adflow_flutter` phụ thuộc trực tiếp coordinate đó qua `android/build.gradle.kts`.

## 1. Nếu `adflow-core`/`adflow-admob` vừa ra tag mới

Cập nhật tag trong `android/build.gradle.kts`:

```kotlin
dependencies {
    api("com.github.wzlibs.adflow:core:v0.2.0")
    api("com.github.wzlibs.adflow:admob:v0.2.0")
}
```

Build lại `example/` để xác nhận vẫn hoạt động với version mới trước khi publish:

```bash
cd example && flutter build apk --debug
```

## 2. Bump version + CHANGELOG

Sửa `version:` trong `pubspec.yaml`, thêm mục mới lên đầu `CHANGELOG.md` mô tả thay đổi.

## 3. Verify trước khi publish

```bash
flutter analyze
flutter pub publish --dry-run
```

`--dry-run` báo lỗi/cảnh báo (thiếu dependency, git chưa commit sạch...) mà không thực sự publish - sửa hết trước khi qua bước 4. Commit toàn bộ thay đổi (kể cả trong `example/`) trước khi publish thật - `dart pub publish` cảnh báo nếu git working tree không sạch.

## 4. Publish thật (cần làm thủ công, không tự động hoá được)

`dart pub login` mở OAuth qua trình duyệt bằng tài khoản Google - bước này cần con người thực hiện, không chạy được từ agent/CI không tương tác.

```bash
dart pub login
cd flutter/adflow_flutter
flutter pub publish
```

Gõ `y` khi được hỏi xác nhận. Sau khi publish, kiểm tra `https://pub.dev/packages/adflow_flutter` hiển thị đúng version.

## Ghi chú

- Publish dưới tài khoản Google cá nhân (không phải verified publisher) - package sẽ hiện "uploader: <email>" thay vì publisher badge, không ảnh hưởng chức năng.
- `android/build.gradle.kts` không còn dùng local Maven repo nữa (đã chuyển hẳn sang JitPack) - không cần bước republish nào ở phía Flutter khi core/admob đổi API, chỉ cần bump tag như mục 1.
