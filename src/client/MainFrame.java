package client;

import common.Message;
import common.Protocol;
import common.GameStateCodec;
import common.GameResultCodec;
import common.TrapEvent;
import model.GameState;
import model.GameResult;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import model.Player;

public class MainFrame extends JFrame implements MessageListener {
    private ClientConnection connection;
    private final SoundManager sound;
    private final CardLayout cardLayout;
    private final JPanel mainPanel;

    private LoginPanel loginPanel;
    private String currentUsername;
    private LobbyPanel lobbyPanel;
    private String connectionHost = "--";
    private int connectionPort = 5000;
    private GamePanel gamePanel;
    private ResultPanel resultPanel;
    private RankingPanel rankingPanel;
    private HistoryPanel historyPanel;
    private String statisticsView;
    private String currentStatus;
    private final Map<String, JDialog> invitationDialogs = new HashMap<>();
    private JDialog rematchDialog;

    public MainFrame(ClientConnection connection) {
        this(connection, new SoundManager());
    }

    MainFrame(ClientConnection connection, SoundManager sound) {
        this.sound = java.util.Objects.requireNonNull(sound);
        this.connection = connection;
        configureConnection(connection);

        setTitle("Treasure Rush - Đua Săn Kho Báu Online");
        setSize(1100, 700);
        setMinimumSize(new Dimension(700, 450));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent event) {
                MainFrame.this.dispose();
            }
        });
        setLocationRelativeTo(null);

        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);
        createPanels();
        installSounds(mainPanel);
        add(mainPanel);
    }

    void installSounds(java.awt.Component root) {
        UiSounds.install(root, sound);
    }

    @Override public void dispose() {
        invitationDialogs.values().forEach(JDialog::dispose);
        invitationDialogs.clear();
        closeRematchDialog();
        if (gamePanel != null) { gamePanel.close(); }
        sound.close();
        connection.disconnect();
        super.dispose();
    }

    private void createPanels() {
        mainPanel.add(createLoginPanel(), "LOGIN");
        mainPanel.add(createLobbyPanel(), "LOBBY");
        mainPanel.add(createGamePanel(), "GAME");
        resultPanel = new ResultPanel(() -> {
            closeRematchDialog();
            resultPanel.setRematchEnabled(false);
            connection.sendMessage(new Message(Protocol.RETURN_TO_LOBBY, ""));
            showLobby();
            connection.sendMessage(new Message(Protocol.GET_ONLINE_LIST, ""));
        }, () -> {
            String opponent = resultPanel.getOpponent();
            if (opponent != null) {
                connection.sendMessage(new Message(Protocol.PLAY_AGAIN, opponent));
            }
        });
        mainPanel.add(resultPanel, "RESULT");
        Runnable backToLobby = () -> {
            showLobby();
            connection.sendMessage(new Message(Protocol.GET_ONLINE_LIST, ""));
        };
        rankingPanel = new RankingPanel(() -> connection.sendMessage(new Message(Protocol.GET_RANKING, "")),
                backToLobby);
        historyPanel = new HistoryPanel(() -> connection.sendMessage(new Message(Protocol.GET_HISTORY, "")),
                backToLobby);
        mainPanel.add(rankingPanel, "RANKING");
        mainPanel.add(historyPanel, "HISTORY");
    }

    // ===== TẠO FORM VÀ NỐI NÚT ĐĂNG NHẬP / ĐĂNG KÝ VỚI HÀM XỬ LÝ =====
    private JPanel createLoginPanel() {
        loginPanel = new LoginPanel(this::sendLogin, this::sendRegister, sound);
        return loginPanel;
    }

    private JPanel createLobbyPanel() {
        lobbyPanel = new LobbyPanel(opponent -> sendInvite(), this::refreshLobby, () -> {
            if (!"FREE".equals(currentStatus)) {
                return;
            }
            statisticsView = "RANKING";
            showRanking();
            connection.sendMessage(new Message(Protocol.GET_RANKING, ""));
        }, () -> {
            if (!"FREE".equals(currentStatus)) {
                return;
            }
            statisticsView = "HISTORY";
            cardLayout.show(mainPanel, "HISTORY");
            connection.sendMessage(new Message(Protocol.GET_HISTORY, ""));
        }, this::logout, sound);
        return lobbyPanel;
    }

    private JPanel createGamePanel() {
        gamePanel = new GamePanel(direction -> connection.sendMessage(new Message(Protocol.MOVE, direction)),
                () -> connection.sendMessage(new Message(Protocol.EXIT_GAME, "")), sound);
        return gamePanel;
    }

    private void sendInvite() {
        if ("FREE".equals(currentStatus) && lobbyPanel.canInvite()) {
            connection.sendMessage(new Message(Protocol.INVITE, lobbyPanel.getSelectedOpponent()));
        }
    }

    private void refreshLobby() {
        lobbyPanel.setConnection(connectionHost, connectionPort, connection.isConnected());
        connection.sendMessage(new Message(Protocol.GET_ONLINE_LIST, ""));
        connection.sendMessage(new Message(Protocol.GET_RANKING, ""));
    }

    // ===== XỬ LÝ ĐĂNG XUẤT VÀ DỌN THÔNG TIN PHIÊN =====
    private void logout() {
        // Chỉ đăng xuất ở trạng thái FREE; đang trong trận thì dùng chức năng thoát trận trước.
        if (!"FREE".equals(currentStatus)) {
            return;
        }
        invitationDialogs.values().forEach(JDialog::dispose);
        invitationDialogs.clear();
        closeRematchDialog();
        connection.setMessageListener(message -> {
        });
        // Báo Server dọn trạng thái online rồi đóng phiên TCP phía Client.
        connection.sendMessage(new Message(Protocol.LOGOUT, ""));
        connection.disconnect();
        // A fresh client session avoids the old receiver closing a later connection.
        // Tạo đối tượng kết nối mới và xóa dữ liệu phiên; tài khoản/lịch sử trong MySQL vẫn giữ nguyên.
        connection = new ClientConnection();
        configureConnection(connection);
        currentUsername = null;
        currentStatus = null;
        statisticsView = null;
        lobbyPanel.setCurrentUsername(null);
        lobbyPanel.setConnection(connectionHost, connectionPort, false);
        loginPanel.setBusy(false);
        loginPanel.clearPasswords();
        loginPanel.setRegistrationMode(false);
        loginPanel.setConnectionStatus(false);
        showLogin();
    }

    private void showInvitation(String inviter) {
        if (!"FREE".equals(currentStatus) || inviter.equals(currentUsername)
                || invitationDialogs.containsKey(inviter)) {
            return;
        }
        JDialog dialog = new JDialog(this, "Th\u00e1ch \u0111\u1ea5u!", false);
        InvitationPanel content = new InvitationPanel(inviter, accepted -> {
            if (!invitationDialogs.remove(inviter, dialog)) {
                return;
            }
            dialog.dispose();
            if ("FREE".equals(currentStatus)) {
                connection
                        .sendMessage(new Message(accepted ? Protocol.INVITE_ACCEPT : Protocol.INVITE_REJECT, inviter));
            }
        });
        installSounds(content);
        dialog.setContentPane(content);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        invitationDialogs.put(inviter, dialog);
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent event) {
                content.answerOnce(false);
            }
        });
        dialog.getRootPane().registerKeyboardAction(event -> content.answerOnce(false),
                javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
                javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.pack();
        dialog.setMinimumSize(new Dimension(430, 260));
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private JPanel createSimplePanel(String text, Color color) {
        JPanel panel = new JPanel();
        panel.setBackground(color);
        panel.add(new JLabel(text));
        return panel;
    }

    // ===== CHỌN LOẠI YÊU CẦU ĐĂNG NHẬP =====
    private void sendLogin() {
        // Hai callback của form dùng chung luồng gửi, chỉ khác loại LOGIN hoặc REGISTER.
        sendAuthentication(Protocol.LOGIN);
    }

    // ===== CHỌN LOẠI YÊU CẦU ĐĂNG KÝ =====
    private void sendRegister() {
        sendAuthentication(Protocol.REGISTER);
    }

    // ===== KẾT NỐI VÀ GỬI LOGIN / REGISTER BẰNG SWINGWORKER =====
    private void sendAuthentication(String type) {
        // Chặn thao tác lặp và kiểm tra form trước khi tạo bản tin gửi lên Server.
        if (loginPanel.isBusy()) {
            return;
        }
        String error = loginPanel.validateInput();
        if (error != null) {
            loginPanel.showFeedback("Ki\u1ec3m tra th\u00f4ng tin", error, true);
            return;
        }
        String ip = loginPanel.getServerIp();
        int port = Integer.parseInt(loginPanel.getPortText());
        // Giữ đúng đối tượng kết nối của lần gửi này để phản hồi nền cũ không sửa nhầm phiên mới.
        ClientConnection attempt = connection;
        boolean changedEndpoint = !ip.equals(connectionHost) || port != connectionPort;
        connectionHost = ip;
        connectionPort = port;
        // Nội dung gửi là username;password, còn Message sẽ bổ sung phần TYPE| ở bên ngoài.
        Message request = new Message(type, loginPanel.getUsername() + ";" + loginPanel.getPassword().trim());
        loginPanel.setBusy(true);
        setStatus(connection.isConnected() ? "\u0110ang ch\u1edd Server..." : "\u0110ang k\u1ebft n\u1ed1i...");
        // Keep the existing connection/protocol sequence off Swing's drawing thread.
        // SwingWorker tách thao tác mạng khỏi EDT để giao diện không bị treo trong lúc chờ kết nối.
        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() {
                // Đổi địa chỉ thì đóng kết nối cũ; nếu cần mở TCP mới thì gửi HELLO trước yêu cầu xác thực.
                if (changedEndpoint) {
                    attempt.disconnect();
                }
                if (!attempt.isConnected()) {
                    attempt.connect(ip, port);
                    if (!attempt.isConnected()) {
                        return false;
                    }
                    attempt.sendMessage(new Message(Protocol.HELLO, "Client connected"));
                }
                attempt.sendMessage(request);
                return attempt.isConnected();
            }

            @Override
            protected void done() {
                // done() chạy trên EDT và chỉ báo tình trạng TCP; kết quả đăng nhập phải chờ LOGIN_SUCCESS/FAIL.
                if (attempt != connection) {
                    return;
                }
                try {
                    boolean connected = get();
                    loginPanel.setConnectionStatus(connected);
                    if (!connected) {
                        authenticationConnectionFailed();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    authenticationConnectionFailed();
                } catch (java.util.concurrent.ExecutionException e) {
                    authenticationConnectionFailed();
                }
            }
        }.execute();
    }

    // ===== BÁO LỖI KHI KHÔNG KẾT NỐI ĐƯỢC TỚI SERVER =====
    private void authenticationConnectionFailed() {
        // Hết chờ/kết nối lỗi thì mở lại các nút và đưa thông báo từ ClientConnection lên form.
        loginPanel.setBusy(false);
        loginPanel.setConnectionStatus(false);
        loginPanel.showFeedback("Kh\u00f4ng th\u1ec3 k\u1ebft n\u1ed1i",
                connection.getConnectionError().isEmpty()
                        ? "Vui l\u00f2ng ki\u1ec3m tra Server IP, Port v\u00e0 Server \u0111ang ch\u1ea1y."
                        : connection.getConnectionError(),
                true);
    }

    // ===== ĐĂNG KÝ HÀM NHẬN BẢN TIN VÀ XỬ LÝ MẤT MẠNG =====
    private void configureConnection(ClientConnection selected) {
        // Đăng ký nơi nhận thông điệp và nơi xử lý mất kết nối cho đúng đối tượng ClientConnection.
        selected.setMessageListener(this);
        selected.setDisconnectListener(reason -> {
            // Bỏ callback của kết nối cũ hoặc callback không còn phù hợp với trạng thái hiện tại.
            if (connection != selected || selected.isConnected()) {
                return;
            }
            // Dọn hộp thoại, trạng thái trận/phiên rồi về Login; người dùng đăng nhập lại sau khi mạng ổn định.
            connection = new ClientConnection();
            configureConnection(connection);
            invitationDialogs.values().forEach(JDialog::dispose);
            invitationDialogs.clear();
            closeRematchDialog();
            gamePanel.finishGame();
            resultPanel.setRematchEnabled(false);
            currentUsername = null;
            currentStatus = null;
            statisticsView = null;
            lobbyPanel.setCurrentUsername(null);
            lobbyPanel.setConnection(connectionHost, connectionPort, false);
            loginPanel.setBusy(false);
            loginPanel.clearPasswords();
            loginPanel.setRegistrationMode(false);
            loginPanel.setConnectionStatus(false);
            showLogin();
            loginPanel.showFeedback("M\u1ea5t k\u1ebft n\u1ed1i", reason, true);
        });
    }

    private void updateOnlineList(String data) {
        if (currentUsername == null) {
            return;
        }
        currentStatus = null;
        java.util.List<Player> onlinePlayers = new java.util.ArrayList<>();
        Set<String> freeOpponents = new HashSet<>();
        String[] players = data == null ? new String[0] : data.split(";");
        for (String playerData : players) {
            String[] columns = playerData.split(",", 4);
            if (columns.length < 4) {
                continue;
            }
            try {
                Player player = new Player();
                player.setUsername(columns[0]);
                player.setTotalRankPoint(Integer.parseInt(columns[1]));
                player.setWins(Integer.parseInt(columns[2]));
                player.setStatus(Player.Status.valueOf(columns[3]));
                if (player.getUsername().isEmpty() || player.getTotalRankPoint() < 0 || player.getWins() < 0) {
                    continue;
                }
                onlinePlayers.add(player);
                if (columns[0].equals(currentUsername)) {
                    currentStatus = columns[3];
                } else if ("FREE".equals(columns[3])) {
                    freeOpponents.add(columns[0]);
                }
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed UI rows; never invent a FREE status.
            }
        }
        lobbyPanel.setConnection(connectionHost, connectionPort, connection.isConnected());
        lobbyPanel.updateOnlinePlayers(onlinePlayers);
        invitationDialogs.entrySet().removeIf(entry -> {
            if (!"FREE".equals(currentStatus)) {
                entry.getValue().dispose();
                return true;
            }
            return false;
        });
        if (resultPanel.getResult() != null && (!"FREE".equals(currentStatus)
                || !freeOpponents.contains(resultPanel.getOpponent()))) {
            closeRematchDialog();
            resultPanel.setRematchEnabled(false);
        }
    }

    private void setStatus(String message) {
        loginPanel.setStatus(message);
    }

    private void closeRematchDialog() {
        if (rematchDialog != null) {
            JDialog dialog = rematchDialog;
            rematchDialog = null;
            dialog.dispose();
        }
    }

    private void showRematch(String opponent) {
        if (!resultPanel.isVisible() || !"FREE".equals(currentStatus)
                || !opponent.equals(resultPanel.getOpponent()) || rematchDialog != null) {
            return;
        }
        JDialog dialog = new JDialog(this, "T\u00e1i \u0111\u1ea5u!", false);
        rematchDialog = dialog;
        InvitationPanel content = new InvitationPanel(opponent, accepted -> {
            if (rematchDialog != dialog) {
                return;
            }
            closeRematchDialog();
            resultPanel.setRematchEnabled(false);
            connection.sendMessage(new Message(accepted
                    ? Protocol.PLAY_AGAIN_ACCEPT
                    : Protocol.PLAY_AGAIN_REJECT, opponent));
        }, true);
        installSounds(content);
        dialog.setContentPane(content);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent event) {
                if (rematchDialog == dialog) {
                    content.answerOnce(false);
                }
            }
        });
        dialog.getRootPane().registerKeyboardAction(event -> content.answerOnce(false),
                javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
                javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.pack();
        dialog.setMinimumSize(new Dimension(430, 260));
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    // ===== CHUYỂN VỀ MÀN HÌNH ĐĂNG NHẬP VÀ DỪNG ÂM THANH =====
    public void showLogin() {
        // Đăng xuất/mất kết nối phải dừng âm thanh và bỏ trạng thái trận của phiên trước.
        sound.stopAll();
        gamePanel.resetSession();
        cardLayout.show(mainPanel, "LOGIN");
    }

    // ===== CHUYỂN VÀO SẢNH, PHÁT NHẠC VÀ LẤY THỐNG KÊ =====
    public void showLobby() {
        // CardLayout chỉ hiển thị màn hình sảnh; username đã được cập nhật khi nhận LOGIN_SUCCESS.
        statisticsView = null;
        cardLayout.show(mainPanel, "LOBBY");
        if (currentUsername != null && connection.isConnected()) {
            // Khi có phiên đăng nhập, chọn nhạc sảnh và yêu cầu Server gửi thống kê xếp hạng.
            sound.startMusic(SoundManager.Music.LOBBY);
            connection.sendMessage(new Message(Protocol.GET_RANKING, ""));
        }
    }

    public void showGame() {
        statisticsView = null;
        cardLayout.show(mainPanel, "GAME");
        sound.startMusic(SoundManager.Music.GAME);
    }

    public void showResult() {
        statisticsView = null;
        cardLayout.show(mainPanel, "RESULT");
        sound.startMusic(SoundManager.Music.LOBBY);
    }

    public void showRanking() {
        cardLayout.show(mainPanel, "RANKING");
    }

    @Override
    // ===== NHẬN PHẢN HỒI SERVER VÀ CẬP NHẬT GIAO DIỆN TRÊN EDT =====
    public void onMessage(Message message) {
        // ClientConnection chuyển phản hồi tới đây; chọn nhánh theo type và cập nhật Swing trên EDT.
        String type = message.getType();
        String data = message.getData();

        SwingUtilities.invokeLater(() -> {
            // Không xử lý bản tin đã lỗi thời sau mất kết nối; trước đăng nhập chỉ nhận các phản hồi cho phép.
            if (!connection.isConnected()) {
                return;
            }
            if (currentUsername == null && !Set.of(Protocol.WELCOME, Protocol.REGISTER_SUCCESS,
                    Protocol.REGISTER_FAIL, Protocol.LOGIN_SUCCESS, Protocol.LOGIN_FAIL, Protocol.ERROR)
                    .contains(type)) {
                return;
            }
            switch (type) {
                case Protocol.WELCOME:
                    loginPanel.setConnectionStatus(true);
                    setStatus("Đã kết nối server");
                    break;
                // ===== XỬ LÝ ĐĂNG KÝ THÀNH CÔNG =====
                case Protocol.REGISTER_SUCCESS:
                    loginPanel.setBusy(false);
                    loginPanel.clearPasswords();
                    loginPanel.setRegistrationMode(false);
                    setStatus(data);
                    loginPanel.showFeedback("\u0110\u0103ng k\u00fd th\u00e0nh c\u00f4ng", data, false);
                    break;
                // ===== XỬ LÝ ĐĂNG KÝ THẤT BẠI =====
                case Protocol.REGISTER_FAIL:
                    loginPanel.setBusy(false);
                    loginPanel.showFeedback("\u0110\u0103ng k\u00fd th\u1ea5t b\u1ea1i", data, true);
                    break;
                // ===== XỬ LÝ ĐĂNG NHẬP THÀNH CÔNG =====
                case Protocol.LOGIN_SUCCESS:
                    // Server đã xác thực: xóa mật khẩu trên form, gắn username vào các màn hình rồi mở sảnh.
                    loginPanel.setBusy(false);
                    loginPanel.clearPasswords();
                    currentUsername = data;
                    lobbyPanel.setCurrentUsername(currentUsername);
                    lobbyPanel.setConnection(connectionHost, connectionPort, connection.isConnected());
                    gamePanel.setCurrentUsername(currentUsername);
                    resultPanel.setCurrentUsername(currentUsername);
                    historyPanel.setCurrentUsername(currentUsername);
                    setStatus("Đăng nhập thành công");
                    showLobby();
                    // Danh sách online từ Server sẽ cung cấp trạng thái FREE/PLAYING để bật đúng các nút thao tác.
                    connection.sendMessage(new Message(Protocol.GET_ONLINE_LIST, ""));
                    break;
                // ===== XỬ LÝ ĐĂNG NHẬP THẤT BẠI =====
                case Protocol.LOGIN_FAIL:
                    loginPanel.setBusy(false);
                    loginPanel.showFeedback("\u0110\u0103ng nh\u1eadp th\u1ea5t b\u1ea1i", data, true);
                    break;
                case Protocol.ONLINE_LIST:
                    updateOnlineList(data);
                    break;
                case Protocol.INVITE_RECEIVED:
                    showInvitation(data);
                    break;
                case Protocol.INVITE_REJECT:
                    AuthFeedback.show(lobbyPanel, "L\u1eddi m\u1eddi b\u1ecb t\u1eeb ch\u1ed1i",
                            data + " \u0111\u00e3 t\u1eeb ch\u1ed1i l\u1eddi m\u1eddi thi \u0111\u1ea5u", false);
                    break;
                case Protocol.ERROR:
                    if (resultPanel.isVisible()) {
                        resultPanel.setRematchEnabled(true);
                    }
                    if (gamePanel.isVisible() && gamePanel.getGameState() != null) {
                        gamePanel.showServerError(data);
                    } else if (lobbyPanel.isVisible()) {
                        AuthFeedback.show(lobbyPanel, "Th\u00f4ng b\u00e1o t\u1eeb Server", data, true);
                    } else {
                        JOptionPane.showMessageDialog(this, data, "Thông báo từ Server", JOptionPane.ERROR_MESSAGE);
                    }
                    break;
                case Protocol.GAME_START:
                    closeRematchDialog();
                    resultPanel.setRematchEnabled(false);
                    String[] players = data.split(";", 2);
                    if (players.length == 2) {
                        gamePanel.setPlayers(players[0], players[1]);
                    }
                    showGame();
                    break;
                case Protocol.GAME_STATE:
                    // [PHẦN PHỤ TRÁCH: NHẬN ĐỒNG BỘ]
                    // Mọi GAME_STATE đều được decode/validate trước, sau đó mới chuyển
                    // sang GamePanel. Client không trộn snapshot này với dữ liệu tự tính.
                    try {
                        GameState state = GameStateCodec.decode(data);
                        if (resultPanel.getResult() != null
                                && resultPanel.getResult().getRoomId().equals(state.getRoomId())) {
                            break;
                        }
                        gamePanel.setGameState(state);
                        showGame();
                    } catch (IllegalArgumentException e) {
                        JOptionPane.showMessageDialog(this, "GAME_STATE không hợp lệ: " + e.getMessage(),
                                "Lỗi dữ liệu trận đấu", JOptionPane.ERROR_MESSAGE);
                    }
                    break;
                case Protocol.TIME_UPDATE:
                    try {
                        gamePanel.updateTime(Integer.parseInt(data));
                    } catch (IllegalArgumentException e) {
                        gamePanel.showServerError("Thời gian Server gửi không hợp lệ");
                    }
                    break;
                case Protocol.TRAP_TRIGGERED:
                    try {
                        gamePanel.showTrapTriggered(TrapEvent.decode(data));
                    } catch (IllegalArgumentException e) {
                        gamePanel.showServerError("Thông báo rương bẫy không hợp lệ: " + e.getMessage());
                    }
                    break;
                case Protocol.TREASURE_COLLECTED:
                    try {
                        String[] collected = data.split(";", -1);
                        if (collected.length != 4 || collected[0].isEmpty()) {
                            throw new IllegalArgumentException("Invalid collection fields");
                        }
                        gamePanel.showTreasureCollected(collected[0], collected[1],
                                Integer.parseInt(collected[2]), Integer.parseInt(collected[3]));
                    } catch (IllegalArgumentException e) {
                        gamePanel.showServerError("Thông báo thu thập không hợp lệ: " + e.getMessage());
                    }
                    break;
                case Protocol.GAME_OVER:
                    try {
                        GameResult result = GameResultCodec.decode(data);
                        if (gamePanel.getGameState() != null
                                && !gamePanel.getGameState().getRoomId().equals(result.getRoomId())) {
                            break;
                        }
                        gamePanel.finishGame(result);
                        resultPanel.setResult(result);
                        resultPanel.setRematchEnabled(!GameResult.DISCONNECT.equals(result.getReason()));
                        showResult();
                    } catch (IllegalArgumentException e) {
                        gamePanel.finishGame();
                        gamePanel.showServerError("GAME_OVER không hợp lệ: " + e.getMessage());
                    }
                    break;
                case Protocol.PLAY_AGAIN:
                    showRematch(data);
                    break;
                case Protocol.PLAY_AGAIN_REJECT:
                    closeRematchDialog();
                    resultPanel.setRematchEnabled(false);
                    if (resultPanel.isVisible()) {
                        showLobby();
                    }
                    connection.sendMessage(new Message(Protocol.GET_ONLINE_LIST, ""));
                    break;
                case Protocol.RANKING:
                    try {
                        java.util.List<Player> ranking = common.RankingCodec.decode(data);
                        rankingPanel.setRanking(ranking);
                        lobbyPanel.updateStatistics(ranking);
                        if ("RANKING".equals(statisticsView)) {
                            showRanking();
                        }
                    } catch (IllegalArgumentException e) {
                        JOptionPane.showMessageDialog(this, "RANKING kh\u00f4ng h\u1ee3p l\u1ec7",
                                "L\u1ed7i d\u1eef li\u1ec7u", JOptionPane.ERROR_MESSAGE);
                    }
                    break;
                case Protocol.HISTORY:
                    try {
                        historyPanel.setHistory(common.HistoryCodec.decode(data));
                        if ("HISTORY".equals(statisticsView)) {
                            cardLayout.show(mainPanel, "HISTORY");
                        }
                    } catch (IllegalArgumentException e) {
                        JOptionPane.showMessageDialog(this, "HISTORY kh\u00f4ng h\u1ee3p l\u1ec7",
                                "L\u1ed7i d\u1eef li\u1ec7u", JOptionPane.ERROR_MESSAGE);
                    }
                    break;
                default:
                    break;
            }
        });
    }
}
