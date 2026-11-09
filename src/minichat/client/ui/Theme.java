package minichat.client.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.plaf.basic.BasicScrollBarUI;

/** Màu sắc, font và các tiện ích giao diện theo phong cách Zalo. */
public final class Theme {

    public static final Color PRIMARY = new Color(0x0068FF);
    public static final Color PRIMARY_DARK = new Color(0x0050C8);
    public static final Color CHAT_BG = new Color(0xEEF0F1);
    public static final Color BORDER = new Color(0xDFE2E7);
    public static final Color TEXT = new Color(0x081C36);
    public static final Color TEXT_MUTED = new Color(0x7589A3);
    public static final Color BUBBLE_ME = new Color(0xE5EFFF);
    public static final Color BUBBLE_ME_BORDER = new Color(0xC7DCFF);
    public static final Color BUBBLE_OTHER = Color.WHITE;
    public static final Color BUBBLE_OTHER_BORDER = new Color(0xDFE2E7);
    public static final Color SELECTED = new Color(0xE5EFFF);
    public static final Color HOVER = new Color(0xF3F5F6);
    public static final Color BADGE = new Color(0xFA383E);
    public static final Color ONLINE = new Color(0x2DC24F);
    public static final Color FIELD_BG = new Color(0xEAEDF0);
    public static final Color PILL_BG = new Color(0xDDE3EA);

    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM");

    private Theme() {
    }

    /** Font "Dialog" hiển thị được cả tiếng Việt lẫn emoji trên Windows. */
    public static Font font(int style, int size) {
        return new Font(Font.DIALOG, style, size);
    }

    public static String shortTime(LocalDateTime time) {
        if (time == null) {
            return "";
        }
        return time.toLocalDate().equals(LocalDate.now()) ? time.format(HOUR) : time.format(DAY);
    }

    public static String hour(LocalDateTime time) {
        return time.format(HOUR);
    }

    /** Bỏ dấu tiếng Việt và chuyển về chữ thường để tìm kiếm ("binh" khớp "Bình"). */
    public static String fold(String text) {
        String noMarks = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return noMarks.replace('đ', 'd').replace('Đ', 'D').toLowerCase();
    }

    public static Graphics2D smooth(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g2;
    }

    /** Nút trong suốt chỉ có icon, nền xám nhạt khi rê chuột. */
    public static JButton iconButton(javax.swing.Icon icon, String tooltip) {
        FlatButton button = new FlatButton(null, icon, null, HOVER, TEXT);
        button.setToolTipText(tooltip);
        button.setPreferredSize(new Dimension(36, 36));
        return button;
    }

    /** Nút chính màu xanh. */
    public static JButton primaryButton(String text) {
        FlatButton button = new FlatButton(text, null, PRIMARY, PRIMARY_DARK, Color.WHITE);
        button.setFont(font(Font.BOLD, 14));
        return button;
    }

    /** Nút phụ màu xám. */
    public static JButton secondaryButton(String text) {
        FlatButton button = new FlatButton(text, null, FIELD_BG, PILL_BG, TEXT);
        button.setFont(font(Font.BOLD, 14));
        return button;
    }

    /** Thanh cuộn mảnh, không có mũi tên. */
    public static JScrollPane scroll(JComponent view) {
        JScrollPane scroll = new JScrollPane(view);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        scroll.getVerticalScrollBar().setUI(new ThinScrollBarUI());
        scroll.getVerticalScrollBar().setPreferredSize(new Dimension(8, 0));
        scroll.getVerticalScrollBar().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        return scroll;
    }

    /** Icon ứng dụng: ô vuông bo góc màu xanh với bong bóng chat. */
    public static BufferedImage appImage(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = smooth(image.getGraphics());
        g.setColor(PRIMARY);
        g.fillRoundRect(0, 0, size, size, size / 3, size / 3);
        Icons.get(Icons.Type.CHAT, (int) (size * 0.7), Color.WHITE).paintIcon(null, g, (int) (size * 0.15), (int) (size * 0.17));
        g.dispose();
        return image;
    }

    private static class ThinScrollBarUI extends BasicScrollBarUI {
        @Override
        protected void configureScrollBarColors() {
            thumbColor = new Color(0xC4CBD4);
            trackColor = new Color(0, 0, 0, 0);
        }

        @Override
        protected JButton createDecreaseButton(int orientation) {
            return zeroButton();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return zeroButton();
        }

        private static JButton zeroButton() {
            JButton button = new JButton();
            button.setPreferredSize(new Dimension(0, 0));
            return button;
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, java.awt.Rectangle r) {
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, java.awt.Rectangle r) {
            if (r.isEmpty()) {
                return;
            }
            Graphics2D g2 = smooth(g);
            g2.setColor(thumbColor);
            g2.fillRoundRect(r.x + 1, r.y + 2, r.width - 2, r.height - 4, r.width - 2, r.width - 2);
            g2.dispose();
        }
    }
}
