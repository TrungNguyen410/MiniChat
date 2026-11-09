package minichat.client.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.util.function.Function;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;

/** Ô trong danh sách cuộc trò chuyện: avatar, tên, tin nhắn cuối, giờ và số tin chưa đọc. */
public class ConversationRenderer extends JPanel implements ListCellRenderer<Conversation> {

    private final Function<Conversation, Icon> avatarProvider;
    private final JLabel avatar = new JLabel();
    private final JLabel name = new JLabel();
    private final JLabel preview = new JLabel();
    private final JLabel time = new JLabel();
    private final Badge badge = new Badge();
    private int hoverIndex = -1;

    public ConversationRenderer(Function<Conversation, Icon> avatarProvider) {
        super(new BorderLayout(12, 0));
        this.avatarProvider = avatarProvider;
        setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        time.setFont(Theme.font(Font.PLAIN, 12));
        time.setForeground(Theme.TEXT_MUTED);

        JPanel top = new JPanel(new BorderLayout(8, 0));
        top.setOpaque(false);
        top.add(name, BorderLayout.CENTER);
        top.add(time, BorderLayout.EAST);
        JPanel bottom = new JPanel(new BorderLayout(8, 0));
        bottom.setOpaque(false);
        bottom.add(preview, BorderLayout.CENTER);
        bottom.add(badge, BorderLayout.EAST);
        JPanel text = new JPanel(new GridLayout(2, 1, 0, 4));
        text.setOpaque(false);
        text.add(top);
        text.add(bottom);

        add(avatar, BorderLayout.WEST);
        add(text, BorderLayout.CENTER);
    }

    public void setHoverIndex(int index) {
        hoverIndex = index;
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends Conversation> list, Conversation c, int index,
            boolean selected, boolean focus) {
        avatar.setIcon(avatarProvider.apply(c));
        name.setText(c.title());
        name.setFont(Theme.font(c.unread > 0 ? Font.BOLD : Font.PLAIN, 15));
        name.setForeground(Theme.TEXT);
        time.setText(Theme.shortTime(c.lastTime));

        if (c.isTyping()) {
            preview.setText(c.isPrivate() ? "đang soạn tin..." : c.typingUser + " đang soạn tin...");
            preview.setFont(Theme.font(Font.ITALIC, 13));
            preview.setForeground(Theme.PRIMARY);
        } else {
            preview.setText(c.preview);
            preview.setFont(Theme.font(c.unread > 0 ? Font.BOLD : Font.PLAIN, 13));
            preview.setForeground(c.unread > 0 ? Theme.TEXT : Theme.TEXT_MUTED);
        }
        badge.count = c.unread;

        setOpaque(true);
        setBackground(selected ? Theme.SELECTED : index == hoverIndex ? Theme.HOVER : java.awt.Color.WHITE);
        return this;
    }

    /** Chấm đỏ đếm số tin chưa đọc (hiện "5+" nếu nhiều hơn 5, giống Zalo). */
    private static class Badge extends JComponent {
        int count;

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(count > 0 ? 24 : 0, 18);
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (count <= 0) {
                return;
            }
            Graphics2D g2 = Theme.smooth(g);
            String text = count > 5 ? "5+" : String.valueOf(count);
            g2.setFont(Theme.font(Font.BOLD, 11));
            FontMetrics fm = g2.getFontMetrics();
            int w = Math.max(18, fm.stringWidth(text) + 10);
            int x = getWidth() - w;
            int y = (getHeight() - 18) / 2;
            g2.setColor(Theme.BADGE);
            g2.fillRoundRect(x, y, w, 18, 18, 18);
            g2.setColor(java.awt.Color.WHITE);
            g2.drawString(text, x + (w - fm.stringWidth(text)) / 2, y + (18 - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }
}
