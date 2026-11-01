import client.HistoryPanel;
import client.RankingPanel;
import common.HistoryCodec;
import common.RankingCodec;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import model.GameResult;
import model.MatchHistory;
import model.Player;

public class StatisticsPanelRenderTest {
    static void check(boolean ok, String message) { if (!ok) { throw new AssertionError(message); } }
    static void layout(Container parent) {
        parent.doLayout();
        for (Component child : parent.getComponents()) { if (child instanceof Container) { layout((Container) child); } }
    }
    static Component named(Container parent, String name) {
        for (Component child : parent.getComponents()) {
            if (name.equals(child.getName())) { return child; }
            if (child instanceof Container) { Component result = named((Container) child, name); if (result != null) { return result; } }
        }
        return null;
    }
    static void tableFits(JTable table) {
        check(table.getTableHeader().getHeight() > 0, "Visible table header");
        for (int col = 0; col < table.getColumnCount(); col++) {
            int width = table.getColumnModel().getColumn(col).getWidth();
            check(table.getTableHeader().getFontMetrics(table.getTableHeader().getFont()).stringWidth(table.getColumnName(col)) + 8 <= width,
                    "Header fits: " + table.getColumnName(col));
            for (int row = 0; row < table.getRowCount(); row++) {
                check(table.getFontMetrics(table.getFont()).stringWidth(table.getValueAt(row, col).toString()) + 8 <= width,
                        "Cell text fits column");
            }
        }
    }
    static void render(JPanel panel, Path file, int width, int height) throws Exception {
        panel.setSize(width, height); layout(panel);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics(); panel.paint(g); g.dispose();
        ImageIO.write(image, "png", file.toFile());
        check(Arrays.stream(((java.awt.image.DataBufferInt) image.getRaster().getDataBuffer()).getData()).distinct().count() > 200, "Colorful nonblank fantasy screen");
        checkUi(panel, panel);
    }

    static void checkUi(Container parent, JPanel root) {
        for (Component child : parent.getComponents()) {
            if (child instanceof JButton && child.getName() != null) {
                JButton button = (JButton) child;
                check(button.getFontMetrics(button.getFont()).stringWidth(button.getText()) + 16 <= button.getWidth(), "Button text fits: " + button.getName());
                java.awt.Point point = SwingUtilities.convertPoint(button, 0, 0, root);
                check(new java.awt.Rectangle(0, 0, root.getWidth(), root.getHeight()).contains(new java.awt.Rectangle(point.x, point.y, button.getWidth(), button.getHeight())), "Footer buttons always visible");
            }
            if (child instanceof javax.swing.JLabel && child.isVisible()) {
                javax.swing.JLabel label = (javax.swing.JLabel) child;
                check(label.getFontMetrics(label.getFont()).stringWidth(label.getText()) <= label.getWidth(), "Heading/empty label fits");
                check(label.getFontMetrics(label.getFont()).getHeight() <= label.getHeight(), "Heading/empty label height fits");
            }
            if (child instanceof Container && !(child instanceof JTable)) { checkUi((Container) child, root); }
        }
        if (parent == root) {
            java.util.List<java.awt.Rectangle> previous = new java.util.ArrayList<>();
            for (Component child : parent.getComponents()) {
                for (java.awt.Rectangle rectangle : previous) { check(!rectangle.intersects(child.getBounds()), "Header/table/footer do not overlap"); }
                previous.add(child.getBounds());
            }
        }
    }

    static void bottomReachable(JPanel panel, JTable table) {
        JScrollPane scroll = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, table);
        scroll.getVerticalScrollBar().setValue(scroll.getVerticalScrollBar().getMaximum());
        scroll.getHorizontalScrollBar().setValue(scroll.getHorizontalScrollBar().getMaximum()); layout(panel);
        java.awt.Rectangle cell = table.getCellRect(table.getRowCount() - 1, table.getColumnCount() - 1, true);
        java.awt.Point point = SwingUtilities.convertPoint(table, cell.x, cell.y, scroll.getViewport());
        check(point.x >= 0 && point.y >= 0 && point.x + cell.width <= scroll.getViewport().getWidth()
                && point.y + cell.height <= scroll.getViewport().getHeight(), "Last row and column fully reachable through scrollbars");
        check(table.getTableHeader().getHeight() > 0, "Header remains visible while scrolling");
    }

    static void extended(Path directory, List<MatchHistory> fixture, String username) throws Exception {
        java.util.List<Player> players = new java.util.ArrayList<>();
        for (int i = 0; i < 40; i++) {
            Player player = new Player(); player.setUsername("Explorer" + i); player.setTotalRankPoint(120 - i);
            player.setWins(40 - i); player.setTotalMatches(50); player.setTotalTreasureCollected(100 - i); players.add(player);
        }
        java.util.List<MatchHistory> manyMatches = new java.util.ArrayList<>();
        for (int i = 0; i < 60; i++) {
            GameResult source = fixture.get(i % fixture.size()).getResult();
            manyMatches.add(new MatchHistory(new GameResult("history-room-" + i, username, "Explorer" + i, source.getWinner(),
                    source.getPlayer1Score(), source.getPlayer2Score(), source.getPlayer1Collected(), source.getPlayer2Collected(),
                    source.getPlayer1RankGain(), source.getPlayer2RankGain(), source.getReason()), 1700000000000L + i * 1000, i % 181));
        }
        String rankingPayload = RankingCodec.encode(players), historyPayload = HistoryCodec.encode(manyMatches);
        for (int[] size : new int[][] {{1100, 700}, {1084, 661}, {900, 600}, {700, 450}, {684, 411}, {1400, 900}}) {
            for (int count : new int[] {0, 1, 40}) {
                RankingPanel panel = new RankingPanel(() -> { }, () -> { }); panel.setRanking(players.subList(0, count));
                render(panel, directory.resolve("ranking-" + count + "-" + size[0] + "x" + size[1] + ".png"), size[0], size[1]);
                JTable table = (JTable) named(panel, "ranking-table"); tableFits(table);
                check(named(panel, "ranking-empty").isVisible() == (count == 0), "Ranking empty message");
                java.awt.Color[] medals = {new java.awt.Color(247, 198, 76), new java.awt.Color(213, 231, 237), new java.awt.Color(225, 163, 106)};
                for (int row = 0; row < Math.min(count, 3); row++) {
                    javax.swing.JLabel cell = (javax.swing.JLabel) table.prepareRenderer(table.getCellRenderer(row, 0), row, 0);
                    check(cell.getForeground().equals(medals[row]) && cell.getText().equals("#" + (row + 1)), "Gold/silver/bronze podium styling");
                }
                for (int row = 0; row < count; row++) { check(table.getValueAt(row, 1).equals(players.get(row).getUsername()), "No client ranking re-sort"); }
                if (count > 1) { check(((JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, table)).getVerticalScrollBar().isVisible(), "Many ranking rows scroll"); bottomReachable(panel, table); }
            }
            for (int count : new int[] {0, 60}) {
                HistoryPanel panel = new HistoryPanel(() -> { }, () -> { }); panel.setCurrentUsername(username); panel.setHistory(manyMatches.subList(0, count));
                render(panel, directory.resolve("history-" + count + "-" + size[0] + "x" + size[1] + ".png"), size[0], size[1]);
                JTable table = (JTable) named(panel, "history-table"); tableFits(table);
                check(named(panel, "history-empty").isVisible() == (count == 0), "History empty message");
                check(table.convertColumnIndexToModel(0) == 1 && table.convertColumnIndexToModel(9) == 0, "Opponent first, room id still available last");
                java.awt.Color[] results = {new java.awt.Color(122, 230, 154), new java.awt.Color(255, 140, 161), new java.awt.Color(122, 230, 154), new java.awt.Color(171, 222, 237)};
                for (int row = 0; row < Math.min(count, 4); row++) {
                    javax.swing.JLabel cell = (javax.swing.JLabel) table.prepareRenderer(table.getCellRenderer(row, 1), row, 1);
                    check(cell.getForeground().equals(results[row]), "History WIN/LOSE/DRAW colors");
                    check(table.getModel().getValueAt(row, 9).equals(manyMatches.get(row).getDurationSeconds()), "Duration from server history");
                }
                if (count > 0) { bottomReachable(panel, table); }
            }
        }
        check(rankingPayload.equals(RankingCodec.encode(players)) && historyPayload.equals(HistoryCodec.encode(manyMatches)), "Rendering never changes server statistics/history");
        Player longName = new Player(); longName.setUsername("Nh\u00e0Th\u00e1mHi\u1ec3mKhoB\u00e1uT\u00eanR\u1ea5tD\u00e0i");
        longName.setTotalRankPoint(Integer.MAX_VALUE); longName.setWins(Integer.MAX_VALUE); longName.setTotalMatches(Integer.MAX_VALUE); longName.setTotalTreasureCollected(Integer.MAX_VALUE);
        RankingPanel longRanking = new RankingPanel(() -> { }, () -> { }); longRanking.setRanking(List.of(longName));
        render(longRanking, directory.resolve("ranking-long-name.png"), 684, 411); JTable lt = (JTable) named(longRanking, "ranking-table"); tableFits(lt);
        check(((JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, lt)).getHorizontalScrollBar().isVisible(), "Long values preserved through horizontal scroll"); bottomReachable(longRanking, lt);
    }
    public static void main(String[] args) throws Exception {
        Path directory = Path.of("out", "test-screenshots"); Files.createDirectories(directory);
        SwingUtilities.invokeAndWait(() -> {
            try {
                Player a = new Player(); a.setUsername("Nguy\u1ec5n An"); a.setTotalRankPoint(7);
                a.setWins(2); a.setTotalMatches(5); a.setTotalTreasureCollected(11);
                Player b = new Player(); b.setUsername("Nina"); b.setTotalRankPoint(4);
                b.setWins(1); b.setTotalMatches(5); b.setTotalTreasureCollected(7);
                List<MatchHistory> matches = Arrays.asList(
                        new MatchHistory(new GameResult("00000000-0000-0000-0000-000000000001", a.getUsername(), "Nina", GameResult.PLAYER1,
                                30, 10, 3, 1, 3, 0), 1700000000000L, 180),
                        new MatchHistory(new GameResult("00000000-0000-0000-0000-000000000002", a.getUsername(), "Nina", GameResult.PLAYER2,
                                100, 0, 4, 0, 0, 3, GameResult.PLAYER_EXIT), 1700000001000L, 42),
                        new MatchHistory(new GameResult("00000000-0000-0000-0000-000000000003", a.getUsername(), "Nina", GameResult.PLAYER1,
                                0, 100, 0, 4, 3, 0, GameResult.DISCONNECT), 1700000002000L, 20),
                        new MatchHistory(new GameResult("00000000-0000-0000-0000-000000000004", a.getUsername(), "Nina", GameResult.DRAW,
                                30, 30, 1, 3, 1, 1), 1700000003000L, 180));
                for (int[] size : new int[][] {{900, 600}, {700, 450}}) {
                    AtomicInteger refresh = new AtomicInteger(), lobby = new AtomicInteger();
                    RankingPanel rank = new RankingPanel(refresh::incrementAndGet, lobby::incrementAndGet);
                    rank.setRanking(RankingCodec.decode(RankingCodec.encode(List.of(a, b))));
                    render(rank, directory.resolve("ranking-" + size[0] + "x" + size[1] + ".png"), size[0], size[1]);
                    JTable table = (JTable) named(rank, "ranking-table"); tableFits(table);
                    check(table.getRowCount() == 2 && Integer.valueOf(1).equals(table.getValueAt(0, 0))
                            && Integer.valueOf(11).equals(table.getValueAt(0, 4)) && Integer.valueOf(5).equals(table.getValueAt(0, 5)), "Rank/lifetime columns");
                    ((JButton) named(rank, "ranking-refresh")).doClick(0); ((JButton) named(rank, "ranking-lobby")).doClick(0);
                    check(refresh.get() == 1 && lobby.get() == 1, "Ranking buttons");
                    rank.setRanking(List.of()); check(table.getRowCount() == 0, "Empty ranking");
                    HistoryPanel history = new HistoryPanel(refresh::incrementAndGet, lobby::incrementAndGet);
                    history.setCurrentUsername(a.getUsername());
                    history.setHistory(HistoryCodec.decode(HistoryCodec.encode(matches)));
                    render(history, directory.resolve("history-" + size[0] + "x" + size[1] + ".png"), size[0], size[1]);
                    JTable ht = (JTable) named(history, "history-table"); tableFits(ht);
                    check(ht.getRowCount() == 4 && "TH\u1eaeNG".equals(ht.getModel().getValueAt(0, 2)) && "THUA".equals(ht.getModel().getValueAt(1, 2))
                            && "H\u00d2A".equals(ht.getModel().getValueAt(3, 2)), "Own score/forfeit/draw outcomes");
                    JScrollPane scroll = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, ht);
                    check(scroll.getHorizontalScrollBar().isVisible(), "Wide history uses horizontal scrolling");
                    scroll.getHorizontalScrollBar().setValue(scroll.getHorizontalScrollBar().getMaximum());
                    render(history, directory.resolve("history-right-" + size[0] + "x" + size[1] + ".png"), size[0], size[1]);
                    history.setCurrentUsername("Nina"); history.setHistory(matches);
                    check("THUA".equals(ht.getModel().getValueAt(0, 2)) && "TH\u1eaeNG".equals(ht.getModel().getValueAt(1, 2))
                            && Integer.valueOf(10).equals(ht.getModel().getValueAt(0, 3)), "P2 perspective");
                    history.setCurrentUsername("Outsider");
                    try { history.setHistory(matches); throw new AssertionError("Foreign history accepted"); }
                    catch (IllegalArgumentException expected) { }
                    ((JButton) named(history, "history-refresh")).doClick(0); ((JButton) named(history, "history-lobby")).doClick(0);
                    check(refresh.get() == 2 && lobby.get() == 2, "History buttons");
                    history.setHistory(List.of()); check(ht.getRowCount() == 0, "Empty history");
                }
                extended(directory, matches, a.getUsername());
            } catch (Exception e) { throw new RuntimeException(e); }
        });
        System.out.println("PASS: ranking/history tables, UTF-8, statistics, both perspectives, empty states, callbacks and 900x600/700x450 rendering");
        System.out.println("PASS: six sizes, empty/many rows, gold/silver/bronze, WIN/LOSE/DRAW colors, full scroll reachability, long values and no data mutation");
        System.out.println("ALL STATISTICS RENDER TESTS PASSED");
    }
}
