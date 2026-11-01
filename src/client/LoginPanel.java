package client;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;

public class LoginPanel extends JPanel {
    // ===== KHAI BÁO CÁC Ô NHẬP TÀI KHOẢN VÀ MẬT KHẨU =====
    private final JTextField username = new JTextField();
    private final JPasswordField password = new JPasswordField();
    private final JPasswordField confirm = new JPasswordField();
    // Người dùng nhập IP/hostname máy Server; 127.0.0.1 chỉ trỏ về chính máy Client.
    // ===== KHAI BÁO ĐỊA CHỈ IP VÀ CỔNG SERVER =====
    private final JTextField host = new JTextField();
    private final JTextField port = new JTextField("5000");
    private final JLabel formTitle = label("B\u1eaeT \u0110\u1ea6U H\u00c0NH TR\u00ccNH", 18, GameTheme.GOLD);
    private final JLabel confirmLabel = label("NH\u1eacP L\u1ea0I M\u1eacT KH\u1ea8U", 12, GameTheme.TEXT);
    private final JLabel hostLabel = label("SERVER IP", 12, GameTheme.TEXT);
    private final JLabel portLabel = label("PORT", 12, GameTheme.TEXT);
    private final JLabel connectionLabel = label(" ", 13, GameTheme.TEXT);
    private final JLabel feedbackLabel = label(" ", 13, GameTheme.TEXT);
    private final GameButton submit = new GameButton("\u0110\u0102NG NH\u1eacP", true);
    private final GameButton secondary = new GameButton("\u0110\u0102NG K\u00dd T\u00c0I KHO\u1ea2N", false);
    // Callback do MainFrame truyền vào; màn hình form không tự xử lý socket hay database.
    private final Runnable loginAction, registerAction;
    private boolean registering, busy;
    private final char echoChar = password.getEchoChar();
    // ===== TẠO NÚT MẮT ĐỂ HIỆN HOẶC ẨN MẬT KHẨU =====
    private final JToggleButton showPassword = new JToggleButton() {
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create(); GameTheme.smooth(g);
            g.setColor(GameTheme.PANEL); g.fillRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 8, 8);
            g.setColor(hasFocus() ? GameTheme.GOLD : isEnabled() ? new Color(113, 191, 222) : new Color(93, 121, 137));
            g.setStroke(new java.awt.BasicStroke(1.7f));
            g.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 8, 8);
            int cx = getWidth() / 2, cy = getHeight() / 2;
            g.drawOval(cx - 11, cy - 6, 22, 12); g.fillOval(cx - 3, cy - 3, 6, 6);
            if (!isSelected()) { g.drawLine(cx - 10, cy + 9, cx + 10, cy - 9); }
            g.dispose();
        }
    };

    public LoginPanel(Runnable loginAction, Runnable registerAction) {
        this(loginAction, registerAction, null);
    }

    // ===== TẠO VÀ SẮP XẾP GIAO DIỆN ĐĂNG NHẬP / ĐĂNG KÝ =====
    public LoginPanel(Runnable loginAction, Runnable registerAction, SoundManager sound) {
        super(new BorderLayout());
        this.loginAction = loginAction; this.registerAction = registerAction;
        setName("login-screen");
        JPanel header = createHeader();
        if (sound != null) { header.add(new SoundToggle(sound, "login-sound-toggle")); }
        add(header, BorderLayout.NORTH);
        RoundedPanel form = new RoundedPanel();
        form.setName("auth-form"); form.setLayout(null);
        form.setPreferredSize(new Dimension(430, 490));
        JLabel userLabel = label("T\u00caN \u0110\u0102NG NH\u1eacP", 12, GameTheme.TEXT);
        JLabel passwordLabel = label("M\u1eacT KH\u1ea8U", 12, GameTheme.TEXT);
        formTitle.setHorizontalAlignment(SwingConstants.CENTER);
        formTitle.setBounds(26, 24, 378, 25); form.add(formTitle);
        userLabel.setBounds(26, 65, 378, 20); form.add(userLabel);
        username.setBounds(26, 87, 378, 42); form.add(username);
        passwordLabel.setBounds(26, 143, 378, 20); form.add(passwordLabel);
        password.setBounds(26, 165, 328, 42); form.add(password);
        showPassword.setBounds(362, 165, 42, 42); form.add(showPassword);
        hostLabel.setBounds(26, 221, 254, 20); form.add(hostLabel);
        portLabel.setBounds(298, 221, 106, 20); form.add(portLabel);
        host.setBounds(26, 243, 254, 42); form.add(host);
        port.setBounds(298, 243, 106, 42); form.add(port);
        confirmLabel.setBounds(26, 221, 378, 20); form.add(confirmLabel);
        confirm.setBounds(26, 243, 378, 42); form.add(confirm);
        submit.setBounds(26, 305, 378, 48); form.add(submit);
        secondary.setBounds(26, 365, 378, 42); form.add(secondary);
        connectionLabel.setBounds(26, 422, 378, 20); form.add(connectionLabel);
        feedbackLabel.setBounds(26, 447, 378, 22); form.add(feedbackLabel);
        connectionLabel.setHorizontalAlignment(SwingConstants.CENTER);
        feedbackLabel.setHorizontalAlignment(SwingConstants.CENTER);
        // ===== ĐẶT TÊN VÀ ĐỊNH DẠNG CÁC ĐIỀU KHIỂN TRÊN FORM =====
        username.setName("auth-username"); password.setName("auth-password");
        confirm.setName("auth-confirm"); host.setName("auth-host"); port.setName("auth-port");
        submit.setName("auth-submit"); secondary.setName("auth-secondary");
        showPassword.setName("auth-show-password"); connectionLabel.setName("auth-connection"); feedbackLabel.setName("auth-feedback");
        for (JTextField field : new JTextField[] {username, password, confirm, host, port}) { GameTheme.styleField(field); }
        userLabel.setLabelFor(username); passwordLabel.setLabelFor(password);
        hostLabel.setLabelFor(host); portLabel.setLabelFor(port); confirmLabel.setLabelFor(confirm);
        showPassword.setOpaque(false); showPassword.setBorderPainted(false); showPassword.setContentAreaFilled(false);
        showPassword.setToolTipText("Hi\u1ec7n m\u1eadt kh\u1ea9u");
        showPassword.getAccessibleContext().setAccessibleName("Hi\u1ec7n / \u1ea9n m\u1eadt kh\u1ea9u");
        // Bật biểu tượng mắt để hiện chữ thật; tắt thì dùng lại ký tự che mật khẩu.
        // ===== XỬ LÝ BẤM NÚT HIỆN / ẨN MẬT KHẨU =====
        showPassword.addActionListener(event -> {
            password.setEchoChar(showPassword.isSelected() ? (char) 0 : echoChar);
            showPassword.setToolTipText(showPassword.isSelected() ? "\u1ea8n m\u1eadt kh\u1ea9u" : "Hi\u1ec7n m\u1eadt kh\u1ea9u");
        });
        // Bấm nút hoặc Enter đều đi qua submit(); nút phụ đảo chế độ đăng ký/đăng nhập.
        // ===== GẮN SỰ KIỆN CHO NÚT ĐĂNG NHẬP, ĐĂNG KÝ VÀ PHÍM ENTER =====
        submit.addActionListener(event -> submit());
        secondary.addActionListener(event -> setRegistrationMode(!registering));
        password.addActionListener(event -> submit()); confirm.addActionListener(event -> submit());
        // ===== THÊM VÙNG CUỘN VÀ CHÂN TRANG CHO FORM =====
        FormArea area = new FormArea(form);
        JScrollPane scroll = new JScrollPane(area);
        scroll.setName("auth-scroll"); GameTheme.styleScrollPane(scroll);
        add(scroll, BorderLayout.CENTER);
        JLabel footer = label("PTIT - L\u1eacP TR\u00ccNH M\u1ea0NG", 12, GameTheme.TEXT);
        footer.setOpaque(true); footer.setBackground(new Color(13, 67, 99));
        footer.setHorizontalAlignment(SwingConstants.CENTER); footer.setPreferredSize(new Dimension(0, 30));
        add(footer, BorderLayout.SOUTH);
        setRegistrationMode(false); setConnectionStatus(false);
    }

    // ===== VẼ PHẦN TIÊU ĐỀ VÀ BIỂU TƯỢNG TRÒ CHƠI =====
    private JPanel createHeader() {
        JPanel header = new JPanel(null) {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create(); GameTheme.smooth(g);
                int w = Math.min(590, getWidth() - 32), x = (getWidth() - w) / 2;
                g.setColor(new Color(65, 43, 29, 80)); g.fillRoundRect(x, 24, w, 102, 8, 8);
                g.setPaint(new java.awt.GradientPaint(0, 20, new Color(141, 87, 45), 0, 116, new Color(86, 54, 34)));
                g.fillRoundRect(x, 19, w, 101, 8, 8);
                g.setColor(new Color(194, 132, 62)); g.setStroke(new java.awt.BasicStroke(2f));
                g.drawRoundRect(x, 19, w, 101, 8, 8);
                g.setColor(new Color(209, 150, 77, 75));
                g.drawLine(x + 8, 56, x + w - 8, 56); g.drawLine(x + 8, 94, x + w - 8, 94);
                for (int px : new int[] {x + 12, x + w - 14}) {
                    g.setColor(GameTheme.GOLD); g.fillOval(px, 29, 4, 4); g.fillOval(px, 105, 4, 4);
                }
                int c = getWidth() / 2;
                g.setColor(GameTheme.GOLD);
                g.fillPolygon(new int[] {c - 19, c - 22, c - 10, c, c + 10, c + 22, c + 19},
                        new int[] {25, 4, 13, 0, 13, 4, 25}, 7); g.dispose();
            }
            @Override public void doLayout() {
                for (java.awt.Component child : getComponents()) {
                    if (child instanceof SoundToggle) { child.setBounds(getWidth() - 42, 4, 32, 32); }
                    else { child.setBounds(12, child.getY(), getWidth() - 24, child.getHeight()); }
                }
            }
        };
        header.setOpaque(false); header.setPreferredSize(new Dimension(0, 150));
        JLabel title = label("\u0110UA S\u0102N KHO B\u00c1U", 32, new Color(255, 225, 115));
        title.setName("auth-title"); title.setHorizontalAlignment(SwingConstants.CENTER); title.setBounds(0, 32, 590, 45);
        JLabel online = label("ONLINE", 24, new Color(117, 224, 252));
        online.setHorizontalAlignment(SwingConstants.CENTER); online.setBounds(0, 78, 590, 34);
        JLabel subtitle = label("KH\u00c1M PH\u00c1 - TH\u00c1CH \u0110\u1ea4U - CHI\u1ebeN TH\u1eaeNG", 12, GameTheme.INK);
        subtitle.setHorizontalAlignment(SwingConstants.CENTER); subtitle.setBounds(0, 126, 590, 20);
        header.add(title); header.add(online); header.add(subtitle); return header;
    }

    private static JLabel label(String text, int size, Color color) {
        JLabel label = new JLabel(text); label.setFont(new Font("SansSerif", Font.BOLD, size)); label.setForeground(color); return label;
    }

    // ===== VẼ HÌNH NỀN CỦA MÀN HÌNH ĐĂNG NHẬP =====
    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create(); TreasureBackground.paint(g, getWidth(), getHeight()); g.dispose();
    }

    // ===== XỬ LÝ BẤM ĐĂNG NHẬP HOẶC ĐĂNG KÝ =====
    private void submit() {
        // Khi đang chờ Server thì chặn gửi lặp; form hợp lệ mới gọi callback của chế độ hiện tại.
        if (busy) { return; }
        String error = validateInput();
        if (error != null) { showFeedback("Ki\u1ec3m tra th\u00f4ng tin", error, true); return; }
        if (registering) { registerAction.run(); } else { loginAction.run(); }
    }

    // ===== KIỂM TRA DỮ LIỆU FORM TRƯỚC KHI GỬI SERVER =====
    public String validateInput() {
        // Username không chứa dấu phân cách giao thức; mật khẩu không chứa ký tự xuống dòng.
        if (getUsername().matches(".*[,;|\\r\\n\\t].*") || getPassword().contains("\n") || getPassword().contains("\r")) {
            return "T\u00ean \u0111\u0103ng nh\u1eadp kh\u00f4ng ch\u1ee9a , ; | ho\u1eb7c k\u00fd t\u1ef1 xu\u1ed1ng d\u00f2ng.";
        }
        // Kiểm tra trường bắt buộc trước; chế độ đăng ký cần thêm mật khẩu xác nhận trùng khớp.
        if (getUsername().isEmpty() || getPassword().trim().isEmpty()) {
            return "Vui l\u00f2ng nh\u1eadp t\u00ean \u0111\u0103ng nh\u1eadp v\u00e0 m\u1eadt kh\u1ea9u.";
        }
        if (registering && new String(confirm.getPassword()).trim().isEmpty()) {
            return "Vui l\u00f2ng nh\u1eadp l\u1ea1i m\u1eadt kh\u1ea9u.";
        }
        if (registering && !getPassword().equals(new String(confirm.getPassword()))) {
            return "M\u1eadt kh\u1ea9u nh\u1eadp l\u1ea1i kh\u00f4ng kh\u1edbp.";
        }
        // Chỉ kiểm tra IP không rỗng và port hợp lệ; kết nối mạng thật nằm ở ClientConnection.connect().
        if (getServerIp().isEmpty()) { return "Vui l\u00f2ng nh\u1eadp Server IP."; }
        try {
            int value = Integer.parseInt(getPortText());
            if (value < 1 || value > 65535) { throw new NumberFormatException(); }
        } catch (NumberFormatException e) { return "Port ph\u1ea3i t\u1eeb 1 \u0111\u1ebfn 65535."; }
        // null nghĩa là hợp lệ; nếu có lỗi, các nhánh phía trên trả chuỗi thông báo cho giao diện.
        return null;
    }

    // ===== CHUYỂN GIAO DIỆN GIỮA ĐĂNG NHẬP VÀ ĐĂNG KÝ =====
    public void setRegistrationMode(boolean registering) {
        // Đổi tiêu đề/nút và hiện ô xác nhận; IP/port được ẩn nhưng vẫn giữ giá trị đã nhập.
        if (busy) { return; }
        this.registering = registering;
        formTitle.setText(registering ? "\u0110\u0102NG K\u00dd T\u00c0I KHO\u1ea2N" : "B\u1eaeT \u0110\u1ea6U H\u00c0NH TR\u00ccNH");
        confirm.setVisible(registering); confirmLabel.setVisible(registering);
        host.setVisible(!registering); port.setVisible(!registering);
        hostLabel.setVisible(!registering); portLabel.setVisible(!registering);
        if (!registering) { confirm.setText(""); }
        setBusy(false); setStatus(" ");
    }

    // ===== KHÓA / MỞ FORM TRONG LÚC CHỜ SERVER =====
    public void setBusy(boolean busy) {
        // Khóa các ô và nút trong lúc đang gửi yêu cầu để tránh người dùng gửi nhiều lần liên tiếp.
        this.busy = busy;
        for (JTextField field : new JTextField[] {username, password, confirm, host, port}) { field.setEnabled(!busy); }
        submit.setEnabled(!busy); secondary.setEnabled(!busy); showPassword.setEnabled(!busy);
        submit.setText(busy ? "\u0110ANG X\u1eec L\u00dd..." : registering ? "\u0110\u0102NG K\u00dd" : "\u0110\u0102NG NH\u1eacP");
        secondary.setText(registering ? "QUAY L\u1ea0I" : "\u0110\u0102NG K\u00dd T\u00c0I KHO\u1ea2N");
    }

    // ===== HIỂN THỊ TRẠNG THÁI ĐÃ KẾT NỐI HAY CHƯA =====
    public void setConnectionStatus(boolean connected) {
        // Hàm này chỉ hiển thị trạng thái TCP; kết nối thành công chưa có nghĩa đã đăng nhập đúng.
        connectionLabel.setText("\u25cf Server: " + (connected ? "\u0110\u00e3 k\u1ebft n\u1ed1i" : "Ch\u01b0a k\u1ebft n\u1ed1i"));
        connectionLabel.setForeground(connected ? new Color(121, 232, 157) : new Color(202, 223, 234));
    }

    public void setStatus(String message) { feedbackLabel.setText(message); feedbackLabel.setToolTipText(message); }
    // ===== HIỂN THỊ THÔNG BÁO THÀNH CÔNG HOẶC BÁO LỖI =====
    public void showFeedback(String title, String message, boolean error) {
        feedbackLabel.setForeground(error ? new Color(255, 166, 141) : GameTheme.GOLD);
        setStatus(title); AuthFeedback.show(this, title, message, error);
    }
    // ===== XÓA MẬT KHẨU ĐÃ NHẬP VÀ KHÔI PHỤC CHẾ ĐỘ CHE =====
    public void clearPasswords() {
        password.setText(""); confirm.setText(""); showPassword.setSelected(false); password.setEchoChar(echoChar);
        showPassword.setToolTipText("Hi\u1ec7n m\u1eadt kh\u1ea9u");
    }
    public boolean isBusy() { return busy; }
    public boolean isRegistrationMode() { return registering; }
    // ===== LẤY DỮ LIỆU TỪ CÁC Ô NHẬP ĐỂ MAINFRAME SỬ DỤNG =====
    public String getUsername() { return username.getText().trim(); }
    public String getPassword() { return new String(password.getPassword()); }
    public String getServerIp() { return host.getText().trim(); }
    public String getPortText() { return port.getText().trim(); }

    // ===== CĂN CHỈNH FORM KHI CUỘN HOẶC ĐỔI KÍCH THƯỚC CỬA SỔ =====
    private static final class FormArea extends JPanel implements Scrollable {
        private final JPanel form;
        FormArea(JPanel form) { super(null); this.form = form; setOpaque(false); add(form); }
        @Override public void doLayout() { form.setBounds((getWidth() - 430) / 2, Math.max(12, (getHeight() - 490) / 2), 430, 490); }
        @Override public Dimension getPreferredSize() { return new Dimension(454, 514); }
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 24; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return Math.max(24, r.height - 24); }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return getParent() != null && getParent().getHeight() >= 514; }
    }
}
