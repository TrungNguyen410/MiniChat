package minichat.client.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;

import minichat.common.Protocol;

/**
 * Hộp thoại "Tạo nhóm" (nhập tên nhóm + chọn thành viên) hoặc "Thêm thành viên" (chỉ chọn thành viên).
 */
public class GroupDialog extends JDialog {

    private final PlaceholderTextField nameField;
    private final Set<String> selected = new LinkedHashSet<>();
    private final JLabel error = new JLabel(" ");
    private boolean confirmed;

    private GroupDialog(Window owner, String title, boolean askName, List<String> users, String confirmText) {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBackground(Color.WHITE);
        root.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JPanel north = new JPanel(new BorderLayout(0, 10));
        north.setOpaque(false);
        if (askName) {
            nameField = new PlaceholderTextField("Nhập tên nhóm (không dấu cách)...");
            nameField.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
                    BorderFactory.createEmptyBorder(8, 4, 8, 4)));
            north.add(nameField, BorderLayout.NORTH);
        } else {
            nameField = null;
        }
        JLabel hint = new JLabel(users.isEmpty() ? "Không có bạn nào khác đang online" : "Chọn bạn bè đang online:");
        hint.setFont(Theme.font(Font.BOLD, 13));
        hint.setForeground(Theme.TEXT);
        north.add(hint, BorderLayout.SOUTH);
        root.add(north, BorderLayout.NORTH);

        DefaultListModel<String> model = new DefaultListModel<>();
        users.forEach(model::addElement);
        JList<String> list = new JList<>(model);
        list.setCellRenderer(new CheckRenderer());
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int index = list.locationToIndex(e.getPoint());
                if (index >= 0 && list.getCellBounds(index, index).contains(e.getPoint())) {
                    String user = model.get(index);
                    if (!selected.remove(user)) {
                        selected.add(user);
                    }
                    list.repaint();
                }
            }
        });
        root.add(Theme.scroll(list), BorderLayout.CENTER);

        error.setForeground(Theme.BADGE);
        error.setFont(Theme.font(Font.PLAIN, 12));
        JButton cancel = Theme.secondaryButton("Hủy");
        cancel.addActionListener(e -> dispose());
        JButton ok = Theme.primaryButton(confirmText);
        ok.addActionListener(e -> confirm(askName));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(cancel);
        buttons.add(ok);
        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.add(error, BorderLayout.NORTH);
        south.add(buttons, BorderLayout.SOUTH);
        root.add(south, BorderLayout.SOUTH);

        setContentPane(root);
        getRootPane().setDefaultButton(ok);
        setSize(380, 460);
        setLocationRelativeTo(owner);
    }

    private void confirm(boolean askName) {
        if (askName && !Protocol.isValidName(getGroupName())) {
            error.setText("Tên nhóm 1-20 ký tự, không chứa khoảng trắng");
            return;
        }
        if (!askName && selected.isEmpty()) {
            error.setText("Hãy chọn ít nhất một người");
            return;
        }
        confirmed = true;
        dispose();
    }

    public String getGroupName() {
        return nameField == null ? null : nameField.getText().trim();
    }

    public List<String> getSelectedUsers() {
        return new ArrayList<>(selected);
    }

    /** Hiện hộp thoại tạo nhóm; trả về null nếu người dùng bấm Hủy. */
    public static GroupDialog createGroup(Window owner, List<String> users) {
        GroupDialog dialog = new GroupDialog(owner, "Tạo nhóm", true, users, "Tạo nhóm");
        dialog.setVisible(true);
        return dialog.confirmed ? dialog : null;
    }

    /** Hiện hộp thoại chọn thành viên để thêm vào nhóm; trả về null nếu bấm Hủy. */
    public static List<String> pickMembers(Window owner, String group, List<String> users) {
        GroupDialog dialog = new GroupDialog(owner, "Thêm thành viên vào " + group, false, users, "Thêm");
        dialog.setVisible(true);
        return dialog.confirmed ? dialog.getSelectedUsers() : null;
    }

    private class CheckRenderer extends JPanel implements ListCellRenderer<String> {
        private final JCheckBox check = new JCheckBox();
        private final JLabel label = new JLabel();

        CheckRenderer() {
            super(new BorderLayout(10, 0));
            setBorder(BorderFactory.createEmptyBorder(6, 4, 6, 4));
            check.setOpaque(false);
            label.setFont(Theme.font(Font.PLAIN, 14));
            label.setForeground(Theme.TEXT);
            label.setIconTextGap(10);
            add(check, BorderLayout.WEST);
            add(label, BorderLayout.CENTER);
            setPreferredSize(new Dimension(0, 52));
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends String> list, String user, int index,
                boolean isSelected, boolean focus) {
            check.setSelected(selected.contains(user));
            label.setText(user);
            label.setIcon(Avatar.of(user, 36, true));
            setBackground(check.isSelected() ? Theme.SELECTED : Color.WHITE);
            return this;
        }
    }
}
