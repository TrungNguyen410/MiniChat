package minichat.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import minichat.common.Protocol;
import minichat.server.ChatStore.Account;

/**
 * MiniChat Server.
 * - Luôn sẵn sàng chờ và chấp nhận kết nối từ client (mỗi client một thread).
 * - Quản lý tài khoản (đăng ký / đăng nhập bằng mật khẩu), danh sách user đang online và các nhóm.
 * - Lưu lâu dài tài khoản, nhóm, tin nhắn và biên nhận (thư mục data/), gửi lại lịch sử khi đăng nhập.
 * - Phân phối tin nhắn, file: chat chung, chat nhóm, chat riêng (kể cả tới người đang offline).
 * - Theo dõi trạng thái tin nhắn: đã gửi / đã nhận / đã xem, và thời điểm truy cập cuối của từng user.
 *
 * Mọi thay đổi trạng thái dùng chung được thực hiện trong khối synchronized của server.
 */
public class ChatServer {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    public static final String DEFAULT_GROUP = "General";

    private final int port;
    private final boolean verbose;
    private final ChatStore store;
    /** User đang online: tên tài khoản -> handler giữ socket của user đó. */
    private final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private volatile ServerSocket serverSocket;

    public ChatServer(int port, Path dataDir, boolean verbose) throws IOException {
        this.port = port;
        this.verbose = verbose;
        this.store = ChatStore.open(dataDir, this::log);
        store.createGroup(DEFAULT_GROUP); // một nhóm có sẵn để user tham gia
    }

    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : Protocol.DEFAULT_PORT;
        Path dataDir = Paths.get(args.length > 1 ? args[1] : "data");
        try {
            ChatServer server = new ChatServer(port, dataDir, true);
            server.log("Thư mục dữ liệu: " + dataDir.toAbsolutePath());
            server.bind();
            server.serve();
        } catch (IOException e) {
            System.err.println("Không khởi động được server: " + e.getMessage());
        }
    }

    /** Mở cổng lắng nghe. */
    public void bind() throws IOException {
        serverSocket = new ServerSocket(port);
        log("MiniChat server đang chạy tại cổng " + port + ", chờ client kết nối...");
    }

    /** Vòng lặp chính: chờ client kết nối và tạo thread xử lý cho từng client. */
    public void serve() {
        try {
            while (true) {
                Socket socket = serverSocket.accept();
                log("Kết nối mới từ " + socket.getRemoteSocketAddress());
                Thread thread = new Thread(new ClientHandler(socket, this), "minichat-client");
                thread.setDaemon(true);
                thread.start();
            }
        } catch (IOException e) {
            if (!serverSocket.isClosed()) {
                log("Lỗi server: " + e.getMessage());
            }
        }
    }

    /** Mở cổng rồi chạy vòng lặp chấp nhận kết nối trên một thread nền (dùng cho kiểm thử). */
    public void startInBackground() throws IOException {
        bind();
        Thread thread = new Thread(this::serve, "minichat-server");
        thread.setDaemon(true);
        thread.start();
    }

    /** Dừng server: ghi thời điểm truy cập của mọi user online, đóng mọi kết nối và file dữ liệu. */
    public void stop() throws IOException {
        if (serverSocket != null) {
            serverSocket.close();
        }
        for (ClientHandler handler : new ArrayList<>(clients.values())) {
            logout(handler);
            handler.close();
        }
        store.close();
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    // ============================================================ tài khoản

    /**
     * Đăng nhập (register = false) hoặc đăng ký rồi đăng nhập (register = true).
     * Việc băm mật khẩu tốn thời gian nên được làm ngoài khóa của server.
     */
    void authenticate(ClientHandler handler, String name, String password, boolean register) {
        if (!Protocol.isValidName(name)) {
            handler.send(Protocol.ERROR + " Tên không hợp lệ (1-20 ký tự, không chứa khoảng trắng)");
            return;
        }
        Account account;
        if (register) {
            if (!Protocol.isValidPassword(password)) {
                handler.send(Protocol.ERROR + " Mật khẩu phải từ " + Protocol.PASSWORD_MIN + " đến "
                        + Protocol.PASSWORD_MAX + " ký tự");
                return;
            }
            if (store.findAccount(name) != null) {
                handler.send(Protocol.ERROR + " Tên đăng nhập đã tồn tại");
                return;
            }
            String salt = PasswordHasher.newSalt();
            String hash = PasswordHasher.hash(password, salt);
            try {
                account = store.createAccount(name, salt, hash, now());
            } catch (IOException e) {
                log("Lỗi lưu trữ khi đăng ký " + name + ": " + e.getMessage());
                handler.send(Protocol.ERROR + " Lỗi lưu trữ trên server, hãy thử lại sau");
                return;
            }
            if (account == null) {
                handler.send(Protocol.ERROR + " Tên đăng nhập đã tồn tại");
                return;
            }
            log("Đăng ký tài khoản mới: " + account.name);
        } else {
            account = store.findAccount(name);
            boolean ok;
            if (account == null) {
                PasswordHasher.dummyVerify(password == null ? "" : password);
                ok = false;
            } else {
                ok = password != null && PasswordHasher.verify(password, account.salt, account.hash);
            }
            if (!ok) {
                log("Đăng nhập thất bại: " + name);
                handler.send(Protocol.ERROR + " Sai tên đăng nhập hoặc mật khẩu");
                return;
            }
        }
        enter(handler, account);
    }

    /** User đã xác thực vào MiniChat: gửi LOGIN_OK, danh sách và lịch sử. */
    private synchronized void enter(ClientHandler handler, Account account) {
        if (clients.containsKey(account.name)) {
            handler.send(Protocol.ERROR + " Tài khoản đang đăng nhập ở nơi khác");
            log("Từ chối đăng nhập trùng: " + account.name);
            return;
        }
        long previousSeen = account.lastSeen;
        handler.setName(account.name);
        clients.put(account.name, handler);
        handler.send(Protocol.LOGIN_OK + " " + account.name + " " + now());
        log(account.name + " đã đăng nhập (" + clients.size() + " user online)");
        broadcast(Protocol.INFO + " " + account.name + " đã tham gia MiniChat");
        broadcastLists();
        sendLoginHistory(handler, previousSeen);
    }

    synchronized void logout(ClientHandler handler) {
        String name = handler.getName();
        if (name == null || !clients.remove(name, handler)) {
            return;
        }
        Account account = store.findAccount(name);
        if (account != null) {
            store.setLastSeen(account, now());
        }
        log(name + " đã thoát (" + clients.size() + " user online)");
        broadcast(Protocol.INFO + " " + name + " đã rời MiniChat");
        broadcastLists();
    }

    // ================================================================= nhóm

    synchronized void createGroup(ClientHandler handler, String group, List<String> invited) {
        if (!Protocol.isValidName(group)) {
            handler.send(Protocol.ERROR + " Tên nhóm không hợp lệ (1-20 ký tự, không chứa khoảng trắng)");
            return;
        }
        for (String existing : store.groups().keySet()) {
            if (existing.equalsIgnoreCase(group)) {
                handler.send(Protocol.ERROR + " Nhóm " + existing + " đã tồn tại");
                return;
            }
        }
        String creator = handler.getName();
        List<String> added = new ArrayList<>();
        try {
            store.createGroup(group);
            store.addMember(group, creator);
            for (String user : invited) {
                Account account = store.findAccount(user.trim());
                if (account != null && store.addMember(group, account.name)) {
                    added.add(account.name);
                }
            }
        } catch (IOException e) {
            storageError(handler, e);
            return;
        }
        log(creator + " tạo nhóm " + group + (added.isEmpty() ? "" : " cùng " + String.join(", ", added)));
        for (String member : store.members(group)) {
            ClientHandler client = clients.get(member);
            if (client != null) {
                client.send(Protocol.JOINED + " " + group);
            }
        }
        postSystem(group, creator + " đã tạo nhóm " + group);
        if (!added.isEmpty()) {
            postSystem(group, creator + " đã thêm " + String.join(", ", added) + " vào nhóm");
        }
        broadcastLists();
    }

    synchronized void addMember(ClientHandler handler, String group, String user) {
        Set<String> members = store.members(group);
        if (members == null || !members.contains(handler.getName())) {
            handler.send(Protocol.ERROR + " Bạn không ở trong nhóm " + group);
            return;
        }
        Account account = store.findAccount(user);
        if (account == null) {
            handler.send(Protocol.ERROR + " Người dùng " + user + " không tồn tại");
            return;
        }
        try {
            if (!store.addMember(group, account.name)) {
                handler.send(Protocol.ERROR + " " + account.name + " đã ở trong nhóm " + group);
                return;
            }
        } catch (IOException e) {
            storageError(handler, e);
            return;
        }
        log(handler.getName() + " thêm " + account.name + " vào nhóm " + group);
        ClientHandler target = clients.get(account.name);
        if (target != null) {
            target.send(Protocol.JOINED + " " + group);
            sendConversationHistory(target, StoredMessage.conversationKey(Protocol.GROUP, group, null), 0);
        }
        postSystem(group, handler.getName() + " đã thêm " + account.name + " vào nhóm");
        broadcastLists();
    }

    synchronized void joinGroup(ClientHandler handler, String group) {
        Set<String> members = store.members(group);
        if (members == null) {
            handler.send(Protocol.ERROR + " Nhóm " + group + " không tồn tại");
            return;
        }
        try {
            if (!store.addMember(group, handler.getName())) {
                handler.send(Protocol.ERROR + " Bạn đã ở trong nhóm " + group);
                return;
            }
        } catch (IOException e) {
            storageError(handler, e);
            return;
        }
        log(handler.getName() + " tham gia nhóm " + group);
        handler.send(Protocol.JOINED + " " + group);
        sendConversationHistory(handler, StoredMessage.conversationKey(Protocol.GROUP, group, null), 0);
        postSystem(group, handler.getName() + " đã tham gia nhóm");
        broadcastLists();
    }

    synchronized void leaveGroup(ClientHandler handler, String group) {
        try {
            if (store.members(group) == null || !store.removeMember(group, handler.getName())) {
                handler.send(Protocol.ERROR + " Bạn không ở trong nhóm " + group);
                return;
            }
        } catch (IOException e) {
            storageError(handler, e);
            return;
        }
        log(handler.getName() + " rời nhóm " + group);
        handler.send(Protocol.LEFT + " " + group);
        postSystem(group, handler.getName() + " đã rời nhóm");
        broadcastLists();
    }

    /** Lưu và gửi một thông báo hệ thống trong nhóm (vd: "X đã tham gia nhóm"). Gọi khi đang giữ khóa. */
    private void postSystem(String group, String text) {
        try {
            StoredMessage m = store.addMessage(Protocol.GROUP, group, Protocol.SYSTEM_SENDER, List.of(), text,
                    null, null, now());
            sendToGroup(group, textLine(m, Protocol.FLAG_NEW));
        } catch (IOException e) {
            log("Lỗi lưu trữ thông báo nhóm " + group + ": " + e.getMessage());
        }
    }

    private void storageError(ClientHandler handler, IOException e) {
        log("Lỗi lưu trữ: " + e.getMessage());
        handler.send(Protocol.ERROR + " Lỗi lưu trữ trên server, hãy thử lại sau");
    }

    // ================================================================= chat

    /**
     * Gửi một tin văn bản (fileName == null) hoặc file / ảnh.
     * cid != null: client mới, server trả ACK/NACK cho người gửi thay vì gửi lại chính tin đó.
     * cid == null: lệnh kiểu cũ (client dòng lệnh), người gửi nhận lại tin như mọi người nhận khác.
     */
    void post(ClientHandler sender, String kind, String target, String cid, String text, String fileName,
            byte[] data) {
        String me = sender.getName();
        List<ClientHandler> deliverTo = new ArrayList<>();
        StoredMessage m;
        synchronized (this) {
            String to;
            List<String> recipients = new ArrayList<>();
            if (Protocol.ALL.equals(kind)) {
                to = Protocol.ALL_TARGET;
                deliverTo.addAll(clients.values());
            } else if (Protocol.GROUP.equals(kind)) {
                Set<String> members = store.members(target);
                if (members == null) {
                    reject(sender, cid, "Nhóm " + target + " không tồn tại");
                    return;
                }
                if (!members.contains(me)) {
                    reject(sender, cid, "Bạn phải tham gia nhóm " + target + " trước khi chat");
                    return;
                }
                to = target;
                for (String member : members) {
                    if (!member.equals(me)) {
                        recipients.add(member);
                    }
                    ClientHandler client = clients.get(member);
                    if (client != null) {
                        deliverTo.add(client);
                    }
                }
            } else if (Protocol.PM.equals(kind)) {
                Account account = store.findAccount(target);
                if (account == null) {
                    reject(sender, cid, "Người dùng " + target + " không tồn tại");
                    return;
                }
                if (account.name.equals(me)) {
                    reject(sender, cid, "Không thể chat riêng với chính mình");
                    return;
                }
                to = account.name;
                recipients.add(to);
                deliverTo.add(sender);
                ClientHandler client = clients.get(to);
                if (client != null) {
                    deliverTo.add(client);
                }
            } else {
                reject(sender, cid, "Loại cuộc trò chuyện không hợp lệ: " + kind);
                return;
            }
            if (cid != null) {
                deliverTo.remove(sender);
            }
            try {
                m = store.addMessage(kind, to, me, recipients, text, fileName, data, now());
            } catch (IOException e) {
                log("Lỗi lưu trữ tin nhắn: " + e.getMessage());
                reject(sender, cid, "Lỗi lưu trữ trên server, tin nhắn chưa được gửi");
                return;
            }
            String scope = kind.equals(Protocol.ALL) ? "[Chung]"
                    : kind.equals(Protocol.GROUP) ? "[Nhóm " + to + "]" : "[Riêng -> " + to + "]";
            log(scope + " " + me + ": " + (fileName == null ? text
                    : "gửi file " + fileName + " (" + data.length / 1024 + " KB)"));
        }
        // ACK trước, để người gửi luôn biết id của tin trước khi nhận STATUS của nó
        if (cid != null) {
            sender.send(Protocol.ACK + " " + cid + " " + m.id + " " + m.time);
        }
        String line = data == null ? textLine(m, Protocol.FLAG_NEW) : fileLine(m, Protocol.FLAG_NEW, data);
        for (ClientHandler client : deliverTo) {
            client.send(line);
        }
    }

    /** Báo lỗi gửi tin: NACK với client mới (có cid), ERROR với lệnh kiểu cũ. */
    void reject(ClientHandler sender, String cid, String reason) {
        sender.send(cid != null ? Protocol.NACK + " " + cid + " " + reason : Protocol.ERROR + " " + reason);
    }

    /** Báo cho những người cùng cuộc trò chuyện (đang online) biết user đang soạn tin. */
    synchronized void typing(ClientHandler sender, String kind, String target) {
        String me = sender.getName();
        List<ClientHandler> recipients = new ArrayList<>();
        String to = target;
        if (Protocol.ALL.equals(kind)) {
            to = Protocol.ALL_TARGET;
            recipients.addAll(clients.values());
        } else if (Protocol.GROUP.equals(kind)) {
            Set<String> members = store.members(target);
            if (members == null || !members.contains(me)) {
                return;
            }
            for (String member : members) {
                ClientHandler client = clients.get(member);
                if (client != null) {
                    recipients.add(client);
                }
            }
        } else if (Protocol.PM.equals(kind)) {
            Account account = store.findAccount(target);
            ClientHandler client = account == null ? null : clients.get(account.name);
            if (client == null) {
                return;
            }
            to = account.name;
            recipients.add(client);
        } else {
            return;
        }
        String message = Protocol.TYPING + " " + kind + " " + to + " " + me;
        for (ClientHandler client : recipients) {
            if (client != sender) {
                client.send(message);
            }
        }
    }

    // ============================================================ biên nhận

    /** Ứng dụng của user đã nhận các tin này. */
    synchronized void received(ClientHandler handler, List<Long> ids) {
        String me = handler.getName();
        long time = now();
        for (long id : ids) {
            StoredMessage m = store.message(id);
            if (m != null && store.markDelivered(m, me, time)) {
                notifySender(m);
            }
        }
    }

    /**
     * User đã xem cuộc trò chuyện tới tin {@code upTo}: đánh dấu đã xem mọi tin trước đó mà user là
     * người nhận. Duyệt ngược và dừng ở tin đã xem, vì đã xem một tin nghĩa là đã xem mọi tin trước nó.
     */
    synchronized void seen(ClientHandler handler, String kind, String target, long upTo) {
        String me = handler.getName();
        String key;
        if (Protocol.GROUP.equals(kind)) {
            Set<String> members = store.members(target);
            if (members == null || !members.contains(me)) {
                return;
            }
            key = StoredMessage.conversationKey(Protocol.GROUP, target, null);
        } else if (Protocol.PM.equals(kind)) {
            Account account = store.findAccount(target);
            if (account == null) {
                return;
            }
            key = StoredMessage.conversationKey(Protocol.PM, me, account.name);
        } else {
            return; // chat chung không theo dõi đã xem
        }
        List<StoredMessage> list = store.conversation(key);
        long time = now();
        for (int i = list.size() - 1; i >= 0; i--) {
            StoredMessage m = list.get(i);
            if (m.id > upTo || !m.isRecipient(me)) {
                continue;
            }
            if (m.seen.containsKey(me)) {
                break;
            }
            if (store.markSeen(m, me, time)) {
                notifySender(m);
            }
        }
    }

    private void notifySender(StoredMessage m) {
        ClientHandler sender = clients.get(m.from);
        if (sender != null) {
            sender.send(m.statusLine());
        }
    }

    // ============================================================== lịch sử

    /** Gửi lịch sử chat chung, các nhóm đang tham gia và các cuộc chat riêng cho user vừa đăng nhập. */
    private void sendLoginHistory(ClientHandler handler, long previousSeen) {
        String me = handler.getName();
        sendConversationHistory(handler, StoredMessage.conversationKey(Protocol.ALL, null, null), previousSeen);
        for (Map.Entry<String, Set<String>> group : store.groups().entrySet()) {
            if (group.getValue().contains(me)) {
                sendConversationHistory(handler,
                        StoredMessage.conversationKey(Protocol.GROUP, group.getKey(), null), previousSeen);
            }
        }
        for (String key : store.privateConversations(me)) {
            sendConversationHistory(handler, key, previousSeen);
        }
    }

    /**
     * Gửi tối đa HISTORY_LIMIT tin gần nhất của một cuộc trò chuyện, sau đó là STATUS của các tin do chính
     * user gửi. Gọi khi đang giữ khóa để lịch sử luôn tới trước các tin mới.
     */
    private void sendConversationHistory(ClientHandler handler, String key, long previousSeen) {
        String me = handler.getName();
        List<StoredMessage> recent = store.recent(key, Protocol.HISTORY_LIMIT);
        for (StoredMessage m : recent) {
            String flag = historyFlag(m, me, previousSeen);
            if (m.isFile()) {
                try {
                    handler.send(fileLine(m, flag, store.readFile(m.id)));
                } catch (IOException e) {
                    log("Không đọc được file của tin " + m.id + ": " + e.getMessage());
                }
            } else {
                handler.send(textLine(m, flag));
            }
        }
        for (StoredMessage m : recent) {
            if (m.from.equals(me) && !m.recipients.isEmpty()) {
                handler.send(m.statusLine());
            }
        }
    }

    /** Cờ của tin trong lịch sử: U nếu user chưa đọc, R nếu đã đọc / là tin của chính user. */
    private static String historyFlag(StoredMessage m, String me, long previousSeen) {
        if (m.from.equals(me) || m.isSystem()) {
            return Protocol.FLAG_READ;
        }
        if (m.kind.equals(Protocol.ALL)) {
            return m.time > previousSeen ? Protocol.FLAG_UNREAD : Protocol.FLAG_READ;
        }
        return m.isRecipient(me) && !m.seen.containsKey(me) ? Protocol.FLAG_UNREAD : Protocol.FLAG_READ;
    }

    private static String textLine(StoredMessage m, String flag) {
        return Protocol.MSG + " " + header(m, flag) + " " + m.text;
    }

    private static String fileLine(StoredMessage m, String flag, byte[] data) {
        return Protocol.MSG_FILE + " " + header(m, flag) + " " + Base64.getEncoder().encodeToString(data) + " "
                + m.fileName;
    }

    private static String header(StoredMessage m, String flag) {
        return m.id + " " + m.time + " " + m.kind + " " + m.target + " " + m.from + " " + flag;
    }

    // ============================================================ phân phối

    private void broadcast(String message) {
        for (ClientHandler client : clients.values()) {
            client.send(message);
        }
    }

    private void sendToGroup(String group, String message) {
        Set<String> members = store.members(group);
        if (members == null) {
            return;
        }
        for (String member : members) {
            ClientHandler client = clients.get(member);
            if (client != null) {
                client.send(message);
            }
        }
    }

    /** Gửi danh sách user online, trạng thái hoạt động, danh sách nhóm và nhóm của từng user tới mọi client. */
    private synchronized void broadcastLists() {
        for (ClientHandler client : clients.values()) {
            sendLists(client);
        }
    }

    synchronized void sendLists(ClientHandler client) {
        client.send(Protocol.USERS + " " + String.join(",", new TreeSet<>(clients.keySet())));

        Map<String, String> presence = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Account account : store.accounts()) {
            presence.put(account.name, clients.containsKey(account.name) ? Protocol.ONLINE
                    : String.valueOf(account.lastSeen));
        }
        List<String> presenceItems = new ArrayList<>();
        presence.forEach((user, state) -> presenceItems.add(user + ":" + state));
        client.send(Protocol.PRESENCE + " " + String.join(",", presenceItems));

        List<String> groupItems = new ArrayList<>();
        List<String> myGroups = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : store.groups().entrySet()) {
            groupItems.add(entry.getKey() + ":" + entry.getValue().size());
            if (entry.getValue().contains(client.getName())) {
                myGroups.add(entry.getKey());
            }
        }
        client.send(Protocol.GROUPS + " " + String.join(",", groupItems));
        client.send(Protocol.MY_GROUPS + " " + String.join(",", myGroups));
    }

    void log(String message) {
        if (verbose) {
            System.out.println("[" + LocalTime.now().format(TIME) + "] " + message);
        }
    }
}
