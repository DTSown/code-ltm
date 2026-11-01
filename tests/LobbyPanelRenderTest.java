import client.InvitationPanel;
import client.LobbyPanel;
import client.PlayerCard;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import model.Player;

public class LobbyPanelRenderTest {
    static void check(boolean ok, String message) { if (!ok) { throw new AssertionError(message); } }
    static Component named(Container parent, String name) {
        for (Component c : parent.getComponents()) {
            if (name.equals(c.getName())) { return c; }
            if (c instanceof Container) { Component result = named((Container) c, name); if (result != null) { return result; } }
        }
        return null;
    }
    static void layout(Container parent) {
        parent.doLayout(); for (Component c : parent.getComponents()) { if (c instanceof Container) { layout((Container) c); } }
    }
    static void settle(Container parent) { for (int i = 0; i < 4; i++) { layout(parent); } }
    static Player player(String name, Player.Status status, int rank, int wins) {
        Player p = new Player(); p.setUsername(name); p.setStatus(status); p.setTotalRankPoint(rank); p.setWins(wins); return p;
    }
    static LobbyPanel panel(List<String> invitations, AtomicInteger refresh, AtomicInteger ranking, AtomicInteger history, AtomicInteger logout) {
        LobbyPanel panel = new LobbyPanel(invitations::add, refresh::incrementAndGet, ranking::incrementAndGet, history::incrementAndGet, logout::incrementAndGet);
        panel.setCurrentUsername("Alex"); panel.setConnection("192.168.1.20", 5000, true); return panel;
    }
    static void controls() {
        List<String> sent = new ArrayList<>(); AtomicInteger refresh = new AtomicInteger(), rank = new AtomicInteger(), history = new AtomicInteger(), logout = new AtomicInteger();
        LobbyPanel panel = panel(sent, refresh, rank, history, logout);
        JButton invite = (JButton) named(panel, "lobby-invite");
        check(!invite.isEnabled() && named(panel, "lobby-empty") != null, "Empty lobby and no invitation");
        Player self = player("Alex", Player.Status.FREE, 10, 2), nina = player("Nina", Player.Status.FREE, 7, 1);
        Player busy = player("Busy", Player.Status.PLAYING, 20, 3);
        panel.updateOnlinePlayers(List.of(self));
        check(!invite.isEnabled() && !panel.selectOpponent("Alex") && named(panel, "opponent-Alex") == null, "Self excluded");
        panel.updateOnlinePlayers(List.of(self, nina, busy));
        check(!invite.isEnabled(), "Selection required");
        PlayerCard card = (PlayerCard) named(panel, "opponent-Nina"); card.doClick(0);
        check(invite.isEnabled() && card.isOpponentSelected() && "Nina".equals(panel.getSelectedOpponent()), "FREE card selection");
        invite.doClick(0); check(sent.equals(List.of("Nina")) && nina.getStatus() == Player.Status.FREE
                && self.getStatus() == Player.Status.FREE, "Invite sends opponent only, no local status mutation");
        check(!((PlayerCard) named(panel, "opponent-Busy")).isEnabled() && !panel.selectOpponent("Busy"), "PLAYING cannot be invited");
        panel.updateOnlinePlayers(List.of(self, nina, busy));
        check("Nina".equals(panel.getSelectedOpponent()) && invite.isEnabled(), "Selection survives refresh");
        nina.setStatus(Player.Status.PLAYING);
        check(invite.isEnabled(), "Mutable external object cannot change the rendered server snapshot");
        panel.updateOnlinePlayers(List.of(self, nina, busy));
        check(!invite.isEnabled() && panel.getSelectedOpponent() == null, "Server PLAYING update clears selection");
        nina.setStatus(Player.Status.FREE); panel.updateOnlinePlayers(List.of(self, nina)); panel.selectOpponent("Nina");
        panel.updateOnlinePlayers(List.of(self)); check(!invite.isEnabled(), "Disconnect removes selected opponent");
        panel.updateOnlinePlayers(List.of(self, nina)); panel.selectOpponent("Nina");
        self.setStatus(Player.Status.PLAYING); panel.updateOnlinePlayers(List.of(self, nina));
        check(!invite.isEnabled() && !panel.selectOpponent("Nina") && ((JLabel) named(panel, "profile-status")).getText().contains("\u0110ANG CH\u01a0I"), "Own PLAYING blocks invite");
        Player stats = player("Alex", Player.Status.FREE, 13, 3); stats.setTotalMatches(8); stats.setTotalTreasureCollected(25);
        panel.updateStatistics(List.of(stats));
        check(((JLabel) named(panel, "profile-status")).getText().contains("\u0110ANG CH\u01a0I")
                && "25".equals(((JLabel) named(panel, "profile-treasure")).getText())
                && "8".equals(((JLabel) named(panel, "profile-matches")).getText()), "Ranking updates profile totals but never FREE/PLAYING");
        self.setStatus(Player.Status.FREE); panel.updateOnlinePlayers(List.of(self, nina));
        ((JButton) named(panel, "lobby-refresh")).doClick(0); ((JButton) named(panel, "lobby-ranking")).doClick(0);
        ((JButton) named(panel, "lobby-history")).doClick(0); ((JButton) named(panel, "lobby-logout")).doClick(0);
        check(refresh.get() == 1 && rank.get() == 1 && history.get() == 1 && logout.get() == 1, "Action callbacks");
        panel.selectOpponent("Nina"); panel.setConnection("192.168.1.20", 5000, false);
        check(!invite.isEnabled() && !panel.selectOpponent("Nina"), "Disconnected cannot invite");
        panel.setCurrentUsername(null);
        check("--".equals(((JLabel) named(panel, "profile-treasure")).getText()) && panel.getSelectedOpponent() == null, "Logout clears profile and selection");
        System.out.println("PASS: empty/self/FREE/PLAYING cards, selection, server snapshots, status authority, stats, disconnect and action callbacks");
    }
    static void fits(Container parent) {
        for (Component c : parent.getComponents()) {
            if (!c.isVisible()) { continue; }
            if (c instanceof JLabel) {
                JLabel l = (JLabel) c;
                if (l.getText() != null) {
                    check(l.getFontMetrics(l.getFont()).stringWidth(l.getText()) <= l.getWidth(), "Label fits: " + l.getText());
                    check(l.getFontMetrics(l.getFont()).getHeight() <= l.getHeight(), "Label height fits");
                }
            } else if (c instanceof JButton && !(c instanceof PlayerCard)) {
                JButton b = (JButton) c;
                if (b.getText() != null && !b.getText().isEmpty()) {
                    check(b.getFontMetrics(b.getFont()).stringWidth(b.getText()) + 20 <= b.getWidth(), "Button fits: " + b.getText());
                }
            }
            if (c instanceof Container) { fits((Container) c); }
        }
    }
    static void siblingsInside(Container parent) {
        List<Rectangle> seen = new ArrayList<>();
        for (Component c : parent.getComponents()) {
            if (!c.isVisible()) { continue; }
            check(new Rectangle(0, 0, parent.getWidth(), parent.getHeight()).contains(c.getBounds()), "Child stays inside " + parent.getName());
            for (Rectangle prior : seen) { check(!prior.intersects(c.getBounds()), "No overlap in " + parent.getName()); }
            seen.add(c.getBounds());
        }
    }
    static BufferedImage render(JPanel panel, Path file, int w, int h) throws Exception {
        panel.setSize(w, h); settle(panel); fits(panel);
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics(); panel.paint(g); g.dispose(); ImageIO.write(image, "png", file.toFile()); return image;
    }
    static void visibleInScroll(JScrollPane scroll, Component control) {
        Point p = SwingUtilities.convertPoint(control, 0, 0, scroll.getViewport());
        check(new Rectangle(0, 0, scroll.getViewport().getWidth(), scroll.getViewport().getHeight())
                .contains(new Rectangle(p.x, p.y, control.getWidth(), control.getHeight())), "Control accessible and not clipped horizontally");
    }
    static void screenshots(Path dir) throws Exception {
        for (int[] size : new int[][] {{1100, 700}, {1100, 665}, {900, 600}, {700, 450}, {684, 411}, {1400, 900}}) {
            for (int count : new int[] {0, 1, 12}) {
                LobbyPanel panel = panel(new ArrayList<>(), new AtomicInteger(), new AtomicInteger(), new AtomicInteger(), new AtomicInteger());
                List<Player> players = new ArrayList<>(); players.add(player("Alex", Player.Status.FREE, 13, 3));
                for (int i = 0; i < count; i++) { players.add(player("Explorer" + i, i % 3 == 2 ? Player.Status.PLAYING : Player.Status.FREE, 10 + i, i)); }
                panel.updateOnlinePlayers(players);
                Player stats = player("Alex", Player.Status.FREE, 13, 3); stats.setTotalTreasureCollected(25); stats.setTotalMatches(8);
                panel.updateStatistics(List.of(stats)); if (count > 0) { panel.selectOpponent("Explorer0"); }
                String suffix = count + "-" + size[0] + "x" + size[1];
                BufferedImage image = render(panel, dir.resolve("lobby-" + suffix + ".png"), size[0], size[1]);
                Set<Integer> colors = new HashSet<>();
                for (int y = 0; y < size[1]; y += 9) { for (int x = 0; x < size[0]; x += 9) { colors.add(image.getRGB(x, y)); } }
                check(colors.size() > 200, "Background and avatars render nonblank");
                siblingsInside((Container) named(panel, "lobby-columns")); siblingsInside((Container) named(panel, "lobby-profile"));
                siblingsInside((Container) named(panel, "lobby-actions")); siblingsInside((Container) named(panel, "lobby-connection-info"));
                JScrollPane list = (JScrollPane) named(panel, "opponents-scroll");
                siblingsInside((Container) list.getViewport().getView());
                check(!list.getHorizontalScrollBar().isVisible(), "Cards never require horizontal scroll");
                check(((JLabel) named(panel, "lobby-online-count")).getText().endsWith(Integer.toString(count + 1)), "Connection count includes self");
                if (count == 12) {
                    check(list.getVerticalScrollBar().isVisible(), "Many players scroll");
                    list.getVerticalScrollBar().setValue(list.getVerticalScrollBar().getMaximum()); settle(panel);
                    visibleInScroll(list, named(panel, "opponent-Explorer9"));
                }
                JScrollPane actions = (JScrollPane) named(panel, "actions-scroll");
                check(!actions.getHorizontalScrollBar().isVisible(), "Actions no horizontal scrolling");
                actions.getVerticalScrollBar().setValue(actions.getVerticalScrollBar().getMaximum()); settle(panel);
                visibleInScroll(actions, named(panel, "lobby-online-count"));
                if (size[1] <= 450) { render(panel, dir.resolve("lobby-bottom-" + suffix + ".png"), size[0], size[1]); }
            }
        }
        LobbyPanel longNames = panel(new ArrayList<>(), new AtomicInteger(), new AtomicInteger(), new AtomicInteger(), new AtomicInteger());
        String longName = "ExplorerWithAnExtremelyLongUsername";
        longNames.setCurrentUsername(longName); longNames.updateOnlinePlayers(List.of(player(longName, Player.Status.FREE, Integer.MAX_VALUE, 10),
                player("OpponentWithAnExtremelyLongUsername", Player.Status.FREE, Integer.MAX_VALUE, 10)));
        longNames.setConnection("very-long-server-hostname.example.ptit.edu.vn", 5000, true);
        render(longNames, dir.resolve("lobby-long-names-700x450.png"), 700, 450);
        for (boolean accepted : new boolean[] {true, false}) {
            List<Boolean> answers = new ArrayList<>(); InvitationPanel invitation = new InvitationPanel("Alex", answers::add);
            render(invitation, dir.resolve("invitation-" + accepted + ".png"), 470, 290);
            siblingsInside(invitation);
            ((JButton) named(invitation, accepted ? "invitation-accept" : "invitation-reject")).doClick(0);
            invitation.answerOnce(!accepted); check(answers.equals(List.of(accepted)), "Invitation answers exactly once");
        }
        InvitationPanel longInvitation = new InvitationPanel(longName, answer -> { });
        render(longInvitation, dir.resolve("invitation-small-long-name.png"), 430, 260);
        System.out.println("PASS: Lobby at six sizes, 0/1/many mixed players, text fit, no overlap, scroll reachability, long names and invite popup");
    }
    public static void main(String[] args) throws Exception {
        Path dir = Path.of("out", "test-screenshots"); Files.createDirectories(dir);
        SwingUtilities.invokeAndWait(() -> { try { controls(); screenshots(dir); } catch (Exception e) { throw new RuntimeException(e); } });
        System.out.println("ALL LOBBY RENDER TESTS PASSED");
    }
}
