package client;

import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import model.GameState;
import model.GameResult;
import common.TrapEvent;
import model.Player;
import model.Treasure;

public class GamePanel extends JPanel {
    private final JPanel header = new JPanel(null) {
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create(); GameTheme.smooth(g);
            int width = Math.min(570, getWidth() - 32), x = (getWidth() - width) / 2;
            g.setColor(new Color(42, 52, 41, 70)); g.fillRoundRect(x + 2, 8, width, 52, 8, 8);
            g.setPaint(new GradientPaint(0, 3, new Color(152, 99, 49), 0, 52, new Color(92, 57, 31)));
            g.fillRoundRect(x, 3, width, 52, 8, 8); g.setColor(GameTheme.GOLD); g.drawRoundRect(x, 3, width, 52, 8, 8);
            g.setColor(new Color(204, 145, 81, 100)); g.drawLine(x + 10, 45, x + width - 10, 45); g.dispose();
        }
    };
    private final JLabel title = label("\u0110UA S\u0102N KHO B\u00c1U", 26, GameTheme.GOLD);
    private final JLabel subtitle = label("THU TH\u1eacP NHI\u1ec0U KHO B\u00c1U NH\u1ea4T \u0110\u1ec2 CHI\u1ebeN TH\u1eaeNG", 11, new Color(255, 237, 200));
    private final JLabel timeLabel = label("--:--", 32, GameTheme.TEXT);
    private final JLabel roomLabel = label("", 11, GameTheme.INK);
    private final PlayerHud player1Hud = new PlayerHud(1, BoardPanel.BLUE);
    private final PlayerHud player2Hud = new PlayerHud(2, BoardPanel.PINK);
    private final BoardPanel board = new BoardPanel(this::updateMoveActions);
    private final SoundManager sound;
    private final boolean ownsSound;
    private final SoundToggle soundToggle;
    private final Set<String> resultRooms = new HashSet<>();
    private long lastTrapEventId = -1;
    private boolean musicStarted;
    private boolean closed;
    private final RoundedPanel noticePanel = new RoundedPanel();
    private final JLabel treasureStatusLabel = label(" ", 13, GameTheme.GOLD);
    private final JLabel moveStatusLabel = label(" ", 12, new Color(137, 28, 54));
    private final JLabel keyboardHint = label("WASD / Ph\u00edm m\u0169i t\u00ean \u0111\u1ec3 di chuy\u1ec3n", 11, GameTheme.INK);
    private GameState currentState;
    private String currentUsername;
    private final Consumer<String> moveSender;
    private final List<Action> moveActions = new ArrayList<>();
    private final List<GameButton> arrows = new ArrayList<>();
    private final GameButton exitButton = new GameButton("THO\u00c1T TR\u1eacN", GameButton.Variant.RED);
    private final Timer noticeTimer = new Timer(2800, event -> clearNotice());
    private String noticeText;

    public GamePanel() { this(direction -> { }); }
    public GamePanel(Consumer<String> moveSender) { this(moveSender, () -> { }); }

    public GamePanel(Consumer<String> moveSender, Runnable exitSender) {
        this(moveSender, exitSender, new SoundManager(), true);
    }

    public GamePanel(Consumer<String> moveSender, Runnable exitSender, SoundManager sound) {
        this(moveSender, exitSender, sound, false);
    }

    private GamePanel(Consumer<String> moveSender, Runnable exitSender, SoundManager sound, boolean ownsSound) {
        super(null); this.moveSender = moveSender; setName("fantasy-game"); setOpaque(false);
        this.sound = Objects.requireNonNull(sound); this.ownsSound = ownsSound;
        soundToggle = new SoundToggle(sound, "sound-toggle");
        header.setOpaque(false); timeLabel.setName("server-time"); timeLabel.setOpaque(true); timeLabel.setBackground(GameTheme.PANEL);
        timeLabel.setForeground(GameTheme.GOLD); roomLabel.setName("game-room");
        for (JLabel label : new JLabel[] {title, subtitle, timeLabel}) { header.add(label); }
        add(header); add(player1Hud); add(player2Hud); add(board); add(roomLabel); add(keyboardHint); add(moveStatusLabel);
        noticePanel.setLayout(null); noticePanel.setName("collection-notification"); noticePanel.setVisible(false);
        treasureStatusLabel.setName("treasure-notice"); noticePanel.add(treasureStatusLabel); header.add(noticePanel);
        // This timer only dismisses a UI notification; it never changes match state or time.
        noticeTimer.setRepeats(false);
        updateSoundToggle(); header.add(soundToggle);
        createMoveButton("UP", "\u2191", KeyEvent.VK_W, KeyEvent.VK_UP);
        createMoveButton("LEFT", "\u2190", KeyEvent.VK_A, KeyEvent.VK_LEFT);
        createMoveButton("DOWN", "\u2193", KeyEvent.VK_S, KeyEvent.VK_DOWN);
        createMoveButton("RIGHT", "\u2192", KeyEvent.VK_D, KeyEvent.VK_RIGHT);
        exitButton.setName("exit-game"); exitButton.setFont(GameTheme.BODY.deriveFont(Font.BOLD, 12f)); exitButton.setEnabled(false);
        exitButton.setFocusable(false);
        exitButton.addActionListener(event -> {
            if (isVisible() && canParticipate() && confirmExit() && canParticipate()) { exitSender.run(); }
        }); add(exitButton);
        UiSounds.install(this, sound);
    }

    void installSounds(java.awt.Component root) { UiSounds.install(root, sound); }

    protected boolean confirmExit() {
        JOptionPane pane = new JOptionPane(
                "Tho\u00e1t tr\u1eadn s\u1ebd b\u1ecb t\u00ednh thua. B\u1ea1n ch\u1eafc ch\u1eafn?",
                JOptionPane.QUESTION_MESSAGE, JOptionPane.YES_NO_OPTION);
        UiSounds.install(pane, sound);
        javax.swing.JDialog dialog = pane.createDialog(this, "Tho\u00e1t tr\u1eadn");
        try {
            dialog.setVisible(true);
            return Integer.valueOf(JOptionPane.YES_OPTION).equals(pane.getValue());
        } finally { dialog.dispose(); }
    }

    private static JLabel label(String text, int size, Color color) {
        JLabel label = new JLabel(text, JLabel.CENTER); label.setFont(GameTheme.BODY.deriveFont(Font.BOLD, (float) size));
        label.setForeground(color); return label;
    }

    @Override public void doLayout() {
        boolean compact = getHeight() < 500;
        int width = getWidth(), height = getHeight(), headerHeight = compact ? 126 : 150, footerHeight = compact ? 68 : 84;
        header.setBounds(0, 8, width, headerHeight);
        title.setBounds(12, 1, width - 24, 36); subtitle.setBounds(12, 38, width - 24, 17);
        timeLabel.setFont(GameTheme.BODY.deriveFont(Font.BOLD, compact ? 26f : 32f));
        timeLabel.setBounds((width - 140) / 2, compact ? 57 : 64, 140, compact ? 36 : 44);
        soundToggle.setBounds(width / 2 + 82, compact ? 59 : 70, 32, 32);
        int bodyY = headerHeight + 16, bodyHeight = Math.max(0, height - bodyY - footerHeight - 8);
        int hudWidth = width < 800 ? 140 : 180, hudHeight = Math.min(350, bodyHeight);
        player1Hud.setBounds(12, bodyY, hudWidth, hudHeight);
        player2Hud.setBounds(width - hudWidth - 12, bodyY, hudWidth, hudHeight);
        int centerWidth = Math.max(0, width - hudWidth * 2 - 48), side = Math.min(centerWidth, bodyHeight);
        board.setBounds((width - side) / 2, bodyY + (bodyHeight - side) / 2, side, side);
        int bottom = height - footerHeight, controlX = (width - 116) / 2;
        arrows.get(0).setBounds(controlX + 40, compact ? bottom - 8 : bottom, 36, 36);
        arrows.get(1).setBounds(controlX, compact ? bottom + 30 : bottom + 38, 36, 36);
        arrows.get(2).setBounds(controlX + 40, compact ? bottom + 30 : bottom + 38, 36, 36);
        arrows.get(3).setBounds(controlX + 80, compact ? bottom + 30 : bottom + 38, 36, 36);
        int sideTextWidth = Math.max(0, controlX - 26);
        roomLabel.setBounds(12, bottom + 2, sideTextWidth, 18); fitLabel(roomLabel, roomLabel.getToolTipText());
        keyboardHint.setBounds(12, bottom + 28, sideTextWidth, 18);
        keyboardHint.setText(width < 800 ? "WASD / \u2191 \u2193 \u2190 \u2192" : "WASD / Ph\u00edm m\u0169i t\u00ean \u0111\u1ec3 di chuy\u1ec3n");
        moveStatusLabel.setBounds(width / 2 + 78, bottom + 2, Math.max(0, width / 2 - 90), 18);
        fitLabel(moveStatusLabel, moveStatusLabel.getToolTipText());
        exitButton.setBounds(width - 152, bottom + 28, 140, 36);
        int noticeWidth = Math.min(760, width - 24);
        noticePanel.setBounds((width - noticeWidth) / 2, compact ? 94 : 118, noticeWidth, 32);
        treasureStatusLabel.setBounds(12, 4, noticePanel.getWidth() - 24, 21); fitLabel(treasureStatusLabel, noticeText);
        player1Hud.doLayout(); player2Hud.doLayout();
    }

    private static void fitLabel(JLabel label, String text) {
        if (text == null || text.isEmpty()) { label.setText(" "); return; }
        String fitted = text;
        while (!fitted.isEmpty() && label.getFontMetrics(label.getFont()).stringWidth(fitted + "...") > label.getWidth()) {
            fitted = fitted.substring(0, fitted.offsetByCodePoints(fitted.length(), -1));
        }
        label.setText(label.getFontMetrics(label.getFont()).stringWidth(text) <= label.getWidth() ? text : fitted + "...");
    }

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create(); TreasureBackground.paint(g, getWidth(), getHeight()); g.dispose();
    }

    private void createMoveButton(String direction, String symbol, int letterKey, int arrowKey) {
        // [PHẦN PHỤ TRÁCH: INPUT DI CHUYỂN Ở CLIENT]
        // Nút/WASD/phím mũi tên chỉ gửi chuỗi hướng lên Server; tuyệt đối không tự
        // đổi Position tại client. Vị trí chỉ đổi khi GAME_STATE mới quay về.
        Action action = new AbstractAction(symbol) {
            @Override public void actionPerformed(ActionEvent event) {
                if (isVisible() && canMove()) {
                    moveStatusLabel.setText(" "); moveStatusLabel.setToolTipText(null); moveSender.accept(direction);
                }
            }
        };
        action.putValue(Action.SHORT_DESCRIPTION, direction); action.setEnabled(false); moveActions.add(action);
        String actionKey = "MOVE_" + direction;
        getActionMap().put(actionKey, action);
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(letterKey, 0), actionKey);
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(arrowKey, 0), actionKey);
        GameButton button = new GameButton(symbol, false); button.setAction(action);
        button.setFont(GameTheme.BODY.deriveFont(Font.BOLD, 20f)); button.setName(actionKey); button.setFocusable(false);
        arrows.add(button); add(button);
    }

    private boolean canParticipate() {
        if (closed || currentState == null || currentUsername == null || !"RUNNING".equals(currentState.getStatus())
                || currentState.getRemainingTime() <= 0) { return false; }
        for (Player player : new Player[] {currentState.getPlayer1(), currentState.getPlayer2()}) {
            if (currentUsername.equals(player.getUsername()) && player.getStatus() == Player.Status.PLAYING) { return true; }
        }
        return false;
    }

    private boolean canMove() { return canParticipate() && !board.isStunned(currentUsername); }

    private void updateMoveActions() {
        exitButton.setEnabled(canParticipate());
        for (Action action : moveActions) { action.setEnabled(canMove()); }
    }

    public void setCurrentUsername(String username) {
        if (!Objects.equals(currentUsername, username)) {
            if (username == null) { resetSession(); } else { clearTransientState(); }
        }
        currentUsername = username;
        if (currentState != null) { setGameState(currentState); } else { updateMoveActions(); }
    }

    public void showServerError(String error) {
        moveStatusLabel.setToolTipText(error); fitLabel(moveStatusLabel, error);
    }

    public static String formatTime(int seconds) {
        if (seconds < 0) { throw new IllegalArgumentException("Negative remaining time"); }
        return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
    }

    public void updateTime(int remainingTime) {
        if (remainingTime < 0) { throw new IllegalArgumentException("Negative remaining time"); }
        if (currentState != null && "RUNNING".equals(currentState.getStatus())) {
            currentState.setRemainingTime(remainingTime); timeLabel.setText(formatTime(remainingTime));
            if (remainingTime == 0) { clearTransientState(); }
            updateMoveActions(); updateMusic();
        }
    }

    public void finishGame() {
        if (currentState != null) { currentState.setStatus("FINISHED"); }
        clearTransientState(); updateMoveActions();
    }

    public void finishGame(GameResult result) {
        if (closed || currentState == null || !currentState.getRoomId().equals(result.getRoomId())
                || currentUsername == null || (!currentUsername.equals(result.getPlayer1Username())
                && !currentUsername.equals(result.getPlayer2Username())) || resultRooms.contains(result.getRoomId())) { return; }
        finishGame();
        resultRooms.add(result.getRoomId());
        if (GameResult.DRAW.equals(result.getWinner())) { return; }
        boolean first = currentUsername.equals(result.getPlayer1Username());
        boolean won = (first ? GameResult.PLAYER1 : GameResult.PLAYER2).equals(result.getWinner());
        sound.play(won ? SoundManager.Effect.WIN : SoundManager.Effect.LOSE);
    }

    public void showTrapTriggered(TrapEvent event) {
        if (event == null || !canParticipate() || !currentState.getRoomId().equals(event.roomId())
                || event.eventId() <= lastTrapEventId || !isPlayingMember(event.username())) { return; }
        lastTrapEventId = event.eventId();
        long received = System.nanoTime();
        board.showStun(event.username(), received + event.stunMillis() * 1_000_000L);
        boolean own = currentUsername.equals(event.username());
        noticeText = own ? "B\u1ea1n \u0111\u00e3 tr\u00fang b\u1eaby! -" + event.pointsLost()
                + " \u0111i\u1ec3m | Cho\u00e1ng 2 gi\u00e2y!"
                : event.username() + " \u0111\u00e3 tr\u00fang b\u1eaby v\u00e0 b\u1ecb cho\u00e1ng!";
        treasureStatusLabel.setToolTipText(noticeText);
        treasureStatusLabel.setForeground(new Color(255, 235, 80));
        noticePanel.setVisible(true); noticeTimer.restart(); doLayout(); repaint();
        sound.play(SoundManager.Effect.TRAP);
    }

    private boolean isPlayingMember(String username) {
        for (Player player : new Player[] {currentState.getPlayer1(), currentState.getPlayer2()}) {
            if (Objects.equals(username, player.getUsername()) && player.getStatus() == Player.Status.PLAYING) { return true; }
        }
        return false;
    }

    public void showTreasureCollected(String username, String type, int value, int totalScore) {
        if (value != Treasure.valueForType(type) || totalScore < value) { throw new IllegalArgumentException("Invalid collection notice"); }
        if (closed || currentState == null || !"RUNNING".equals(currentState.getStatus())
                || currentState.getRemainingTime() <= 0 || !isPlayingMember(username)) { return; }
        String name = Treasure.GOLD.equals(type) ? "R\u01b0\u01a1ng v\u00e0ng" : Treasure.SILVER.equals(type)
                ? "R\u01b0\u01a1ng b\u1ea1c" : "R\u01b0\u01a1ng th\u01b0\u1eddng";
        noticeText = username + " \u0111\u00e3 thu th\u1eadp " + name + " +" + value + " \u0111i\u1ec3m!";
        treasureStatusLabel.setToolTipText(noticeText + " (total: " + totalScore + ")");
        treasureStatusLabel.setForeground(GameTheme.GOLD);
        noticePanel.setVisible(true); noticeTimer.restart(); doLayout(); repaint();
        if (Objects.equals(username, currentUsername)) {
            sound.play(Treasure.GOLD.equals(type) ? SoundManager.Effect.GOLD : SoundManager.Effect.PICKUP);
        }
    }

    private void clearNotice() {
        noticeTimer.stop(); noticeText = null; treasureStatusLabel.setText(" "); treasureStatusLabel.setToolTipText(null);
        noticePanel.setVisible(false); repaint();
    }

    private void clearTransientState() {
        board.clearStuns(); clearNotice(); musicStarted = false;
        if (ownsSound) { sound.stopAll(); }
    }

    public void resetSession() {
        currentState = null; lastTrapEventId = -1; resultRooms.clear(); clearTransientState();
        board.setSnapshot(null, currentUsername); updateMoveActions();
    }

    public void close() {
        if (closed) { return; }
        closed = true; resetSession();
        if (ownsSound) { sound.close(); }
    }

    @Override public void removeNotify() { clearTransientState(); super.removeNotify(); }

    public void setSoundEnabled(boolean enabled) {
        sound.setEnabled(enabled); updateSoundToggle();
    }

    public boolean isSoundEnabled() { return sound.isEnabled(); }

    private void updateSoundToggle() {
        soundToggle.refresh();
    }

    private void updateMusic() {
        updateSoundToggle();
        if (!ownsSound) { return; }
        boolean active = canParticipate();
        if (active && !musicStarted) { sound.startMusic(); }
        else if (!active && musicStarted) { sound.stopMusic(); }
        musicStarted = active;
    }

    public void setPlayers(String player1, String player2) {
        currentState = null; lastTrapEventId = -1; clearTransientState(); updateMoveActions();
        moveStatusLabel.setText(" "); moveStatusLabel.setToolTipText(null);
        Player first = new Player(); first.setUsername(player1); Player second = new Player(); second.setUsername(player2);
        player1Hud.setPlayer(first, currentUsername); player2Hud.setPlayer(second, currentUsername);
        roomLabel.setText(" "); roomLabel.setToolTipText(null); timeLabel.setText("--:--"); board.setSnapshot(null, currentUsername);
    }

    public void setGameState(GameState state) {
        // [PHẦN PHỤ TRÁCH: RENDER SNAPSHOT]
        // Thay toàn bộ snapshot local bằng bản Server gửi, rồi cập nhật HUD, thời gian,
        // bản đồ, người chơi và rương. Đây là điểm duy nhất gameplay được phản ánh lên UI.
        if (closed) { return; }
        if (currentState == null || !currentState.getRoomId().equals(state.getRoomId())) {
            lastTrapEventId = -1; clearTransientState();
        }
        currentState = state; updateMoveActions();
        if (!canParticipate()) { clearTransientState(); }
        updateMusic();
        moveStatusLabel.setText(" "); moveStatusLabel.setToolTipText(null);
        player1Hud.setPlayer(state.getPlayer1(), currentUsername); player2Hud.setPlayer(state.getPlayer2(), currentUsername);
        roomLabel.setToolTipText(common.RoomName.display(state.getRoomId())); fitLabel(roomLabel, roomLabel.getToolTipText());
        timeLabel.setText(formatTime(state.getRemainingTime())); board.setSnapshot(state, currentUsername); repaint();
    }

    public GameState getGameState() { return currentState; }
}
