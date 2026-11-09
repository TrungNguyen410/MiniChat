package minichat.common;

/**
 * Chuỗi trạng thái hoạt động hiển thị cho người dùng (dùng chung cho client giao diện và dòng lệnh).
 */
public final class Presence {

    public static final String ONLINE_TEXT = "Đang hoạt động";

    private static final long MINUTE = 60_000L;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = 24 * HOUR;

    private Presence() {
    }

    /**
     * Trạng thái của một user đang offline, truy cập lần cuối lúc {@code lastSeen}, tính tại thời điểm
     * {@code now} (cả hai theo giờ server):
     * dưới 1 phút "Vừa truy cập", 1-59 phút "Truy cập N phút trước", 1-23 giờ "Truy cập N giờ trước",
     * từ 1 ngày "Truy cập N ngày trước".
     */
    public static String describe(long lastSeen, long now) {
        long elapsed = Math.max(0, now - lastSeen);
        if (elapsed < MINUTE) {
            return "Vừa truy cập";
        }
        if (elapsed < HOUR) {
            return "Truy cập " + elapsed / MINUTE + " phút trước";
        }
        if (elapsed < DAY) {
            return "Truy cập " + elapsed / HOUR + " giờ trước";
        }
        return "Truy cập " + elapsed / DAY + " ngày trước";
    }

    /** Trạng thái đầy đủ: "Đang hoạt động" nếu online, ngược lại như {@link #describe}. */
    public static String describe(boolean online, long lastSeen, long now) {
        return online ? ONLINE_TEXT : describe(lastSeen, now);
    }
}
