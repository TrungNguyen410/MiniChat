package minichat.client.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;

import minichat.client.ChatConnection;
import minichat.common.Protocol;

/** Màn hình đăng nhập / đăng ký tài khoản MiniChat. */
public class LoginDialog extends JDialog {

    private final PlaceholderTextField nameField = new PlaceholderTextField("Tên đăng nhập (không dấu cách)");
    private final JPasswordField passwordField = new JPasswordField();
    private final JPasswordField confirmField = new JPasswordField();
    private final PlaceholderTextField hostField = new PlaceholderTextField("Địa chỉ server");
    private final PlaceholderTextField portField = new PlaceholderTextField("Cổng");
    private final JLabel error = new JLabel(" ");
    private final JButton submitButton = Theme.primaryButton("Đăng nhập");
    private final JLabel switchLink = new JLabel();
    private final JPanel confirmPanel;
    private final JComponent confirmGap = (JComponent) Box.createVerticalStrut(12);
    private boolean registerMode;
    private ChatConnection connection;

    private LoginDialog(String host, int port) {
        super((java.awt.Frame) null, "MiniChat - Đăng nhập", true);
        setIconImage(Theme.appImage(64));
        hostField.setText(host);
        portField.setText(String.valueOf(port));

        JPanel header = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setPaint(new GradientPaint(0, 0, Theme.PRIMARY, getWidth(), getHeight(), new Color(0x00A2FF)));
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        header.setPreferredSize(new Dimension(0, 170));
        JLabel logo = new JLabel(new ImageIcon(Theme.appImage(56)), SwingConstants.CENTER);
        JLabel title = new JLabel("MiniChat", SwingConstants.CENTER);
        title.setFont(Theme.font(Font.BOLD, 30));
        title.setForeground(Color.WHITE);
        JLabel subtitle = new JLabel("Kết nối và trò chuyện cùng bạn bè", SwingConstants.CENTER);
        subtitle.setFont(Theme.font(Font.PLAIN, 13));
        subtitle.setForeground(new Color(0xE0ECFF));
        JPanel brand = new JPanel();
        brand.setOpaque(false);
        brand.setLayout(new BoxLayout(brand, BoxLayout.Y_AXIS));
        brand.setBorder(BorderFactory.createEmptyBorder(22, 0, 0, 0));
        for (JComponent c : new JComponent[] {logo, title, subtitle}) {
            c.setAlignmentX(Component.CENTER_ALIGNMENT);
            c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height + 4));
            brand.add(c);
            brand.add(Box.createVerticalStrut(4));
        }
        header.add(brand, BorderLayout.CENTER);

        for (JPasswordField field : new JPasswordField[] {passwordField, confirmField}) {
            field.setFont(Theme.font(Font.PLAIN, 14));
            field.setForeground(Theme.TEXT);
            field.setCaretColor(Theme.PRIMARY);
        }

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBackground(Color.WHITE);
        form.setBorder(BorderFactory.createEmptyBorder(22, 32, 20, 32));
        form.add(field("Tên đăng nhập", nameField));
        form.add(Box.createVerticalStrut(12));
        form.add(field("Mật khẩu", passwordField));
        form.add(confirmGap);
        confirmPanel = field("Nhập lại mật khẩu", confirmField);
        form.add(confirmPanel);
        form.add(Box.createVerticalStrut(12));
        JPanel serverRow = new JPanel(new BorderLayout(10, 0));
        serverRow.setOpaque(false);
        serverRow.add(field("Server", hostField), BorderLayout.CENTER);
        JPanel portPanel = field("Cổng", portField);
        portPanel.setPreferredSize(new Dimension(90, portPanel.getPreferredSize().height));
        serverRow.add(portPanel, BorderLayout.EAST);
        serverRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        serverRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, serverRow.getPreferredSize().height));
        form.add(serverRow);
        form.add(Box.createVerticalStrut(8));
        error.setForeground(Theme.BADGE);
        error.setFont(Theme.font(Font.PLAIN, 12));
        error.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(error);
        form.add(Box.createVerticalStrut(10));
        submitButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        submitButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        submitButton.addActionListener(e -> submit());
        form.add(submitButton);
        form.add(Box.createVerticalStrut(12));
        switchLink.setFont(Theme.font(Font.PLAIN, 13));
        switchLink.setForeground(Theme.PRIMARY);
        switchLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        switchLink.setAlignmentX(Component.LEFT_ALIGNMENT);
        switchLink.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (submitButton.isEnabled()) {
                    setRegisterMode(!registerMode);
                }
            }
        });
        form.add(switchLink);

        JPanel root = new JPanel(new BorderLayout());
        root.add(header, BorderLayout.NORTH);
        root.add(form, BorderLayout.CENTER);
        setContentPane(root);
        getRootPane().setDefaultButton(submitButton);
        setResizable(false);
        setRegisterMode(false);
        setLocationRelativeTo(null);
    }

    /** Chuyển giữa chế độ Đăng nhập và Đăng ký (hiện thêm ô nhập lại mật khẩu). */
    private void setRegisterMode(boolean register) {
        registerMode = register;
        setTitle(register ? "MiniChat - Đăng ký tài khoản" : "MiniChat - Đăng nhập");
        submitButton.setText(register ? "Đăng ký" : "Đăng nhập");
        switchLink.setText(register ? "Đã có tài khoản? Đăng nhập" : "Chưa có tài khoản? Đăng ký ngay");
        confirmPanel.setVisible(register);
        confirmGap.setVisible(register);
        confirmField.setText("");
        error.setText(" ");
        pack();
        setSize(400, getHeight());
        validate();
    }

    private static JPanel field(String label, JTextField input) {
        JLabel title = new JLabel(label);
        title.setFont(Theme.font(Font.BOLD, 12));
        title.setForeground(Theme.TEXT_MUTED);
        input.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
                BorderFactory.createEmptyBorder(8, 2, 8, 2)));
        JPanel panel = new JPanel(new BorderLayout(0, 2));
        panel.setOpaque(false);
        panel.add(title, BorderLayout.NORTH);
        panel.add(input, BorderLayout.CENTER);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
        return panel;
    }

    private void submit() {
        String name = nameField.getText().trim();
        String password = new String(passwordField.getPassword());
        String host = hostField.getText().trim();
        boolean register = registerMode;
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            error.setText("Cổng không hợp lệ");
            return;
        }
        if (name.isEmpty()) {
            error.setText("Hãy nhập tên đăng nhập");
            return;
        }
        if (password.isEmpty()) {
            error.setText("Hãy nhập mật khẩu");
            return;
        }
        if (register) {
            if (!Protocol.isValidName(name)) {
                error.setText("Tên 1-20 ký tự, chỉ gồm chữ, số, _ - .");
                return;
            }
            if (!Protocol.isValidPassword(password)) {
                error.setText("Mật khẩu phải từ " + Protocol.PASSWORD_MIN + " đến " + Protocol.PASSWORD_MAX + " ký tự");
                return;
            }
            if (!password.equals(new String(confirmField.getPassword()))) {
                error.setText("Mật khẩu nhập lại không khớp");
                return;
            }
        }
        submitButton.setEnabled(false);
        submitButton.setText(register ? "Đang đăng ký..." : "Đang kết nối...");
        error.setText(" ");
        new SwingWorker<ChatConnection, Void>() {
            @Override
            protected ChatConnection doInBackground() throws IOException {
                return ChatConnection.connect(host, port, name, password, register);
            }

            @Override
            protected void done() {
                try {
                    connection = get();
                    dispose();
                } catch (InterruptedException | ExecutionException e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    String message = cause.getMessage();
                    if (cause instanceof java.net.ConnectException || cause instanceof java.net.UnknownHostException) {
                        message = "Không kết nối được tới server " + host + ":" + port;
                    }
                    error.setText(message);
                    submitButton.setEnabled(true);
                    submitButton.setText(registerMode ? "Đăng ký" : "Đăng nhập");
                }
            }
        }.execute();
    }

    /** Hiện màn hình đăng nhập; trả về kết nối đã đăng nhập, hoặc null nếu người dùng đóng cửa sổ. */
    public static ChatConnection show(String host, int port) {
        LoginDialog dialog = new LoginDialog(host, port);
        dialog.setVisible(true);
        return dialog.connection;
    }
}
