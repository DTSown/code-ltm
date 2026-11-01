package client;

import java.awt.BorderLayout;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.table.DefaultTableModel;
import model.GameResult;
import model.MatchHistory;

public class HistoryPanel extends FantasyScreen {
    private final DefaultTableModel model = new DefaultTableModel(new String[] {
            "Ph\u00f2ng", "\u0110\u1ed1i th\u1ee7", "K\u1ebft qu\u1ea3", "\u0110i\u1ec3m b\u1ea1n", "\u0110i\u1ec3m \u0111\u1ed1i th\u1ee7",
            "R\u01b0\u01a1ng b\u1ea1n", "R\u01b0\u01a1ng \u0111\u1ed1i th\u1ee7", "L\u00fd do", "K\u1ebft th\u00fac", "Th\u1eddi gian (s)" }, 0) {
        @Override public boolean isCellEditable(int row, int col) { return false; }
    };
    private final FantasyTable table = new FantasyTable(model, false);
    private final JLabel empty = label("Ch\u01b0a c\u00f3 tr\u1eadn \u0111\u1ea5u", 16, GameTheme.TEXT);
    private String username;
    private final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    public HistoryPanel(Runnable refresh, Runnable returnToLobby) {
        super("L\u1ecaCH S\u1eec TR\u1eacN \u0110\u1ea4U");
        table.setName("history-table");
        table.moveColumn(0, 9);
        JPanel body = new JPanel(new BorderLayout(0, 10)); body.setOpaque(false);
        empty.setName("history-empty"); body.add(empty, BorderLayout.NORTH); body.add(table.scrollPane(), BorderLayout.CENTER);
        add(body, BorderLayout.CENTER); fitColumns();
        GameButton refreshButton = new GameButton("L\u00c0M M\u1edaI", true);
        refreshButton.setName("history-refresh");
        refreshButton.addActionListener(event -> refresh.run());
        GameButton lobbyButton = new GameButton("V\u1ec0 S\u1ea2NH", false);
        lobbyButton.setName("history-lobby");
        lobbyButton.addActionListener(event -> returnToLobby.run());
        addFooter(refreshButton, lobbyButton);
    }

    public void setCurrentUsername(String username) { this.username = username; }

    public void setHistory(List<MatchHistory> history) {
        for (MatchHistory match : history) {
            if (!match.containsPlayer(username)) { throw new IllegalArgumentException("History belongs to another player"); }
        }
        model.setRowCount(0);
        for (MatchHistory match : history) {
            GameResult r = match.getResult();
            boolean p1 = r.getPlayer1Username().equals(username);
            boolean win = (p1 ? GameResult.PLAYER1 : GameResult.PLAYER2).equals(r.getWinner());
            String reason = GameResult.TIME_UP.equals(r.getReason()) ? "H\u1ebft th\u1eddi gian"
                    : GameResult.PLAYER_EXIT.equals(r.getReason()) ? "Tho\u00e1t tr\u1eadn" : "M\u1ea5t k\u1ebft n\u1ed1i";
            model.addRow(new Object[] {common.RoomName.display(r.getRoomId()), p1 ? r.getPlayer2Username() : r.getPlayer1Username(),
                    GameResult.DRAW.equals(r.getWinner()) ? "H\u00d2A" : win ? "TH\u1eaeNG" : "THUA",
                    p1 ? r.getPlayer1Score() : r.getPlayer2Score(), p1 ? r.getPlayer2Score() : r.getPlayer1Score(),
                    p1 ? r.getPlayer1Collected() : r.getPlayer2Collected(), p1 ? r.getPlayer2Collected() : r.getPlayer1Collected(),
                    reason, dateFormat.format(Instant.ofEpochMilli(match.getEndedAtEpochMillis())), match.getDurationSeconds()});
        }
        empty.setVisible(history.isEmpty()); fitColumns();
    }

    private void fitColumns() { table.fitColumns(new int[] {150, 100, 110, 130, 120, 140, 160, 185, 125, 170}); }
}
