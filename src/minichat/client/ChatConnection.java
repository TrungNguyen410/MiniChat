package minichat.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;

import minichat.common.Protocol;

/**
 * Phần kết nối mạng của MiniChat Client (dùng chung cho bản console và bản giao diện).
 *
 * Ngoài việc gửi/nhận thông điệp, lớp này tự báo "đã nhận" (RECEIVED) cho server ngay khi đọc được
 * một tin nhắn nhóm / chat riêng của người khác, nên cả hai phiên bản client đều có trạng thái "Đã nhận".
 */
public class ChatConnection {

    /** Nhận các dòng thông điệp từ server (gọi trên thread đọc socket). */
    public interface Listener {
        void onMessage(String line);

        void onDisconnected();
    }

    /** Số id tối đa trong một lệnh RECEIVED. */
    private static final int RECEIVED_BATCH = 50;

    private final Socket socket;
    private final BufferedReader in;
    private final PrintWriter out;
    private final String name;
    /** Giờ server - giờ máy này (mili giây), để tính "N phút trước" đúng dù đồng hồ máy bị lệch. */
    private final long clockOffset;

    private ChatConnection(Socket socket, BufferedReader in, PrintWriter out, String name, long clockOffset) {
        this.socket = socket;
        this.in = in;
        this.out = out;
        this.name = name;
        this.clockOffset = clockOffset;
    }

    /**
     * Kết nối tới server rồi đăng nhập (register = false) hoặc đăng ký tài khoản mới (register = true).
     * Ném IOException nếu không kết nối được hoặc bị từ chối (thông báo lỗi là lý do của server).
     */
    public static ChatConnection connect(String host, int port, String name, String password, boolean register)
            throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 5000);
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

        out.println((register ? Protocol.REGISTER : Protocol.LOGIN) + " " + name + " " + password);
        String reply = in.readLine();
        if (reply != null && reply.startsWith(Protocol.LOGIN_OK + " ")) {
            // LOGIN_OK <tên> <giờ server>
            String[] a = reply.split(" ");
            String account = a.length > 1 ? a[1] : name;
            long offset = 0;
            if (a.length > 2) {
                try {
                    offset = Long.parseLong(a[2]) - System.currentTimeMillis();
                } catch (NumberFormatException e) {
                    // server cũ: coi như không lệch giờ
                }
            }
            return new ChatConnection(socket, in, out, account, offset);
        }
        socket.close();
        if (reply == null) {
            throw new IOException("Server đã đóng kết nối");
        }
        throw new IOException(reply.startsWith(Protocol.ERROR + " ") ? reply.substring(Protocol.ERROR.length() + 1) : reply);
    }

    /** Bắt đầu thread lắng nghe thông điệp từ server. */
    public void startListening(Listener listener) {
        Thread reader = new Thread(() -> {
            List<String> received = new ArrayList<>();
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    listener.onMessage(line);
                    String id = receivedId(line);
                    if (id != null) {
                        received.add(id);
                    }
                    // gom nhiều id (vd: lúc nhận lịch sử) vào một lệnh RECEIVED
                    if (!received.isEmpty() && (received.size() >= RECEIVED_BATCH || !in.ready())) {
                        send(Protocol.RECEIVED + " " + String.join(",", received));
                        received.clear();
                    }
                }
            } catch (IOException e) {
                // mất kết nối
            } finally {
                listener.onDisconnected();
            }
        }, "minichat-reader");
        reader.setDaemon(true);
        reader.start();
    }

    /**
     * Id của tin cần báo "đã nhận": MSG / MSG_FILE có cờ N hoặc U, thuộc nhóm hoặc chat riêng,
     * do người khác gửi. Trả về null nếu không cần báo.
     */
    private String receivedId(String line) {
        if (!line.startsWith(Protocol.MSG + " ") && !line.startsWith(Protocol.MSG_FILE + " ")) {
            return null;
        }
        // MSG <id> <ts> <loại> <đích> <từ> <cờ> ...
        String[] a = line.split(" ", 8);
        if (a.length < 7) {
            return null;
        }
        boolean needed = (a[6].equals(Protocol.FLAG_NEW) || a[6].equals(Protocol.FLAG_UNREAD))
                && (a[3].equals(Protocol.GROUP) || a[3].equals(Protocol.PM))
                && !a[5].equals(name) && !a[5].equals(Protocol.SYSTEM_SENDER);
        return needed ? a[1] : null;
    }

    public String getName() {
        return name;
    }

    /** Giờ hiện tại theo đồng hồ server (mili giây). */
    public long serverNow() {
        return System.currentTimeMillis() + clockOffset;
    }

    // ---------------------------------------------------- lệnh kiểu cũ (console)

    public void sendAll(String text) {
        send(Protocol.ALL + " " + text);
    }

    public void sendGroup(String group, String text) {
        send(Protocol.GROUP + " " + group + " " + text);
    }

    public void sendPrivate(String user, String text) {
        send(Protocol.PM + " " + user + " " + text);
    }

    // ------------------------------------------------ lệnh mới (có mã tạm cid)

    /** Gửi tin văn bản tới một cuộc trò chuyện (kind: ALL / GROUP / PM); server trả ACK/NACK với cid. */
    public void sendText(String kind, String target, String cid, String text) {
        send(Protocol.SEND + " " + kind + " " + target + " " + cid + " " + text);
    }

    /** Gửi file / ảnh tới một cuộc trò chuyện (kind: ALL / GROUP / PM); server trả ACK/NACK với cid. */
    public void sendFile(String kind, String target, String cid, String fileName, byte[] data) {
        send(Protocol.SENDFILE + " " + kind + " " + target + " " + cid + " "
                + Base64.getEncoder().encodeToString(data) + " " + fileName);
    }

    /** Báo đã xem mọi tin tới {@code upToId} trong cuộc trò chuyện. */
    public void sendSeen(String kind, String target, long upToId) {
        send(Protocol.SEEN + " " + kind + " " + target + " " + upToId);
    }

    public void sendTyping(String kind, String target) {
        send(Protocol.TYPING + " " + kind + " " + target);
    }

    public void createGroup(String group) {
        send(Protocol.CREATE + " " + group);
    }

    public void createGroup(String group, Collection<String> members) {
        send(Protocol.CREATE + " " + group + (members.isEmpty() ? "" : " " + String.join(",", members)));
    }

    public void addMember(String group, String user) {
        send(Protocol.ADD + " " + group + " " + user);
    }

    public void joinGroup(String group) {
        send(Protocol.JOIN + " " + group);
    }

    public void leaveGroup(String group) {
        send(Protocol.LEAVE + " " + group);
    }

    public void requestLists() {
        send(Protocol.LIST);
    }

    public void close() {
        send(Protocol.QUIT);
        try {
            socket.close();
        } catch (IOException e) {
            // bỏ qua
        }
    }

    private synchronized void send(String line) {
        // giao thức theo dòng: không cho phép xuống dòng trong tin nhắn
        out.println(line.replace('\r', ' ').replace('\n', ' '));
    }
}
