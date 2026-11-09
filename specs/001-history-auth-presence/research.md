# Research: 001-history-auth-presence

Mỗi mục: Decision / Rationale / Alternatives considered.

## R1. Lưu trữ phía server

- **Decision**: Các file nhật ký chỉ ghi nối thêm (append-only), UTF-8, mỗi dòng một bản ghi, trong
  thư mục `data/`: `accounts.log`, `groups.log`, `messages.log`, `receipts.log`, và `data/files/<id>`
  chứa nội dung ảnh/file. Khi server khởi động, đọc lại toàn bộ (replay) để dựng trạng thái trong
  bộ nhớ; khi chạy, mọi thay đổi được ghi thêm một dòng rồi `flush()`.
- **Rationale**: Không cần thư viện (Nguyên tắc I). Ghi nối thêm không bao giờ ghi đè dữ liệu cũ nên
  server bị tắt đột ngột cũng chỉ có thể mất / hỏng dòng cuối. Định dạng dòng giống giao thức (các
  trường cách nhau bằng dấu cách, trường cuối tự do) nên dễ đọc khi vấn đáp.
- **Chống hỏng dữ liệu**: Trước khi mở để ghi, nếu file không kết thúc bằng `\n` (dòng cuối bị ghi
  dở) thì ghi thêm `\n` để dòng mới không dính vào dòng hỏng. Khi đọc, dòng sai định dạng bị bỏ qua
  kèm cảnh báo trong log. File đính kèm ghi ra `<id>.tmp` rồi đổi tên thành `<id>` (atomic move) TRƯỚC
  khi ghi dòng tin nhắn tham chiếu tới nó.
- **Alternatives**: SQLite/JDBC (cần jar ngoài, người dùng đã chọn file); serialize Java object
  (không đọc được bằng mắt, hỏng toàn bộ nếu ghi dở); ghi lại toàn bộ file JSON mỗi lần (chậm dần khi
  có 10.000 tin, nguy cơ mất hết khi ghi dở).

## R2. Băm mật khẩu

- **Decision**: PBKDF2WithHmacSHA256 (có sẵn trong `javax.crypto`), salt ngẫu nhiên 16 byte từ
  `SecureRandom`, 65.536 vòng lặp, khóa 256 bit; lưu `salt` và `hash` dạng Base64. So sánh bằng
  `MessageDigest.isEqual` (thời gian hằng). Khi tên không tồn tại vẫn băm một lần với salt giả để thời
  gian phản hồi giống trường hợp sai mật khẩu.
- **Rationale**: Chuẩn khuyến nghị cho mật khẩu, có trong JDK, đáp ứng ràng buộc bảo mật của
  constitution. ~50 ms mỗi lần băm, chấp nhận được khi đăng nhập.
- **Alternatives**: SHA-256 + salt một vòng (quá nhanh, dễ dò); BCrypt/Argon2 (cần thư viện ngoài).

## R3. Thay đổi giao thức

- **Decision**: Bổ sung lệnh mới, giữ lệnh cũ khi có thể:
  - `LOGIN` thêm trường mật khẩu; thêm `REGISTER`. (Phá vỡ cú pháp cũ: cập nhật cả 2 client.)
  - Tin nhắn server → client gộp về một định dạng có định danh và thời điểm:
    `MSG <id> <ts> <loại> <đích> <từ> <cờ> <tin>` và `MSG_FILE ... <base64> <tên file>`.
    Thay cho `MSG_ALL`, `MSG_GROUP`, `MSG_PM`, `GROUP_INFO` (thông báo nhóm là `MSG` với người gửi `*`).
  - Client giao diện gửi bằng `SEND` / `SENDFILE` kèm mã tạm `cid`; server trả `ACK <cid> <id> <ts>`
    (Đã gửi) hoặc `NACK <cid> <lý do>` (Gửi thất bại) thay vì gửi lại tin cho người gửi.
    Các lệnh cũ `ALL`/`GROUP`/`PM`/`FILE` vẫn dùng được (client dòng lệnh) và người gửi nhận lại `MSG`.
  - Biên nhận: client gửi `RECEIVED <id,...>` và `SEEN <loại> <đích> <id>`; server báo người gửi
    `STATUS <id> <nhận> <xem> <tổng> <người xem>`.
  - Trạng thái hoạt động: `PRESENCE u1:ON,u2:<ms>,...`; `LOGIN_OK <tên> <giờ server>` để client tính
    độ lệch đồng hồ.
- **Rationale**: Một định dạng tin duy nhất cho cả tin trực tiếp lẫn lịch sử giúp client xử lý giống
  nhau; `cờ` (`N` mới, `U` lịch sử chưa đọc, `R` lịch sử đã đọc) cho client biết có đếm chưa đọc /
  báo thông báo hay không. `ACK` tránh gửi lại 5 MB file về cho chính người gửi.
- **Alternatives**: Giữ `MSG_ALL/MSG_GROUP/MSG_PM` và thêm trường id → 3 cú pháp khác nhau phải sửa
  cùng lúc, phức tạp hơn; đổi sang JSON từng dòng → lệch với giao thức văn bản hiện tại.

## R4. Ai báo "Đã nhận"?

- **Decision**: Lớp `ChatConnection` (dùng chung cho 2 client) tự gửi `RECEIVED <id>` ngay khi đọc
  được một `MSG`/`MSG_FILE` có cờ `N` hoặc `U`, loại `GROUP` hoặc `PM`, người gửi không phải mình.
- **Rationale**: FR-022 yêu cầu "Đã nhận" chỉ khi ứng dụng người nhận thật sự nhận được; server ghi
  thành công vào socket chưa chứng minh được điều đó. Đặt ở `ChatConnection` để cả client dòng lệnh
  cũng báo nhận (Assumptions của spec).
- **Alternatives**: Server tự đánh dấu khi ghi socket (không đúng FR-022).

## R5. "Đã xem"

- **Decision**: Client giao diện gửi `SEEN <loại> <đích> <id lớn nhất của tin người khác>` khi cuộc trò
  chuyện đang mở VÀ cửa sổ đang được chọn: lúc chọn cuộc trò chuyện, lúc cửa sổ được kích hoạt, và
  lúc có tin mới tới cuộc trò chuyện đang mở. Server đánh dấu đã xem (kèm đã nhận) mọi tin trong cuộc
  trò chuyện có id ≤ id đó mà người này là người nhận; duyệt ngược và dừng khi gặp tin đã xem (vì "xem
  một tin = xem mọi tin trước nó").
- **Rationale**: Khớp FR-026; một lệnh cho cả loạt tin, không phải gửi từng id.
- **Alternatives**: Gửi `SEEN` cho từng tin (nhiều dòng thừa).

## R6. Người nhận và tổng N

- **Decision**: Mỗi tin `GROUP`/`PM` lưu danh sách người nhận tại thời điểm gửi (nhóm: thành viên trừ
  người gửi; riêng: người kia). Tin `ALL` và thông báo hệ thống không lưu người nhận, không có biên
  nhận.
- **Rationale**: Edge case "rời nhóm sau khi tin được gửi" → vẫn tính trong N (FR-027).

## R7. Lịch sử khi đăng nhập

- **Decision**: Sau `LOGIN_OK` và các danh sách, server gửi tối đa 100 tin gần nhất của: chat chung,
  từng nhóm đang tham gia, từng cuộc chat riêng có tham gia; sau mỗi cuộc trò chuyện gửi `STATUS` cho
  các tin của chính người đó. Khi được thêm / tham gia nhóm lúc đang online, server gửi lịch sử của
  nhóm đó ngay sau `JOINED`. Cờ chưa đọc: chat chung → tin của người khác sau lần truy cập cuối trước
  đó; nhóm/riêng → người này là người nhận và chưa xem.
- **Rationale**: SC-004 (2 giây với 10.000 tin) dễ đạt vì toàn bộ chỉ mục nằm trong bộ nhớ; đọc nội
  dung file từ đĩa ngoài khóa đồng bộ.
- **Rủi ro chấp nhận**: Lịch sử có nhiều file lớn sẽ tải lâu (100 × 5 MB). Chấp nhận ở mức đồ án;
  "xem tin cũ hơn"/tải file theo yêu cầu nằm ngoài phạm vi.

## R8. Thời gian

- **Decision**: Mọi thời điểm là epoch millis của server. `LOGIN_OK` gửi giờ server; client lưu
  `offset = giờServer - giờMáy` và dùng `now + offset` khi tính "N phút trước". Chuỗi hiển thị do
  `minichat.common.Presence.describe(lastSeen, now)` tạo ra (dùng chung cho 2 client, kiểm thử được).

## R9. Đồng thời ở server

- **Decision**: Giữ mô hình một thread mỗi client. Thay đổi trạng thái (tài khoản online, nhóm, lưu
  tin, biên nhận) nằm trong khối `synchronized` của `ChatServer`; việc băm mật khẩu và việc giao tin
  mới (có thể là file 5 MB) ra socket làm ngoài khóa. `ChatStore` có phương thức `synchronized` riêng.
- **Điều chỉnh khi cài đặt**: lịch sử (lúc đăng nhập / vào nhóm) được gửi TRONG khóa, để lịch sử
  luôn tới trước mọi tin mới của cùng cuộc trò chuyện (client chỉ nối thêm tin vào cuối). Đánh đổi:
  lịch sử có nhiều file lớn làm các client khác chờ trong lúc gửi – chấp nhận ở mức đồ án (R7).
- **Rationale**: Nguyên tắc III; tránh một client gửi file 5 MB làm treo cả server.

## R10. Kiểm thử

- **Decision**: `minichat.selftest.SelfTest` (có `main`, không JUnit): kiểm tra `PasswordHasher`,
  `Presence`, và một kịch bản tích hợp chạy server thật trên cổng trống với thư mục dữ liệu tạm, kết
  nối bằng socket thô, gồm cả khởi động lại server. Chạy bằng `run-selftest.bat`. Giao diện kiểm thử
  thủ công theo `quickstart.md`.
