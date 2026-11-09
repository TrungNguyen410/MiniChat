package minichat.selftest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import minichat.common.Presence;
import minichat.server.ChatServer;
import minichat.server.PasswordHasher;

/**
 * Kiểm thử tự động của MiniChat (không cần thư viện ngoài): chạy bằng run-selftest.bat.
 *
 * Gồm kiểm thử đơn vị (băm mật khẩu, chuỗi trạng thái hoạt động) và một kịch bản tích hợp chạy
 * server thật trên một cổng trống với thư mục dữ liệu tạm, kết nối bằng socket như một client:
 * đăng ký / đăng nhập, tin nhắn tới người offline, biên nhận đã nhận / đã xem, nhóm, trạng thái hoạt
 * động, khởi động lại server và dữ liệu hỏng.
 */
public final class SelfTest {

    private static int passed;
    private static int failed;

    private SelfTest() {
    }

    public static void main(String[] args) throws Exception {
        System.out.println("===== MINICHAT SELFTEST =====");
        testPresence();
        testPasswordHasher();
        Path dir = Files.createTempDirectory("minichat-selftest");
        try {
            testIntegration(dir);
        } catch (AssertionError | Exception e) {
            failed++;
            System.out.println("  [LỖI] kịch bản tích hợp dừng giữa chừng: " + e.getMessage());
        } finally {
            deleteRecursively(dir);
        }
        System.out.println();
        System.out.println(failed == 0 ? "TẤT CẢ ĐỀU ĐẠT (" + passed + " kiểm tra)"
                : "CÓ LỖI: " + failed + " kiểm tra thất bại, " + passed + " đạt");
        System.exit(failed == 0 ? 0 : 1);
    }

    private static void check(boolean condition, String description) {
        if (condition) {
            passed++;
            System.out.println("  [ĐẠT] " + description);
        } else {
            failed++;
            System.out.println("  [LỖI] " + description);
        }
    }

    // ------------------------------------------------------------ đơn vị

    private static void testPresence() {
        System.out.println("Presence:");
        long now = 1_000_000_000_000L;
        check(Presence.describe(now - 30_000, now).equals("Vừa truy cập"), "dưới 1 phút -> Vừa truy cập");
        check(Presence.describe(now - 17 * 60_000, now).equals("Truy cập 17 phút trước"), "17 phút");
        check(Presence.describe(now - 59 * 60_000 - 59_000, now).equals("Truy cập 59 phút trước"), "59 phút 59 giây");
        check(Presence.describe(now - 3 * 3_600_000, now).equals("Truy cập 3 giờ trước"), "3 giờ");
        check(Presence.describe(now - 2 * 86_400_000L, now).equals("Truy cập 2 ngày trước"), "2 ngày");
        check(Presence.describe(true, 0, now).equals("Đang hoạt động"), "online -> Đang hoạt động");
        check(Presence.describe(now + 5_000, now).equals("Vừa truy cập"), "lệch giờ âm vẫn an toàn");
    }

    private static void testPasswordHasher() {
        System.out.println("PasswordHasher:");
        String salt = PasswordHasher.newSalt();
        String hash = PasswordHasher.hash("mật khẩu 1", salt);
        check(PasswordHasher.verify("mật khẩu 1", salt, hash), "đúng mật khẩu (có dấu, có khoảng trắng)");
        check(!PasswordHasher.verify("mật khẩu 2", salt, hash), "sai mật khẩu bị từ chối");
        check(!PasswordHasher.verify("Mật khẩu 1", salt, hash), "phân biệt hoa/thường");
        String salt2 = PasswordHasher.newSalt();
        check(!salt.equals(salt2) && !hash.equals(PasswordHasher.hash("mật khẩu 1", salt2)),
                "salt khác nhau -> hash khác nhau");
        check(!hash.contains("mật khẩu"), "hash không chứa mật khẩu gốc");
    }

    // ---------------------------------------------------------- tích hợp

    private static void testIntegration(Path dir) throws Exception {
        System.out.println("Tích hợp (server thật, thư mục dữ liệu tạm):");
        int port = freePort();
        ChatServer server = new ChatServer(port, dir, false);
        server.startInBackground();

        // --- US1: đăng ký / đăng nhập
        Client an = new Client(port);
        check(an.request("REGISTER an matkhau1", "LOGIN_OK ").startsWith("LOGIN_OK an "), "đăng ký an");
        Client other = new Client(port);
        check(other.request("REGISTER AN abcdef", "ERROR").equals("ERROR Tên đăng nhập đã tồn tại"),
                "đăng ký trùng tên (khác hoa/thường) bị từ chối");
        check(other.request("REGISTER binh 123", "ERROR").contains("Mật khẩu phải từ 6"), "mật khẩu quá ngắn bị từ chối");
        check(other.request("LOGIN an sai12345", "ERROR").equals("ERROR Sai tên đăng nhập hoặc mật khẩu"), "sai mật khẩu");
        check(other.request("LOGIN khongco 123456", "ERROR").equals("ERROR Sai tên đăng nhập hoặc mật khẩu"),
                "tên không tồn tại: cùng thông báo với sai mật khẩu");
        check(other.request("LOGIN an matkhau1", "ERROR").equals("ERROR Tài khoản đang đăng nhập ở nơi khác"),
                "đăng nhập trùng phiên bị từ chối");
        an.send("LIST");
        check(an.expect("USERS ").equals("USERS an"), "phiên đầu tiên không bị ảnh hưởng");
        check(other.request("REGISTER binh abc def ", "LOGIN_OK ").startsWith("LOGIN_OK binh "),
                "đăng ký binh (mật khẩu có khoảng trắng)");
        other.close();
        an.expectMatching(l -> l.startsWith("PRESENCE ") && l.matches(".*binh:\\d+.*"));
        check(true, "binh thoát -> PRESENCE có thời điểm truy cập cuối của binh");

        // --- US2 + US4: tin riêng tới người offline, đã nhận, đã xem
        String ack = an.request("SEND PM binh c1 xin chào bạn", "ACK c1 ");
        long pmId = Long.parseLong(ack.split(" ")[2]);
        check(pmId > 0, "tin riêng tới người offline được chấp nhận (ACK, Đã gửi)");

        Client binh = new Client(port);
        check(binh.request("LOGIN binh abc def ", "LOGIN_OK ").startsWith("LOGIN_OK binh "),
                "binh đăng nhập lại (mật khẩu có khoảng trắng ở cuối)");
        String msg = binh.expect("MSG " + pmId + " ");
        check(msg.endsWith(" PM binh an U xin chào bạn"), "binh nhận tin chờ, đánh dấu chưa đọc (U)");
        binh.send("RECEIVED " + pmId);
        check(an.expect("STATUS " + pmId + " ").equals("STATUS " + pmId + " 1 0 1 -"), "an thấy Đã nhận");
        binh.send("SEEN PM an " + pmId);
        check(an.expect("STATUS " + pmId + " ").equals("STATUS " + pmId + " 1 1 1 binh"), "an thấy Đã xem");
        int mark = an.received.size();
        binh.send("SEEN PM an " + pmId);
        binh.send("RECEIVED " + pmId);
        binh.send("LIST");
        binh.expect("MY_GROUPS"); // binh đã xử lý xong 2 lệnh trên
        an.send("LIST");
        an.expect("MY_GROUPS");
        check(an.received.subList(mark, an.received.size()).stream().noneMatch(l -> l.startsWith("STATUS " + pmId + " ")),
                "báo nhận / xem lại không đổi trạng thái (không lùi, không gửi thừa)");

        // --- nhóm: N tính tại thời điểm gửi, người offline cũng là thành viên
        Client chi = new Client(port);
        chi.request("REGISTER chi 654321", "LOGIN_OK ");
        chi.close();
        an.send("CREATE DoAn binh,chi");
        an.expect("JOINED DoAn");
        binh.expect("JOINED DoAn");
        String groupAck = an.request("SEND GROUP DoAn c2 họp lúc 8 giờ", "ACK c2 ");
        long groupId = Long.parseLong(groupAck.split(" ")[2]);
        binh.expect("MSG " + groupId + " ");
        binh.send("RECEIVED " + groupId);
        check(an.expect("STATUS " + groupId + " ").equals("STATUS " + groupId + " 1 0 2 -"),
                "nhóm: 1/2 đã nhận (chi offline vẫn tính trong tổng)");
        binh.send("SEEN GROUP DoAn " + groupId);
        check(an.expect("STATUS " + groupId + " ").equals("STATUS " + groupId + " 1 1 2 binh"), "nhóm: Đã xem 1/2");

        // --- lệnh kiểu cũ (client dòng lệnh) vẫn chạy, người gửi nhận lại tin
        binh.send("PM an chào an");
        check(binh.expect("MSG ").contains(" PM an binh N chào an"), "lệnh PM kiểu cũ: người gửi nhận lại MSG");
        check(an.expect("MSG ").contains(" PM an binh N chào an"), "lệnh PM kiểu cũ: người nhận nhận MSG");
        check(binh.request("SEND PM khongco c3 alo", "NACK c3").equals("NACK c3 Người dùng khongco không tồn tại"),
                "gửi tới tên không tồn tại -> NACK (Gửi thất bại)");

        binh.close();
        an.close();

        // --- khởi động lại server: không mất gì
        server.stop();
        server = new ChatServer(port, dir, false);
        server.startInBackground();
        Client an2 = new Client(port);
        an2.request("LOGIN an matkhau1", "LOGIN_OK ");
        an2.send("LIST");
        List<String> history = an2.collectUntilSecond("USERS ");
        check(history.stream().anyMatch(l -> l.startsWith("MSG " + pmId + " ") && l.endsWith(" PM binh an R xin chào bạn")),
                "sau khởi động lại: còn tin riêng");
        check(history.contains("STATUS " + pmId + " 1 1 1 binh"), "sau khởi động lại: còn trạng thái Đã xem");
        check(history.contains("STATUS " + groupId + " 1 1 2 binh"), "sau khởi động lại: còn trạng thái nhóm 1/2");
        check(history.stream().anyMatch(l -> l.contains(" GROUP DoAn * R an đã tạo nhóm DoAn")),
                "sau khởi động lại: còn thông báo nhóm");
        check(history.stream().anyMatch(l -> l.startsWith("MY_GROUPS ") && l.contains("DoAn")),
                "sau khởi động lại: an vẫn ở trong nhóm DoAn");
        check(history.stream().anyMatch(l -> l.startsWith("PRESENCE ") && l.matches(".*binh:\\d+.*")),
                "sau khởi động lại: còn thời điểm truy cập cuối");

        Client chi2 = new Client(port);
        chi2.request("LOGIN chi 654321", "LOGIN_OK ");
        check(chi2.expect("MSG " + groupId + " ").contains(" GROUP DoAn an U họp lúc 8 giờ"),
                "chi (offline lúc gửi) nhận tin nhóm, chưa đọc");
        chi2.send("RECEIVED " + groupId);
        check(an2.expect("STATUS " + groupId + " ").equals("STATUS " + groupId + " 2 1 2 binh"), "nhóm: 2/2 đã nhận");
        chi2.send("SEEN GROUP DoAn " + groupId);
        check(an2.expect("STATUS " + groupId + " ").equals("STATUS " + groupId + " 2 2 2 binh,chi"), "nhóm: Đã xem 2/2");
        chi2.close();
        an2.close();
        server.stop();

        String accounts = Files.readString(dir.resolve("accounts.log"), StandardCharsets.UTF_8);
        check(!accounts.contains("matkhau1") && !accounts.contains("abc def") && !accounts.contains("654321"),
                "mật khẩu gốc không có trong dữ liệu đã lưu");

        // --- dữ liệu hỏng: dòng rác + dòng cuối bị ghi dở
        Files.writeString(dir.resolve("messages.log"), "RAC khong hop le\nTEXT 99 12", StandardCharsets.UTF_8,
                StandardOpenOption.APPEND);
        server = new ChatServer(port, dir, false);
        server.startInBackground();
        Client an3 = new Client(port);
        an3.request("LOGIN an matkhau1", "LOGIN_OK ");
        long allId = Long.parseLong(an3.request("SEND ALL * c4 sau khi hỏng dữ liệu", "ACK c4 ").split(" ")[2]);
        an3.close();
        server.stop();
        server = new ChatServer(port, dir, false);
        server.startInBackground();
        Client an4 = new Client(port);
        an4.request("LOGIN an matkhau1", "LOGIN_OK ");
        an4.send("LIST");
        List<String> after = an4.collectUntilSecond("USERS ");
        check(after.stream().anyMatch(l -> l.startsWith("MSG " + allId + " ") && l.endsWith(" sau khi hỏng dữ liệu"))
                && after.stream().anyMatch(l -> l.startsWith("MSG " + pmId + " ")),
                "dòng hỏng bị bỏ qua, server vẫn chạy và không mất dữ liệu khác");
        an4.close();
        server.stop();
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static void deleteRecursively(Path dir) {
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException e) {
            // thư mục tạm, bỏ qua
        }
    }

    /** Client thử nghiệm: gửi lệnh thô và chờ dòng trả về mong đợi. */
    private static final class Client {
        private final Socket socket;
        private final PrintWriter out;
        private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();
        final List<String> received = new ArrayList<>();

        Client(int port) throws IOException {
            socket = new Socket("localhost", port);
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            Thread reader = new Thread(() -> {
                try {
                    String line;
                    while ((line = in.readLine()) != null) {
                        queue.add(line);
                    }
                } catch (IOException e) {
                    // đã đóng
                }
            });
            reader.setDaemon(true);
            reader.start();
        }

        void send(String line) {
            out.println(line);
        }

        String request(String line, String expectedPrefix) throws InterruptedException {
            send(line);
            return expect(expectedPrefix);
        }

        String expect(String prefix) throws InterruptedException {
            return expectMatching(l -> l.startsWith(prefix));
        }

        String expectMatching(java.util.function.Predicate<String> condition) throws InterruptedException {
            long deadline = System.currentTimeMillis() + 5000;
            while (true) {
                long left = deadline - System.currentTimeMillis();
                String line = left > 0 ? queue.poll(left, TimeUnit.MILLISECONDS) : null;
                if (line == null) {
                    throw new AssertionError("hết thời gian chờ; các dòng đã nhận: " + received);
                }
                received.add(line);
                if (condition.test(line)) {
                    return line;
                }
            }
        }

        /** Thu mọi dòng cho tới (và gồm) lần thứ hai gặp dòng bắt đầu bằng prefix. */
        List<String> collectUntilSecond(String prefix) throws InterruptedException {
            int start = received.size();
            expect(prefix);
            expect(prefix);
            return new ArrayList<>(received.subList(start, received.size()));
        }

        void close() throws IOException, InterruptedException {
            send("QUIT");
            socket.close();
            Thread.sleep(150); // để server kịp xử lý việc thoát
        }
    }
}
