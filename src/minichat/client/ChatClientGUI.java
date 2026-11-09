package minichat.client;

import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;

import minichat.client.ui.Avatar;
import minichat.client.ui.ChatMessage;
import minichat.client.ui.Conversation;
import minichat.client.ui.ConversationRenderer;
import minichat.client.ui.EmojiPicker;
import minichat.client.ui.FlatButton;
import minichat.client.ui.GroupDialog;
import minichat.client.ui.Icons;
import minichat.client.ui.LoginDialog;
import minichat.client.ui.PlaceholderTextField;
import minichat.client.ui.RoundedPanel;
import minichat.client.ui.Theme;
import minichat.common.Presence;
import minichat.common.Protocol;

/**
 * MiniChat Client có giao diện theo phong cách Zalo: chat chung, chat nhóm, chat riêng,
 * gửi emoji / ảnh / file, trạng thái đang soạn tin và thông báo tin nhắn mới,
 * lịch sử tin nhắn, trạng thái hoạt động của bạn bè và trạng thái tin nhắn
 * (Đang gửi / Đã gửi / Đã nhận / Đã xem / Gửi thất bại).
 */
public class ChatClientGUI extends JFrame implements ChatConnection.Listener {

    private static final String CHATS = "chats";
    private static final String CONTACTS = "contacts";

    private final ChatConnection connection;
    private final String me;
    private final String host;
    private final int port;

    private final Map<String, Conversation> conversations = new HashMap<>();
    private final List<String> onlineUsers = new ArrayList<>();
    /** Mọi tài khoản đã đăng ký (kể cả offline). */
    private final List<String> accounts = new ArrayList<>();
    /** Thời điểm truy cập cuối (giờ server) của các tài khoản đang offline. */
    private final Map<String, Long> lastSeen = new HashMap<>();
    /** Tin do mình gửi đang chờ server xác nhận (theo mã tạm cid). */
    private final Map<String, Sent> pending = new HashMap<>();
    /** Tin do mình gửi đã được server xác nhận (theo id), để cập nhật trạng thái khi có STATUS. */
    private final Map<Long, Sent> sent = new HashMap<>();
    private int nextCid;
    private boolean refreshScheduled;
    private final Map<String, Integer> groupSizes = new TreeMap<>();
    private final Set<String> myGroups = new HashSet<>();
    /** Nhóm mà chính user này vừa yêu cầu tạo / tham gia: mở ngay khi server xác nhận. */
    private final Set<String> pendingOpen = new HashSet<>();
    private Conversation current;
    private boolean updatingList;
    private boolean closing;
    private long lastTypingSent;

    // Cột trái
    private final DefaultListModel<Conversation> conversationModel = new DefaultListModel<>();
    private final JList<Conversation> conversationList = new JList<>(conversationModel) {
        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true; // không rộng hơn khung nhìn, chữ dài sẽ được rút gọn "..."
        }
    };
    private final ConversationRenderer conversationRenderer = new ConversationRenderer(c -> avatarFor(c, 48));
    private final DefaultListModel<Contact> contactModel = new DefaultListModel<>();
    private final JList<Contact> contactList = new JList<>(contactModel) {
        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }
    };
    private final PlaceholderTextField searchField = new PlaceholderTextField("Tìm kiếm");
    private final CardLayout leftCards = new CardLayout();
    private final JPanel leftContent = new JPanel(leftCards);
    private final SidebarButton chatsTab = new SidebarButton(Icons.Type.CHAT, "Tin nhắn");
    private final SidebarButton contactsTab = new SidebarButton(Icons.Type.CONTACTS, "Danh bạ");

    // Khung chat
    private final JLabel headerAvatar = new JLabel();
    private final JLabel headerTitle = new JLabel();
    private final JLabel headerSubtitle = new JLabel();
    private final JButton addMemberButton = Theme.iconButton(Icons.get(Icons.Type.ADD_USER, 22, Theme.TEXT), "Thêm thành viên");
    private final JButton leaveButton = Theme.iconButton(Icons.get(Icons.Type.LOGOUT, 22, Theme.BADGE), "Rời nhóm");
    private final JScrollPane chatScroll = Theme.scroll(new JPanel());
    private final JLabel typingLabel = new JLabel(" ");
    private final PlaceholderTextField input = new PlaceholderTextField("");
    private final FlatButton sendButton = new FlatButton(null, null, null, Theme.HOVER, Theme.PRIMARY);
    private final EmojiPicker emojiPicker = new EmojiPicker(this::insertEmoji);
    private final Timer typingTimer = new Timer(1000, e -> refreshTyping());
    /** Cập nhật "Truy cập N phút trước" mà người dùng không cần làm gì. */
    private final Timer presenceTimer = new Timer(30_000, e -> refreshPresence());
    private TrayIcon trayIcon;

    public ChatClientGUI(ChatConnection connection, String host, int port) {
        super("MiniChat - " + connection.getName());
        this.connection = connection;
        this.me = connection.getName();
        this.host = host;
        this.port = port;
        setIconImage(Theme.appImage(64));
        buildUi();
        setupTray();

        Conversation all = conversation(Protocol.ALL, Protocol.ALL_TARGET);
        all.preview = "Trò chuyện với mọi người";
        all.lastTime = LocalDateTime.now();
        select(all);

        typingTimer.start();
        presenceTimer.start();
        connection.startListening(this);
    }

    // ================================================================ giao diện

    private void buildUi() {
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdown();
                System.exit(0);
            }

            @Override
            public void windowActivated(WindowEvent e) {
                updateTitle();
                reportSeen(current); // mở lại cửa sổ = đã xem cuộc trò chuyện đang mở
            }
        });

        JPanel root = new JPanel(new BorderLayout());
        root.add(buildSidebar(), BorderLayout.WEST);
        JPanel main = new JPanel(new BorderLayout());
        main.add(buildLeftPanel(), BorderLayout.WEST);
        main.add(buildChatPanel(), BorderLayout.CENTER);
        root.add(main, BorderLayout.CENTER);
        setContentPane(root);
        setMinimumSize(new Dimension(900, 560));
        setSize(1180, 740);
        setLocationRelativeTo(null);
    }

    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBackground(Theme.PRIMARY);
        sidebar.setBorder(BorderFactory.createEmptyBorder(18, 8, 14, 8));
        sidebar.setPreferredSize(new Dimension(64, 0));

        JLabel myAvatar = new JLabel(Avatar.of(me, 44, true));
        myAvatar.setToolTipText(me + " (bạn)");
        myAvatar.setAlignmentX(Component.CENTER_ALIGNMENT);
        SidebarButton logout = new SidebarButton(Icons.Type.LOGOUT, "Đăng xuất");

        chatsTab.addActionListener(e -> showTab(CHATS));
        contactsTab.addActionListener(e -> showTab(CONTACTS));
        logout.addActionListener(e -> logout());

        sidebar.add(myAvatar);
        sidebar.add(Box.createVerticalStrut(22));
        sidebar.add(chatsTab);
        sidebar.add(Box.createVerticalStrut(6));
        sidebar.add(contactsTab);
        sidebar.add(Box.createVerticalGlue());
        sidebar.add(logout);
        return sidebar;
    }

    private JPanel buildLeftPanel() {
        RoundedPanel searchBox = new RoundedPanel(new BorderLayout(), Theme.FIELD_BG, null, 10);
        JLabel searchIcon = new JLabel(Icons.get(Icons.Type.SEARCH, 16, Theme.TEXT_MUTED));
        searchIcon.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));
        searchField.setOpaque(false);
        searchField.setBorder(BorderFactory.createEmptyBorder(7, 8, 7, 8));
        searchField.getDocument().addDocumentListener(onChange(() -> {
            if (!searchField.getText().isEmpty()) {
                showTab(CHATS);
            }
            refreshConversationList();
        }));
        searchBox.add(searchIcon, BorderLayout.WEST);
        searchBox.add(searchField, BorderLayout.CENTER);
        JButton newGroup = Theme.iconButton(Icons.get(Icons.Type.ADD_GROUP, 22, Theme.TEXT), "Tạo nhóm chat");
        newGroup.addActionListener(e -> createGroup());
        JPanel searchRow = new JPanel(new BorderLayout(8, 0));
        searchRow.setBackground(Color.WHITE);
        searchRow.setBorder(BorderFactory.createEmptyBorder(14, 14, 10, 10));
        searchRow.add(searchBox, BorderLayout.CENTER);
        searchRow.add(newGroup, BorderLayout.EAST);

        conversationList.setCellRenderer(conversationRenderer);
        conversationList.setFixedCellHeight(72);
        conversationList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        conversationList.addListSelectionListener(e -> {
            Conversation selected = conversationList.getSelectedValue();
            if (!updatingList && !e.getValueIsAdjusting() && selected != null && selected != current) {
                select(selected);
            }
        });
        MouseAdapter hover = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                conversationRenderer.setHoverIndex(conversationList.locationToIndex(e.getPoint()));
                conversationList.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                conversationRenderer.setHoverIndex(-1);
                conversationList.repaint();
            }
        };
        conversationList.addMouseMotionListener(hover);
        conversationList.addMouseListener(hover);

        contactList.setCellRenderer(new ContactRenderer());
        contactList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        contactList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int index = contactList.locationToIndex(e.getPoint());
                if (index >= 0 && contactList.getCellBounds(index, index).contains(e.getPoint())) {
                    openContact(contactModel.get(index));
                }
                contactList.clearSelection();
            }
        });

        leftContent.add(Theme.scroll(conversationList), CHATS);
        leftContent.add(Theme.scroll(contactList), CONTACTS);

        JPanel left = new JPanel(new BorderLayout());
        left.setBackground(Color.WHITE);
        left.setPreferredSize(new Dimension(340, 0));
        left.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.BORDER));
        left.add(searchRow, BorderLayout.NORTH);
        left.add(leftContent, BorderLayout.CENTER);
        showTab(CHATS);
        return left;
    }

    private JPanel buildChatPanel() {
        // Tiêu đề cuộc trò chuyện
        headerTitle.setFont(Theme.font(Font.BOLD, 17));
        headerTitle.setForeground(Theme.TEXT);
        headerSubtitle.setFont(Theme.font(Font.PLAIN, 13));
        JPanel titles = new JPanel(new GridLayout(2, 1, 0, 2));
        titles.setOpaque(false);
        titles.add(headerTitle);
        titles.add(headerSubtitle);
        addMemberButton.addActionListener(e -> addMembers());
        leaveButton.addActionListener(e -> leaveGroup());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        actions.setOpaque(false);
        actions.add(addMemberButton);
        actions.add(leaveButton);
        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setBackground(Color.WHITE);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
                BorderFactory.createEmptyBorder(10, 16, 10, 12)));
        header.add(headerAvatar, BorderLayout.WEST);
        header.add(titles, BorderLayout.CENTER);
        header.add(actions, BorderLayout.EAST);

        chatScroll.getViewport().setBackground(Theme.CHAT_BG);

        // Thanh công cụ: emoji, ảnh, file + trạng thái đang soạn tin
        JButton emojiButton = Theme.iconButton(Icons.get(Icons.Type.EMOJI, 22, Theme.TEXT), "Biểu cảm");
        emojiButton.addActionListener(e -> emojiPicker.show(emojiButton, 0, -emojiPicker.getPreferredSize().height - 4));
        JButton imageButton = Theme.iconButton(Icons.get(Icons.Type.IMAGE, 22, Theme.TEXT), "Gửi hình ảnh");
        imageButton.addActionListener(e -> chooseAndSendFile(true));
        JButton fileButton = Theme.iconButton(Icons.get(Icons.Type.FILE, 22, Theme.TEXT), "Gửi file");
        fileButton.addActionListener(e -> chooseAndSendFile(false));
        JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        tools.setOpaque(false);
        tools.add(emojiButton);
        tools.add(imageButton);
        tools.add(fileButton);
        typingLabel.setFont(Theme.font(Font.ITALIC, 12));
        typingLabel.setForeground(Theme.PRIMARY);
        typingLabel.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 0));
        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setOpaque(false);
        toolbar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        toolbar.add(tools, BorderLayout.WEST);
        toolbar.add(typingLabel, BorderLayout.CENTER);

        // Ô nhập tin nhắn
        input.setBorder(BorderFactory.createEmptyBorder(10, 4, 10, 4));
        input.setFont(Theme.font(Font.PLAIN, 15));
        input.addActionListener(e -> sendMessage());
        input.getDocument().addDocumentListener(onChange(this::onInputChanged));
        sendButton.setPreferredSize(new Dimension(52, 44));
        sendButton.setBorder(BorderFactory.createEmptyBorder());
        sendButton.addActionListener(e -> sendMessage());
        updateSendButton();
        JPanel inputRow = new JPanel(new BorderLayout(8, 0));
        inputRow.setOpaque(false);
        inputRow.setBorder(BorderFactory.createEmptyBorder(6, 14, 8, 10));
        inputRow.add(input, BorderLayout.CENTER);
        inputRow.add(sendButton, BorderLayout.EAST);

        JPanel composer = new JPanel(new BorderLayout());
        composer.setBackground(Color.WHITE);
        composer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER));
        composer.add(toolbar, BorderLayout.NORTH);
        composer.add(inputRow, BorderLayout.CENTER);

        JPanel chat = new JPanel(new BorderLayout());
        chat.add(header, BorderLayout.NORTH);
        chat.add(chatScroll, BorderLayout.CENTER);
        chat.add(composer, BorderLayout.SOUTH);
        return chat;
    }

    private void setupTray() {
        if (!SystemTray.isSupported()) {
            return;
        }
        trayIcon = new TrayIcon(Theme.appImage(16), "MiniChat - " + me);
        trayIcon.setImageAutoSize(true);
        trayIcon.addActionListener(e -> bringToFront());
        try {
            SystemTray.getSystemTray().add(trayIcon);
        } catch (AWTException e) {
            trayIcon = null;
        }
    }

    // ======================================================== cuộc trò chuyện

    private Conversation conversation(String kind, String target) {
        return conversations.computeIfAbsent(Conversation.key(kind, target), k -> {
            Conversation c = new Conversation(kind, target, me);
            SwingUtilities.invokeLater(this::refreshConversationList);
            return c;
        });
    }

    private void select(Conversation c) {
        current = c;
        c.unread = 0;
        chatScroll.setViewportView(c.messages);
        input.setPlaceholder("Nhập tin nhắn tới " + c.title());
        updateHeader();
        if (!searchField.getText().isEmpty()) {
            searchField.setText("");
        }
        showTab(CHATS);
        refreshConversationList();
        refreshTyping();
        updateTitle();
        scrollToBottom();
        input.requestFocusInWindow();
        reportSeen(c);
    }

    /**
     * Báo server "đã xem" cuộc trò chuyện đang mở, khi cửa sổ MiniChat đang được chọn
     * (chat chung không theo dõi đã xem).
     */
    private void reportSeen(Conversation c) {
        if (c == null || c != current || c.isAll() || !isActive() || closing) {
            return;
        }
        if (c.lastIncomingId > c.seenReported) {
            connection.sendSeen(c.kind, c.target, c.lastIncomingId);
            c.seenReported = c.lastIncomingId;
        }
    }

    /** Gom nhiều lần cập nhật danh sách cuộc trò chuyện (vd: khi nhận lịch sử) thành một lần. */
    private void scheduleConversationRefresh() {
        if (!refreshScheduled) {
            refreshScheduled = true;
            SwingUtilities.invokeLater(() -> {
                refreshScheduled = false;
                refreshConversationList();
                updateTitle();
            });
        }
    }

    private void refreshConversationList() {
        String query = Theme.fold(searchField.getText().trim());
        List<Conversation> visible = conversations.values().stream()
                .filter(c -> query.isEmpty() || Theme.fold(c.title()).contains(query))
                .sorted(Comparator.comparing((Conversation c) -> c.lastTime,
                        Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(Conversation::title))
                .collect(Collectors.toList());
        updatingList = true;
        conversationModel.clear();
        conversationModel.addAll(visible);
        if (current != null && visible.contains(current)) {
            conversationList.setSelectedValue(current, false);
        }
        updatingList = false;
    }

    private void refreshContacts() {
        contactModel.clear();
        List<String> friends = friends();
        long online = friends.stream().filter(this::isOnline).count();
        contactModel.addElement(new Contact(Contact.HEADER, "Bạn bè (" + friends.size() + ") · " + online
                + " đang hoạt động"));
        friends.forEach(u -> contactModel.addElement(new Contact(Contact.USER, u)));
        contactModel.addElement(new Contact(Contact.HEADER, "Nhóm (" + groupSizes.size() + ")"));
        groupSizes.keySet().forEach(g -> contactModel.addElement(new Contact(Contact.GROUP, g)));
    }

    private void showTab(String tab) {
        leftCards.show(leftContent, tab);
        chatsTab.setActive(tab.equals(CHATS));
        contactsTab.setActive(tab.equals(CONTACTS));
    }

    private void updateHeader() {
        if (current == null) {
            return;
        }
        headerAvatar.setIcon(avatarFor(current, 46));
        headerTitle.setText(current.title());
        headerSubtitle.setForeground(Theme.TEXT_MUTED);
        if (current.isAll()) {
            headerSubtitle.setText(onlineUsers.size() + " người đang online");
        } else if (current.isGroup()) {
            headerSubtitle.setText(groupSizes.getOrDefault(current.target, 0) + " thành viên");
        } else {
            headerSubtitle.setText(presenceText(current.target));
            if (isOnline(current.target)) {
                headerSubtitle.setForeground(Theme.ONLINE);
            }
        }
        addMemberButton.setVisible(current.isGroup());
        leaveButton.setVisible(current.isGroup());
    }

    private Icon avatarFor(Conversation c, int size) {
        if (c.isAll()) {
            return Avatar.everyone(size);
        }
        return Avatar.of(c.target, size, c.isPrivate() && onlineUsers.contains(c.target));
    }

    private boolean isOnline(String user) {
        return onlineUsers.contains(user);
    }

    /** "Đang hoạt động" hoặc "Truy cập N phút trước" (tính theo giờ server). */
    private String presenceText(String user) {
        if (isOnline(user)) {
            return Presence.ONLINE_TEXT;
        }
        Long seen = lastSeen.get(user);
        return seen == null ? "Không trực tuyến" : Presence.describe(seen, connection.serverNow());
    }

    private void refreshPresence() {
        updateHeader();
        contactList.repaint();
    }

    /**
     * Thêm một tin vào cuộc trò chuyện. {@code flag}: N = tin mới (có thể báo thông báo),
     * U = lịch sử chưa đọc (tăng số chưa đọc), R = lịch sử đã đọc.
     */
    private void receive(Conversation c, ChatMessage message, String flag) {
        boolean live = Protocol.FLAG_NEW.equals(flag);
        boolean system = message.kind == ChatMessage.Kind.SYSTEM;
        boolean mine = me.equals(message.from);
        if (mine && message.status == null) {
            // tin của mình trong lịch sử / từ lệnh kiểu cũ: server đã lưu nên ít nhất là "Đã gửi"
            message.status = ChatMessage.Status.SENT;
            if (message.id > 0) {
                sent.put(message.id, new Sent(c, message));
            }
        }
        c.messages.add(message);
        if (!mine && !system && message.id > 0) {
            c.lastIncomingId = Math.max(c.lastIncomingId, message.id);
            if (Protocol.FLAG_READ.equals(flag)) {
                c.seenReported = Math.max(c.seenReported, message.id);
            }
        }
        if (system) {
            c.preview = message.text;
            if (c.lastTime == null) {
                c.lastTime = message.time;
            }
        } else {
            String summary = message.summary();
            c.preview = mine ? "Bạn: " + summary : c.isPrivate() ? summary : message.from + ": " + summary;
            c.lastTime = message.time;
            if (message.from.equals(c.typingUser)) {
                c.typingUntil = 0;
            }
        }
        boolean countable = !mine && !system && (live || Protocol.FLAG_UNREAD.equals(flag));
        if (c == current) {
            scrollToBottom();
            reportSeen(c);
        } else if (countable) {
            c.unread++;
        }
        if (live && !mine && !system && !isActive()) {
            notifyNewMessage(c, message);
        }
        scheduleConversationRefresh();
        if (live) {
            refreshTyping();
        }
    }

    /** Hiện ngay tin mình vừa gửi với trạng thái "Đang gửi"; trả về mã tạm cid để gửi kèm. */
    private String showPending(Conversation c, ChatMessage message) {
        String cid = "c" + (++nextCid);
        message.status = ChatMessage.Status.SENDING;
        pending.put(cid, new Sent(c, message));
        receive(c, message, Protocol.FLAG_NEW);
        return cid;
    }

    private void notifyNewMessage(Conversation c, ChatMessage message) {
        Toolkit.getDefaultToolkit().beep();
        if (trayIcon != null) {
            String title = c.isPrivate() ? message.from : c.title();
            String text = (c.isPrivate() ? "" : message.from + ": ") + message.summary();
            trayIcon.displayMessage(title, text, TrayIcon.MessageType.NONE);
        }
    }

    private void updateTitle() {
        int unread = conversations.values().stream().mapToInt(c -> c.unread).sum();
        setTitle((unread > 0 ? "(" + unread + ") " : "") + "MiniChat - " + me);
    }

    private void refreshTyping() {
        typingLabel.setText(current != null && current.isTyping() ? current.typingUser + " đang soạn tin..." : " ");
        conversationList.repaint();
    }

    private void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            chatScroll.validate();
            JScrollBar bar = chatScroll.getVerticalScrollBar();
            bar.setValue(bar.getMaximum());
        });
    }

    // ================================================================ hành động

    private void sendMessage() {
        if (current == null || !input.isEnabled()) {
            return;
        }
        String text = input.getText().trim();
        if (text.isEmpty()) {
            text = "👍";
        }
        String cid = showPending(current, ChatMessage.text(me, text, LocalDateTime.now()));
        connection.sendText(current.kind, current.target, cid, text);
        input.setText("");
        lastTypingSent = 0;
    }

    private void onInputChanged() {
        updateSendButton();
        long now = System.currentTimeMillis();
        if (!input.getText().isEmpty() && current != null && now - lastTypingSent > 2500) {
            connection.sendTyping(current.kind, current.target);
            lastTypingSent = now;
        }
    }

    /** Ô nhập trống thì nút gửi là "👍" (gửi nhanh), có chữ thì là icon máy bay giấy. */
    private void updateSendButton() {
        if (input.getText().trim().isEmpty()) {
            sendButton.setIcon(null);
            sendButton.setText("👍");
            sendButton.setFont(Theme.font(Font.PLAIN, 22));
            sendButton.setToolTipText("Gửi nhanh 👍");
        } else {
            sendButton.setText(null);
            sendButton.setIcon(Icons.get(Icons.Type.SEND, 24, Theme.PRIMARY));
            sendButton.setToolTipText("Gửi (Enter)");
        }
    }

    private void insertEmoji(String emoji) {
        input.replaceSelection(emoji);
        input.requestFocusInWindow();
    }

    private void chooseAndSendFile(boolean imagesOnly) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(imagesOnly ? "Chọn hình ảnh" : "Chọn file");
        if (imagesOnly) {
            chooser.setFileFilter(new FileNameExtensionFilter("Hình ảnh (png, jpg, gif, bmp)", "png", "jpg", "jpeg", "gif", "bmp"));
        }
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        if (file.length() > Protocol.MAX_FILE_SIZE) {
            JOptionPane.showMessageDialog(this, "File quá lớn. Dung lượng tối đa là "
                    + Protocol.MAX_FILE_SIZE / (1024 * 1024) + " MB.", "MiniChat", JOptionPane.WARNING_MESSAGE);
            return;
        }
        Conversation target = current;
        new Thread(() -> {
            try {
                byte[] data = Files.readAllBytes(file.toPath());
                String[] cid = new String[1];
                SwingUtilities.invokeAndWait(() -> cid[0] = showPending(target,
                        ChatMessage.file(me, file.getName(), data, LocalDateTime.now())));
                connection.sendFile(target.kind, target.target, cid[0], file.getName(), data);
            } catch (InterruptedException | java.lang.reflect.InvocationTargetException ex) {
                Thread.currentThread().interrupt();
            } catch (IOException ex) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                        "Không đọc được file: " + ex.getMessage(), "MiniChat", JOptionPane.ERROR_MESSAGE));
            }
        }, "minichat-send-file").start();
    }

    /** Mọi tài khoản trừ mình: người đang online trước, sau đó người truy cập gần đây hơn. */
    private List<String> friends() {
        return accounts.stream().filter(u -> !u.equals(me))
                .sorted(Comparator.comparing((String u) -> !isOnline(u))
                        .thenComparing(u -> -lastSeen.getOrDefault(u, 0L))
                        .thenComparing(String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
    }

    private void createGroup() {
        GroupDialog dialog = GroupDialog.createGroup(this, friends());
        if (dialog != null) {
            pendingOpen.add(dialog.getGroupName());
            connection.createGroup(dialog.getGroupName(), dialog.getSelectedUsers());
        }
    }

    private void addMembers() {
        List<String> users = GroupDialog.pickMembers(this, current.target, friends());
        if (users != null) {
            users.forEach(u -> connection.addMember(current.target, u));
        }
    }

    private void leaveGroup() {
        int answer = JOptionPane.showConfirmDialog(this, "Bạn có chắc muốn rời nhóm " + current.target + "?",
                "Rời nhóm", JOptionPane.YES_NO_OPTION);
        if (answer == JOptionPane.YES_OPTION) {
            connection.leaveGroup(current.target);
        }
    }

    private void openContact(Contact contact) {
        if (contact.kind.equals(Contact.USER)) {
            select(conversation(Protocol.PM, contact.name));
        } else if (contact.kind.equals(Contact.GROUP)) {
            if (myGroups.contains(contact.name)) {
                select(conversation(Protocol.GROUP, contact.name));
            } else {
                pendingOpen.add(contact.name);
                connection.joinGroup(contact.name);
            }
        }
    }

    private void bringToFront() {
        setVisible(true);
        setExtendedState(getExtendedState() & ~ICONIFIED);
        toFront();
    }

    private void shutdown() {
        closing = true;
        typingTimer.stop();
        presenceTimer.stop();
        connection.close();
        if (trayIcon != null) {
            SystemTray.getSystemTray().remove(trayIcon);
        }
    }

    private void logout() {
        int answer = JOptionPane.showConfirmDialog(this, "Đăng xuất khỏi MiniChat?", "Đăng xuất",
                JOptionPane.YES_NO_OPTION);
        if (answer == JOptionPane.YES_OPTION) {
            shutdown();
            dispose();
            SwingUtilities.invokeLater(() -> start(host, port));
        }
    }

    // ================================================== thông điệp từ server

    @Override
    public void onMessage(String line) {
        SwingUtilities.invokeLater(() -> handleServerMessage(line));
    }

    @Override
    public void onDisconnected() {
        if (closing) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            input.setEnabled(false);
            sendButton.setEnabled(false);
            for (Sent s : pending.values()) {
                s.message.status = ChatMessage.Status.FAILED;
                s.message.failReason = "Mất kết nối tới server";
                s.conversation.messages.updateStatus(s.message);
            }
            pending.clear();
            current.messages.add(ChatMessage.system("Mất kết nối tới server"));
            headerSubtitle.setText("Mất kết nối tới server");
            headerSubtitle.setForeground(Theme.BADGE);
            JOptionPane.showMessageDialog(this, "Mất kết nối tới server MiniChat.", "MiniChat",
                    JOptionPane.WARNING_MESSAGE);
        });
    }

    private void handleServerMessage(String line) {
        String[] p = line.split(" ", 2);
        String type = p[0];
        String body = p.length > 1 ? p[1] : "";
        switch (type) {
            case Protocol.MSG: {
                // MSG <id> <ts> <loại> <đích> <từ> <cờ> <tin nhắn>
                String[] a = body.split(" ", 7);
                if (a.length < 7) {
                    break;
                }
                LocalDateTime time = ChatMessage.toTime(parseLong(a[1]));
                ChatMessage message = a[4].equals(Protocol.SYSTEM_SENDER) ? ChatMessage.system(a[6], time)
                        : ChatMessage.text(a[4], a[6], time);
                message.id = parseLong(a[0]);
                receive(conversationOf(a[2], a[3], a[4]), message, a[5]);
                break;
            }
            case Protocol.MSG_FILE: {
                // MSG_FILE <id> <ts> <loại> <đích> <từ> <cờ> <base64> <tên file>
                String[] a = body.split(" ", 8);
                if (a.length < 8) {
                    break;
                }
                byte[] data;
                try {
                    data = Base64.getDecoder().decode(a[6]);
                } catch (IllegalArgumentException e) {
                    break;
                }
                ChatMessage message = ChatMessage.file(a[4], a[7], data, ChatMessage.toTime(parseLong(a[1])));
                message.id = parseLong(a[0]);
                receive(conversationOf(a[2], a[3], a[4]), message, a[5]);
                break;
            }
            case Protocol.ACK: {
                // ACK <cid> <id> <ts>: server đã lưu tin -> "Đã gửi"
                String[] a = body.split(" ");
                Sent s = a.length >= 2 ? pending.remove(a[0]) : null;
                if (s != null) {
                    s.message.id = parseLong(a[1]);
                    s.message.status = ChatMessage.Status.SENT;
                    sent.put(s.message.id, s);
                    s.conversation.messages.updateStatus(s.message);
                }
                break;
            }
            case Protocol.NACK: {
                // NACK <cid> <lý do>: tin bị từ chối -> "Gửi thất bại"
                String[] a = body.split(" ", 2);
                Sent s = pending.remove(a[0]);
                String reason = field(a, 1);
                if (s != null) {
                    s.message.status = ChatMessage.Status.FAILED;
                    s.message.failReason = reason;
                    s.conversation.messages.updateStatus(s.message);
                }
                JOptionPane.showMessageDialog(this, "Không gửi được tin nhắn: " + reason, "MiniChat",
                        JOptionPane.WARNING_MESSAGE);
                break;
            }
            case Protocol.STATUS: {
                // STATUS <id> <đã nhận> <đã xem> <tổng> <người xem,...|->
                String[] a = body.split(" ");
                Sent s = a.length >= 5 ? sent.get(parseLong(a[0])) : null;
                if (s != null) {
                    s.message.applyReceipts((int) parseLong(a[1]), (int) parseLong(a[2]), (int) parseLong(a[3]), a[4]);
                    s.conversation.messages.updateStatus(s.message);
                }
                break;
            }
            case Protocol.PRESENCE:
                // PRESENCE <u1:ON,u2:<ts>,...>
                accounts.clear();
                lastSeen.clear();
                for (String item : splitList(body)) {
                    int colon = item.lastIndexOf(':');
                    if (colon <= 0) {
                        continue;
                    }
                    String user = item.substring(0, colon);
                    String state = item.substring(colon + 1);
                    accounts.add(user);
                    if (!state.equals(Protocol.ONLINE)) {
                        lastSeen.put(user, parseLong(state));
                    }
                }
                refreshContacts();
                updateHeader();
                conversationList.repaint();
                break;
            case Protocol.TYPING: {
                // TYPING <loại> <đích> <từ>
                String[] a = body.split(" ", 3);
                if (a.length == 3) {
                    Conversation c = conversationOf(a[0], a[1], a[2]);
                    c.typingUser = a[2];
                    c.typingUntil = System.currentTimeMillis() + 3500;
                    refreshTyping();
                }
                break;
            }
            case Protocol.INFO:
                receive(conversation(Protocol.ALL, Protocol.ALL_TARGET), ChatMessage.system(body), Protocol.FLAG_NEW);
                break;
            case Protocol.ERROR:
                JOptionPane.showMessageDialog(this, body, "MiniChat", JOptionPane.WARNING_MESSAGE);
                break;
            case Protocol.JOINED: {
                myGroups.add(body);
                Conversation c = conversation(Protocol.GROUP, body);
                if (pendingOpen.remove(body)) {
                    select(c);
                }
                refreshContacts();
                break;
            }
            case Protocol.LEFT: {
                myGroups.remove(body);
                Conversation removed = conversations.remove(Conversation.key(Protocol.GROUP, body));
                if (removed == current) {
                    select(conversation(Protocol.ALL, Protocol.ALL_TARGET));
                }
                refreshConversationList();
                refreshContacts();
                break;
            }
            case Protocol.USERS:
                onlineUsers.clear();
                onlineUsers.addAll(List.of(splitList(body)));
                refreshContacts();
                updateHeader();
                conversationList.repaint();
                break;
            case Protocol.GROUPS:
                groupSizes.clear();
                for (String item : splitList(body)) {
                    int colon = item.lastIndexOf(':');
                    groupSizes.put(item.substring(0, colon), Integer.parseInt(item.substring(colon + 1)));
                }
                refreshContacts();
                updateHeader();
                break;
            case Protocol.MY_GROUPS:
                myGroups.clear();
                for (String group : splitList(body)) {
                    myGroups.add(group);
                    conversation(Protocol.GROUP, group);
                }
                contactList.repaint();
                break;
            default:
        }
    }

    /** Cuộc trò chuyện tương ứng với một tin nhắn: với chat riêng là cuộc trò chuyện với người kia. */
    private Conversation conversationOf(String kind, String target, String from) {
        if (kind.equals(Protocol.PM)) {
            return conversation(Protocol.PM, from.equals(me) ? target : from);
        }
        if (kind.equals(Protocol.GROUP)) {
            return conversation(Protocol.GROUP, target);
        }
        return conversation(Protocol.ALL, Protocol.ALL_TARGET);
    }

    private static long parseLong(String text) {
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String field(String[] parts, int index) {
        return index < parts.length ? parts[index] : "";
    }

    private static String[] splitList(String body) {
        return body.isBlank() ? new String[0] : body.split(",");
    }

    private static DocumentListener onChange(Runnable action) {
        return new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                action.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                action.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                action.run();
            }
        };
    }

    /** Một tin do mình gửi và cuộc trò chuyện chứa nó. */
    private static final class Sent {
        final Conversation conversation;
        final ChatMessage message;

        Sent(Conversation conversation, ChatMessage message) {
            this.conversation = conversation;
            this.message = message;
        }
    }

    // ============================================================== danh bạ

    /** Một dòng trong tab Danh bạ: tiêu đề mục, một user online hoặc một nhóm. */
    private static final class Contact {
        static final String HEADER = "HEADER";
        static final String USER = "USER";
        static final String GROUP = "GROUP";
        final String kind;
        final String name;

        Contact(String kind, String name) {
            this.kind = kind;
            this.name = name;
        }
    }

    private class ContactRenderer implements ListCellRenderer<Contact> {
        private final JLabel header = new JLabel();
        private final JPanel row = new JPanel(new BorderLayout(12, 0));
        private final JLabel avatar = new JLabel();
        private final JLabel name = new JLabel();
        private final JLabel detail = new JLabel();
        private final JLabel action = new JLabel();

        ContactRenderer() {
            header.setFont(Theme.font(Font.BOLD, 13));
            header.setForeground(Theme.TEXT_MUTED);
            header.setOpaque(true);
            header.setBackground(Color.WHITE);
            header.setBorder(BorderFactory.createEmptyBorder(14, 16, 6, 16));
            name.setFont(Theme.font(Font.PLAIN, 15));
            name.setForeground(Theme.TEXT);
            detail.setFont(Theme.font(Font.PLAIN, 12));
            action.setFont(Theme.font(Font.BOLD, 12));
            JPanel text = new JPanel(new GridLayout(2, 1, 0, 2));
            text.setOpaque(false);
            text.add(name);
            text.add(detail);
            row.setBackground(Color.WHITE);
            row.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
            row.add(avatar, BorderLayout.WEST);
            row.add(text, BorderLayout.CENTER);
            row.add(action, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Contact> list, Contact c, int index,
                boolean selected, boolean focus) {
            if (c.kind.equals(Contact.HEADER)) {
                header.setText(c.name);
                return header;
            }
            name.setText(c.name);
            if (c.kind.equals(Contact.USER)) {
                boolean online = isOnline(c.name);
                avatar.setIcon(Avatar.of(c.name, 42, online));
                detail.setText(presenceText(c.name));
                detail.setForeground(online ? Theme.ONLINE : Theme.TEXT_MUTED);
                action.setText("Nhắn tin");
                action.setForeground(Theme.PRIMARY);
            } else {
                boolean joined = myGroups.contains(c.name);
                avatar.setIcon(Avatar.of(c.name, 42));
                detail.setText(groupSizes.getOrDefault(c.name, 0) + " thành viên");
                detail.setForeground(Theme.TEXT_MUTED);
                action.setText(joined ? "Đã tham gia" : "Tham gia");
                action.setForeground(joined ? Theme.TEXT_MUTED : Theme.PRIMARY);
            }
            return row;
        }
    }

    /** Nút trên thanh bên màu xanh; nền đậm hơn khi đang được chọn. */
    private static class SidebarButton extends JButton {
        private boolean active;

        SidebarButton(Icons.Type icon, String tooltip) {
            super(Icons.get(icon, 26, Color.WHITE));
            setToolTipText(tooltip);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setRolloverEnabled(true);
            setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            Dimension size = new Dimension(48, 48);
            setPreferredSize(size);
            setMaximumSize(size);
            setAlignmentX(Component.CENTER_ALIGNMENT);
        }

        void setActive(boolean active) {
            this.active = active;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (active || getModel().isRollover()) {
                Graphics2D g2 = Theme.smooth(g);
                g2.setColor(active ? Theme.PRIMARY_DARK : new Color(255, 255, 255, 40));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.dispose();
            }
            super.paintComponent(g);
        }
    }

    // ==================================================================== main

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : Protocol.DEFAULT_PORT;
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                // dùng giao diện mặc định
            }
            start(host, port);
        });
    }

    private static void start(String host, int port) {
        ChatConnection connection = LoginDialog.show(host, port);
        if (connection == null) {
            System.exit(0);
        }
        new ChatClientGUI(connection, host, port).setVisible(true);
    }
}
