# Feature Specification: Tài khoản, lịch sử tin nhắn, trạng thái hoạt động và trạng thái tin nhắn

**Feature Branch**: `001-history-auth-presence`

**Created**: 2026-11-09

**Status**: Draft

**Input**: User description: "lưu lại lịch sử tin nhắn có đăng nhập đăng ký hiển trạng thái hoạt động vừa truy cập onl vào bao nhiêu phút trước trạng thái tin nhắn gửi nhận"

## Clarifications

### Session 2026-11-09

- Q: Khi một tài khoản đang đăng nhập mà có lần đăng nhập thứ hai thì sao? → A: Từ chối phiên mới
  ("Tài khoản đang đăng nhập ở nơi khác"), phiên cũ giữ nguyên.
- Q: Có thêm trạng thái "Đã xem" không? → A: Có, cho cả chat riêng và chat nhóm (nhóm hiển thị
  "Đã xem K/N" kèm danh sách người đã xem).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Đăng ký và đăng nhập bằng tài khoản (Priority: P1)

Hiện tại ai gõ tên nào cũng vào được MiniChat, và tên đó bị "mất" khi người dùng thoát. Người dùng
mới cần tạo một tài khoản (tên đăng nhập + mật khẩu) để giữ danh tính lâu dài; những lần sau họ
đăng nhập bằng đúng tên và mật khẩu đó. Không ai khác dùng được tên của họ.

**Why this priority**: Mọi tính năng còn lại (lịch sử, trạng thái hoạt động, tin nhắn chờ khi
offline) đều cần một danh tính cố định. Không có tài khoản thì không biết lịch sử thuộc về ai.

**Independent Test**: Đăng ký tài khoản `an`, thoát, đăng nhập lại bằng đúng mật khẩu (thành công),
bằng sai mật khẩu (bị từ chối), và thử đăng ký lại tên `An` từ máy khác (bị từ chối).

**Acceptance Scenarios**:

1. **Given** tên `an` chưa được đăng ký, **When** người dùng nhập tên `an`, mật khẩu hợp lệ và nhập
   lại mật khẩu trùng khớp rồi chọn Đăng ký, **Then** tài khoản được tạo và người dùng vào thẳng
   màn hình chat với tên `an`.
2. **Given** tài khoản `an` đã tồn tại, **When** một người khác đăng ký tên `an` hoặc `AN`,
   **Then** hệ thống từ chối với thông báo "Tên đăng nhập đã tồn tại".
3. **Given** tài khoản `an` đã tồn tại và đang offline, **When** người dùng đăng nhập bằng `an` và
   đúng mật khẩu, **Then** đăng nhập thành công.
4. **Given** tài khoản `an` đã tồn tại, **When** người dùng đăng nhập bằng sai mật khẩu hoặc tên
   chưa đăng ký, **Then** hệ thống từ chối với cùng một thông báo "Sai tên đăng nhập hoặc mật
   khẩu" (không tiết lộ tên có tồn tại hay không).
5. **Given** người dùng đang ở màn hình đăng ký, **When** mật khẩu ngắn hơn 6 ký tự hoặc hai ô mật
   khẩu không khớp, **Then** hệ thống báo lỗi cụ thể và không tạo tài khoản.
6. **Given** tài khoản `an` đang đăng nhập ở một máy, **When** có người đăng nhập `an` ở máy thứ
   hai, **Then** lần đăng nhập mới bị từ chối với thông báo "Tài khoản đang đăng nhập ở nơi khác";
   phiên ở máy thứ nhất không bị ảnh hưởng. Khi phiên thứ nhất thoát hoặc server phát hiện mất kết
   nối, tài khoản đăng nhập lại được bình thường.
7. **Given** server vừa khởi động lại, **When** người dùng đăng nhập bằng tài khoản đã tạo trước
   đó, **Then** đăng nhập vẫn thành công (tài khoản không bị mất).

---

### User Story 2 - Lịch sử tin nhắn được lưu lại (Priority: P2)

Người dùng thoát MiniChat rồi vào lại (kể cả sau khi server khởi động lại) vẫn thấy các cuộc trò
chuyện cũ: chat chung, các nhóm mình đang tham gia và các cuộc chat riêng, kèm tin nhắn, ảnh và file
đã gửi trước đó, đúng thứ tự thời gian. Tin nhắn riêng gửi tới một người đang offline không bị báo
lỗi nữa mà được giữ lại và hiện ra khi người đó đăng nhập.

**Why this priority**: Đây là nhu cầu chính của người dùng ("lưu lại lịch sử tin nhắn"); hiện mọi
tin nhắn mất ngay khi đóng ứng dụng.

**Independent Test**: `an` và `binh` chat riêng, chat trong nhóm `General` và gửi 1 ảnh. Cả hai thoát,
tắt và bật lại server, rồi đăng nhập lại: mọi tin nhắn và ảnh hiện đủ, đúng thứ tự, đúng người gửi.

**Acceptance Scenarios**:

1. **Given** `an` đã chat với `binh` rồi thoát, **When** `an` đăng nhập lại, **Then** danh sách hội
   thoại có cuộc chat với `binh` cùng toàn bộ tin nhắn cũ, sắp xếp theo tin mới nhất như hiện nay.
2. **Given** `an` là thành viên nhóm `DoAn`, **When** `an` đăng nhập lại, **Then** `an` tự động vẫn
   ở trong nhóm `DoAn` (không phải tham gia lại) và thấy lịch sử của nhóm.
3. **Given** `binh` đang offline, **When** `an` gửi tin nhắn riêng cho `binh`, **Then** tin nhắn được
   chấp nhận (không báo lỗi "không online"), và khi `binh` đăng nhập thì thấy tin nhắn đó, được
   đếm là chưa đọc.
4. **Given** nhóm `DoAn` có thành viên `chi` đang offline, **When** có tin nhắn mới trong nhóm,
   **Then** lần đăng nhập sau `chi` thấy các tin đó, được đếm là chưa đọc.
5. **Given** server đã tắt và bật lại, **When** người dùng đăng nhập, **Then** các nhóm, thành viên
   nhóm và lịch sử trước khi tắt server vẫn còn nguyên.
6. **Given** `an` đã rời nhóm `DoAn`, **When** `an` đăng nhập lại, **Then** nhóm `DoAn` không còn
   trong danh sách hội thoại của `an` và `an` không nhận tin mới của nhóm.
7. **Given** cuộc trò chuyện có ảnh hoặc file đã gửi trước đó, **When** người dùng mở lại cuộc trò
   chuyện sau khi đăng nhập lại, **Then** ảnh vẫn xem được và file vẫn lưu về máy được.

---

### User Story 3 - Trạng thái hoạt động: "Đang hoạt động" / "Truy cập X phút trước" (Priority: P3)

Người dùng nhìn danh bạ và đầu cuộc chat riêng là biết bạn mình đang online hay đã truy cập lần cuối
cách đây bao lâu, để quyết định nhắn ngay hay chờ.

**Why this priority**: Khi đã có tài khoản thì danh bạ gồm cả người đang offline; người dùng cần biết
ai đang online. Tính năng này giá trị nhưng không bắt buộc để chat được.

**Independent Test**: `an` và `binh` cùng online; `an` thấy `binh` "Đang hoạt động". `binh` thoát;
trong vòng vài giây `an` thấy "Vừa truy cập", sau 5 phút thấy "Truy cập 5 phút trước" mà không cần
làm gì.

**Acceptance Scenarios**:

1. **Given** `binh` đang online, **When** `an` xem danh bạ hoặc mở chat riêng với `binh`, **Then**
   avatar của `binh` có chấm xanh và dòng trạng thái ghi "Đang hoạt động".
2. **Given** `binh` vừa thoát (hoặc mất kết nối), **When** `an` đang xem danh bạ, **Then** trong vòng
   5 giây chấm xanh biến mất và trạng thái đổi thành "Vừa truy cập".
3. **Given** `binh` offline đã 17 phút, **When** `an` nhìn trạng thái của `binh`, **Then** thấy
   "Truy cập 17 phút trước"; con số tự tăng theo thời gian mà không cần tải lại.
4. **Given** thời gian offline lần lượt dưới 1 phút, 1–59 phút, 1–23 giờ, từ 1 ngày trở lên,
   **Then** hiển thị lần lượt "Vừa truy cập", "Truy cập N phút trước", "Truy cập N giờ trước",
   "Truy cập N ngày trước".
5. **Given** danh bạ có cả người online và offline, **When** người dùng mở tab Danh bạ, **Then** mọi
   tài khoản đã đăng ký đều được liệt kê, người đang online xếp trước.
6. **Given** server khởi động lại, **When** người dùng xem trạng thái người khác, **Then** thời điểm
   truy cập cuối vẫn đúng như trước khi server tắt.

---

### User Story 4 - Trạng thái tin nhắn: Đang gửi / Đã gửi / Đã nhận / Đã xem (Priority: P4)

Người gửi biết tin nhắn của mình đã tới server chưa ("Đã gửi"), đã tới máy người nhận chưa
("Đã nhận") và người nhận đã đọc chưa ("Đã xem"), cả trong chat riêng lẫn chat nhóm, giống
Zalo/Messenger.

**Why this priority**: Tăng độ tin cậy và trải nghiệm, nhưng dựa trên các story trước (tin nhắn chờ
khi offline chỉ có ý nghĩa khi đã có lịch sử và tài khoản).

**Independent Test**: `binh` offline, `an` gửi tin riêng → thấy "Đã gửi". `binh` đăng nhập → trong
vòng 3 giây trạng thái tin của `an` đổi thành "Đã nhận". `binh` mở cuộc chat với `an` → trong vòng
3 giây đổi thành "Đã xem". Lặp lại trong nhóm 3 người: thấy "Đã xem 1/2" rồi "Đã xem 2/2".

**Acceptance Scenarios**:

1. **Given** `an` bấm gửi, **When** server chưa xác nhận, **Then** tin nhắn hiện ngay trong khung
   chat với trạng thái "Đang gửi".
2. **Given** server đã nhận và lưu tin nhắn, **Then** trạng thái đổi thành "Đã gửi".
3. **Given** tin riêng gửi cho `binh` đang online, **When** ứng dụng của `binh` nhận được tin,
   **Then** trạng thái phía `an` đổi thành "Đã nhận" trong vòng 3 giây.
4. **Given** tin riêng gửi cho `binh` đang offline, **Then** trạng thái giữ "Đã gửi" cho tới khi
   `binh` đăng nhập, rồi đổi thành "Đã nhận"; nếu lúc đó `an` đã offline thì lần đăng nhập sau
   `an` thấy "Đã nhận".
5. **Given** tin nhắn trong nhóm, **Then** trạng thái là "Đã nhận" khi mọi thành viên khác (tính
   tại thời điểm gửi) đã nhận, ngược lại là "Đã gửi".
6. **Given** tin trong chat chung, **Then** chỉ hiển thị tới mức "Đã gửi" (không theo dõi người
   nhận hay người xem vì người nhận là tất cả mọi người).
7. **Given** mất kết nối trước khi server xác nhận, hoặc server từ chối tin (ví dụ file quá 5 MB),
   **Then** tin nhắn hiển thị "Gửi thất bại".
8. **Given** một cuộc trò chuyện có nhiều tin `an` đã gửi, **Then** trạng thái chỉ hiển thị dưới tin
   gần nhất của `an` trong cuộc trò chuyện đó (tránh rối mắt), trừ tin "Gửi thất bại" luôn hiển thị.
9. **Given** `binh` đã nhận tin riêng của `an`, **When** `binh` mở cuộc chat với `an` trong lúc cửa
   sổ MiniChat đang được chọn (cũng là lúc số tin chưa đọc của cuộc chat đó về 0), **Then** trạng
   thái phía `an` đổi thành "Đã xem" trong vòng 3 giây, áp dụng cho mọi tin tới thời điểm đó.
10. **Given** `binh` đang mở sẵn cuộc chat với `an` và cửa sổ đang được chọn, **When** tin mới của
    `an` tới, **Then** tin đó chuyển thẳng sang "Đã xem".
11. **Given** `binh` nhận tin khi cửa sổ MiniChat bị thu nhỏ, không được chọn hoặc đang mở cuộc trò
    chuyện khác, **Then** trạng thái chỉ là "Đã nhận" cho tới khi `binh` thật sự mở cuộc chat đó.
12. **Given** tin nhắn trong nhóm có N thành viên khác (tính tại thời điểm gửi), **When** có K người
    (K ≥ 1) đã xem, **Then** trạng thái hiển thị "Đã xem K/N"; di chuột vào trạng thái hiện danh
    sách tên những người đã xem. Khi chưa ai xem thì áp dụng kịch bản 5 ("Đã nhận" / "Đã gửi").
13. **Given** tin đã ở "Đã xem", **Then** trạng thái không bao giờ lùi về mức thấp hơn (thứ tự:
    Đang gửi → Đã gửi → Đã nhận → Đã xem).
14. **Given** `an` offline lúc `binh` xem tin, **When** `an` đăng nhập lại, **Then** `an` thấy đúng
    trạng thái "Đã xem" (hoặc "Đã xem K/N" trong nhóm).

---

### Edge Cases

- Đăng ký tên chỉ khác hoa/thường với tên đã có (`an` và `An`) → bị từ chối như trùng tên.
- Tên đăng nhập chứa khoảng trắng, quá 20 ký tự hoặc ký tự đặc biệt → bị từ chối theo luật tên hiện
  tại (1–20 ký tự gồm chữ có dấu, số, `_`, `-`, `.`).
- Mật khẩu có khoảng trắng hoặc chữ tiếng Việt có dấu → được chấp nhận và phân biệt hoa/thường.
- Client mất kết nối đột ngột (rút mạng, tắt máy) → thời điểm truy cập cuối là lúc server phát hiện
  mất kết nối; người khác thấy chuyển sang offline.
- Server tắt đột ngột giữa lúc đang có tin nhắn → các tin đã báo "Đã gửi" không được mất; dữ liệu
  cũ không bị hỏng.
- Dữ liệu lưu trữ có một bản ghi bị hỏng → server bỏ qua bản ghi đó, ghi log cảnh báo và vẫn khởi
  động được.
- Gửi tin riêng cho tên không tồn tại → báo lỗi "Người dùng không tồn tại".
- Thêm vào nhóm một người đang offline → được phép; người đó thấy nhóm khi đăng nhập.
- Cuộc trò chuyện rất dài → chỉ tải phần gần nhất (xem Assumptions), ứng dụng không bị treo.
- Nhóm mà mọi thành viên đã rời → nhóm vẫn tồn tại như hiện nay (giữ nguyên hành vi), ai cũng có thể
  tham gia lại.
- Thành viên rời nhóm sau khi tin được gửi mà chưa xem → vẫn được tính trong N của tin đó nhưng
  không bao giờ được tính là đã xem; tin đó có thể dừng ở "Đã xem K/N" với K < N.
- Người nhận xem lại lịch sử cũ trên máy khác (sau khi đăng nhập lại) → không làm thay đổi trạng thái
  đã có (trạng thái không lùi, FR-021).
- Nhóm `General` luôn tồn tại; người dùng mới đăng ký chưa ở trong nhóm nào (giữ hành vi hiện tại).
- Đồng hồ máy client lệch giờ → thời gian tin nhắn và "X phút trước" vẫn đúng vì dựa trên giờ của
  server.

## Requirements *(mandatory)*

### Functional Requirements

**Tài khoản**

- **FR-001**: Hệ thống MUST cho phép đăng ký tài khoản mới với tên đăng nhập (theo luật tên hiện
  tại) và mật khẩu 6–50 ký tự, có ô nhập lại mật khẩu.
- **FR-002**: Hệ thống MUST từ chối đăng ký tên đã tồn tại, không phân biệt hoa/thường.
- **FR-003**: Hệ thống MUST chỉ cho đăng nhập khi tên và mật khẩu khớp; khi sai MUST trả về cùng
  một thông báo chung cho cả hai trường hợp sai tên và sai mật khẩu.
- **FR-004**: Hệ thống MUST không lưu, không hiển thị và không ghi nhật ký mật khẩu ở dạng đọc được;
  ô nhập mật khẩu MUST che ký tự.
- **FR-005**: Tài khoản MUST tồn tại lâu dài, kể cả khi server khởi động lại.
- **FR-006**: Mỗi tài khoản MUST chỉ có tối đa một phiên đăng nhập tại một thời điểm; lần đăng nhập
  thứ hai MUST bị từ chối với thông báo "Tài khoản đang đăng nhập ở nơi khác" và phiên đang có
  không bị ảnh hưởng.
- **FR-007**: Màn hình đăng nhập MUST có lựa chọn chuyển qua lại giữa Đăng nhập và Đăng ký; vẫn giữ
  các ô Server và Cổng như hiện nay.
- **FR-008**: Ứng dụng dòng lệnh MUST cũng đăng ký và đăng nhập được bằng tài khoản.

**Lịch sử tin nhắn**

- **FR-009**: Hệ thống MUST lưu lâu dài mọi tin nhắn văn bản, ảnh và file của chat chung, chat nhóm
  và chat riêng, cùng người gửi, nơi nhận và thời điểm gửi (theo giờ server).
- **FR-010**: Hệ thống MUST lưu lâu dài danh sách nhóm và thành viên; khi đăng nhập, người dùng MUST
  tự động ở lại trong các nhóm đã tham gia.
- **FR-011**: Khi đăng nhập, người dùng MUST nhận lại lịch sử của chat chung, các nhóm đang tham gia
  và các cuộc chat riêng của mình, hiển thị đúng thứ tự thời gian và đúng người gửi.
- **FR-012**: Người dùng MUST chỉ nhận được lịch sử của cuộc trò chuyện mà mình có quyền xem: chat
  riêng chỉ của hai người trong cuộc, nhóm chỉ khi đang là thành viên.
- **FR-013**: Người dùng MUST gửi được tin riêng tới người đang offline, và thêm được người đang
  offline vào nhóm; tin sẽ được giao khi người đó đăng nhập.
- **FR-014**: Tin nhận được trong lúc offline MUST được tính là chưa đọc khi đăng nhập lại, dùng
  chung cơ chế đếm chưa đọc hiện có.
- **FR-015**: Thông báo hệ thống trong nhóm ("X đã tham gia nhóm"...) SHOULD được lưu cùng lịch sử
  nhóm.

**Trạng thái hoạt động**

- **FR-016**: Hệ thống MUST ghi lại thời điểm truy cập cuối của mỗi tài khoản (lúc thoát hoặc mất
  kết nối) và giữ được qua các lần server khởi động lại.
- **FR-017**: Danh bạ MUST liệt kê mọi tài khoản đã đăng ký (trừ chính mình), người online xếp trước,
  mỗi người có chấm xanh nếu online và dòng trạng thái theo FR-018.
- **FR-018**: Dòng trạng thái MUST là "Đang hoạt động" khi online; khi offline lần lượt là "Vừa truy
  cập" (dưới 1 phút), "Truy cập N phút trước" (1–59 phút), "Truy cập N giờ trước" (1–23 giờ),
  "Truy cập N ngày trước" (từ 1 ngày).
- **FR-019**: Đầu khung chat riêng MUST hiển thị trạng thái hoạt động của người kia theo FR-018.
- **FR-020**: Thay đổi online/offline MUST được báo tới các người dùng khác trong vòng 5 giây; dòng
  "N phút trước" MUST tự cập nhật ít nhất mỗi phút.

**Trạng thái tin nhắn**

- **FR-021**: Mỗi tin nhắn người dùng gửi MUST có một trong các trạng thái: Đang gửi, Đã gửi,
  Đã nhận, Đã xem, Gửi thất bại, theo các kịch bản của User Story 4; trạng thái MUST chỉ tiến lên,
  không lùi lại.
- **FR-022**: Trạng thái "Đã nhận" MUST chỉ được đặt khi ứng dụng của người nhận thật sự đã nhận tin
  (cho chat nhóm: mọi thành viên khác tại thời điểm gửi đã nhận).
- **FR-023**: Trạng thái tin nhắn (kể cả ai đã nhận, ai đã xem) MUST được lưu lại, để người gửi đăng
  nhập lại vẫn thấy đúng trạng thái mới nhất.
- **FR-024**: Giao diện MUST chỉ hiển thị trạng thái dưới tin gần nhất người dùng đã gửi trong mỗi
  cuộc trò chuyện, riêng "Gửi thất bại" luôn hiển thị ở tin bị lỗi.
- **FR-025**: Mỗi tin nhắn MUST có định danh duy nhất để trạng thái được gắn đúng tin.
- **FR-026**: Một tin MUST được tính là "đã xem" bởi một người nhận khi người đó mở cuộc trò chuyện
  chứa tin trong lúc cửa sổ ứng dụng đang được chọn (đúng thời điểm số tin chưa đọc của cuộc trò
  chuyện đó được xóa về 0); xem một tin đồng nghĩa với xem mọi tin trước nó trong cuộc trò chuyện.
- **FR-027**: Áp dụng "Đã xem" cho chat riêng và chat nhóm; trong nhóm MUST hiển thị "Đã xem K/N"
  (N = số thành viên khác tại thời điểm gửi) và cho xem danh sách tên người đã xem. Chat chung
  không theo dõi "Đã xem".

**Tương thích**

- **FR-028**: Mọi tính năng hiện có (chat chung, nhóm, chat riêng, ảnh/file 5 MB, emoji, đang soạn
  tin, đếm chưa đọc, thông báo khay hệ thống) MUST tiếp tục hoạt động như trước.

### Key Entities *(include if feature involves data)*

- **Tài khoản (Account)**: một người dùng đã đăng ký. Thuộc tính: tên đăng nhập (duy nhất, không
  phân biệt hoa/thường), thông tin xác thực mật khẩu (không đọc được ra mật khẩu gốc), thời điểm tạo,
  thời điểm truy cập cuối.
- **Trạng thái hoạt động (Presence)**: online hay offline của một tài khoản, cùng thời điểm truy cập
  cuối; suy ra từ việc tài khoản có đang kết nối hay không.
- **Cuộc trò chuyện (Conversation)**: chat chung (duy nhất), một nhóm, hoặc một cặp hai người chat
  riêng.
- **Nhóm (Group)**: tên nhóm (duy nhất), danh sách thành viên là các tài khoản; tồn tại lâu dài.
- **Tin nhắn (Message)**: định danh duy nhất, cuộc trò chuyện, người gửi, thời điểm gửi, loại (văn
  bản, ảnh, file, thông báo hệ thống), nội dung hoặc tệp đính kèm cùng tên tệp.
- **Biên nhận (Receipt)**: ghi nhận một người nhận cụ thể đã nhận, hoặc đã xem, một tin nhắn cụ
  thể, kèm thời điểm; trạng thái "Đã nhận" / "Đã xem (K/N)" của tin được suy ra từ các biên nhận.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Sau khi người dùng thoát và vào lại, hoặc server khởi động lại, 100% tin nhắn, ảnh,
  file, nhóm và thành viên nhóm từ trước vẫn còn, đúng thứ tự và đúng người gửi.
- **SC-002**: Người dùng mới hoàn tất đăng ký và vào màn hình chat trong vòng 1 phút; người dùng cũ
  đăng nhập trong vòng 15 giây.
- **SC-003**: 100% lần đăng nhập sai mật khẩu bị từ chối; không tìm thấy mật khẩu gốc của bất kỳ
  tài khoản nào trong dữ liệu đã lưu hay trong nhật ký của server.
- **SC-004**: Lịch sử hiện đầy đủ trong vòng 2 giây sau khi đăng nhập, khi hệ thống đã lưu 10.000
  tin nhắn.
- **SC-005**: Thay đổi online/offline hiện tới người khác trong vòng 5 giây; dòng "Truy cập N phút
  trước" sai lệch không quá 1 phút so với thực tế.
- **SC-006**: Khi người nhận online, trạng thái "Đã nhận" hiện phía người gửi trong vòng 3 giây;
  không có trường hợp nào "Đã nhận" hiện ra khi người nhận chưa nhận được tin.
- **SC-006a**: "Đã xem" (hoặc "Đã xem K/N") hiện phía người gửi trong vòng 3 giây sau khi người nhận
  mở cuộc trò chuyện; 0 trường hợp "Đã xem" khi người nhận chưa mở cuộc trò chuyện đó.
- **SC-007**: Hệ thống phục vụ 20 người dùng đồng thời trong mạng LAN mà các chỉ số trên vẫn đạt.
- **SC-008**: Người dùng thử nghiệm làm được mọi thao tác cũ (chat chung/nhóm/riêng, gửi ảnh/file)
  mà không cần hướng dẫn thêm ngoài việc đăng ký tài khoản.

## Assumptions

- Ứng dụng dùng trong mạng LAN / lớp học; khóa tài khoản sau nhiều lần đăng nhập sai, quên mật khẩu,
  đổi mật khẩu, xóa tài khoản và mã hóa đường truyền nằm ngoài phạm vi feature này.
- Dữ liệu cũ không cần chuyển đổi: hiện chưa lưu gì, nên hệ thống bắt đầu với dữ liệu trống (chỉ có
  nhóm `General`).
- Khi đăng nhập, mỗi cuộc trò chuyện tải tối đa 100 tin gần nhất; nút "xem tin cũ hơn" nằm ngoài
  phạm vi (toàn bộ lịch sử vẫn được lưu đầy đủ).
- Người mới đăng ký thấy 100 tin gần nhất của chat chung (kể cả tin trước khi họ đăng ký), vì chat
  chung là công khai cho mọi người.
- Lịch sử được lưu vô thời hạn; không có chức năng xóa tin, thu hồi hay sửa tin trong feature này.
- Ảnh và file trong lịch sử giữ giới hạn 5 MB mỗi tệp như hiện nay.
- Mọi thời điểm (giờ gửi tin, truy cập cuối) lấy theo đồng hồ của server.
- Ứng dụng dòng lệnh chỉ cần đăng ký/đăng nhập, nhận lịch sử và xem ai online/offline trong lệnh
  `/users`; không bắt buộc hiển thị trạng thái tin nhắn và không gửi tín hiệu "Đã xem" (tin nhận
  bằng ứng dụng dòng lệnh chỉ dừng ở "Đã nhận").
- Người dùng không thể tắt việc gửi "Đã xem" (cài đặt quyền riêng tư ngoài phạm vi).
- Người dùng không thể ẩn trạng thái hoạt động của mình (cài đặt quyền riêng tư ngoài phạm vi).
