package minichat.client.ui;

import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.JTextField;

/** Ô nhập có dòng chữ gợi ý mờ khi chưa nhập gì. */
public class PlaceholderTextField extends JTextField {

    private String placeholder;

    public PlaceholderTextField(String placeholder) {
        this.placeholder = placeholder;
        setFont(Theme.font(Font.PLAIN, 14));
        setForeground(Theme.TEXT);
        setCaretColor(Theme.PRIMARY);
        setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
    }

    public void setPlaceholder(String placeholder) {
        this.placeholder = placeholder;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (getText().isEmpty() && placeholder != null) {
            Graphics2D g2 = Theme.smooth(g);
            g2.setColor(Theme.TEXT_MUTED);
            g2.setFont(getFont());
            Insets in = getInsets();
            int y = (getHeight() - g2.getFontMetrics().getHeight()) / 2 + g2.getFontMetrics().getAscent();
            g2.drawString(placeholder, in.left + 2, y);
            g2.dispose();
        }
    }
}
