# MiniChat – Ứng dụng chat Client/Server bằng Java Socket

## 1. Chức năng

| Chức năng | Mô tả |
|---|---|
| Chat chung | Gửi tin nhắn tới tất cả user đang online |
| Chat nhóm | Tạo nhóm, tham gia nhóm có sẵn, rời nhóm; tin nhắn chỉ gửi tới thành viên trong nhóm |
| Chat riêng | Gửi tin nhắn tới một user cụ thể |
| Gửi ảnh / file | Ảnh hiển thị ngay trong khung chat (nhấp để xem lớn, lưu về máy); file tối đa 5 MB |
| Emoji | Bảng chọn biểu cảm; ô nhập trống thì nút gửi là 👍 để gửi nhanh |
| Đang soạn tin | Hiện "X đang soạn tin..." cho người cùng cuộc trò chuyện |
| Nhóm kiểu Zalo | Tạo nhóm kèm chọn thành viên, thêm thành viên, rời nhóm |
| Thông báo | Đếm tin chưa đọc, sắp xếp hội thoại theo tin mới nhất, thông báo ở khay hệ thống khi cửa sổ không được chọn |
| Tài khoản | Đăng ký / đăng nhập bằng mật khẩu (băm PBKDF2 + salt, không lưu mật khẩu gốc); mỗi tài khoản chỉ đăng nhập ở một nơi |
| Lịch sử tin nhắn | Server lưu tin nhắn, ảnh, file, nhóm vào thư mục `data/`; đăng nhập lại (kể cả sau khi tắt server) vẫn thấy lịch sử; nhắn được cho người đang offline |
| Trạng thái hoạt động | Danh bạ gồm mọi tài khoản: "Đang hoạt động" (chấm xanh) hoặc "Truy cập N phút / giờ / ngày trước" |
| Trạng thái tin nhắn | Đang gửi → Đã gửi → Đã nhận → Đã xem (nhóm: "Đã xem K/N", di chuột để xem ai đã xem); Gửi thất bại |

- **Server**: luôn chờ kết nối, mỗi client được xử lý bởi một thread riêng; giữ danh sách kết nối (user → socket) và danh sách nhóm (nhóm → thành viên); phân phối tin nhắn.
- **Client**: có 2 phiên bản dùng chung phần kết nối mạng:
  - `ChatClientGUI` – giao diện Swing (khuyên dùng).
  - `ConsoleClient` – chạy trên cmd.
- Tài khoản mới chưa ở trong nhóm nào. Server tạo sẵn nhóm `General` để tham gia. Nhóm và thành viên được lưu lâu dài (offline vẫn là thành viên).
- Dữ liệu server nằm trong thư mục `data/` (tự tạo khi chạy server). Xóa thư mục này để bắt đầu lại từ đầu.

## 2. Cấu trúc project

```
MiniChat/
├── src/minichat/
│   ├── common/Protocol.java        # định nghĩa giao thức (các lệnh)
│   ├── common/Presence.java        # chuỗi "Đang hoạt động" / "Truy cập N phút trước"
│   ├── server/ChatServer.java      # server: tài khoản, nhóm, phân phối tin, lịch sử, biên nhận
│   ├── server/ClientHandler.java   # thread xử lý từng client
│   ├── server/ChatStore.java       # lưu trữ lâu dài trong data/ (tài khoản, nhóm, tin, biên nhận)
│   ├── server/AppendLog.java       # file nhật ký chỉ ghi nối thêm, bỏ qua dòng hỏng
│   ├── server/StoredMessage.java   # một tin đã lưu + người nhận + đã nhận / đã xem
│   ├── server/PasswordHasher.java  # băm mật khẩu PBKDF2 + salt
│   ├── client/ChatConnection.java  # kết nối socket phía client
│   ├── client/ChatClientGUI.java   # client giao diện Swing (phong cách Zalo)
│   ├── client/ConsoleClient.java   # client dòng lệnh
│   └── client/ui/                  # các thành phần giao diện: bong bóng chat, avatar,
│                                   # icon, đăng nhập/đăng ký, tạo nhóm, emoji...
│   └── selftest/SelfTest.java      # kiểm thử tự động (không cần JUnit)
├── specs/001-history-auth-presence # tài liệu Spec Kit: spec, plan, tasks, quickstart (kịch bản kiểm thử)
├── data/                           # dữ liệu server (tự tạo, không đưa lên git)
├── build.bat                       # biên dịch vào thư mục bin
├── run-server.bat
├── run-client.bat                  # client giao diện
├── run-console-client.bat          # client dòng lệnh
├── run-selftest.bat                # chạy kiểm thử tự động
└── .project / .classpath           # để import vào Eclipse
```

## 3. Cách chạy

Yêu cầu: JDK 17 trở lên.

### Cách 1 – Eclipse
1. `File > Import > General > Existing Projects into Workspace` → chọn thư mục `MiniChat`.
2. Chạy `minichat.server.ChatServer` (chuột phải → `Run As > Java Application`).
3. Chạy `minichat.client.ChatClientGUI` nhiều lần (mỗi lần là một user). Có thể chạy `ConsoleClient` để chat trên console.

### Cách 2 – Dòng lệnh (cmd)
1. Nhấp đúp `build.bat` để biên dịch (nếu `javac` chưa có trong PATH, đặt biến môi trường `JAVA_HOME` trỏ tới thư mục JDK).
2. Nhấp đúp `run-server.bat`.
3. Nhấp đúp `run-client.bat` (hoặc `run-console-client.bat`) nhiều lần để mở nhiều client.

Mặc định server chạy ở cổng **5000** và lưu dữ liệu ở `data/`. Đổi cổng: `run-server.bat 6000` (hoặc `run-server.bat 6000 <thư mục dữ liệu>`), client: `run-client.bat <ip-server> 6000`.

Kiểm thử tự động: nhấp đúp `run-selftest.bat`, phải thấy `TẤT CẢ ĐỀU ĐẠT`. Kịch bản kiểm thử bằng tay: `specs/001-history-auth-presence/quickstart.md`.
Để chat giữa các máy trong cùng mạng LAN, nhập IP của máy chạy server ở ô *Server* khi đăng nhập.

## 4. Hướng dẫn sử dụng

**Client giao diện**
- Lần đầu: bấm **Chưa có tài khoản? Đăng ký ngay**, nhập tên (không dấu cách), mật khẩu (6-50 ký tự) hai lần.
- Những lần sau: đăng nhập bằng tên và mật khẩu đó (cùng server, cổng).
- Thanh bên màu xanh: tab **Tin nhắn**, tab **Danh bạ**, nút **Đăng xuất**.
- Tab Tin nhắn: các cuộc trò chuyện sắp xếp theo tin mới nhất, chấm đỏ là số tin chưa đọc; ô tìm kiếm gõ không dấu cũng được.
- Tab Danh bạ: mọi tài khoản, người đang online (chấm xanh) xếp trước, người offline hiện "Truy cập N phút trước"; bấm vào để chat riêng (kể cả khi người đó offline). Bấm vào nhóm để tham gia / mở nhóm.
- Dưới tin cuối cùng bạn gửi: **Đang gửi / Đã gửi / Đã nhận / Đã xem** (nhóm: **Đã xem K/N**, di chuột để xem tên). "Đã xem" được tính khi người nhận mở cuộc trò chuyện trong lúc cửa sổ MiniChat đang được chọn.
- Nút cạnh ô tìm kiếm: **Tạo nhóm** (đặt tên + chọn thành viên).
- Trong nhóm: nút **Thêm thành viên** và **Rời nhóm** ở góc trên bên phải.
- Thanh công cụ trên ô nhập: emoji, gửi ảnh, gửi file. Nhấp vào ảnh để xem lớn, nhấp vào file để lưu.

**Client console**

Khi mở: chọn `1` để đăng nhập, `2` để đăng ký (mật khẩu không hiện khi gõ). Sau khi đăng nhập, lịch sử được in kèm giờ, tin chưa đọc có chữ "mới".

| Lệnh | Ý nghĩa |
|---|---|
| `<tin nhắn>` | chat chung |
| `/g <nhóm> <tin nhắn>` | chat nhóm |
| `/pm <user> <tin nhắn>` | chat riêng |
| `/create <nhóm>` / `/join <nhóm>` / `/leave <nhóm>` | tạo / vào / rời nhóm |
| `/users`, `/groups` | xem mọi tài khoản kèm trạng thái hoạt động, danh sách nhóm |
| `/quit` | thoát |

## 5. Giao thức

Mỗi thông điệp là một dòng văn bản UTF-8, các trường cách nhau bởi dấu cách.

| Client → Server | Server → Client |
|---|---|
| `REGISTER <tên> <mật khẩu>` / `LOGIN <tên> <mật khẩu>` | `LOGIN_OK <tên> <giờ server>` / `ERROR <lý do>`, rồi danh sách và lịch sử |
| `CREATE <nhóm> [u1,u2]` | `JOINED <nhóm>` (gửi người tạo và các thành viên đang online) |
| `ADD <nhóm> <user>` | `JOINED <nhóm>` + lịch sử nhóm (gửi user được thêm) |
| `JOIN <nhóm>` | `JOINED <nhóm>` + lịch sử nhóm |
| `LEAVE <nhóm>` | `LEFT <nhóm>` |
| `SEND <loại> <đích> <cid> <tin>` | `ACK <cid> <id> <ts>` (người gửi) hoặc `NACK <cid> <lý do>`; `MSG ...` (người nhận) |
| `SENDFILE <loại> <đích> <cid> <base64> <tên file>` | như trên, người nhận nhận `MSG_FILE ...` |
| `ALL <tin>`, `GROUP <nhóm> <tin>`, `PM <user> <tin>`, `FILE ...` | lệnh kiểu cũ (client console): mọi người kể cả người gửi nhận `MSG` / `MSG_FILE` |
| `RECEIVED <id1,id2>` | `STATUS ...` gửi người gửi tin |
| `SEEN <loại> <đích> <id>` | `STATUS ...` gửi người gửi tin |
| `TYPING <loại> <đích>` | `TYPING <loại> <đích> <từ>` (gửi những người còn lại) |
| `LIST` | `USERS u1,u2`, `PRESENCE u1:ON,u2:<ts>`, `GROUPS g1:2,g2:1`, `MY_GROUPS g1` |
| `QUIT` | `INFO <tên> đã rời MiniChat` (gửi những người còn lại) |

Tin nhắn server → client:

- `MSG <id> <ts> <loại> <đích> <từ> <cờ> <tin>` – `<từ>` là `*` với thông báo hệ thống của nhóm ("X đã tham gia nhóm").
- `MSG_FILE <id> <ts> <loại> <đích> <từ> <cờ> <base64> <tên file>`
- `<cờ>`: `N` tin mới, `U` lịch sử chưa đọc, `R` lịch sử đã đọc. `<ts>` là giờ server (mili giây).
- `STATUS <id> <số đã nhận> <số đã xem> <tổng người nhận> <người xem,...|->`

`<loại>` là `ALL` (chat chung, đích là `*`), `GROUP` (đích là tên nhóm) hoặc `PM` (đích là tên người nhận).
File được mã hóa Base64 để truyền trên cùng một dòng văn bản. Khi đăng nhập, server gửi lại tối đa 100 tin gần nhất của mỗi cuộc trò chuyện.
Chi tiết và ví dụ: `specs/001-history-auth-presence/contracts/protocol.md`.

Server tự gửi lại `USERS`, `PRESENCE`, `GROUPS`, `MY_GROUPS` cho mọi client mỗi khi có user vào/ra hoặc nhóm thay đổi.

## 6. Lưu trữ (thư mục `data/`)

| File | Nội dung |
|---|---|
| `accounts.log` | `ACCOUNT <tên> <salt> <hash> <tạo lúc>`, `LASTSEEN <tên> <ms>` |
| `groups.log` | `GROUP <nhóm>`, `JOIN <nhóm> <user>`, `LEAVE <nhóm> <user>` |
| `messages.log` | `TEXT`/`FILE <id> <ms> <loại> <đích> <từ> <người nhận> <nội dung / tên file>` |
| `receipts.log` | `D <id> <user> <ms>` (đã nhận), `S <id> <user> <ms>` (đã xem) |
| `files/<id>` | nội dung ảnh / file |

Các file chỉ được **ghi nối thêm**, nên server tắt đột ngột cũng không làm hỏng dữ liệu cũ; khi khởi động, server đọc lại toàn bộ và bỏ qua dòng hỏng (có cảnh báo trong log).
