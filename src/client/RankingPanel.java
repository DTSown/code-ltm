package client;

import java.awt.BorderLayout;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.table.DefaultTableModel;
import model.Player;

public class RankingPanel extends FantasyScreen {
    private final DefaultTableModel model = new DefaultTableModel(new String[] {
            "H\u1ea1ng", "Ng\u01b0\u1eddi ch\u01a1i", "\u0110i\u1ec3m rank", "Th\u1eafng", "Kho b\u00e1u", "S\u1ed1 tr\u1eadn" }, 0) {
        @Override public boolean isCellEditable(int row, int col) { return false; }
        @Override public Class<?> getColumnClass(int col) { return col == 1 ? String.class : Integer.class; }
    };
    private final FantasyTable table = new FantasyTable(model, true);
    private final JLabel empty = label("Ch\u01b0a c\u00f3 ng\u01b0\u1eddi ch\u01a1i", 16, GameTheme.TEXT);

    public RankingPanel(Runnable refresh, Runnable returnToLobby) {
        super("B\u1ea2NG X\u1ebeP H\u1ea0NG");
        table.setName("ranking-table");
        JPanel body = new JPanel(new BorderLayout(0, 10)); body.setOpaque(false);
        empty.setName("ranking-empty"); body.add(empty, BorderLayout.NORTH); body.add(table.scrollPane(), BorderLayout.CENTER);
        add(body, BorderLayout.CENTER); table.fitColumns(new int[] {62, 180, 110, 80, 100, 90});
        GameButton refreshButton = new GameButton("L\u00c0M M\u1edaI", true);
        refreshButton.setName("ranking-refresh");
        refreshButton.addActionListener(event -> refresh.run());
        GameButton lobbyButton = new GameButton("V\u1ec0 S\u1ea2NH", false);
        lobbyButton.setName("ranking-lobby");
        lobbyButton.addActionListener(event -> returnToLobby.run());
        addFooter(refreshButton, lobbyButton);
    }

    public void setRanking(List<Player> players) {
        model.setRowCount(0);
        int rank = 1;
        for (Player p : players) {
            model.addRow(new Object[] {rank++, p.getUsername(), p.getTotalRankPoint(), p.getWins(),
                    p.getTotalTreasureCollected(), p.getTotalMatches()});
        }
        empty.setVisible(players.isEmpty()); table.fitColumns(new int[] {62, 180, 110, 80, 100, 90});
    }
}
