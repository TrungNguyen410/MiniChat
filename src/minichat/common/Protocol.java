package minichat.common;

/**
 * Giao thức trao đổi giữa MiniChat Client và MiniChat Server.
 *
 * Mỗi thông điệp là MỘT DÒNG văn bản (UTF-8), các trường cách nhau bằng
 * khoảng trắng, trường cuối cùng (nội dung tin nhắn, mật khẩu) được phép chứa khoảng trắng.
 * <loại> là ALL (chat chung, đích là *), GROUP (đích là tên nhóm) hoặc PM (đích là tên người nhận).
 * <ts> là thời điểm theo đồng hồ server (số mili giây kể từ 1970).
 *
 * Client -> Server:
 *   REGISTER <tên> <mật khẩu>   tạo tài khoản mới rồi đăng nhập luôn
 *   LOGIN <tên> <mật khẩu>      đăng nhập
 *   CREATE <nhóm> [u1,u2,...]   tạo nhóm mới (người tạo và các user được mời tự động vào nhóm)
 *   ADD <nhóm> <user>           thêm một user (online hoặc offline) vào nhóm
 *   JOIN <nhóm>                 tham gia nhóm có sẵn
 *   LEAVE <nhóm>                rời nhóm
 *   LIST                        yêu cầu gửi lại danh sách user / nhóm
 *   SEND <loại> <đích> <cid> <tin nhắn>            gửi tin, cid là mã tạm do client đặt
 *   SENDFILE <loại> <đích> <cid> <base64> <tên file> gửi file/ảnh
 *   RECEIVED <id1,id2,...>      báo ứng dụng đã nhận các tin nhắn
 *   SEEN <loại> <đích> <id>     đã xem mọi tin tới <id> trong cuộc trò chuyện
 *   ALL <tin nhắn> / GROUP <nhóm> <tin nhắn> / PM <user> <tin nhắn>   gửi tin kiểu cũ (client dòng lệnh)
 *   FILE <loại> <đích> <base64> <tên file>          gửi file kiểu cũ
 *   TYPING <loại> <đích>        báo đang soạn tin
 *   QUIT                        thoát
 *
 * Server -> Client:
 *   LOGIN_OK <tên> <giờ server>
 *   ERROR <mô tả lỗi>
 *   INFO <thông báo chung>
 *   USERS <u1,u2,...>           danh sách user đang online
 *   PRESENCE <u1:ON,u2:<ts>,...> mọi tài khoản: ON nếu online, ngược lại thời điểm truy cập cuối
 *   GROUPS <g1:số TV,g2:số TV>  danh sách nhóm đang tồn tại
 *   MY_GROUPS <g1,g2,...>       các nhóm mà user này đang tham gia
 *   JOINED <nhóm> / LEFT <nhóm> user này vừa vào / rời nhóm
 *   MSG <id> <ts> <loại> <đích> <từ> <cờ> <tin nhắn>          (từ = * : thông báo hệ thống)
 *   MSG_FILE <id> <ts> <loại> <đích> <từ> <cờ> <base64> <tên file>
 *   ACK <cid> <id> <ts>         server đã lưu tin (Đã gửi)
 *   NACK <cid> <lý do>          tin bị từ chối (Gửi thất bại)
 *   STATUS <id> <số đã nhận> <số đã xem> <tổng người nhận> <người xem,...|->
 *   TYPING <loại> <đích> <từ>
 *
 * <cờ>: N = tin mới, U = tin trong lịch sử chưa đọc, R = tin trong lịch sử đã đọc.
 */
public final class Protocol {

    public static final int DEFAULT_PORT = 5000;
    /** Dung lượng tối đa của một file / ảnh gửi qua MiniChat. */
    public static final int MAX_FILE_SIZE = 5 * 1024 * 1024;
    /** Đích của tin nhắn chat chung. */
    public static final String ALL_TARGET = "*";
    /** Người gửi của thông báo hệ thống (không phải tên hợp lệ nên không trùng user). */
    public static final String SYSTEM_SENDER = "*";
    /** Danh sách rỗng trong các trường dạng a,b,c. */
    public static final String EMPTY_LIST = "-";
    /** Giá trị trong PRESENCE cho user đang online. */
    public static final String ONLINE = "ON";
    /** Số tin nhắn gần nhất mỗi cuộc trò chuyện gửi lại khi đăng nhập. */
    public static final int HISTORY_LIMIT = 100;
    public static final int PASSWORD_MIN = 6;
    public static final int PASSWORD_MAX = 50;

    // Cờ của tin nhắn
    public static final String FLAG_NEW = "N";
    public static final String FLAG_UNREAD = "U";
    public static final String FLAG_READ = "R";

    // Client -> Server
    public static final String REGISTER = "REGISTER";
    public static final String LOGIN = "LOGIN";
    public static final String CREATE = "CREATE";
    public static final String ADD = "ADD";
    public static final String SEND = "SEND";
    public static final String SENDFILE = "SENDFILE";
    public static final String RECEIVED = "RECEIVED";
    public static final String SEEN = "SEEN";
    public static final String FILE = "FILE";
    public static final String TYPING = "TYPING";
    public static final String JOIN = "JOIN";
    public static final String LEAVE = "LEAVE";
    public static final String LIST = "LIST";
    public static final String ALL = "ALL";
    public static final String GROUP = "GROUP";
    public static final String PM = "PM";
    public static final String QUIT = "QUIT";

    // Server -> Client
    public static final String LOGIN_OK = "LOGIN_OK";
    public static final String ERROR = "ERROR";
    public static final String INFO = "INFO";
    public static final String USERS = "USERS";
    public static final String PRESENCE = "PRESENCE";
    public static final String GROUPS = "GROUPS";
    public static final String MY_GROUPS = "MY_GROUPS";
    public static final String JOINED = "JOINED";
    public static final String LEFT = "LEFT";
    public static final String MSG = "MSG";
    public static final String MSG_FILE = "MSG_FILE";
    public static final String ACK = "ACK";
    public static final String NACK = "NACK";
    public static final String STATUS = "STATUS";

    private Protocol() {
    }

    /** Tên user / tên nhóm: 1-20 ký tự gồm chữ (có dấu), số, '_', '-', '.'. */
    public static boolean isValidName(String name) {
        return name != null && name.matches("[\\p{L}\\p{N}_.\\-]{1,20}");
    }

    /** Mật khẩu: 6-50 ký tự bất kỳ (kể cả khoảng trắng, chữ có dấu), không xuống dòng. */
    public static boolean isValidPassword(String password) {
        return password != null && password.length() >= PASSWORD_MIN && password.length() <= PASSWORD_MAX
                && password.indexOf('\n') < 0 && password.indexOf('\r') < 0;
    }

    public static boolean isValidKind(String kind) {
        return ALL.equals(kind) || GROUP.equals(kind) || PM.equals(kind);
    }
}
