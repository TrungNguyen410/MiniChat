package minichat.client.ui;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;

/** Nút phẳng bo góc, đổi màu nền khi rê chuột. */
public class FlatButton extends JButton {

    private final Color normal;
    private final Color hover;
    private int arc = 8;

    public FlatButton(String text, Icon icon, Color normal, Color hover, Color foreground) {
        super(text, icon);
        this.normal = normal;
        this.hover = hover;
        setForeground(foreground);
        setFont(Theme.font(java.awt.Font.PLAIN, 13));
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setRolloverEnabled(true);
        setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    public FlatButton withArc(int arc) {
        this.arc = arc;
        return this;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Color background = getModel().isRollover() || getModel().isPressed() ? hover : normal;
        if (background != null && isEnabled()) {
            Graphics2D g2 = Theme.smooth(g);
            g2.setColor(background);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
            g2.dispose();
        }
        super.paintComponent(g);
    }
}
