package server;

import common.Message;
import common.Protocol;
import common.GameStateCodec;
import common.GameResultCodec;
import common.TrapEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Comparator;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import model.GameResult;
import model.Player;
import model.MatchHistory;
import model.Treasure;

public class ServerManager implements AutoCloseable {
    // clientsByUsername gắn tài khoản đang online với handler/socket; onlinePlayers giữ Player của phiên.
    private final Map<String, ClientHandler> clientsByUsername;
    private final Map<String, Player> onlinePlayers;
    // accounts giữ cả tài khoản offline; danh sách này được nạp từ nơi lưu trữ khi Server khởi động.
    private final Map<String, Account> accounts;
    private final Map<String, MatchHistory> matchHistory = new java.util.LinkedHashMap<>();
    private final DataStore dataStore;
    // true nghĩa là dữ liệu trong RAM đã đổi nhưng chưa lưu thành công; lần lưu sau/close sẽ thử lại.
    private boolean persistenceDirty;
    private final List<GameRoom> gameRooms;
    private final Map<String, Set<String>> pendingInvites = new HashMap<>();
    private final Map<String, GameRoom> resultRooms = new HashMap<>();
    private final Map<String, String> pendingRematches = new HashMap<>();
    private final ScheduledThreadPoolExecutor timerScheduler;
    private final int matchDurationSeconds;
    private boolean closed;
    private long lastRoomNumber;

    // ===== CHỌN NƠI LƯU TRỮ VÀ KHỞI TẠO SERVER =====
    public ServerManager() {
        // Chọn MySQL hoặc TSV theo cấu hình; trận mặc định 180 giây và cấu trúc lưu trữ được khởi tạo sau khi nạp.
        this(DatabaseConfig.defaultStore(), 180);
        try {
            dataStore.initialize();
        } catch (IOException e) {
            close();
            throw new IllegalStateException("Cannot initialize server data storage", e);
        }
    }

    public ServerManager(int matchDurationSeconds) {
        this(matchDurationSeconds, null);
    }

    public ServerManager(int matchDurationSeconds, Path dataFile) {
        this(dataFile == null ? null : new ServerDataStore(dataFile), matchDurationSeconds);
    }

    public static ServerManager withDataStore(int seconds, DataStore store) {
        return new ServerManager(store, seconds);
    }

    // ===== TẠO BỘ NHỚ QUẢN LÝ VÀ NẠP TÀI KHOẢN / LỊCH SỬ =====
    private ServerManager(DataStore store, int matchDurationSeconds) {
        if (matchDurationSeconds <= 0 || matchDurationSeconds > 180) {
            throw new IllegalArgumentException("Match duration must be from 1 to 180 seconds");
        }
        this.matchDurationSeconds = matchDurationSeconds;
        timerScheduler = new ScheduledThreadPoolExecutor(1, task -> {
            Thread thread = new Thread(task, "GameTimer");
            thread.setDaemon(true);
            return thread;
        });
        timerScheduler.setRemoveOnCancelPolicy(true);
        this.clientsByUsername = new ConcurrentHashMap<>();
        this.onlinePlayers = new ConcurrentHashMap<>();
        this.accounts = new ConcurrentHashMap<>();
        this.gameRooms = new ArrayList<>();
        dataStore = store;
        if (dataStore != null) {
            try {
                // Nạp dữ liệu lâu dài vào RAM; khôi phục số phòng lớn nhất để không bắt đầu lại thứ tự phòng.
                dataStore.load(accounts, matchHistory);
                for (String roomId : matchHistory.keySet()) {
                    lastRoomNumber = Math.max(lastRoomNumber, common.RoomName.number(roomId));
                }
            } catch (IOException e) {
                timerScheduler.shutdownNow();
                throw new IllegalStateException("Cannot load server data; storage was not reset", e);
            }
        }
    }

    // ===== XỬ LÝ ĐĂNG KÝ: KIỂM TRA TRÙNG TÊN VÀ LƯU TÀI KHOẢN =====
    public synchronized String registerAccount(String username, String password) {
        // synchronized giữ trọn bước kiểm tra-trùng-và-thêm khi nhiều thread Client cùng đăng ký.
        // Kiểm tra lại ở Server vì không thể chỉ dựa vào dữ liệu đã kiểm tra trên giao diện Client.
        if (username != null && username.matches(".*[,;|\\r\\n\\t].*")
                || password != null && (password.contains("\r") || password.contains("\n"))) {
            return "T\u00ean \u0111\u0103ng nh\u1eadp kh\u00f4ng ch\u1ee9a , ; | ho\u1eb7c k\u00fd t\u1ef1 xu\u1ed1ng d\u00f2ng";
        }
        if (isBlank(username) || isBlank(password)) {
            return "Username và password không được rỗng";
        }

        // Username là khóa tài khoản; tài khoản offline cũng bị coi là trùng nếu tên đã tồn tại.
        if (accounts.containsKey(username)) {
            return "Username đã tồn tại";
        }

        Account account = Account.register(username, password);
        // Tạo bản sao có tài khoản mới; chỉ cập nhật map chính sau khi nơi lưu trữ xác nhận lưu thành công.
        Map<String, Account> candidate = new HashMap<>(accounts);
        candidate.put(username, account);
        if (dataStore != null) {
            try {
                // MySQL lưu hồ sơ và mật khẩu rõ phục vụ demo trong cùng transaction đăng ký.
                dataStore.saveRegistration(candidate, matchHistory, username, password);
                persistenceDirty = false;
            } catch (IOException e) {
                System.err.println("Registration could not be saved: " + e.getMessage());
                return "Kh\u00f4ng l\u01b0u \u0111\u01b0\u1ee3c t\u00e0i kho\u1ea3n, vui l\u00f2ng th\u1eed l\u1ea1i";
            }
        }
        // Quy ước trả về: null là thành công; chuỗi khác null là thông báo lỗi cho ClientHandler.
        accounts.put(username, account);
        return null;
    }

    // ===== XỬ LÝ ĐĂNG NHẬP: KIỂM TRA MẬT KHẨU VÀ CHỐNG TRÙNG PHIÊN =====
    public synchronized String loginClient(ClientHandler clientHandler, String username, String password) {
        // Một handler chỉ đại diện một phiên đăng nhập; kiểm tra bắt buộc trước khi tra tài khoản.
        if (clientHandler.getPlayer() != null) {
            return "Client đã đăng nhập";
        }
        if (isBlank(username) || isBlank(password)) {
            return "Username và password không được rỗng";
        }

        // Xác thực từ Account trong RAM; verifyPassword() băm và so khớp, không truy vấn mật khẩu bằng SQL ở đây.
        Account account = accounts.get(username);
        if (account == null || !account.verifyPassword(password)) {
            return "Tài khoản hoặc mật khẩu không đúng";
        }

        // Không cho cùng username xuất hiện ở hai kết nối; synchronized bảo vệ cả kiểm tra và thêm phiên online.
        if (clientsByUsername.containsKey(username)) {
            return "Username đang online";
        }

        if (dataStore != null) {
            try {
                // Chỉ sau xác thực đúng mới bổ sung mật khẩu rõ cho bản demo và kiểm tra hoàn tất chuyển đổi salt/hash.
                dataStore.recordSuccessfulLogin(username, password);
            } catch (IOException e) {
                // Do not log exceptions that may contain SQL parameter values.
                System.err.println("Could not save login data for the MySQL demo.");
                return "Kh\u00f4ng l\u01b0u \u0111\u01b0\u1ee3c d\u1eef li\u1ec7u \u0111\u0103ng nh\u1eadp, vui l\u00f2ng th\u1eed l\u1ea1i";
            }
        }

        // Sao chép thống kê vào Player mới của phiên này và đưa người chơi về trạng thái rảnh trong sảnh.
        Player player = account.createPlayer();
        player.setStatus(Player.Status.FREE);

        // Gắn danh tính với handler và ghi vào hai danh sách online; ClientHandler sẽ gửi LOGIN_SUCCESS sau đó.
        clientHandler.setClientUsername(username);
        clientHandler.setPlayer(player);
        clientsByUsername.put(username, clientHandler);
        onlinePlayers.put(username, player);

        System.out.println(username + " logged in");
        return null;
    }

    // ===== DỌN NGƯỜI CHƠI MẤT KẾT NỐI VÀ XỬ LÝ TRẬN ĐANG CHƠI =====
    public synchronized void removeClient(ClientHandler clientHandler) {
        // Chỉ xóa nếu map còn gắn username với chính handler này, tránh phiên cũ xóa nhầm phiên mới.
        String username = clientHandler.getClientUsername();
        if (clientsByUsername.remove(username, clientHandler)) {
            onlinePlayers.remove(username);
            clearInvitesFor(username);
            clearRematchFor(username, true);
            // Dọn lời mời/tái đấu; nếu Server còn chạy và người này đang đấu thì xử thua do DISCONNECT, lưu kết quả.
            GameRoom room = findRunningRoom(clientHandler.getPlayer());
            if (!closed && room != null) {
                finishRoom(room, clientHandler.getPlayer(), GameResult.DISCONNECT);
            }
        }

        System.out.println("Client disconnected: " + username);
    }

    public void broadcast(Message message) {
        for (ClientHandler client : clientsByUsername.values()) {
            client.sendMessage(message);
        }
    }

    public void broadcast(String rawMessage) {
        broadcast(Message.decode(rawMessage));
    }

    public void sendToPlayer(String username, Message message) {
        ClientHandler client = clientsByUsername.get(username);
        if (client != null) {
            client.sendMessage(message);
        }
    }

    public synchronized List<Player> getOnlinePlayers() {
        return new ArrayList<>(onlinePlayers.values());
    }

    public synchronized String buildOnlineListData() {
        StringBuilder builder = new StringBuilder();
        for (Player player : onlinePlayers.values()) {
            if (builder.length() > 0) {
                builder.append(";");
            }
            builder.append(player.getUsername()).append(",")
                    .append(player.getTotalRankPoint()).append(",")
                    .append(player.getWins()).append(",")
                    .append(player.getStatus());
        }
        return builder.toString();
    }

    public synchronized void sendOnlineList(ClientHandler clientHandler) {
        clientHandler.sendMessage(new Message(Protocol.ONLINE_LIST, buildOnlineListData()));
    }

    public synchronized void broadcastOnlineList() {
        broadcast(new Message(Protocol.ONLINE_LIST, buildOnlineListData()));
    }

    // ===== TẠO VÀ SẮP XẾP BẢNG XẾP HẠNG =====
    public synchronized List<Player> getRanking() {
        // Xếp hạng được tạo từ Account trong RAM và sắp xếp bằng Java, bao gồm cả tài khoản đang offline.
        List<Player> ranking = new ArrayList<>();
        for (Account account : accounts.values()) {
            ranking.add(account.createPlayer());
        }
        ranking.sort(Comparator.comparingInt(Player::getTotalRankPoint).reversed()
                .thenComparing(Comparator.comparingInt(Player::getWins).reversed())
                .thenComparing(Comparator.comparingInt(Player::getTotalTreasureCollected).reversed())
                .thenComparing(Player::getUsername));
        return ranking;
    }

    // ===== LỌC LỊCH SỬ TRẬN CỦA MỘT TÀI KHOẢN =====
    public synchronized List<MatchHistory> getMatchHistory(String username) {
        // Lọc trận có username tham gia rồi sắp xếp trận mới nhất lên trước; không đọc lại toàn bộ MySQL tại đây.
        List<MatchHistory> history = new ArrayList<>();
        for (MatchHistory match : matchHistory.values()) {
            if (match.containsPlayer(username)) {
                history.add(match);
            }
        }
        history.sort(Comparator.comparingLong(MatchHistory::getEndedAtEpochMillis).reversed()
                .thenComparing(MatchHistory::getRoomId));
        return history;
    }

    public synchronized void sendRanking(ClientHandler client) {
        if (!isLoggedIn(client)) {
            client.sendMessage(new Message(Protocol.ERROR, "Login required"));
            return;
        }
        client.sendMessage(new Message(Protocol.RANKING, common.RankingCodec.encode(getRanking())));
    }

    public synchronized void sendHistory(ClientHandler client) {
        if (!isLoggedIn(client)) {
            client.sendMessage(new Message(Protocol.ERROR, "Login required"));
            return;
        }
        client.sendMessage(
                new Message(Protocol.HISTORY, common.HistoryCodec.encode(getMatchHistory(client.getClientUsername()))));
    }

    private boolean isLoggedIn(ClientHandler client) {
        return client.getPlayer() != null && clientsByUsername.get(client.getClientUsername()) == client;
    }

    // ===== LƯU TÀI KHOẢN VÀ LỊCH SỬ XUỐNG NƠI LƯU TRỮ =====
    private boolean saveData() {
        // Lưu ảnh dữ liệu hiện tại; nếu thất bại thì giữ dữ liệu ở RAM và đánh dấu cần lưu lại.
        if (dataStore == null) {
            return true;
        }
        try {
            dataStore.save(accounts, matchHistory);
            persistenceDirty = false;
            return true;
        } catch (IOException e) {
            persistenceDirty = true;
            System.err.println("Server data is still pending in memory: " + e.getMessage());
            return false;
        }
    }

    public void addOnlinePlayer(Player player) {
        if (player != null && player.getUsername() != null) {
            onlinePlayers.put(player.getUsername(), player);
        }
    }

    public void removeOnlinePlayer(String username) {
        onlinePlayers.remove(username);
    }

    public synchronized GameRoom createGameRoom(Player player1, Player player2) {
        return createGameRoom(player1, player2, null);
    }

    private GameRoom createGameRoom(Player player1, Player player2, int[][] previousMap) {
        if (closed) {
            throw new IllegalStateException("Server manager is closed");
        }
        if (player1 == null || player2 == null || player1 == player2
                || player1.getUsername().equals(player2.getUsername())
                || onlinePlayers.get(player1.getUsername()) != player1
                || onlinePlayers.get(player2.getUsername()) != player2
                || player1.getStatus() != Player.Status.FREE
                || player2.getStatus() != Player.Status.FREE) {
            throw new IllegalArgumentException("Hai người chơi phải online và FREE");
        }
        for (GameRoom existing : gameRooms) {
            if (existing.isStarted() && (existing.getPlayer1() == player1
                    || existing.getPlayer2() == player1 || existing.getPlayer1() == player2
                    || existing.getPlayer2() == player2)) {
                throw new IllegalArgumentException("Người chơi đã có phòng thi đấu");
            }
        }
        GameRoom room = new GameRoom(player1, player2, Math.incrementExact(lastRoomNumber));
        room.startGame(previousMap);
        lastRoomNumber++;
        clearRematchFor(player1.getUsername(), false);
        clearRematchFor(player2.getUsername(), false);
        room.getGameState().setRemainingTime(matchDurationSeconds);
        gameRooms.add(room);
        player1.setStatus(Player.Status.PLAYING);
        player2.setStatus(Player.Status.PLAYING);
        System.out.println(
                "Room created: " + room.getId() + " (" + player1.getUsername() + " vs " + player2.getUsername() + ")");
        return room;
    }

    public synchronized void invite(ClientHandler sender, String recipient) {
        String error = validateFreePlayers(sender, recipient);
        if (error != null) {
            sender.sendMessage(new Message(Protocol.ERROR, error));
            return;
        }
        Set<String> inviters = pendingInvites.computeIfAbsent(recipient, key -> new HashSet<>());
        if (!inviters.add(sender.getClientUsername())) {
            sender.sendMessage(new Message(Protocol.ERROR, "Bạn đã gửi lời mời cho người này"));
            return;
        }
        sendToPlayer(recipient, new Message(Protocol.INVITE_RECEIVED, sender.getClientUsername()));
        System.out.println(sender.getClientUsername() + " invited " + recipient);
    }

    public synchronized void acceptInvite(ClientHandler recipient, String inviter) {
        String error = validateFreePlayers(recipient, inviter);
        if (error == null && !hasInvite(recipient.getClientUsername(), inviter)) {
            error = "Lời mời không tồn tại hoặc đã hết hiệu lực";
        }
        if (error != null) {
            recipient.sendMessage(new Message(Protocol.ERROR, error));
            return;
        }

        GameRoom room;
        try {
            room = createGameRoom(onlinePlayers.get(inviter), recipient.getPlayer());
        } catch (IllegalArgumentException | IllegalStateException e) {
            recipient.sendMessage(new Message(Protocol.ERROR, "Không thể khởi tạo trận đấu: " + e.getMessage()));
            return;
        }
        clearInvitesFor(inviter);
        clearInvitesFor(recipient.getClientUsername());
        startRoom(room);
    }

    private void startRoom(GameRoom room) {
        broadcastOnlineList();
        Message start = new Message(Protocol.GAME_START,
                room.getPlayer1().getUsername() + ";" + room.getPlayer2().getUsername());
        sendToRoom(room, start);
        // [PHẦN PHỤ TRÁCH: ĐỒNG BỘ GAME_STATE]
        // Encode đúng một snapshot rồi gửi cùng payload cho cả hai client trong phòng.
        Message state = new Message(Protocol.GAME_STATE, GameStateCodec.encode(room.getGameState()));
        sendToRoom(room, state);
        room.startTimer(timerScheduler, () -> tickRoom(room));
    }

    private synchronized void tickRoom(GameRoom room) {
        if (closed || !gameRooms.contains(room)) {
            room.cancelTimer();
            return;
        }
        synchronized (room) {
            if (!room.isStarted() || !"RUNNING".equals(room.getGameState().getStatus())) {
                cleanupRoom(room);
                return;
            }
            int remaining = Math.max(0, room.getGameState().getRemainingTime() - 1);
            room.getGameState().setRemainingTime(remaining);
            sendToRoom(room, new Message(Protocol.TIME_UPDATE, Integer.toString(remaining)));
            if (remaining == 0) {
                finishRoom(room, null, GameResult.TIME_UP);
            }
        }
    }

    // Manager lock always precedes room lock, including timer, exit and disconnect.
    // ===== KẾT THÚC TRẬN: CẬP NHẬT THỐNG KÊ VÀ LƯU KẾT QUẢ =====
    private void finishRoom(GameRoom room, Player forfeiting, String reason) {
        // GameRoom tính kết quả/thống kê một lần; ServerManager thêm lịch sử và lưu cùng hồ sơ hai người.
        synchronized (room) {
            GameResult result = room.finishGame(forfeiting, reason);
            if (result == null) {
                return;
            }
            System.out
                    .println("Game finished: " + room.getId() + " winner=" + result.getWinner() + " reason=" + reason);
            MatchHistory history = new MatchHistory(result, System.currentTimeMillis(),
                    matchDurationSeconds - room.getGameState().getRemainingTime());
            boolean saved = true;
            // roomId là khóa chống lưu một trận nhiều lần khi hết giờ/thoát/mất kết nối xảy ra sát nhau.
            if (matchHistory.putIfAbsent(room.getId(), history) == null) {
                for (Player player : new Player[] { room.getPlayer1(), room.getPlayer2() }) {
                    Account account = accounts.get(player.getUsername());
                    if (account != null) {
                        accounts.put(player.getUsername(), account.withStatistics(player));
                    }
                }
                saved = saveData();
            }
            if (onlinePlayers.get(room.getPlayer1().getUsername()) == room.getPlayer1()
                    && onlinePlayers.get(room.getPlayer2().getUsername()) == room.getPlayer2()) {
                resultRooms.put(room.getPlayer1().getUsername(), room);
                resultRooms.put(room.getPlayer2().getUsername(), room);
            }
            sendToRoom(room, new Message(Protocol.GAME_STATE, GameStateCodec.encode(room.getGameState())));
            sendToRoom(room, new Message(Protocol.GAME_OVER, GameResultCodec.encode(result)));
            if (!saved) {
                sendToRoom(room, new Message(Protocol.ERROR,
                        "Kh\u00f4ng l\u01b0u \u0111\u01b0\u1ee3c d\u1eef li\u1ec7u; k\u1ebft qu\u1ea3 \u0111ang ch\u1edd l\u01b0u tr\u00ean Server"));
            }
            cleanupRoom(room);
        }
    }

    private GameRoom findRunningRoom(Player player) {
        if (player == null) {
            return null;
        }
        for (GameRoom room : gameRooms) {
            if ((room.getPlayer1() == player || room.getPlayer2() == player)
                    && room.isStarted() && "RUNNING".equals(room.getGameState().getStatus())) {
                return room;
            }
        }
        return null;
    }

    public synchronized void exitGame(ClientHandler sender) {
        GameRoom room = clientsByUsername.get(sender.getClientUsername()) == sender
                ? findRunningRoom(sender.getPlayer())
                : null;
        if (room == null) {
            sender.sendMessage(new Message(Protocol.ERROR, "No RUNNING room to exit"));
            return;
        }
        finishRoom(room, sender.getPlayer(), GameResult.PLAYER_EXIT);
    }

    private String validateRematch(ClientHandler sender, String opponent) {
        String error = validateFreePlayers(sender, opponent);
        if (error != null) {
            return error;
        }
        GameRoom previous = resultRooms.get(sender.getClientUsername());
        if (previous == null || resultRooms.get(opponent) != previous) {
            return "Rematch expired: opponent returned to lobby or disconnected";
        }
        return null;
    }

    public synchronized void requestRematch(ClientHandler sender, String opponent) {
        String error = validateRematch(sender, opponent);
        if (error != null) {
            sender.sendMessage(new Message(Protocol.ERROR, error));
            return;
        }
        String username = sender.getClientUsername();
        if (opponent.equals(pendingRematches.get(username))) {
            launchRematch(sender);
        } else if (!username.equals(pendingRematches.get(opponent))) {
            pendingRematches.put(opponent, username);
            sendToPlayer(opponent, new Message(Protocol.PLAY_AGAIN, username));
        }
    }

    public synchronized void acceptRematch(ClientHandler sender, String opponent) {
        String error = validateRematch(sender, opponent);
        if (error == null && !opponent.equals(pendingRematches.get(sender.getClientUsername()))) {
            error = "No pending rematch request";
        }
        if (error != null) {
            sender.sendMessage(new Message(Protocol.ERROR, error));
            return;
        }
        launchRematch(sender);
    }

    private void launchRematch(ClientHandler sender) {
        GameRoom previous = resultRooms.get(sender.getClientUsername());
        Player p1 = previous.getPlayer1();
        Player p2 = previous.getPlayer2();
        GameRoom room;
        try {
            // Generate before consuming requests or inserting an active room.
            room = createGameRoom(p1, p2, previous.getGameState().getMap());
        } catch (IllegalArgumentException | IllegalStateException e) {
            sender.sendMessage(new Message(Protocol.ERROR, "Could not start rematch: " + e.getMessage()));
            return;
        }
        clearInvitesFor(p1.getUsername());
        clearInvitesFor(p2.getUsername());
        startRoom(room);
    }

    public synchronized void rejectRematch(ClientHandler sender, String opponent) {
        String error = validateRematch(sender, opponent);
        if (error == null && !opponent.equals(pendingRematches.get(sender.getClientUsername()))) {
            error = "No pending rematch request";
        }
        if (error != null) {
            sender.sendMessage(new Message(Protocol.ERROR, error));
            return;
        }
        clearRematchFor(sender.getClientUsername(), true);
    }

    public synchronized void returnToLobby(ClientHandler sender) {
        if (clientsByUsername.get(sender.getClientUsername()) != sender || sender.getPlayer() == null
                || sender.getPlayer().getStatus() != Player.Status.FREE) {
            sender.sendMessage(new Message(Protocol.ERROR, "Only FREE players can return to lobby"));
            return;
        }
        clearRematchFor(sender.getClientUsername(), true);
        sendOnlineList(sender);
    }

    private void clearRematchFor(String username, boolean notify) {
        GameRoom previous = resultRooms.remove(username);
        pendingRematches.remove(username);
        pendingRematches.entrySet().removeIf(entry -> username.equals(entry.getValue()));
        if (previous == null) {
            return;
        }
        String opponent = previous.getPlayer1().getUsername().equals(username)
                ? previous.getPlayer2().getUsername()
                : previous.getPlayer1().getUsername();
        resultRooms.remove(opponent, previous);
        pendingRematches.remove(opponent);
        if (notify) {
            sendToPlayer(username, new Message(Protocol.PLAY_AGAIN_REJECT, username));
            sendToPlayer(opponent, new Message(Protocol.PLAY_AGAIN_REJECT, username));
        }
    }

    private boolean sendToRoom(GameRoom room, Message message) {
        boolean sentToBoth = true;
        for (Player player : new Player[] { room.getPlayer1(), room.getPlayer2() }) {
            ClientHandler client = clientsByUsername.get(player.getUsername());
            // An old room must not send state to a new login with the same username.
            if (client == null || client.getPlayer() != player || !client.sendMessage(message)) {
                sentToBoth = false;
            }
        }
        return sentToBoth;
    }

    private void cleanupRoom(GameRoom room) {
        room.cancelTimer();
        gameRooms.remove(room);
        room.getPlayer1().setStatus(Player.Status.FREE);
        room.getPlayer2().setStatus(Player.Status.FREE);
        broadcastOnlineList();
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        for (GameRoom room : gameRooms) {
            room.endGame();
            room.getPlayer1().setStatus(Player.Status.FREE);
            room.getPlayer2().setStatus(Player.Status.FREE);
        }
        gameRooms.clear();
        resultRooms.clear();
        pendingRematches.clear();
        pendingInvites.clear();
        if (persistenceDirty) {
            saveData();
        }
        timerScheduler.shutdownNow();
    }

    public boolean isTimerSchedulerShutdown() {
        return timerScheduler.isShutdown();
    }

    public synchronized void rejectInvite(ClientHandler recipient, String inviter) {
        String error = validateFreePlayers(recipient, inviter);
        if (error == null && !hasInvite(recipient.getClientUsername(), inviter)) {
            error = "Lời mời không tồn tại hoặc đã hết hiệu lực";
        }
        if (error != null) {
            recipient.sendMessage(new Message(Protocol.ERROR, error));
            return;
        }
        Set<String> inviters = pendingInvites.get(recipient.getClientUsername());
        inviters.remove(inviter);
        if (inviters.isEmpty()) {
            pendingInvites.remove(recipient.getClientUsername());
        }
        sendToPlayer(inviter, new Message(Protocol.INVITE_REJECT, recipient.getClientUsername()));
    }

    /**
     * [PHẦN PHỤ TRÁCH: SERVER AUTHORITATIVE + XỬ LÝ ĐỒNG THỜI]
     * Client chỉ được gửi hướng. Server tự tìm Player từ phiên đăng nhập, kiểm tra
     * phòng, áp dụng va chạm, nhặt rương, tính điểm và cuối cùng phát GAME_STATE.
     * synchronized ở Manager giữ thứ tự chung; synchronized(room) bảo vệ toàn bộ
     * chuỗi thay đổi của một phòng trước các luồng ClientHandler và luồng timer.
     */
    public synchronized void move(ClientHandler sender, String direction) {
        Player player = sender.getPlayer();
        if (clientsByUsername.get(sender.getClientUsername()) != sender || player == null) {
            sender.sendMessage(new Message(Protocol.ERROR, "Bạn cần đăng nhập trước"));
            return;
        }
        if (player.getStatus() != Player.Status.PLAYING) {
            sender.sendMessage(new Message(Protocol.ERROR, "Bạn không đang PLAYING"));
            return;
        }
        for (GameRoom room : gameRooms) {
            if (room.getPlayer1() != player && room.getPlayer2() != player) {
                continue;
            }
            // Một critical section bao trọn: di chuyển -> nhặt -> cộng điểm/sinh rương -> gửi snapshot.
            synchronized (room) {
                String error = room.handleMove(sender.getClientUsername(), direction);
                if (error != null) {
                    // Khi bị choáng, client có thể đang giữ phím và gửi nhiều MOVE.
                    // Bỏ qua im lặng để không làm đầy hàng đợi socket bằng ERROR lặp lại.
                    if (GameRoom.MOVE_STUNNED.equals(error)) {
                        return;
                    }
                    sender.sendMessage(new Message(Protocol.ERROR, error));
                    return;
                }
                Treasure collected = room.collectTreasure(player);
                TrapEvent trap = room.pollTrapEvent();
                if (trap != null) {
                    // Event bẫy phải đi trước snapshot điểm mới và giống nhau ở hai client.
                    sendToRoom(room, new Message(Protocol.TRAP_TRIGGERED, trap.encode()));
                } else if (collected != null) {
                    System.out.println(player.getUsername() + " collected " + collected.getType() + " +"
                            + Treasure.valueForType(collected.getType()));
                    Message notice = new Message(Protocol.TREASURE_COLLECTED,
                            player.getUsername() + ";" + collected.getType() + ";"
                                    + Treasure.valueForType(collected.getType()) + ";" + player.getMatchScore());
                    sendToRoom(room, notice);
                }
                // Client không tự cập nhật tọa độ/điểm. Snapshot authoritative này mới
                // là dữ liệu cuối cùng mà cả hai giao diện dùng để render.
                Message state = new Message(Protocol.GAME_STATE, GameStateCodec.encode(room.getGameState()));
                sendToRoom(room, state);
            }
            return;
        }
        sender.sendMessage(new Message(Protocol.ERROR, "Bạn không thuộc phòng thi đấu nào"));
    }

    private String validateFreePlayers(ClientHandler sender, String otherUsername) {
        if (clientsByUsername.get(sender.getClientUsername()) != sender || sender.getPlayer() == null) {
            return "Bạn cần đăng nhập trước";
        }
        if (sender.getClientUsername().equals(otherUsername)) {
            return "Không được mời chính mình";
        }
        Player other = otherUsername == null ? null : onlinePlayers.get(otherUsername);
        if (other == null) {
            return "Đối thủ không online hoặc đã ngắt kết nối";
        }
        if (sender.getPlayer().getStatus() != Player.Status.FREE || other.getStatus() != Player.Status.FREE) {
            return "Cả hai người chơi phải đang FREE";
        }
        return null;
    }

    private boolean hasInvite(String recipient, String inviter) {
        Set<String> inviters = pendingInvites.get(recipient);
        return inviters != null && inviters.contains(inviter);
    }

    private void clearInvitesFor(String username) {
        pendingInvites.remove(username);
        pendingInvites.values().forEach(inviters -> inviters.remove(username));
        pendingInvites.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    public List<ClientHandler> getClients() {
        return new ArrayList<>(clientsByUsername.values());
    }

    public synchronized List<GameRoom> getGameRooms() {
        return new ArrayList<>(gameRooms);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
