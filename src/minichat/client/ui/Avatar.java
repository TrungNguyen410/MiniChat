package minichat.client.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;

import javax.swing.Icon;

/** Avatar tròn có chữ cái đầu tên, màu cố định theo tên; có chấm xanh khi đang online. */
public class Avatar implements Icon {

    private static final Color[] PALETTE = {
        new Color(0x1E88E5), new Color(0x43A047), new Color(0xF4511E), new Color(0x8E24AA),
        new Color(0x00897B), new Color(0xD81B60), new Color(0x3949AB), new Color(0xFB8C00),
        new Color(0x6D4C41), new Color(0x546E7A)
    };

    private final String name;
    private final int size;
    private final boolean online;
    private final boolean everyone;

    private Avatar(String name, int size, boolean online, boolean everyone) {
        this.name = name;
        this.size = size;
        this.online = online;
        this.everyone = everyone;
    }

    public static Avatar of(String name, int size) {
        return new Avatar(name, size, false, false);
    }

    public static Avatar of(String name, int size, boolean online) {
        return new Avatar(name, size, online, false);
    }

    /** Avatar của cuộc trò chuyện "Chat chung". */
    public static Avatar everyone(int size) {
        return new Avatar("*", size, false, true);
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = Theme.smooth(g);
        g2.translate(x, y);
        g2.setColor(everyone ? Theme.PRIMARY : PALETTE[Math.floorMod(name.hashCode(), PALETTE.length)]);
        g2.fillOval(0, 0, size, size);

        if (everyone) {
            int inner = (int) (size * 0.6);
            Icons.get(Icons.Type.CONTACTS, inner, Color.WHITE).paintIcon(c, g2, (size - inner) / 2, (size - inner) / 2);
        } else {
            String initials = initials(name);
            g2.setColor(Color.WHITE);
            g2.setFont(Theme.font(Font.BOLD, Math.max(10, (int) (size * (initials.length() > 1 ? 0.36 : 0.42)))));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(initials, (size - fm.stringWidth(initials)) / 2, (size - fm.getHeight()) / 2 + fm.getAscent());
        }

        if (online) {
            int dot = Math.max(8, size / 4);
            int pos = size - dot;
            g2.setColor(Theme.ONLINE);
            g2.fillOval(pos, pos, dot, dot);
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(2f));
            g2.drawOval(pos, pos, dot, dot);
        }
        g2.dispose();
    }

    static String initials(String name) {
        String[] words = name.trim().split("[\\s_.\\-]+");
        if (words.length >= 2 && !words[0].isEmpty() && !words[words.length - 1].isEmpty()) {
            return (words[0].substring(0, 1) + words[words.length - 1].substring(0, 1)).toUpperCase();
        }
        return name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
    }

    @Override
    public int getIconWidth() {
        return size;
    }

    @Override
    public int getIconHeight() {
        return size;
    }
}
