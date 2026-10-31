# GpxT Proxy App

Ứng dụng Proxy HTTP/HTTPS chạy trên Android, hỗ trợ bảo vệ bằng Key bản quyền.

## Tính năng
- Chạy Proxy Server trực tiếp trên điện thoại (Port mặc định: 8888).
- Hỗ trợ HTTP và HTTPS (thông qua CONNECT tunnel).
- Có khóa Wifi Lock chống ngắt kết nối khi tắt màn hình.
- Giao diện tối giản, tự tạo bằng code Java (không cần file XML).

## Key Bản Quyền
- Key mặc định: `123456`
- Để đổi key, sửa biến `SECRET_KEY` trong file `MainActivity.java`.

## Hướng dẫn Build
1. Tải về máy.
2. Mở bằng AIDE Pro hoặc Android Studio.
3. Build và cài đặt APK.
