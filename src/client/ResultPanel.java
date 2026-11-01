package client;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import model.GameResult;

public class ResultPanel extends FantasyScreen {
    private final JLabel outcomeLabel = label(" ", 28, GameTheme.GOLD);
    private final JLabel roomLabel = label(" ", 11, GameTheme.INK);
    private final JLabel rankLabel = label(" ", 17, GameTheme.GOLD);
    private final JLabel reasonLabel = label(" ", 14, GameTheme.INK);
    private final ResultPlayerCard first = new ResultPlayerCard(1, BoardPanel.BLUE), second = new ResultPlayerCard(2, BoardPanel.PINK);
    private String currentUsername;
    private GameResult currentResult;
    private final GameButton playAgainButton = new GameButton("CH\u01a0I TI\u1ebeP", true);
    private final JPanel trophy = new JPanel() {
        { setOpaque(false); }
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create(); GameTheme.smooth(g);
            double scale = Math.min(1, getHeight() / 94.0);
            g.translate((getWidth() - 110 * scale) / 2, 0); g.scale(scale, scale); g.setColor(new Color(158, 100, 25));
            g.setStroke(new java.awt.BasicStroke(6)); g.drawArc(3, 9, 30, 43, 70, 240); g.drawArc(77, 9, 30, 43, -130, 240);
            g.setColor(GameTheme.GOLD); g.fillRoundRect(24, 5, 62, 52, 8, 8);
            g.fillRect(49, 54, 12, 21); g.fillRoundRect(30, 75, 50, 12, 4, 4);
            g.setColor(new Color(255, 237, 145)); g.fillPolygon(new int[] {55, 61, 72, 63, 65, 55, 45, 47, 38, 49},
                    new int[] {15, 27, 28, 37, 47, 42, 47, 37, 28, 27}, 10); g.dispose();
        }
    };

    private final class ResultBody extends JPanel implements Scrollable {
        ResultBody() { super(null); setOpaque(false); }
        private boolean compact() { return getParent() != null && getParent().getHeight() > 0 && getParent().getHeight() < 320; }
        @Override public Dimension getPreferredSize() { return new Dimension(750, compact() ? 248 : 420); }
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 24; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return Math.max(24, r.height - 24); }
        @Override public void doLayout() {
            boolean compact = compact();
            int width = getWidth(), cardWidth = width < 800 ? 165 : 230, centerX = cardWidth + 20, centerWidth = width - 2 * cardWidth - 40;
            first.setBounds(4, compact ? 0 : 8, cardWidth, compact ? 220 : 376);
            second.setBounds(width - cardWidth - 4, compact ? 0 : 8, cardWidth, compact ? 220 : 376);
            trophy.setBounds(centerX, compact ? 10 : 55, centerWidth, compact ? 50 : 94);
            outcomeLabel.setFont(GameTheme.BODY.deriveFont(Font.BOLD, compact ? 24f : 28f));
            outcomeLabel.setBounds(centerX, compact ? 74 : 167, centerWidth, compact ? 34 : 43);
            rankLabel.setFont(GameTheme.BODY.deriveFont(Font.BOLD, compact ? 14f : 17f));
            rankLabel.setBounds(centerX, compact ? 117 : 221, centerWidth, 26);
            reasonLabel.setFont(GameTheme.BODY.deriveFont(Font.BOLD, compact ? 12f : 14f));
            reasonLabel.setBounds(centerX + 8, compact ? 154 : 268, centerWidth - 16, 26);
            roomLabel.setBounds(8, compact ? 226 : 394, width - 16, 20);
            String room = roomLabel.getToolTipText();
            if (room != null) {
                String fitted = room;
                while (!fitted.isEmpty() && roomLabel.getFontMetrics(roomLabel.getFont()).stringWidth(fitted + "...") > roomLabel.getWidth()) {
                    fitted = fitted.substring(0, fitted.offsetByCodePoints(fitted.length(), -1));
                }
                roomLabel.setText(roomLabel.getFontMetrics(roomLabel.getFont()).stringWidth(room) <= roomLabel.getWidth() ? room : fitted + "...");
            }
            first.doLayout(); second.doLayout();
        }
    }

    public ResultPanel() { this(() -> { }); }
    public ResultPanel(Runnable returnToLobby) { this(returnToLobby, () -> { }); }
    public ResultPanel(Runnable returnToLobby, Runnable playAgain) {
        super("K\u1ebeT QU\u1ea2 TR\u1eacN \u0110\u1ea4U");
        outcomeLabel.setName("result-outcome"); roomLabel.setName("result-room"); rankLabel.setName("result-rank");
        reasonLabel.setName("result-reason");
        ResultBody body = new ResultBody(); body.setName("result-comparison");
        body.add(first); body.add(second); body.add(trophy); body.add(outcomeLabel); body.add(rankLabel); body.add(reasonLabel); body.add(roomLabel);
        JScrollPane scroll = new JScrollPane(body); scroll.setName("result-scroll"); GameTheme.styleScrollPane(scroll);
        scroll.getViewport().addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent event) { body.revalidate(); }
        });
        add(scroll, java.awt.BorderLayout.CENTER);
        GameButton lobbyButton = new GameButton("V\u1ec0 S\u1ea2NH", false); lobbyButton.setName("return-lobby");
        lobbyButton.addActionListener(event -> returnToLobby.run());
        playAgainButton.setName("play-again"); playAgainButton.setEnabled(false);
        playAgainButton.addActionListener(event -> {
            if (currentResult != null && playAgainButton.isEnabled()) { playAgainButton.setEnabled(false); playAgain.run(); }
        }); addFooter(playAgainButton, lobbyButton);
    }

    public void setCurrentUsername(String username) {
        currentUsername = username; if (currentResult != null) { setResult(currentResult); }
    }

    public void setResult(GameResult result) {
        currentResult = result; playAgainButton.setEnabled(!GameResult.DISCONNECT.equals(result.getReason()));
        boolean draw = GameResult.DRAW.equals(result.getWinner()), p1Wins = GameResult.PLAYER1.equals(result.getWinner());
        String winner = p1Wins ? result.getPlayer1Username() : result.getPlayer2Username();
        boolean p1 = result.getPlayer1Username().equals(currentUsername);
        boolean participant = p1 || result.getPlayer2Username().equals(currentUsername), won = winner.equals(currentUsername);
        outcomeLabel.setText(draw ? "H\u00d2A" : participant ? won ? "CHI\u1ebeN TH\u1eaeNG!" : "TH\u1ea4T B\u1ea0I" : "K\u1ebeT QU\u1ea2");
        outcomeLabel.setForeground(draw ? FantasyTable.DRAW.darker() : won ? new Color(21, 100, 63) : new Color(164, 34, 64));
        rankLabel.setText(participant ? "+" + (p1 ? result.getPlayer1RankGain() : result.getPlayer2RankGain()) + " \u0111i\u1ec3m x\u1ebfp h\u1ea1ng" : " ");
        rankLabel.setForeground(new Color(110, 69, 19));
        reasonLabel.setText(reasonText(result, currentUsername));
        roomLabel.setToolTipText(common.RoomName.display(result.getRoomId())); roomLabel.setText(roomLabel.getToolTipText());
        first.setData(result.getPlayer1Username(), result.getPlayer1Score(), result.getPlayer1Collected(), draw ? "H\u00d2A" : p1Wins ? "TH\u1eaeNG" : "THUA", result.getPlayer1RankGain(), p1);
        second.setData(result.getPlayer2Username(), result.getPlayer2Score(), result.getPlayer2Collected(), draw ? "H\u00d2A" : p1Wins ? "THUA" : "TH\u1eaeNG", result.getPlayer2RankGain(), result.getPlayer2Username().equals(currentUsername));
        revalidate(); repaint();
    }

    static String reasonText(GameResult result, String username) {
        if (GameResult.TIME_UP.equals(result.getReason())) { return "H\u1ebft th\u1eddi gian"; }
        String winner = GameResult.PLAYER1.equals(result.getWinner()) ? result.getPlayer1Username() : result.getPlayer2Username();
        boolean ownLoss = !winner.equals(username) && (result.getPlayer1Username().equals(username) || result.getPlayer2Username().equals(username));
        return GameResult.PLAYER_EXIT.equals(result.getReason())
                ? ownLoss ? "B\u1ea1n \u0111\u00e3 r\u1eddi tr\u1eadn" : "\u0110\u1ed1i th\u1ee7 r\u1eddi tr\u1eadn"
                : ownLoss ? "B\u1ea1n m\u1ea5t k\u1ebft n\u1ed1i" : "\u0110\u1ed1i th\u1ee7 m\u1ea5t k\u1ebft n\u1ed1i";
    }

    public GameResult getResult() { return currentResult; }
    public void setRematchEnabled(boolean enabled) { playAgainButton.setEnabled(enabled); }
    public String getOpponent() {
        if (currentResult == null) { return null; }
        return currentResult.getPlayer1Username().equals(currentUsername) ? currentResult.getPlayer2Username() : currentResult.getPlayer1Username();
    }
}
