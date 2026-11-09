package minichat.client.ui;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Một tin nhắn hiển thị trong khung chat: văn bản, file/ảnh hoặc thông báo hệ thống.
 * Tin do chính mình gửi có thêm trạng thái: Đang gửi / Đã gửi / Đã nhận / Đã xem / Gửi thất bại.
 */
public class ChatMessage {

    public enum Kind {
        TEXT, FILE, SYSTEM
    }

    public enum Status {
        SENDING, SENT, DELIVERED, SEEN, FAILED
    }

    public final Kind kind;
    public final String from;
    public final String text;
    public final String fileName;
    public final byte[] data;
    public final LocalDateTime time;

    /** Định danh do server cấp (0 khi server chưa xác nhận). */
    public long id;
    /** Trạng thái của tin do mình gửi; null với tin của người khác. */
    public Status status;
    /** Lý do khi gửi thất bại. */
    public String failReason;
    /** Số người đã nhận / đã xem / tổng số người nhận (tổng = 0 với chat chung). */
    public int deliveredCount;
    public int seenCount;
    public int totalRecipients;
    public final List<String> seenBy = new ArrayList<>();

    private ChatMessage(Kind kind, String from, String text, String fileName, byte[] data, LocalDateTime time) {
        this.kind = kind;
        this.from = from;
        this.text = text;
        this.fileName = fileName;
        this.data = data;
        this.time = time;
    }

    public static ChatMessage text(String from, String text, LocalDateTime time) {
        return new ChatMessage(Kind.TEXT, from, text, null, null, time);
    }

    public static ChatMessage file(String from, String fileName, byte[] data, LocalDateTime time) {
        return new ChatMessage(Kind.FILE, from, null, fileName, data, time);
    }

    public static ChatMessage system(String text, LocalDateTime time) {
        return new ChatMessage(Kind.SYSTEM, null, text, null, null, time);
    }

    public static ChatMessage system(String text) {
        return system(text, LocalDateTime.now());
    }

    /** Đổi thời điểm của server (mili giây) sang giờ địa phương để hiển thị. */
    public static LocalDateTime toTime(long millis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
    }

    public boolean isImage() {
        if (fileName == null) {
            return false;
        }
        String name = fileName.toLowerCase(Locale.ROOT);
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
                || name.endsWith(".gif") || name.endsWith(".bmp");
    }

    /** Nội dung rút gọn hiển thị trong danh sách cuộc trò chuyện. */
    public String summary() {
        switch (kind) {
            case FILE:
                return isImage() ? "[Hình ảnh]" : "[File] " + fileName;
            case SYSTEM:
            case TEXT:
            default:
                return text;
        }
    }

    /**
     * Cập nhật biên nhận từ server (STATUS). Số liệu chỉ tăng, không bao giờ giảm, nên trạng thái
     * không lùi lại.
     */
    public void applyReceipts(int delivered, int seen, int total, String seenNames) {
        if (status == Status.FAILED || status == Status.SENDING) {
            status = Status.SENT;
        }
        deliveredCount = Math.max(deliveredCount, delivered);
        seenCount = Math.max(seenCount, seen);
        totalRecipients = Math.max(totalRecipients, total);
        if (seenNames != null && !seenNames.equals("-")) {
            for (String name : Arrays.asList(seenNames.split(","))) {
                if (!seenBy.contains(name)) {
                    seenBy.add(name);
                }
            }
        }
        if (seenCount > 0) {
            status = Status.SEEN;
        } else if (totalRecipients > 0 && deliveredCount >= totalRecipients) {
            status = Status.DELIVERED;
        } else {
            status = Status.SENT;
        }
    }

    /** Dòng trạng thái hiển thị dưới tin của mình; {@code group} = tin trong chat nhóm. */
    public String statusText(boolean group) {
        if (status == null) {
            return "";
        }
        switch (status) {
            case SENDING:
                return "Đang gửi";
            case FAILED:
                return "Gửi thất bại";
            case SEEN:
                return group ? "Đã xem " + seenCount + "/" + totalRecipients : "Đã xem";
            case DELIVERED:
                return "Đã nhận";
            case SENT:
            default:
                return "Đã gửi";
        }
    }
}
