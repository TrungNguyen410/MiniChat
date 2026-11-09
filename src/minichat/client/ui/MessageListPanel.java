package minichat.client.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.IdentityHashMap;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.text.View;

/**
 * Danh sách tin nhắn của một cuộc trò chuyện, hiển thị dạng bong bóng giống Zalo:
 * tin của mình bên phải (xanh nhạt), tin người khác bên trái (trắng) kèm avatar.
 * Dưới tin gần nhất của mình có dòng trạng thái (Đang gửi / Đã gửi / Đã nhận / Đã xem);
 * tin "Gửi thất bại" luôn hiện trạng thái.
 */
public class MessageListPanel extends JPanel implements Scrollable {

    private static final int MAX_BUBBLE_WIDTH = 420;
    private static final int AVATAR_SIZE = 36;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final String me;
    private final boolean showSenderNames;
    private final boolean groupChat;
    /** Dòng trạng thái của từng tin do mình gửi. */
    private final Map<ChatMessage, JLabel> statusLabels = new IdentityHashMap<>();
    /** Tin gần nhất do mình gửi (chỉ tin này hiện trạng thái). */
    private ChatMessage lastMine;
    private String lastSender;
    private LocalDateTime lastTime;
    private LocalDate lastDate;

    public MessageListPanel(String me, boolean showSenderNames, boolean groupChat) {
        this.me = me;
        this.showSenderNames = showSenderNames;
        this.groupChat = groupChat;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(Theme.CHAT_BG);
        setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        add(Box.createVerticalGlue()); // đẩy tin nhắn xuống đáy khi còn ít tin
    }

    public void add(ChatMessage message) {
        LocalDate day = message.time.toLocalDate();
        if (!day.equals(lastDate)) {
            lastDate = day;
            lastSender = null;
            addCentered(pill(day.equals(LocalDate.now()) ? "Hôm nay" : day.format(DATE)));
        }
        if (message.kind == ChatMessage.Kind.SYSTEM) {
            lastSender = null;
            addCentered(pill(message.text));
            return;
        }

        boolean mine = message.from.equals(me);
        boolean continued = message.from.equals(lastSender) && lastTime != null
                && Duration.between(lastTime, message.time).toMinutes() < 3;
        lastSender = message.from;
        lastTime = message.time;

        RoundedPanel bubble = bubble(message, mine);
        bubble.setAlignmentX(mine ? Component.RIGHT_ALIGNMENT : Component.LEFT_ALIGNMENT);

        JPanel column = new JPanel();
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        column.setOpaque(false);
        column.setAlignmentY(Component.TOP_ALIGNMENT);
        if (!mine && showSenderNames && !continued) {
            JLabel name = new JLabel(message.from);
            name.setFont(Theme.font(Font.PLAIN, 12));
            name.setForeground(Theme.TEXT_MUTED);
            name.setBorder(BorderFactory.createEmptyBorder(0, 2, 3, 0));
            name.setAlignmentX(Component.LEFT_ALIGNMENT);
            column.add(name);
        }
        column.add(bubble);
        if (mine) {
            JLabel status = new JLabel();
            status.setFont(Theme.font(Font.PLAIN, 11));
            status.setBorder(BorderFactory.createEmptyBorder(3, 0, 0, 4));
            status.setAlignmentX(Component.RIGHT_ALIGNMENT);
            column.add(status);
            statusLabels.put(message, status);
            ChatMessage previous = lastMine;
            lastMine = message;
            if (previous != null) {
                refreshStatus(previous);
            }
            refreshStatus(message);
        }

        Row row = new Row();
        if (mine) {
            row.add(Box.createHorizontalGlue());
            row.add(column);
        } else {
            JLabel avatar = new JLabel(continued ? null : Avatar.of(message.from, AVATAR_SIZE));
            Dimension size = new Dimension(AVATAR_SIZE, AVATAR_SIZE);
            avatar.setPreferredSize(size);
            avatar.setMaximumSize(size);
            avatar.setAlignmentY(Component.TOP_ALIGNMENT);
            row.add(avatar);
            row.add(Box.createHorizontalStrut(8));
            row.add(column);
            row.add(Box.createHorizontalGlue());
        }
        add(Box.createVerticalStrut(continued ? 4 : 12));
        add(row);
        revalidate();
        repaint();
    }

    /** Cập nhật dòng trạng thái của một tin do mình gửi (sau ACK / NACK / STATUS). */
    public void updateStatus(ChatMessage message) {
        refreshStatus(message);
        revalidate();
        repaint();
    }

    private void refreshStatus(ChatMessage message) {
        JLabel label = statusLabels.get(message);
        if (label == null) {
            return;
        }
        boolean failed = message.status == ChatMessage.Status.FAILED;
        label.setVisible(message.status != null && (message == lastMine || failed));
        label.setText(message.statusText(groupChat));
        label.setForeground(failed ? Theme.BADGE
                : message.status == ChatMessage.Status.SEEN ? Theme.PRIMARY : Theme.TEXT_MUTED);
        if (failed) {
            label.setToolTipText(message.failReason);
        } else if (message.status == ChatMessage.Status.SEEN && groupChat) {
            label.setToolTipText("Đã xem: " + String.join(", ", message.seenBy));
        } else {
            label.setToolTipText(null);
        }
    }

    private RoundedPanel bubble(ChatMessage message, boolean mine) {
        RoundedPanel bubble = new RoundedPanel(new BorderLayout(0, 4),
                mine ? Theme.BUBBLE_ME : Theme.BUBBLE_OTHER,
                mine ? Theme.BUBBLE_ME_BORDER : Theme.BUBBLE_OTHER_BORDER, 16) {
            @Override
            public Dimension getMaximumSize() {
                return getPreferredSize();
            }
        };
        JComponent content;
        if (message.kind == ChatMessage.Kind.TEXT) {
            content = textContent(message.text);
            bubble.setBorder(BorderFactory.createEmptyBorder(9, 12, 7, 12));
        } else {
            content = message.isImage() ? imageContent(message) : null;
            if (content == null) {
                content = fileContent(message);
            }
            bubble.setBorder(BorderFactory.createEmptyBorder(8, 8, 6, 10));
        }
        bubble.add(content, BorderLayout.CENTER);

        JLabel time = new JLabel(Theme.hour(message.time));
        time.setFont(Theme.font(Font.PLAIN, 11));
        time.setForeground(Theme.TEXT_MUTED);
        bubble.add(time, BorderLayout.SOUTH);
        return bubble;
    }

    private static JComponent textContent(String text) {
        return new BubbleText(text);
    }

    /**
     * Nội dung chữ trong bong bóng: rộng vừa đủ với câu ngắn, tự xuống dòng khi dài hơn
     * MAX_BUBBLE_WIDTH. Kích thước tính lúc hiển thị để dùng đúng font của màn hình.
     */
    private static final class BubbleText extends JTextArea {
        BubbleText(String text) {
            super(text);
            setFont(Theme.font(Font.PLAIN, 14));
            setForeground(Theme.TEXT);
            setLineWrap(true);
            setWrapStyleWord(true);
            setEditable(false);
            setOpaque(false);
            setBorder(null);
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont());
            int width = Math.min(MAX_BUBBLE_WIDTH, fm.stringWidth(getText()) + 8);
            View root = getUI().getRootView(this);
            root.setSize(width, Integer.MAX_VALUE);
            int height = (int) Math.ceil(root.getPreferredSpan(View.Y_AXIS));
            return new Dimension(width, height);
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }
    }

    private JComponent imageContent(ChatMessage message) {
        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(message.data));
        } catch (IOException e) {
            image = null;
        }
        if (image == null) {
            return null;
        }
        double scale = Math.min(1.0, Math.min(260.0 / image.getWidth(), 260.0 / image.getHeight()));
        int w = Math.max(1, (int) (image.getWidth() * scale));
        int h = Math.max(1, (int) (image.getHeight() * scale));
        JLabel label = new JLabel(new ImageIcon(image.getScaledInstance(w, h, Image.SCALE_SMOOTH)));
        label.setToolTipText("Nhấp để xem ảnh");
        label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        BufferedImage full = image;
        label.addMouseListener(onClick(() -> showImage(full, message)));
        return label;
    }

    private JComponent fileContent(ChatMessage message) {
        JPanel panel = new JPanel(new BorderLayout(10, 0));
        panel.setOpaque(false);
        panel.add(new JLabel(Icons.get(Icons.Type.FILE, 36, Theme.PRIMARY)), BorderLayout.WEST);

        String name = message.fileName.length() > 32 ? message.fileName.substring(0, 29) + "..." : message.fileName;
        JLabel nameLabel = new JLabel(name);
        nameLabel.setFont(Theme.font(Font.BOLD, 14));
        nameLabel.setForeground(Theme.TEXT);
        JLabel sizeLabel = new JLabel(formatSize(message.data.length) + "  ·  Nhấp để lưu");
        sizeLabel.setFont(Theme.font(Font.PLAIN, 12));
        sizeLabel.setForeground(Theme.TEXT_MUTED);
        JPanel info = new JPanel(new BorderLayout(0, 2));
        info.setOpaque(false);
        info.add(nameLabel, BorderLayout.NORTH);
        info.add(sizeLabel, BorderLayout.SOUTH);
        panel.add(info, BorderLayout.CENTER);

        panel.setToolTipText(message.fileName);
        panel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        panel.addMouseListener(onClick(() -> saveFile(this, message)));
        return panel;
    }

    private void showImage(BufferedImage image, ChatMessage message) {
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        double scale = Math.min(1.0, Math.min(screen.width * 0.8 / image.getWidth(), screen.height * 0.75 / image.getHeight()));
        int w = Math.max(1, (int) (image.getWidth() * scale));
        int h = Math.max(1, (int) (image.getHeight() * scale));

        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), message.fileName);
        JLabel picture = new JLabel(new ImageIcon(image.getScaledInstance(w, h, Image.SCALE_SMOOTH)));
        picture.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        javax.swing.JButton save = Theme.primaryButton("Lưu ảnh");
        save.addActionListener(e -> saveFile(dialog, message));
        JPanel south = new JPanel();
        south.add(save);
        dialog.add(picture, BorderLayout.CENTER);
        dialog.add(south, BorderLayout.SOUTH);
        dialog.pack();
        dialog.setLocationRelativeTo(SwingUtilities.getWindowAncestor(this));
        dialog.setVisible(true);
    }

    private static void saveFile(Component parent, ChatMessage message) {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(message.fileName));
        if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            Files.write(chooser.getSelectedFile().toPath(), message.data);
            JOptionPane.showMessageDialog(parent, "Đã lưu: " + chooser.getSelectedFile().getAbsolutePath());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(parent, "Không lưu được file: " + e.getMessage(), "Lỗi",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }

    private static JComponent pill(String text) {
        RoundedPanel pill = new RoundedPanel(new BorderLayout(), Theme.PILL_BG, null, 20) {
            @Override
            public Dimension getMaximumSize() {
                return getPreferredSize();
            }
        };
        pill.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setFont(Theme.font(Font.PLAIN, 12));
        label.setForeground(Theme.TEXT_MUTED);
        pill.add(label);
        return pill;
    }

    private void addCentered(JComponent component) {
        Row row = new Row();
        row.add(Box.createHorizontalGlue());
        row.add(component);
        row.add(Box.createHorizontalGlue());
        add(Box.createVerticalStrut(12));
        add(row);
        revalidate();
        repaint();
    }

    private static MouseAdapter onClick(Runnable action) {
        return new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                action.run();
            }
        };
    }

    /** Một hàng ngang không bị kéo giãn theo chiều cao. */
    private static class Row extends JPanel {
        Row() {
            setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
            setOpaque(false);
            setAlignmentX(Component.LEFT_ALIGNMENT);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }
    }

    // --------------------------------------------------------- Scrollable

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 18;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return visibleRect.height - 40;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return getParent() instanceof JViewport && getParent().getHeight() > getPreferredSize().height;
    }
}
