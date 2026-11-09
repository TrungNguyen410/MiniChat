package minichat.client.ui;

import java.awt.Font;
import java.awt.GridLayout;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;

/** Bảng chọn emoji bật lên phía trên ô nhập tin nhắn. */
public class EmojiPicker extends JPopupMenu {

    private static final String[] EMOJIS = {
        "😀", "😁", "😂", "🤣", "😊", "😍", "😘", "😎",
        "🤔", "😅", "😉", "😋", "😴", "😱", "😢", "😭",
        "😡", "🥰", "🤗", "😇", "🙄", "😬", "😏", "🤩",
        "👍", "👎", "👏", "🙏", "💪", "👋", "✌", "👌",
        "❤", "💔", "🎉", "🔥", "⭐", "🌹", "🎂", "✅"
    };

    public EmojiPicker(Consumer<String> onPick) {
        JPanel grid = new JPanel(new GridLayout(0, 8, 2, 2));
        grid.setBackground(java.awt.Color.WHITE);
        grid.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        for (String emoji : EMOJIS) {
            JButton button = new FlatButton(emoji, null, null, Theme.HOVER, Theme.TEXT);
            button.setFont(Theme.font(Font.PLAIN, 22));
            button.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
            button.addActionListener(e -> {
                onPick.accept(emoji);
                setVisible(false);
            });
            grid.add(button);
        }
        setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        add(grid);
    }
}
