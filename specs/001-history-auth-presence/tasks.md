---
description: "Task list for 001-history-auth-presence"
---

# Tasks: Tài khoản, lịch sử tin nhắn, trạng thái hoạt động và trạng thái tin nhắn

**Input**: Design documents from `/specs/001-history-auth-presence/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/protocol.md, quickstart.md

**Tests**: Constitution IV yêu cầu `SelfTest` cho logic tách riêng + kịch bản quickstart.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Có thể làm song song (file khác nhau, không phụ thuộc)
- **[Story]**: US1–US4 theo spec.md

---

## Phase 1: Setup

- [X] T001 Thêm `data/` vào `.gitignore`
- [X] T002 [P] Thêm `src\minichat\selftest\*.java` vào lệnh biên dịch trong `build.bat`; tạo `run-selftest.bat`

---

## Phase 2: Foundational (chặn mọi user story)

- [X] T003 Cập nhật `src/minichat/common/Protocol.java`: hằng số REGISTER, SEND, SENDFILE, RECEIVED, SEEN, MSG, ACK, NACK, STATUS, PRESENCE, SYSTEM_SENDER, cờ N/U/R, PASSWORD_MIN/MAX, `isValidPassword`; bỏ MSG_ALL/MSG_GROUP/MSG_PM/GROUP_INFO; Javadoc giao thức mới
- [X] T004 [P] Tạo `src/minichat/server/AppendLog.java`: đọc từng dòng (bỏ qua dòng hỏng có log), ghi nối thêm + flush, tự thêm `\n` nếu dòng cuối ghi dở
- [X] T005 [P] Tạo `src/minichat/server/StoredMessage.java` theo data-model (recipients, delivered, seen, conversationKey)
- [X] T006 Tạo `src/minichat/server/ChatStore.java`: nạp/ghi accounts, groups, messages (+ `data/files`), receipts; chỉ mục theo cuộc trò chuyện và PM theo user
- [X] T007 Sửa `src/minichat/client/ui/ChatMessage.java`: id, cid, giờ server, `Status`, số nhận/xem/tổng, người xem

**Checkpoint**: Nền tảng lưu trữ và giao thức sẵn sàng.

---

## Phase 3: User Story 1 – Đăng ký / đăng nhập (P1) 🎯 MVP

**Goal**: Tài khoản có mật khẩu, lâu dài, một phiên mỗi tài khoản.

**Independent Test**: quickstart US1 (1.1–1.9) + SelfTest phần tài khoản.

- [X] T008 [P] [US1] Tạo `src/minichat/server/PasswordHasher.java` (PBKDF2WithHmacSHA256, salt 16 byte, so sánh thời gian hằng)
- [X] T009 [US1] Sửa `src/minichat/server/ClientHandler.java`: `LOGIN <tên> <mk>`, `REGISTER <tên> <mk>`
- [X] T010 [US1] Sửa `src/minichat/server/ChatServer.java`: register/login (băm ngoài khóa), từ chối phiên trùng, `LOGIN_OK <tên> <giờ>`, `main` dùng `data/`; logout không còn xóa thành viên nhóm
- [X] T011 [US1] Sửa `src/minichat/client/ChatConnection.java`: `connect(host, port, name, password, register)`, đọc giờ server → `clockOffset`
- [X] T012 [US1] Sửa `src/minichat/client/ui/LoginDialog.java`: ô mật khẩu, ô nhập lại (chế độ đăng ký), nút chuyển Đăng nhập ↔ Đăng ký, kiểm tra độ dài / khớp
- [X] T013 [P] [US1] Sửa `src/minichat/client/ConsoleClient.java`: menu 1 Đăng nhập / 2 Đăng ký, đọc mật khẩu ẩn bằng `Console.readPassword` khi có

**Checkpoint**: Đăng ký / đăng nhập chạy được ở cả 2 client.

---

## Phase 4: User Story 2 – Lịch sử tin nhắn (P2)

**Goal**: Lưu và gửi lại lịch sử; nhắn được tới người offline; nhóm lâu dài.

**Independent Test**: quickstart US2 (2.1–2.6) + SelfTest phần khởi động lại server.

- [X] T014 [US2] `ChatServer`: lưu mọi tin (văn bản, file, thông báo nhóm) qua `ChatStore`, giao `MSG`/`MSG_FILE` cờ `N`; `SEND`/`SENDFILE` → `ACK`/`NACK`; lệnh cũ ALL/GROUP/PM/FILE vẫn chạy
- [X] T015 [US2] `ChatServer`: PM tới tài khoản offline, CREATE/ADD với user offline, nhóm lưu lâu dài, thông báo nhóm thành tin hệ thống
- [X] T016 [US2] `ChatServer`: gửi lịch sử khi đăng nhập (100 tin/cuộc, cờ U/R) và khi vừa vào nhóm; đọc file đính kèm ngoài khóa
- [X] T017 [US2] `ClientHandler`: phân tích SEND, SENDFILE
- [X] T018 [US2] `ChatConnection`: `sendText/sendFile` với cid
- [X] T019 [US2] `ChatClientGUI`: phân tích `MSG`/`MSG_FILE`, lịch sử không báo thông báo, cờ U tăng chưa đọc, giờ tin theo server; danh sách bạn để tạo nhóm/thêm thành viên gồm cả người offline
- [X] T020 [P] [US2] `ConsoleClient`: phân tích `MSG`/`MSG_FILE`, in lịch sử kèm giờ

**Checkpoint**: Thoát / khởi động lại server không mất gì.

---

## Phase 5: User Story 3 – Trạng thái hoạt động (P3)

**Goal**: Danh bạ mọi tài khoản với "Đang hoạt động" / "Truy cập N … trước".

**Independent Test**: quickstart US3 (3.1–3.5) + SelfTest `Presence`.

- [X] T021 [P] [US3] Tạo `src/minichat/common/Presence.java` (`describe(lastSeen, now)`, `ONLINE_TEXT`)
- [X] T022 [US3] `ChatServer`: lưu LASTSEEN khi thoát / mất kết nối, gửi `PRESENCE` mỗi khi có người vào/ra
- [X] T023 [US3] `ChatClientGUI`: danh bạ mọi tài khoản (online trước), chấm xanh, dòng trạng thái; tiêu đề chat riêng; timer 30 s cập nhật
- [X] T024 [P] [US3] `ConsoleClient`: `/users` in mọi tài khoản kèm trạng thái

---

## Phase 6: User Story 4 – Trạng thái tin nhắn (P4)

**Goal**: Đang gửi → Đã gửi → Đã nhận → Đã xem (K/N); Gửi thất bại.

**Independent Test**: quickstart US4 (4.1–4.10) + SelfTest phần biên nhận.

- [X] T025 [US4] `ChatServer` + `ClientHandler`: `RECEIVED`, `SEEN` (chỉ người nhận hợp lệ, không lùi trạng thái), gửi `STATUS` cho người gửi; `STATUS` sau lịch sử
- [X] T026 [US4] `ChatConnection`: tự gửi `RECEIVED` khi nhận `MSG`/`MSG_FILE` cờ N/U (GROUP/PM, không phải của mình); `sendSeen`
- [X] T027 [US4] `Conversation` + `MessageListPanel`: dòng trạng thái chỉ dưới tin cuối của mình (trừ "Gửi thất bại"), tooltip người đã xem, cập nhật tại chỗ
- [X] T028 [US4] `ChatClientGUI`: tin hiện ngay "Đang gửi", `ACK`/`NACK`/`STATUS`, `SEEN` khi mở cuộc trò chuyện + cửa sổ được chọn, mất kết nối → "Gửi thất bại"

---

## Phase 7: Polish & Cross-Cutting

- [X] T029 Tạo `src/minichat/selftest/SelfTest.java`: PasswordHasher, Presence, kịch bản tích hợp (đăng ký, trùng tên, sai mật khẩu, phiên trùng, PM offline, RECEIVED/SEEN/STATUS, nhóm K/N, khởi động lại server, mật khẩu không có trong file)
- [X] T030 [P] Cập nhật `README.md` (chức năng, giao thức, data/, selftest)
- [X] T031 Chạy `build.bat` + `SelfTest`, sửa lỗi; rà lại quickstart

---

## Dependencies & Execution Order

- Setup (T001–T002) → Foundational (T003–T007) → US1 → US2 → US3 → US4 → Polish.
- US2 cần US1 (danh tính). US3 chỉ cần US1 (có thể làm song song với US2). US4 cần US2 (id tin, lưu trữ).
- Trong mỗi story: server (`ChatServer`/`ClientHandler`) trước, rồi `ChatConnection`, rồi GUI/console.

## Parallel Example

```text
Foundational: T004 AppendLog.java  |  T005 StoredMessage.java   (song song)
US1:          T008 PasswordHasher  |  T013 ConsoleClient         (song song)
US3:          T021 Presence.java   |  T024 ConsoleClient /users  (song song)
```

## Implementation Strategy

MVP = Phase 1–3 (đăng ký/đăng nhập). Sau đó thêm lần lượt US2 → US3 → US4, mỗi story kiểm tra bằng
phần tương ứng của quickstart trước khi sang story tiếp theo. Vì `ChatServer.java` và
`ChatClientGUI.java` bị sửa bởi nhiều story, các task trên cùng file làm tuần tự.
