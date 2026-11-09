# Quickstart & kịch bản kiểm thử: 001-history-auth-presence

## 0. Chuẩn bị

1. Xóa thư mục `data/` (nếu muốn bắt đầu sạch).
2. `build.bat` → phải in "Bien dich thanh cong."
3. `run-selftest.bat` → phải in `TẤT CẢ ĐỀU ĐẠT` (kiểm thử tự động: băm mật khẩu, chuỗi trạng thái hoạt
   động, và kịch bản tích hợp đăng ký/đăng nhập/lịch sử/biên nhận/khởi động lại server).
4. `run-server.bat`, sau đó mở 3 cửa sổ `run-client.bat` (gọi là A, B, C).

## US1 – Đăng ký / đăng nhập

| # | Thao tác | Kết quả mong đợi |
|---|---|---|
| 1.1 | A: chọn "Đăng ký", tên `an`, mật khẩu `123456` hai lần | Vào màn hình chat, tiêu đề "MiniChat - an" |
| 1.2 | B: đăng ký `AN` | Báo "Tên đăng nhập đã tồn tại" |
| 1.3 | B: đăng ký `binh`, mật khẩu `12345` | Báo mật khẩu phải từ 6 ký tự |
| 1.4 | B: đăng ký `binh`, hai mật khẩu khác nhau | Báo "Mật khẩu nhập lại không khớp" |
| 1.5 | B: đăng ký `binh` / `abcdef` | Thành công |
| 1.6 | C: đăng nhập `an` / `sai1234` và `khongco` / `123456` | Cả hai báo "Sai tên đăng nhập hoặc mật khẩu" |
| 1.7 | C: đăng nhập `an` / `123456` (A vẫn đang mở) | Báo "Tài khoản đang đăng nhập ở nơi khác"; A không bị ảnh hưởng |
| 1.8 | Mở `data/accounts.log` | Không thấy `123456` hay `abcdef` |
| 1.9 | Tắt server, bật lại; A, B đăng nhập lại | Thành công |

## US2 – Lịch sử

| # | Thao tác | Kết quả mong đợi |
|---|---|---|
| 2.1 | A, B tham gia `General`, chat vài câu; A gửi 1 ảnh cho B (chat riêng) | Hiện như cũ |
| 2.2 | C: đăng ký `chi`; A tạo nhóm `DoAn` chọn B và C | Cả 3 thấy nhóm |
| 2.3 | C đăng xuất; A nhắn trong `DoAn` và nhắn riêng cho `chi` | Không báo lỗi |
| 2.4 | C đăng nhập `chi` | Thấy `DoAn` và chat riêng với `an` với chấm đỏ chưa đọc; mở ra thấy đủ tin |
| 2.5 | Tắt server, bật lại; mọi người đăng nhập lại | Đủ lịch sử chat chung, nhóm, riêng; ảnh vẫn mở/lưu được |
| 2.6 | B rời `DoAn`, đăng xuất rồi đăng nhập lại | Không còn `DoAn` trong danh sách |

## US3 – Trạng thái hoạt động

| # | Thao tác | Kết quả mong đợi |
|---|---|---|
| 3.1 | A mở Danh bạ khi B online | `binh` có chấm xanh, "Đang hoạt động", xếp trên người offline |
| 3.2 | B đăng xuất | Trong ≤ 5 giây, A thấy `binh` "Vừa truy cập", mất chấm xanh |
| 3.3 | Chờ 2 phút | A thấy "Truy cập 2 phút trước" mà không làm gì |
| 3.4 | A mở chat riêng với `binh` | Dòng dưới tên hiện cùng trạng thái |
| 3.5 | Tắt/bật server | Thời điểm truy cập cuối của `binh` không đổi |

## US4 – Trạng thái tin nhắn

| # | Thao tác | Kết quả mong đợi |
|---|---|---|
| 4.1 | B offline; A nhắn riêng cho `binh` | Dưới tin: "Đã gửi" |
| 4.2 | B đăng nhập (không mở chat với `an`) | Trong ≤ 3 giây A thấy "Đã nhận" |
| 4.3 | B mở chat với `an` | Trong ≤ 3 giây A thấy "Đã xem" |
| 4.4 | B đang mở chat với `an`, thu nhỏ cửa sổ; A nhắn tiếp | "Đã nhận"; B mở lại cửa sổ → "Đã xem" |
| 4.5 | Nhóm `DoAn` (A, B, C); A nhắn; chỉ B mở nhóm | "Đã xem 1/2", di chuột thấy "binh" |
| 4.6 | C mở nhóm | "Đã xem 2/2" |
| 4.7 | A nhắn 3 tin liên tiếp | Chỉ tin cuối có dòng trạng thái |
| 4.8 | A gửi file > 5 MB qua server (đổi giới hạn phía client không được) / tắt server rồi gửi | "Gửi thất bại" màu đỏ |
| 4.9 | Chat chung | Chỉ hiện "Đã gửi" |
| 4.10 | A đăng xuất trước khi B xem; B xem; A đăng nhập lại | A thấy "Đã xem" |

## Client dòng lệnh

| # | Thao tác | Kết quả mong đợi |
|---|---|---|
| 5.1 | `run-console-client.bat`, chọn 2 (đăng ký) rồi 1 (đăng nhập) | Hoạt động, mật khẩu không hiện khi gõ (nếu chạy trong cmd) |
| 5.2 | Sau đăng nhập | In lịch sử kèm giờ |
| 5.3 | `/users` | Liệt kê mọi tài khoản với "Đang hoạt động" / "Truy cập N phút trước" |
| 5.4 | Nhận tin riêng từ A trong console | A thấy "Đã nhận" (không bao giờ "Đã xem") |
