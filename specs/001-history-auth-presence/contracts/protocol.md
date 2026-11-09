# Contract: Giao thức MiniChat v2 (feature 001)

Mỗi thông điệp là một dòng UTF-8, các trường cách nhau bằng dấu cách, trường cuối được chứa dấu cách.
`<loại>` ∈ `ALL` | `GROUP` | `PM`; với `ALL` thì `<đích>` là `*`. `<ts>` là epoch millis giờ server.

## Client → Server

| Lệnh | Trạng thái | Mô tả |
|---|---|---|
| `REGISTER <tên> <mật khẩu>` | mới | Tạo tài khoản rồi đăng nhập luôn. Trả `LOGIN_OK` hoặc `ERROR` |
| `LOGIN <tên> <mật khẩu>` | **đổi** (thêm mật khẩu) | Trả `LOGIN_OK` hoặc `ERROR` |
| `SEND <loại> <đích> <cid> <tin>` | mới | Gửi văn bản; `<cid>` là mã tạm do client đặt (không dấu cách) |
| `SENDFILE <loại> <đích> <cid> <base64> <tên file>` | mới | Gửi ảnh / file |
| `RECEIVED <id1,id2,...>` | mới | Báo ứng dụng đã nhận các tin |
| `SEEN <loại> <đích> <id>` | mới | Đã xem mọi tin tới `<id>` trong cuộc trò chuyện |
| `ALL <tin>`, `GROUP <nhóm> <tin>`, `PM <user> <tin>` | giữ | Như cũ; người gửi nhận lại `MSG` thay vì `ACK` |
| `FILE <loại> <đích> <base64> <tên file>` | giữ | Như cũ |
| `CREATE`, `ADD`, `JOIN`, `LEAVE`, `LIST`, `TYPING`, `QUIT` | giữ | `CREATE`/`ADD` chấp nhận cả user offline |

## Server → Client

| Thông điệp | Trạng thái | Mô tả |
|---|---|---|
| `LOGIN_OK <tên> <giờ server>` | **đổi** | `<tên>` là tên chuẩn của tài khoản |
| `MSG <id> <ts> <loại> <đích> <từ> <cờ> <tin>` | mới | Tin văn bản; `<từ>` = `*` là thông báo hệ thống |
| `MSG_FILE <id> <ts> <loại> <đích> <từ> <cờ> <base64> <tên file>` | **đổi** | Tin ảnh / file |
| `ACK <cid> <id> <ts>` | mới | Server đã lưu tin (Đã gửi) |
| `NACK <cid> <lý do>` | mới | Tin bị từ chối (Gửi thất bại) |
| `STATUS <id> <đã nhận> <đã xem> <tổng> <người xem,...\|->` | mới | Gửi cho người gửi khi biên nhận thay đổi, và sau lịch sử |
| `PRESENCE <u:ON\|ms,...>` | mới | Mọi tài khoản: `ON` nếu online, ngược lại thời điểm truy cập cuối |
| `USERS`, `GROUPS`, `MY_GROUPS`, `JOINED`, `LEFT`, `INFO`, `ERROR`, `TYPING` | giữ | Như cũ |
| `MSG_ALL`, `MSG_GROUP`, `MSG_PM`, `GROUP_INFO` | **bỏ** | Thay bằng `MSG` |

`<cờ>`: `N` = tin mới trực tiếp; `U` = lịch sử, chưa đọc; `R` = lịch sử, đã đọc.

## Thông báo lỗi (tiếng Việt)

| Trường hợp | Thông báo |
|---|---|
| Đăng ký trùng tên | `Tên đăng nhập đã tồn tại` |
| Sai tên hoặc mật khẩu | `Sai tên đăng nhập hoặc mật khẩu` |
| Đăng nhập trùng phiên | `Tài khoản đang đăng nhập ở nơi khác` |
| Mật khẩu sai độ dài | `Mật khẩu phải từ 6 đến 50 ký tự` |
| Tên không hợp lệ | `Tên không hợp lệ (1-20 ký tự, không chứa khoảng trắng)` |
| PM tới tên không tồn tại | `Người dùng <tên> không tồn tại` |

## Trình tự đăng nhập

```
C: LOGIN an matkhau1
S: LOGIN_OK an 1762680000000
S: USERS an,binh
S: GROUPS DoAn:3,General:2
S: MY_GROUPS DoAn
S: PRESENCE an:ON,binh:ON,chi:1762679000000
S: INFO an đã tham gia MiniChat          (gửi mọi người)
S: MSG 12 1762600000000 ALL * binh R chào cả nhà
S: MSG 40 1762670000000 PM an binh U tối nay họp nhé
C: RECEIVED 40
S: MSG 41 ... GROUP DoAn an R mình gửi file nhé
S: STATUS 41 2 1 2 binh
```

## Trình tự gửi và biên nhận (chat riêng)

```
an  → S: SEND PM binh c7 hello
S → an : ACK c7 42 1762680100000                 (Đã gửi)
S → binh: MSG 42 1762680100000 PM binh an N hello
binh → S: RECEIVED 42
S → an : STATUS 42 1 0 1 -                       (Đã nhận)
binh → S: SEEN PM an 42
S → an : STATUS 42 1 1 1 binh                    (Đã xem)
```
