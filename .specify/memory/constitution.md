<!--
Sync Impact Report
- Version change: (template) → 1.0.0
- Modified principles: none (initial adoption)
- Added principles:
  I. Thuần Java SE, không phụ thuộc ngoài
  II. Giao thức văn bản có tài liệu và tương thích ngược
  III. Server là nguồn sự thật, client không được tin cậy
  IV. Kiểm thử được bằng kịch bản lặp lại
  V. Đơn giản, dễ đọc, giao diện tiếng Việt
- Added sections: Ràng buộc kỹ thuật & bảo mật; Quy trình phát triển; Governance
- Removed sections: none
- Templates: plan/spec/tasks templates read the constitution at runtime; not modified.
- Follow-up TODOs: none
-->

# MiniChat Constitution

## Core Principles

### I. Thuần Java SE, không phụ thuộc ngoài

- Mã nguồn MUST biên dịch được chỉ bằng JDK 17+ (`javac`) qua `build.bat`, và chạy được bằng
  các file `run-*.bat` cũng như import vào Eclipse mà không cần thư viện bên ngoài.
- Không được thêm jar, Maven/Gradle hay framework. Nếu một tính năng thật sự cần thư viện ngoài,
  plan MUST nêu lý do và được chấp thuận qua sửa đổi constitution trước.
- Mã được tổ chức theo 3 package: `minichat.common` (dùng chung), `minichat.server`,
  `minichat.client` (+ `client.ui`). Code dùng chung giữa client và server MUST nằm ở `common`.

Lý do: đồ án phải chạy được ngay trên máy chấm chỉ có JDK; giảm rủi ro môi trường.

### II. Giao thức văn bản có tài liệu và tương thích ngược

- Mọi thông điệp client ↔ server MUST là một dòng văn bản UTF-8, các trường cách nhau bằng
  dấu cách, trường cuối được phép chứa dấu cách.
- Mọi lệnh mới hoặc thay đổi cú pháp lệnh MUST được khai báo hằng số trong `Protocol.java`,
  mô tả trong Javadoc của `Protocol.java` VÀ bảng giao thức của `README.md` trong cùng một thay đổi.
- Thay đổi giao thức SHOULD là bổ sung (thêm lệnh / thêm trường cuối tùy chọn). Nếu phá vỡ cú pháp
  cũ, MUST cập nhật đồng thời cả `ChatClientGUI` và `ConsoleClient` trong cùng một feature.

Lý do: giao thức là hợp đồng dịch vụ giữa client và server; nó phải đọc được, kiểm tra được bằng
tay (telnet / console client) và luôn khớp với tài liệu nộp kèm.

### III. Server là nguồn sự thật, client không được tin cậy

- Server MUST kiểm tra quyền và tính hợp lệ của mọi lệnh (đăng nhập chưa, có trong nhóm không,
  tên hợp lệ, kích thước dữ liệu) trước khi xử lý; client chỉ kiểm tra để cải thiện trải nghiệm.
- Trạng thái dùng chung (user, nhóm, lịch sử, trạng thái hoạt động, trạng thái tin nhắn) MUST do
  server quyết định và phát xuống client; client không tự suy diễn trạng thái của người khác.
- Lỗi MUST được trả về bằng `ERROR <mô tả tiếng Việt>`; server không được dừng hoặc rơi thread
  chính vì dữ liệu sai từ một client.
- Truy cập trạng thái chia sẻ giữa các thread MUST an toàn (đồng bộ hóa hoặc cấu trúc concurrent).

Lý do: nhiều client chạy đồng thời trên nhiều máy; chỉ server có cái nhìn nhất quán.

### IV. Kiểm thử được bằng kịch bản lặp lại

- Mỗi feature MUST có `quickstart.md` mô tả kịch bản kiểm thử thủ công từng bước (mở server,
  mở N client, thao tác, kết quả mong đợi) bao phủ mọi acceptance scenario trong spec.
- Logic không phụ thuộc giao diện (lưu trữ, băm mật khẩu, phân tích lệnh, định dạng thời gian)
  SHOULD được viết thành lớp tách riêng để có thể kiểm thử bằng một lớp `main` tự kiểm tra
  (không dùng JUnit, theo Nguyên tắc I).
- Một thay đổi chỉ được coi là xong khi `build.bat` biên dịch không lỗi và kịch bản trong
  `quickstart.md` chạy đúng.

Lý do: không có framework kiểm thử, nên kịch bản rõ ràng là cách duy nhất để chứng minh tính đúng.

### V. Đơn giản, dễ đọc, giao diện tiếng Việt

- Ưu tiên giải pháp đơn giản nhất đáp ứng spec (YAGNI); mọi độ phức tạp thêm MUST được ghi lý do
  trong mục Complexity Tracking của plan.
- Toàn bộ chữ hiển thị cho người dùng và log server MUST bằng tiếng Việt có dấu; tên lớp, biến,
  hằng số bằng tiếng Anh; chú thích giải thích bằng tiếng Việt.
- Giao diện mới MUST dùng lại `Theme`, `Icons`, `Avatar` và các thành phần trong `client.ui`
  để giữ phong cách thống nhất.
- Server MUST ghi log các sự kiện quan trọng (đăng nhập, đăng ký, lỗi lưu trữ) kèm giờ.

Lý do: đây là đồ án học tập; code phải giải thích được khi vấn đáp.

## Ràng buộc kỹ thuật & bảo mật

- Lưu trữ phía server MUST dùng file văn bản UTF-8 trong thư mục `data/` cạnh nơi chạy server;
  thư mục được tạo tự động nếu chưa có và MUST nằm trong `.gitignore`.
- Ghi file MUST không làm hỏng dữ liệu cũ khi server tắt đột ngột (ghi nối thêm theo dòng, hoặc
  ghi file tạm rồi đổi tên).
- Mật khẩu MUST KHÔNG được lưu hoặc ghi log ở dạng gốc; MUST băm bằng thuật toán có sẵn trong JDK
  (ví dụ PBKDF2 hoặc SHA-256) kèm salt ngẫu nhiên riêng cho mỗi tài khoản.
- Giới hạn hiện có (file tối đa 5 MB, tên 1-20 ký tự theo `Protocol.isValidName`) MUST được giữ,
  trừ khi spec thay đổi rõ ràng.
- Hiệu năng mục tiêu: phục vụ mượt ít nhất 20 client đồng thời trong mạng LAN; tải lịch sử khi
  đăng nhập không được làm client chờ quá 2 giây với dữ liệu mức đồ án.

## Quy trình phát triển

- Feature đi theo quy trình Spec Kit: `/speckit-specify` → (`/speckit-clarify`) → `/speckit-plan`
  → `/speckit-tasks` → (`/speckit-analyze`) → `/speckit-implement` → `/speckit-converge`.
  Tài liệu nằm trong `specs/<NNN-tên-feature>/`.
- Mỗi bước kết thúc bằng việc người dùng xem lại kết quả trước khi sang bước tiếp theo.
- Cổng chất lượng trước khi coi feature là xong: biên dịch sạch bằng `build.bat`, chạy đúng
  `quickstart.md`, `README.md` và Javadoc `Protocol.java` đã cập nhật.

## Governance

- Constitution này có hiệu lực cao hơn mọi thói quen hay hướng dẫn khác trong project. Mọi plan
  MUST có mục Constitution Check đối chiếu với 5 nguyên tắc trên; vi phạm phải được ghi lý do
  trong Complexity Tracking hoặc sửa lại thiết kế.
- Sửa đổi: chạy `/speckit-constitution` với nội dung thay đổi, cập nhật Sync Impact Report và
  phiên bản theo semantic versioning (MAJOR: bỏ/định nghĩa lại nguyên tắc; MINOR: thêm nguyên tắc
  hoặc mở rộng đáng kể; PATCH: sửa câu chữ).
- `/speckit-analyze` và `/speckit-converge` MUST kiểm tra sự tuân thủ constitution.

**Version**: 1.0.0 | **Ratified**: 2026-11-09 | **Last Amended**: 2026-11-09
