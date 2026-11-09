package minichat.server;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;

import minichat.common.Protocol;

/**
 * Dữ liệu lâu dài của MiniChat Server: tài khoản, nhóm, tin nhắn và biên nhận.
 *
 * Toàn bộ dữ liệu được giữ trong bộ nhớ để truy cập nhanh, đồng thời mọi thay đổi được ghi nối thêm
 * vào các file trong thư mục dữ liệu (xem specs/001-history-auth-presence/data-model.md):
 * <pre>
 *   accounts.log   ACCOUNT &lt;tên&gt; &lt;salt&gt; &lt;hash&gt; &lt;tạo lúc&gt; | LASTSEEN &lt;tên&gt; &lt;ms&gt;
 *   groups.log     GROUP &lt;nhóm&gt; | JOIN &lt;nhóm&gt; &lt;user&gt; | LEAVE &lt;nhóm&gt; &lt;user&gt;
 *   messages.log   TEXT|FILE &lt;id&gt; &lt;ms&gt; &lt;loại&gt; &lt;đích&gt; &lt;từ&gt; &lt;người nhận|-&gt; &lt;nội dung | tên file&gt;
 *   receipts.log   D|S &lt;id&gt; &lt;user&gt; &lt;ms&gt;      (D = đã nhận, S = đã xem)
 *   files/&lt;id&gt;     nội dung của file / ảnh
 * </pre>
 * Mỗi thay đổi được ghi xuống đĩa TRƯỚC rồi mới cập nhật bộ nhớ, nên lỗi ghi không tạo ra dữ liệu "ma".
 */
final class ChatStore implements AutoCloseable {

    /** Một tài khoản đã đăng ký. */
    static final class Account {
        final String name;
        final String salt;
        final String hash;
        final long created;
        volatile long lastSeen;

        Account(String name, String salt, String hash, long created) {
            this.name = name;
            this.salt = salt;
            this.hash = hash;
            this.created = created;
            this.lastSeen = created;
        }
    }

    private final Path filesDir;
    private final Consumer<String> logger;
    private final AppendLog accountLog;
    private final AppendLog groupLog;
    private final AppendLog messageLog;
    private final AppendLog receiptLog;

    /** tên chữ thường -> tài khoản. */
    private final Map<String, Account> accounts = new HashMap<>();
    /** tên nhóm -> thành viên (sắp xếp theo tên nhóm để danh sách ổn định). */
    private final Map<String, Set<String>> groups = new TreeMap<>();
    private final Map<Long, StoredMessage> messages = new HashMap<>();
    /** khóa cuộc trò chuyện -> các tin theo thứ tự id tăng dần. */
    private final Map<String, List<StoredMessage>> conversations = new HashMap<>();
    /** tên chữ thường -> các khóa cuộc chat riêng có tham gia. */
    private final Map<String, Set<String>> privateChats = new HashMap<>();
    private long nextId = 1;

    private ChatStore(Path dir, Consumer<String> logger) {
        this.filesDir = dir.resolve("files");
        this.logger = logger;
        this.accountLog = new AppendLog(dir.resolve("accounts.log"), logger);
        this.groupLog = new AppendLog(dir.resolve("groups.log"), logger);
        this.messageLog = new AppendLog(dir.resolve("messages.log"), logger);
        this.receiptLog = new AppendLog(dir.resolve("receipts.log"), logger);
    }

    /** Mở (và tạo nếu chưa có) thư mục dữ liệu, nạp lại toàn bộ dữ liệu cũ. */
    static ChatStore open(Path dir, Consumer<String> logger) throws IOException {
        Files.createDirectories(dir.resolve("files"));
        ChatStore store = new ChatStore(dir, logger);
        store.load();
        return store;
    }

    private void load() throws IOException {
        accountLog.load(5, (f, line) -> {
            if (f[0].equals("ACCOUNT")) {
                Account a = new Account(f[1], f[2], f[3], Long.parseLong(f[4]));
                accounts.put(key(a.name), a);
            } else if (f[0].equals("LASTSEEN")) {
                requireAccount(f[1]).lastSeen = Long.parseLong(f[2]);
            } else {
                throw new IllegalArgumentException("loại bản ghi lạ");
            }
        });
        groupLog.load(3, (f, line) -> {
            if (f[0].equals("GROUP")) {
                groups.putIfAbsent(f[1], new LinkedHashSet<>());
            } else if (f[0].equals("JOIN")) {
                requireGroup(f[1]).add(f[2]);
            } else if (f[0].equals("LEAVE")) {
                requireGroup(f[1]).remove(f[2]);
            } else {
                throw new IllegalArgumentException("loại bản ghi lạ");
            }
        });
        messageLog.load(8, (f, line) -> {
            boolean file = f[0].equals("FILE");
            if (!file && !f[0].equals("TEXT")) {
                throw new IllegalArgumentException("loại bản ghi lạ");
            }
            if (!Protocol.isValidKind(f[3])) {
                throw new IllegalArgumentException("loại cuộc trò chuyện lạ");
            }
            String content = f.length > 7 ? f[7] : "";
            StoredMessage m = new StoredMessage(Long.parseLong(f[1]), Long.parseLong(f[2]), f[3], f[4], f[5],
                    splitList(f[6]), file ? null : content, file ? content : null);
            index(m);
        });
        receiptLog.load(4, (f, line) -> {
            StoredMessage m = messages.get(Long.parseLong(f[1]));
            if (m == null) {
                throw new IllegalArgumentException("tin nhắn không tồn tại");
            }
            long time = Long.parseLong(f[3]);
            if (f[0].equals("D")) {
                m.delivered.putIfAbsent(f[2], time);
            } else if (f[0].equals("S")) {
                m.delivered.putIfAbsent(f[2], time);
                m.seen.putIfAbsent(f[2], time);
            } else {
                throw new IllegalArgumentException("loại bản ghi lạ");
            }
        });
        logger.accept("Đã nạp dữ liệu: " + accounts.size() + " tài khoản, " + groups.size() + " nhóm, "
                + messages.size() + " tin nhắn");
    }

    private Account requireAccount(String name) {
        Account a = accounts.get(key(name));
        if (a == null) {
            throw new IllegalArgumentException("tài khoản không tồn tại");
        }
        return a;
    }

    private Set<String> requireGroup(String group) {
        Set<String> members = groups.get(group);
        if (members == null) {
            throw new IllegalArgumentException("nhóm không tồn tại");
        }
        return members;
    }

    private void index(StoredMessage m) {
        messages.put(m.id, m);
        String conversation = m.conversationKey();
        List<StoredMessage> list = conversations.computeIfAbsent(conversation, k -> new ArrayList<>());
        list.add(m);
        if (list.size() > 1 && list.get(list.size() - 2).id > m.id) {
            list.sort((a, b) -> Long.compare(a.id, b.id));
        }
        if (m.kind.equals(Protocol.PM)) {
            privateChats.computeIfAbsent(key(m.from), k -> new LinkedHashSet<>()).add(conversation);
            privateChats.computeIfAbsent(key(m.target), k -> new LinkedHashSet<>()).add(conversation);
        }
        nextId = Math.max(nextId, m.id + 1);
    }

    // ------------------------------------------------------------- tài khoản

    synchronized Account findAccount(String name) {
        return name == null ? null : accounts.get(key(name));
    }

    synchronized Collection<Account> accounts() {
        return new ArrayList<>(accounts.values());
    }

    /** Tạo tài khoản; trả về null nếu tên đã tồn tại (không phân biệt hoa/thường). */
    synchronized Account createAccount(String name, String salt, String hash, long now) throws IOException {
        if (accounts.containsKey(key(name))) {
            return null;
        }
        accountLog.append("ACCOUNT " + name + " " + salt + " " + hash + " " + now);
        Account a = new Account(name, salt, hash, now);
        accounts.put(key(name), a);
        return a;
    }

    synchronized void setLastSeen(Account account, long now) {
        try {
            accountLog.append("LASTSEEN " + account.name + " " + now);
        } catch (IOException e) {
            logger.accept("Lỗi lưu trữ (thời điểm truy cập của " + account.name + "): " + e.getMessage());
        }
        account.lastSeen = now;
    }

    // ------------------------------------------------------------------ nhóm

    /** Các nhóm và thành viên (chỉ đọc, chỉ dùng khi đang giữ khóa của server). */
    synchronized Map<String, Set<String>> groups() {
        return Collections.unmodifiableMap(groups);
    }

    synchronized Set<String> members(String group) {
        Set<String> members = groups.get(group);
        return members == null ? null : Collections.unmodifiableSet(members);
    }

    synchronized void createGroup(String group) throws IOException {
        if (!groups.containsKey(group)) {
            groupLog.append("GROUP " + group);
            groups.put(group, new LinkedHashSet<>());
        }
    }

    /** Thêm thành viên; trả về false nếu đã là thành viên. */
    synchronized boolean addMember(String group, String user) throws IOException {
        Set<String> members = requireGroup(group);
        if (members.contains(user)) {
            return false;
        }
        groupLog.append("JOIN " + group + " " + user);
        members.add(user);
        return true;
    }

    /** Bỏ thành viên; trả về false nếu không phải thành viên. */
    synchronized boolean removeMember(String group, String user) throws IOException {
        Set<String> members = requireGroup(group);
        if (!members.contains(user)) {
            return false;
        }
        groupLog.append("LEAVE " + group + " " + user);
        members.remove(user);
        return true;
    }

    // -------------------------------------------------------------- tin nhắn

    /**
     * Lưu một tin mới. Với file, nội dung được ghi ra files/&lt;id&gt; (ghi file tạm rồi đổi tên) trước khi
     * ghi dòng tin nhắn, để không bao giờ có tin trỏ tới file chưa ghi xong.
     */
    synchronized StoredMessage addMessage(String kind, String target, String from, List<String> recipients,
            String text, String fileName, byte[] data, long now) throws IOException {
        long id = nextId;
        if (fileName != null) {
            Path tmp = filesDir.resolve(id + ".tmp");
            Files.write(tmp, data);
            try {
                Files.move(tmp, filesDir.resolve(String.valueOf(id)), StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, filesDir.resolve(String.valueOf(id)), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        String recipientList = recipients.isEmpty() ? Protocol.EMPTY_LIST : String.join(",", recipients);
        messageLog.append((fileName != null ? "FILE " : "TEXT ") + id + " " + now + " " + kind + " " + target + " "
                + from + " " + recipientList + " " + (fileName != null ? fileName : text));
        StoredMessage m = new StoredMessage(id, now, kind, target, from, new ArrayList<>(recipients), text, fileName);
        index(m);
        return m;
    }

    synchronized StoredMessage message(long id) {
        return messages.get(id);
    }

    /** Các tin của một cuộc trò chuyện theo thứ tự id tăng dần (bản sao). */
    synchronized List<StoredMessage> conversation(String key) {
        List<StoredMessage> list = conversations.get(key);
        return list == null ? new ArrayList<>() : new ArrayList<>(list);
    }

    /** Tối đa {@code limit} tin gần nhất của một cuộc trò chuyện. */
    synchronized List<StoredMessage> recent(String key, int limit) {
        List<StoredMessage> list = conversations.getOrDefault(key, List.of());
        return new ArrayList<>(list.subList(Math.max(0, list.size() - limit), list.size()));
    }

    /** Khóa các cuộc chat riêng mà user có tham gia. */
    synchronized Set<String> privateConversations(String user) {
        return new LinkedHashSet<>(privateChats.getOrDefault(key(user), Set.of()));
    }

    /** Đọc nội dung file / ảnh của tin (gọi ngoài khóa của server vì có thể lớn). */
    byte[] readFile(long id) throws IOException {
        return Files.readAllBytes(filesDir.resolve(String.valueOf(id)));
    }

    /** Đánh dấu đã nhận; trả về true nếu trạng thái thay đổi. */
    synchronized boolean markDelivered(StoredMessage m, String user, long now) {
        if (!m.isRecipient(user) || m.delivered.containsKey(user)) {
            return false;
        }
        appendReceipt("D " + m.id + " " + user + " " + now);
        m.delivered.put(user, now);
        return true;
    }

    /** Đánh dấu đã xem (kéo theo đã nhận); trả về true nếu trạng thái thay đổi. */
    synchronized boolean markSeen(StoredMessage m, String user, long now) {
        if (!m.isRecipient(user) || m.seen.containsKey(user)) {
            return false;
        }
        appendReceipt("S " + m.id + " " + user + " " + now);
        m.delivered.putIfAbsent(user, now);
        m.seen.put(user, now);
        return true;
    }

    private void appendReceipt(String line) {
        try {
            receiptLog.append(line);
        } catch (IOException e) {
            logger.accept("Lỗi lưu trữ biên nhận: " + e.getMessage());
        }
    }

    @Override
    public synchronized void close() throws IOException {
        accountLog.close();
        groupLog.close();
        messageLog.close();
        receiptLog.close();
    }

    static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private static List<String> splitList(String list) {
        return list.equals(Protocol.EMPTY_LIST) ? new ArrayList<>() : new ArrayList<>(Arrays.asList(list.split(",")));
    }
}
