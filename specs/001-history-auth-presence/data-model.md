# Data Model: 001-history-auth-presence

## Thực thể (trong bộ nhớ server)

### Account
| Trường | Kiểu | Ghi chú |
|---|---|---|
| name | String | Tên chuẩn (giữ hoa/thường lúc đăng ký); khóa tra cứu = `name.toLowerCase(Locale.ROOT)` |
| salt | String (Base64) | 16 byte ngẫu nhiên |
| hash | String (Base64) | PBKDF2WithHmacSHA256, 65.536 vòng, 256 bit |
| created | long (ms) | |
| lastSeen | long (ms) | Lúc thoát / mất kết nối gần nhất; lúc đăng ký = created |

Ràng buộc: `Protocol.isValidName(name)`; tên duy nhất không phân biệt hoa/thường; mật khẩu 6–50 ký tự.
Online = có trong `ChatServer.clients` (không lưu xuống đĩa).

### Group
| Trường | Kiểu | Ghi chú |
|---|---|---|
| name | String | Duy nhất không phân biệt hoa/thường (như hiện tại) |
| members | Set<String> | Tên tài khoản; không bị xóa khi thành viên offline |

`General` luôn tồn tại (tạo khi khởi động nếu chưa có).

### StoredMessage
| Trường | Kiểu | Ghi chú |
|---|---|---|
| id | long | Tăng dần, bắt đầu từ 1; sau khi khởi động = max(id) + 1 |
| time | long (ms) | Giờ server |
| kind | `ALL` / `GROUP` / `PM` | Loại cuộc trò chuyện |
| target | String | `*` / tên nhóm / tên người nhận (PM) |
| from | String | Tên người gửi; `*` = thông báo hệ thống |
| recipients | List<String> | Người nhận tại lúc gửi (GROUP: thành viên trừ người gửi; PM: người kia; ALL & hệ thống: rỗng) |
| text | String | Nội dung văn bản / thông báo; null nếu là file |
| fileName | String | Tên file nếu là file/ảnh, ngược lại null |
| delivered | Map<String, Long> | người nhận → lúc nhận |
| seen | Map<String, Long> | người nhận → lúc xem (đã xem ⇒ đã nhận) |

Khóa cuộc trò chuyện (`conversationKey`): `ALL:*`, `GROUP:<nhóm>`, `PM:<a>|<b>` (hai tên chữ thường,
sắp xếp tăng dần). Server giữ `Map<conversationKey, List<StoredMessage>>` theo thứ tự id và
`Map<user, Set<conversationKey PM>>` để tìm nhanh các cuộc chat riêng của một người.

### Trạng thái suy ra (gửi trong `STATUS`)
- `total` = |recipients|, `deliveredCount` = |delivered|, `seenCount` = |seen|, `seenNames`.
- Client hiển thị: `seenCount > 0` → PM "Đã xem", nhóm "Đã xem K/N"; ngược lại
  `deliveredCount == total` → "Đã nhận"; ngược lại "Đã gửi". Không bao giờ lùi vì các map chỉ thêm.

### Trạng thái tin phía client (`ChatMessage.Status`)
`SENDING` → (`ACK`) `SENT` → (`STATUS`) `DELIVERED` / `SEEN`; `SENDING` → (`NACK` / mất kết nối) `FAILED`.

## Định dạng file trong `data/`

Mọi file UTF-8, mỗi dòng một bản ghi, trường cách nhau bằng 1 dấu cách, trường cuối được chứa dấu cách.
`-` nghĩa là danh sách rỗng.

### `data/accounts.log`
```
ACCOUNT <tên> <salt> <hash> <createdMs>
LASTSEEN <tên> <ms>
```
### `data/groups.log`
```
GROUP <nhóm>
JOIN <nhóm> <user>
LEAVE <nhóm> <user>
```
### `data/messages.log`
```
TEXT <id> <ms> <loại> <đích> <từ> <người nhận,...|-> <nội dung>
FILE <id> <ms> <loại> <đích> <từ> <người nhận,...|-> <tên file>
```
Nội dung file của `FILE` nằm ở `data/files/<id>`.
### `data/receipts.log`
```
D <id> <user> <ms>     (đã nhận)
S <id> <user> <ms>     (đã xem, kéo theo đã nhận)
```

Khi khởi động: đọc lần lượt accounts → groups → messages → receipts; dòng không hợp lệ bị bỏ qua và
ghi cảnh báo `Bỏ qua dòng hỏng ở <file>:<số dòng>`.
