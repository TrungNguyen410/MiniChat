package minichat.client;

import java.io.BufferedReader;
import java.io.Console;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import minichat.common.Presence;
import minichat.common.Protocol;

/**
 * MiniChat Client chạy trên cửa sổ dòng lệnh (cmd).
 *
 * Đăng nhập / đăng ký bằng tài khoản, sau đó gõ tin nhắn bình thường để chat chung,
 * hoặc dùng các lệnh bắt đầu bằng '/' (gõ /help để xem).
 */
public class ConsoleClient implements ChatConnection.Listener {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private final ChatConnection connection;
    private final PrintStream out;
    private volatile String presence = "";
    private volatile String groups = "";
    private volatile String myGroups = "";

    private ConsoleClient(ChatConnection connection, PrintStream out) {
        this.connection = connection;
        this.out = out;
    }

    public static void main(String[] args) throws IOException {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : Protocol.DEFAULT_PORT;

        Console console = System.console();
        Charset charset = console != null ? console.charset() : Charset.defaultCharset();
        PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), true, charset);
        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in, charset));

        out.println("===== MINICHAT CLIENT =====");
        ChatConnection connection = null;
        while (connection == null) {
            out.print("Chọn 1 = Đăng nhập, 2 = Đăng ký: ");
            String choice = stdin.readLine();
            if (choice == null) {
                return;
            }
            boolean register = choice.trim().equals("2");
            out.print("Tên đăng nhập: ");
            String name = stdin.readLine();
            if (name == null) {
                return;
            }
            name = name.replace("﻿", "").trim();
            String password = readPassword(console, stdin, out, "Mật khẩu: ");
            if (password == null) {
                return;
            }
            if (register) {
                if (!Protocol.isValidPassword(password)) {
                    out.println("Mật khẩu phải từ " + Protocol.PASSWORD_MIN + " đến " + Protocol.PASSWORD_MAX + " ký tự.");
                    continue;
                }
                String confirm = readPassword(console, stdin, out, "Nhập lại mật khẩu: ");
                if (confirm == null) {
                    return;
                }
                if (!password.equals(confirm)) {
                    out.println("Mật khẩu nhập lại không khớp.");
                    continue;
                }
            }
            try {
                connection = ChatConnection.connect(host, port, name, password, register);
            } catch (ConnectException | UnknownHostException e) {
                out.println("Không kết nối được tới server " + host + ":" + port + " (" + e.getMessage() + ")");
                return;
            } catch (IOException e) {
                out.println((register ? "Đăng ký" : "Đăng nhập") + " thất bại: " + e.getMessage());
            }
        }

        ConsoleClient client = new ConsoleClient(connection, out);
        out.println("Đăng nhập thành công với tên " + connection.getName() + ". Gõ /help để xem các lệnh.");
        connection.startListening(client);
        client.readCommands(stdin);
    }

    /** Đọc mật khẩu không hiện ký tự khi chạy trong cmd; nếu không có console thì đọc bình thường. */
    private static String readPassword(Console console, BufferedReader stdin, PrintStream out, String prompt)
            throws IOException {
        if (console != null) {
            char[] password = console.readPassword(prompt);
            return password == null ? null : new String(password);
        }
        out.print(prompt);
        return stdin.readLine();
    }

    private void readCommands(BufferedReader stdin) throws IOException {
        String line;
        while ((line = stdin.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (!line.startsWith("/")) {
                connection.sendAll(line);
                continue;
            }
            String[] parts = line.split(" ", 3);
            String command = parts[0].toLowerCase();
            String arg1 = parts.length > 1 ? parts[1] : "";
            String rest = parts.length > 2 ? parts[2] : "";
            switch (command) {
                case "/help":
                    printHelp();
                    break;
                case "/users":
                    printUsers();
                    break;
                case "/groups":
                    print("Các nhóm (tên:số thành viên): " + groups.replace(",", ", "));
                    print("Nhóm bạn đang tham gia: " + (myGroups.isEmpty() ? "(chưa có)" : myGroups.replace(",", ", ")));
                    break;
                case "/create":
                    connection.createGroup(arg1);
                    break;
                case "/join":
                    connection.joinGroup(arg1);
                    break;
                case "/leave":
                    connection.leaveGroup(arg1);
                    break;
                case "/g":
                    if (rest.isEmpty()) {
                        print("Cú pháp: /g <nhóm> <tin nhắn>");
                    } else {
                        connection.sendGroup(arg1, rest);
                    }
                    break;
                case "/pm":
                    if (rest.isEmpty()) {
                        print("Cú pháp: /pm <user> <tin nhắn>");
                    } else {
                        connection.sendPrivate(arg1, rest);
                    }
                    break;
                case "/quit":
                    connection.close();
                    return;
                default:
                    print("Lệnh không hợp lệ, gõ /help để xem các lệnh.");
            }
        }
        connection.close();
    }

    private void printHelp() {
        print("Các lệnh:");
        print("  <tin nhắn>              chat chung với tất cả mọi người");
        print("  /g <nhóm> <tin nhắn>    chat trong nhóm");
        print("  /pm <user> <tin nhắn>   chat riêng với một user (kể cả khi người đó offline)");
        print("  /create <nhóm>          tạo nhóm mới");
        print("  /join <nhóm>            tham gia nhóm");
        print("  /leave <nhóm>           rời nhóm");
        print("  /users                  xem mọi tài khoản và trạng thái hoạt động");
        print("  /groups                 xem danh sách nhóm");
        print("  /quit                   thoát");
    }

    /** In mọi tài khoản: "Đang hoạt động" hoặc "Truy cập N phút trước". */
    private void printUsers() {
        print("Tài khoản MiniChat:");
        long now = connection.serverNow();
        for (String item : presence.isBlank() ? new String[0] : presence.split(",")) {
            int colon = item.lastIndexOf(':');
            if (colon <= 0) {
                continue;
            }
            String user = item.substring(0, colon);
            String state = item.substring(colon + 1);
            String text;
            if (state.equals(Protocol.ONLINE)) {
                text = Presence.ONLINE_TEXT;
            } else {
                try {
                    text = Presence.describe(Long.parseLong(state), now);
                } catch (NumberFormatException e) {
                    text = "Không trực tuyến";
                }
            }
            print("  " + user + (user.equals(connection.getName()) ? " (bạn)" : "") + " - " + text);
        }
    }

    @Override
    public void onMessage(String line) {
        String[] p = line.split(" ", 2);
        String type = p[0];
        String body = p.length > 1 ? p[1] : "";
        switch (type) {
            case Protocol.MSG: {
                // MSG <id> <ts> <loại> <đích> <từ> <cờ> <tin nhắn>
                String[] a = body.split(" ", 7);
                if (a.length == 7) {
                    print(prefix(a) + (a[4].equals(Protocol.SYSTEM_SENDER) ? "* " : sender(a)) + a[6]);
                }
                break;
            }
            case Protocol.MSG_FILE: {
                // MSG_FILE <id> <ts> <loại> <đích> <từ> <cờ> <base64> <tên file>
                String[] a = body.split(" ", 8);
                if (a.length == 8) {
                    print(prefix(a) + sender(a) + "[file] " + a[7] + " (" + a[6].length() / 4 * 3 / 1024
                            + " KB, mở bằng bản giao diện để xem)");
                }
                break;
            }
            case Protocol.TYPING:
            case Protocol.STATUS:
            case Protocol.ACK:
                break;
            case Protocol.NACK: {
                String[] a = body.split(" ", 2);
                print("! Không gửi được tin nhắn: " + (a.length > 1 ? a[1] : ""));
                break;
            }
            case Protocol.INFO:
                print("* " + body);
                break;
            case Protocol.ERROR:
                print("! Lỗi: " + body);
                break;
            case Protocol.JOINED:
                print("* Bạn đã vào nhóm " + body + ". Chat nhóm bằng: /g " + body + " <tin nhắn>");
                break;
            case Protocol.LEFT:
                print("* Bạn đã rời nhóm " + body);
                break;
            case Protocol.PRESENCE:
                presence = body;
                break;
            case Protocol.USERS:
                break;
            case Protocol.GROUPS:
                groups = body;
                break;
            case Protocol.MY_GROUPS:
                myGroups = body;
                break;
            default:
                print(line);
        }
    }

    /** "(lịch sử 09/11 14:05) [Nhóm X] " hoặc "[Riêng từ Y] ". */
    private String prefix(String[] a) {
        String history = "";
        if (!a[5].equals(Protocol.FLAG_NEW)) {
            String time;
            try {
                time = Instant.ofEpochMilli(Long.parseLong(a[1])).atZone(ZoneId.systemDefault()).format(TIME);
            } catch (NumberFormatException e) {
                time = "?";
            }
            history = "(" + (a[5].equals(Protocol.FLAG_UNREAD) ? "mới " : "") + time + ") ";
        }
        String scope;
        if (a[2].equals(Protocol.GROUP)) {
            scope = "[Nhóm " + a[3] + "] ";
        } else if (a[2].equals(Protocol.PM)) {
            scope = a[4].equals(connection.getName()) ? "[Riêng -> " + a[3] + "] " : "[Riêng từ " + a[4] + "] ";
        } else {
            scope = "[Chung] ";
        }
        return history + scope;
    }

    private String sender(String[] a) {
        return a[2].equals(Protocol.PM) ? "" : a[4] + ": ";
    }

    @Override
    public void onDisconnected() {
        print("*** Đã ngắt kết nối với server ***");
        System.exit(0);
    }

    private synchronized void print(String text) {
        out.println(text);
    }
}
