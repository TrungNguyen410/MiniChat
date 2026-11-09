package minichat.server;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import minichat.common.Protocol;

/**
 * Một tin nhắn đã được server lưu: văn bản, file/ảnh hoặc thông báo hệ thống,
 * cùng danh sách người nhận tại thời điểm gửi và biên nhận (đã nhận / đã xem) của từng người.
 */
final class StoredMessage {

    final long id;
    final long time;
    /** ALL / GROUP / PM. */
    final String kind;
    /** "*" với chat chung, tên nhóm với GROUP, tên người nhận với PM. */
    final String target;
    /** Tên người gửi, hoặc {@link Protocol#SYSTEM_SENDER} với thông báo hệ thống. */
    final String from;
    /** Người nhận tại lúc gửi (rỗng với chat chung và thông báo hệ thống). */
    final List<String> recipients;
    /** Nội dung văn bản; null nếu là file. */
    final String text;
    /** Tên file nếu là file / ảnh; null nếu là văn bản. */
    final String fileName;
    /** người nhận -> thời điểm đã nhận. */
    final Map<String, Long> delivered = new LinkedHashMap<>();
    /** người nhận -> thời điểm đã xem (đã xem thì cũng đã nhận). */
    final Map<String, Long> seen = new LinkedHashMap<>();

    StoredMessage(long id, long time, String kind, String target, String from, List<String> recipients,
            String text, String fileName) {
        this.id = id;
        this.time = time;
        this.kind = kind;
        this.target = target;
        this.from = from;
        this.recipients = Collections.unmodifiableList(recipients);
        this.text = text;
        this.fileName = fileName;
    }

    boolean isFile() {
        return fileName != null;
    }

    boolean isSystem() {
        return Protocol.SYSTEM_SENDER.equals(from);
    }

    boolean isRecipient(String user) {
        return recipients.contains(user);
    }

    /** Khóa của cuộc trò chuyện chứa tin này (giống nhau với cả hai phía của chat riêng). */
    String conversationKey() {
        return conversationKey(kind, target, from);
    }

    /**
     * Khóa cuộc trò chuyện: "ALL:*", "GROUP:&lt;nhóm&gt;" hoặc "PM:&lt;a&gt;|&lt;b&gt;" (hai tên chữ thường,
     * sắp xếp tăng dần). Với PM, {@code a} và {@code b} là hai người trong cuộc.
     */
    static String conversationKey(String kind, String a, String b) {
        if (Protocol.GROUP.equals(kind)) {
            return Protocol.GROUP + ":" + a;
        }
        if (Protocol.PM.equals(kind)) {
            String x = a.toLowerCase(Locale.ROOT);
            String y = b.toLowerCase(Locale.ROOT);
            return Protocol.PM + ":" + (x.compareTo(y) <= 0 ? x + "|" + y : y + "|" + x);
        }
        return Protocol.ALL + ":" + Protocol.ALL_TARGET;
    }

    /** Dòng STATUS gửi cho người gửi: STATUS id nhận xem tổng người-xem. */
    String statusLine() {
        return Protocol.STATUS + " " + id + " " + delivered.size() + " " + seen.size() + " " + recipients.size()
                + " " + (seen.isEmpty() ? Protocol.EMPTY_LIST : String.join(",", seen.keySet()));
    }
}
