package minichat.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import minichat.common.Protocol;

/**
 * Thread xử lý một client: đọc từng dòng lệnh từ socket và gọi ChatServer
 * để thực hiện (đăng ký/đăng nhập, tạo/vào/rời nhóm, gửi tin nhắn, biên nhận).
 */
class ClientHandler implements Runnable {

    private final Socket socket;
    private final ChatServer server;
    private PrintWriter out;
    private volatile String name;

    ClientHandler(Socket socket, ChatServer server) {
        this.socket = socket;
        this.server = server;
    }

    String getName() {
        return name;
    }

    /** Gán tên tài khoản sau khi đăng nhập thành công (do ChatServer gọi). */
    void setName(String name) {
        this.name = name;
    }

    @Override
    public void run() {
        try (Socket s = socket;
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8))) {
            synchronized (this) {
                out = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true);
            }
            String line;
            while ((line = in.readLine()) != null) {
                if (!handle(line)) {
                    break;
                }
            }
        } catch (IOException e) {
            // client ngắt kết nối đột ngột
        } finally {
            if (name != null) {
                server.logout(this);
            } else {
                server.log("Đóng kết nối " + socket.getRemoteSocketAddress());
            }
        }
    }

    /** Xử lý một lệnh; trả về false khi client muốn thoát. */
    private boolean handle(String raw) {
        String line = raw.stripLeading();
        if (line.isBlank()) {
            return true;
        }
        String[] parts = line.split(" ", 2);
        String command = parts[0].toUpperCase();
        // tham số giữ nguyên (không cắt khoảng trắng) để mật khẩu được phép có khoảng trắng ở cuối
        String rawArg = parts.length > 1 ? parts[1] : "";
        String arg = rawArg.trim();

        if (name == null && !command.equals(Protocol.LOGIN) && !command.equals(Protocol.REGISTER)
                && !command.equals(Protocol.QUIT)) {
            send(Protocol.ERROR + " Bạn cần đăng nhập trước");
            return true;
        }

        switch (command) {
            case Protocol.LOGIN:
            case Protocol.REGISTER:
                handleLogin(rawArg, command.equals(Protocol.REGISTER));
                break;
            case Protocol.CREATE: {
                String[] a = arg.split(" ", 2);
                List<String> invited = a.length > 1 ? Arrays.asList(a[1].trim().split(",")) : List.of();
                server.createGroup(this, a[0], invited);
                break;
            }
            case Protocol.ADD: {
                String[] a = arg.split(" ", 2);
                if (a.length < 2) {
                    send(Protocol.ERROR + " Cú pháp: ADD <nhóm> <user>");
                } else {
                    server.addMember(this, a[0], a[1].trim());
                }
                break;
            }
            case Protocol.JOIN:
                server.joinGroup(this, arg);
                break;
            case Protocol.LEAVE:
                server.leaveGroup(this, arg);
                break;
            case Protocol.LIST:
                server.sendLists(this);
                break;
            case Protocol.SEND: {
                // SEND <loại> <đích> <cid> <tin nhắn>
                String[] a = arg.split(" ", 4);
                if (a.length < 4 || a[3].isBlank()) {
                    if (a.length >= 3) {
                        server.reject(this, a[2], "Tin nhắn trống");
                    } else {
                        send(Protocol.ERROR + " Cú pháp: SEND <loại> <đích> <cid> <tin nhắn>");
                    }
                } else {
                    server.post(this, a[0].toUpperCase(), a[1], a[2], a[3].trim(), null, null);
                }
                break;
            }
            case Protocol.SENDFILE: {
                // SENDFILE <loại> <đích> <cid> <base64> <tên file>
                String[] a = arg.split(" ", 5);
                if (a.length < 5) {
                    if (a.length >= 3) {
                        server.reject(this, a[2], "Cú pháp gửi file không hợp lệ");
                    } else {
                        send(Protocol.ERROR + " Cú pháp: SENDFILE <loại> <đích> <cid> <base64> <tên file>");
                    }
                } else {
                    postFile(a[0].toUpperCase(), a[1], a[2], a[3], a[4].trim());
                }
                break;
            }
            case Protocol.ALL:
                if (!arg.isEmpty()) {
                    server.post(this, Protocol.ALL, Protocol.ALL_TARGET, null, arg, null, null);
                }
                break;
            case Protocol.GROUP:
            case Protocol.PM: {
                String[] a = arg.split(" ", 2);
                if (a.length < 2 || a[1].isBlank()) {
                    send(Protocol.ERROR + " Cú pháp: " + command + " <" + (command.equals(Protocol.PM) ? "user" : "nhóm")
                            + "> <tin nhắn>");
                } else {
                    server.post(this, command, a[0], null, a[1].trim(), null, null);
                }
                break;
            }
            case Protocol.FILE: {
                String[] a = arg.split(" ", 4);
                if (a.length < 4) {
                    send(Protocol.ERROR + " Cú pháp: FILE <loại> <đích> <base64> <tên file>");
                } else {
                    postFile(a[0].toUpperCase(), a[1], null, a[2], a[3].trim());
                }
                break;
            }
            case Protocol.RECEIVED: {
                List<Long> ids = new ArrayList<>();
                for (String id : arg.split(",")) {
                    try {
                        ids.add(Long.parseLong(id.trim()));
                    } catch (NumberFormatException e) {
                        // bỏ qua id không hợp lệ
                    }
                }
                server.received(this, ids);
                break;
            }
            case Protocol.SEEN: {
                // SEEN <loại> <đích> <id>
                String[] a = arg.split(" ");
                if (a.length == 3) {
                    try {
                        server.seen(this, a[0].toUpperCase(), a[1], Long.parseLong(a[2]));
                    } catch (NumberFormatException e) {
                        send(Protocol.ERROR + " Cú pháp: SEEN <loại> <đích> <id>");
                    }
                }
                break;
            }
            case Protocol.TYPING: {
                String[] a = arg.split(" ", 2);
                server.typing(this, a[0].toUpperCase(), a.length > 1 ? a[1].trim() : Protocol.ALL_TARGET);
                break;
            }
            case Protocol.QUIT:
                return false;
            default:
                send(Protocol.ERROR + " Lệnh không hợp lệ: " + command);
        }
        return true;
    }

    private void postFile(String kind, String target, String cid, String base64, String fileName) {
        if (fileName.isEmpty()) {
            server.reject(this, cid, "Thiếu tên file");
            return;
        }
        if (base64.length() / 4L * 3 > Protocol.MAX_FILE_SIZE + 3L) {
            server.reject(this, cid, "File quá lớn (tối đa " + Protocol.MAX_FILE_SIZE / (1024 * 1024) + " MB)");
            return;
        }
        byte[] data;
        try {
            data = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            server.reject(this, cid, "Dữ liệu file không hợp lệ");
            return;
        }
        if (data.length > Protocol.MAX_FILE_SIZE) {
            server.reject(this, cid, "File quá lớn (tối đa " + Protocol.MAX_FILE_SIZE / (1024 * 1024) + " MB)");
            return;
        }
        server.post(this, kind, target, cid, null, fileName, data);
    }

    private void handleLogin(String arg, boolean register) {
        if (name != null) {
            send(Protocol.ERROR + " Bạn đã đăng nhập với tên " + name);
            return;
        }
        String[] a = arg.stripLeading().split(" ", 2);
        if (a.length < 2 || a[1].isEmpty()) {
            send(Protocol.ERROR + " Cú pháp: " + (register ? Protocol.REGISTER : Protocol.LOGIN) + " <tên> <mật khẩu>");
            return;
        }
        server.authenticate(this, a[0], a[1], register);
    }

    synchronized void send(String message) {
        if (out != null) {
            out.println(message);
        }
    }

    void close() {
        try {
            socket.close();
        } catch (IOException e) {
            // bỏ qua
        }
    }
}
