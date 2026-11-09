package minichat.client.ui;

import java.time.LocalDateTime;

import minichat.common.Protocol;

/** Một cuộc trò chuyện: chat chung (ALL), nhóm (GROUP) hoặc chat riêng (PM). */
public class Conversation {

    public final String kind;
    /** "*" với chat chung, tên nhóm với GROUP, tên người kia với PM. */
    public final String target;
    public final MessageListPanel messages;

    public String preview = "";
    public LocalDateTime lastTime;
    public int unread;
    public String typingUser;
    public long typingUntil;
    /** Id lớn nhất của tin do người khác gửi trong cuộc trò chuyện này. */
    public long lastIncomingId;
    /** Id lớn nhất đã báo "đã xem" cho server. */
    public long seenReported;

    public Conversation(String kind, String target, String me) {
        this.kind = kind;
        this.target = target;
        this.messages = new MessageListPanel(me, !kind.equals(Protocol.PM), kind.equals(Protocol.GROUP));
    }

    public static String key(String kind, String target) {
        return kind + ":" + target;
    }

    public String key() {
        return key(kind, target);
    }

    public String title() {
        return isAll() ? "Chat chung" : target;
    }

    public boolean isAll() {
        return kind.equals(Protocol.ALL);
    }

    public boolean isGroup() {
        return kind.equals(Protocol.GROUP);
    }

    public boolean isPrivate() {
        return kind.equals(Protocol.PM);
    }

    public boolean isTyping() {
        return typingUser != null && System.currentTimeMillis() < typingUntil;
    }
}
