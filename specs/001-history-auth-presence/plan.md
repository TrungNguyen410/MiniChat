# Implementation Plan: Tài khoản, lịch sử tin nhắn, trạng thái hoạt động và trạng thái tin nhắn

**Branch**: `001-history-auth-presence` | **Date**: 2026-11-09 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/001-history-auth-presence/spec.md`

## Summary

Thêm tài khoản có mật khẩu (PBKDF2 + salt), lưu lâu dài tài khoản / nhóm / tin nhắn / biên nhận
bằng các file nhật ký ghi nối thêm trong `data/`, gửi lại lịch sử khi đăng nhập, cho phép nhắn tới
người offline, hiển thị trạng thái hoạt động ("Đang hoạt động" / "Truy cập N phút trước") và trạng
thái tin (Đang gửi / Đã gửi / Đã nhận / Đã xem (K/N) / Gửi thất bại). Giao thức được mở rộng với
định danh tin, `ACK`/`NACK`, `RECEIVED`/`SEEN`/`STATUS` và `PRESENCE` (xem [contracts/protocol.md](contracts/protocol.md)).

## Technical Context

**Language/Version**: Java 17 (biên dịch `--release 17`; máy dev có JDK 21)

**Primary Dependencies**: Chỉ JDK: `java.net` (socket), `javax.swing`, `javax.crypto` (PBKDF2), `java.nio.file`

**Storage**: File văn bản UTF-8 ghi nối thêm trong `data/` (+ `data/files/<id>` cho ảnh/file) – xem [data-model.md](data-model.md)

**Testing**: `minichat.selftest.SelfTest` (lớp `main` tự kiểm tra, có kịch bản tích hợp qua socket) + kịch bản thủ công [quickstart.md](quickstart.md)

**Target Platform**: Windows (bat), chạy được mọi nơi có JDK 17+; Eclipse / VS Code

**Project Type**: Ứng dụng desktop client/server qua TCP socket

**Performance Goals**: 20 client đồng thời trong LAN; lịch sử hiện ≤ 2 s khi có 10.000 tin; biên nhận ≤ 3 s; trạng thái online ≤ 5 s

**Constraints**: Không thư viện ngoài; file ≤ 5 MB; mật khẩu không lưu dạng gốc; không mất tin đã "Đã gửi" khi server bị tắt đột ngột

**Scale/Scope**: Mức đồ án: hàng chục tài khoản, ~10.000 tin

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Nguyên tắc | Đánh giá | Kết quả |
|---|---|---|
| I. Thuần Java SE | Chỉ dùng JDK; `build.bat` thêm package `selftest` | ✅ |
| II. Giao thức có tài liệu | Mọi lệnh mới có hằng số + Javadoc trong `Protocol.java` và bảng README. Có thay đổi phá vỡ (`LOGIN`, `MSG_*`, `GROUP_INFO`) → cập nhật cả `ChatClientGUI` và `ConsoleClient` trong feature này | ✅ (ghi ở Complexity Tracking) |
| III. Server là nguồn sự thật | Server kiểm tra mật khẩu, phiên, quyền xem lịch sử, quyền gửi biên nhận (chỉ người nhận); trạng thái do server tính rồi gửi `STATUS`/`PRESENCE` | ✅ |
| IV. Kiểm thử được | `quickstart.md` phủ mọi acceptance scenario; logic tách lớp (`PasswordHasher`, `Presence`, `ChatStore`) có `SelfTest` | ✅ |
| V. Đơn giản, tiếng Việt | Dùng lại `Theme`, `Avatar`, `PlaceholderTextField`; mọi thông báo tiếng Việt; log đăng ký / đăng nhập / lỗi lưu trữ | ✅ |
| Ràng buộc lưu trữ / bảo mật | `data/` tự tạo, thêm vào `.gitignore`; ghi nối thêm + đổi tên atomic; PBKDF2 + salt | ✅ |

**Re-check sau Phase 1**: thiết kế trong data-model / contracts không thêm phụ thuộc, không vi phạm
nguyên tắc nào → ✅.

## Project Structure

### Documentation (this feature)

```text
specs/001-history-auth-presence/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/protocol.md
├── checklists/requirements.md
└── tasks.md
```

### Source Code (repository root)

```text
src/minichat/
├── common/
│   ├── Protocol.java          # SỬA: lệnh mới, bỏ MSG_ALL/MSG_GROUP/MSG_PM/GROUP_INFO, luật mật khẩu
│   └── Presence.java          # MỚI: "Đang hoạt động" / "Truy cập N phút trước"
├── server/
│   ├── ChatServer.java        # SỬA: đăng ký/đăng nhập, nhóm lâu dài, lưu & giao tin, biên nhận, lịch sử, presence
│   ├── ClientHandler.java     # SỬA: phân tích lệnh mới
│   ├── PasswordHasher.java    # MỚI: PBKDF2 + salt
│   ├── AppendLog.java         # MỚI: đọc/ghi file nhật ký nối thêm, bỏ qua dòng hỏng
│   ├── ChatStore.java         # MỚI: tài khoản, nhóm, tin nhắn, biên nhận + lưu xuống data/
│   └── StoredMessage.java     # MỚI: một tin đã lưu + người nhận + biên nhận
├── client/
│   ├── ChatConnection.java    # SỬA: login/register, SEND/SENDFILE với cid, tự gửi RECEIVED, SEEN, lệch đồng hồ
│   ├── ChatClientGUI.java     # SỬA: lịch sử, ACK/NACK/STATUS, SEEN, danh bạ có presence, timer cập nhật
│   ├── ConsoleClient.java     # SỬA: menu đăng nhập/đăng ký, in lịch sử, /users có presence
│   └── ui/
│       ├── ChatMessage.java       # SỬA: id, giờ server, trạng thái
│       ├── Conversation.java      # SỬA: id tin cuối của người khác, id đã báo SEEN
│       ├── MessageListPanel.java  # SỬA: dòng trạng thái dưới tin cuối của mình
│       └── LoginDialog.java       # SỬA: ô mật khẩu, chế độ Đăng nhập / Đăng ký
└── selftest/
    └── SelfTest.java          # MỚI: kiểm thử tự động
build.bat, run-selftest.bat (MỚI), .gitignore (thêm data/), README.md
```

**Structure Decision**: Giữ cấu trúc một project, 3 package như constitution; thêm package
`minichat.selftest` cho kiểm thử không phụ thuộc framework.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Thay đổi phá vỡ giao thức (`LOGIN` thêm mật khẩu; `MSG_*`/`GROUP_INFO` → `MSG`) | Cần mật khẩu; tin phải có id + giờ để làm lịch sử và biên nhận | Giữ 4 định dạng cũ + thêm trường → 4 nhánh phân tích ở mỗi client, dễ lệch; cả 2 client đều được cập nhật trong feature này nên không ai dùng cú pháp cũ |
