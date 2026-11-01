import client.ResultPanel;
import client.InvitationPanel;
import common.GameResultCodec;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import model.GameResult;

public class ResultPanelRenderTest {
    private static void check(boolean condition, String description) {
        if (!condition) { throw new AssertionError(description); }
    }

    private static void layout(Container container) {
        container.doLayout();
        for (Component component : container.getComponents()) {
            if (component instanceof Container) { layout((Container) component); }
        }
    }

    private static Component named(Container container, String name) {
        for (Component component : container.getComponents()) {
            if (name.equals(component.getName())) { return component; }
            if (component instanceof Container) {
                Component found = named((Container) component, name);
                if (found != null) { return found; }
            }
        }
        return null;
    }

    private static void checkLabels(Container container) {
        for (Component component : container.getComponents()) {
            if (component instanceof JLabel) {
                JLabel label = (JLabel) component;
                check(label.getFontMetrics(label.getFont()).stringWidth(label.getText() == null ? "" : label.getText()) <= label.getWidth(), "Result label text fits");
                check(label.getFontMetrics(label.getFont()).getHeight() <= label.getHeight(), "Result label height fits: " + label.getText());
            } else if (component instanceof Container) { checkLabels((Container) component); }
        }
    }

    private static void noOverlap(Container parent) {
        if (!(parent instanceof JScrollPane) && !(parent instanceof javax.swing.JViewport)) {
            java.util.List<java.awt.Rectangle> bounds = new java.util.ArrayList<>();
            for (Component c : parent.getComponents()) {
                if (!c.isVisible() || c.getWidth() == 0 || c.getHeight() == 0) { continue; }
                check(new java.awt.Rectangle(0, 0, parent.getWidth(), parent.getHeight()).contains(c.getBounds()), "Result component inside parent: "
                        + c.getClass().getName() + " " + c.getName() + " " + c.getBounds() + " parent " + parent.getClass().getName() + " " + parent.getSize());
                for (java.awt.Rectangle previous : bounds) { check(!previous.intersects(c.getBounds()), "Result components never overlap"); }
                bounds.add(c.getBounds());
            }
        }
        for (Component c : parent.getComponents()) { if (c instanceof Container) { noOverlap((Container) c); } }
    }

    private static void cardData(ResultPanel panel, GameResult result) {
        check(((JLabel) named(panel, "result-name-1")).getToolTipText().equals(result.getPlayer1Username())
                && ((JLabel) named(panel, "result-name-2")).getToolTipText().equals(result.getPlayer2Username()), "Full player names retained");
        check(((JLabel) named(panel, "result-score-1")).getText().equals(Integer.toString(result.getPlayer1Score()))
                && ((JLabel) named(panel, "result-score-2")).getText().equals(Integer.toString(result.getPlayer2Score())), "Both server scores");
        check(((JLabel) named(panel, "result-collected-1")).getText().equals("S\u1ed1 r\u01b0\u01a1ng: " + result.getPlayer1Collected())
                && ((JLabel) named(panel, "result-collected-2")).getText().equals("S\u1ed1 r\u01b0\u01a1ng: " + result.getPlayer2Collected()), "Both server treasure counts");
        check(((JLabel) named(panel, "result-player-rank-1")).getText().equals("Rank: +" + result.getPlayer1RankGain())
                && ((JLabel) named(panel, "result-player-rank-2")).getText().equals("Rank: +" + result.getPlayer2RankGain()), "Both server rank gains");
    }

    private static void popup(Path directory) throws Exception {
        for (boolean accepted : new boolean[] {true, false}) {
            AtomicInteger answers = new AtomicInteger();
            InvitationPanel panel = new InvitationPanel("Alex", value -> { check(value == accepted, "Rematch answer"); answers.incrementAndGet(); }, true);
            panel.setSize(470, 290); layout(panel); noOverlap(panel); checkLabels(panel);
            check(((JTextArea) named(panel, "invitation-message")).getText().equals("Alex mu\u1ed1n t\u00e1i \u0111\u1ea5u"), "Rematch popup wording");
            check(((JButton) named(panel, "invitation-accept")).getText().equals("\u0110\u1ed2NG \u00dd"), "Accept rematch wording");
            BufferedImage image = new BufferedImage(470, 290, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics(); panel.paint(g); g.dispose();
            ImageIO.write(image, "png", directory.resolve("rematch-" + accepted + ".png").toFile());
            ((JButton) named(panel, accepted ? "invitation-accept" : "invitation-reject")).doClick(0);
            panel.answerOnce(!accepted); check(answers.get() == 1, "Only one rematch answer, including close/Escape");
        }
        InvitationPanel longName = new InvitationPanel("Nh\u00e0Th\u00e1mHi\u1ec3mT\u00eanR\u1ea5tD\u00e0i", answer -> { }, true);
        longName.setSize(430, 260); layout(longName); noOverlap(longName); checkLabels(longName);
        check(((JTextArea) named(longName, "invitation-message")).getLineWrap(), "Long rematch names wrap");
    }

    private static void clickLobby(Container container) {
        for (Component component : container.getComponents()) {
            if (component instanceof JButton && "return-lobby".equals(component.getName())) { ((JButton) component).doClick(0); }
            else if (component instanceof Container) { clickLobby((Container) component); }
        }
    }

    public static void main(String[] args) throws Exception {
        Path directory = Paths.get("out", "test-screenshots");
        Files.createDirectories(directory);
        SwingUtilities.invokeAndWait(() -> {
            try {
                GameResult[] results = {
                    new GameResult("result-win", "Alex", "Nina", GameResult.PLAYER1, 70, 40, 4, 2, 3, 0),
                    new GameResult("result-loss", "Alex", "Nina", GameResult.PLAYER2, 40, 70, 2, 4, 0, 3),
                    new GameResult("result-draw", "Alex", "Nina", GameResult.DRAW, 30, 30, 1, 3, 1, 1),
                    new GameResult("result-exit", "Alex", "Nina", GameResult.PLAYER2, 100, 0, 4, 0, 0, 3, GameResult.PLAYER_EXIT),
                    new GameResult("result-disconnect", "Alex", "Nina", GameResult.PLAYER1, 0, 100, 0, 4, 3, 0, GameResult.DISCONNECT)
                };
                String[] headlines = { "CHI\u1ebeN TH\u1eaeNG!", "TH\u1ea4T B\u1ea0I", "H\u00d2A", "TH\u1ea4T B\u1ea0I", "CHI\u1ebeN TH\u1eaeNG!" };
                for (int i = 0; i < results.length; i++) {
                    for (int[] size : new int[][] { {1100, 700}, {1084, 661}, {900, 600}, {700, 450}, {684, 411}, {1400, 900} }) {
                        AtomicInteger returned = new AtomicInteger();
                        AtomicInteger rematches = new AtomicInteger();
                        ResultPanel panel = new ResultPanel(returned::incrementAndGet, rematches::incrementAndGet);
                        panel.setCurrentUsername("Alex");
                        GameResult result = GameResultCodec.decode(GameResultCodec.encode(results[i]));
                        panel.setResult(result);
                        check("Nina".equals(panel.getOpponent()), "Rematch uses previous opponent");
                        JButton play = (JButton) named(panel, "play-again");
                        play.doClick(0); play.doClick(0);
                        check(rematches.get() == (GameResult.DISCONNECT.equals(result.getReason()) ? 0 : 1)
                                && !play.isEnabled(), "Single request; disconnect disables rematch");
                        panel.setRematchEnabled(!GameResult.DISCONNECT.equals(result.getReason()));
                        panel.setSize(size[0], size[1]);
                        layout(panel);
                        checkLabels(panel); noOverlap(panel);
                        if (size[1] < 500) {
                            JScrollPane resultScroll = (JScrollPane) named(panel, "result-scroll");
                            for (String name : new String[] {"result-score-1", "result-score-2", "result-collected-1", "result-collected-2", "result-player-rank-1", "result-player-rank-2", "result-rank"}) {
                                Component item = named(panel, name);
                                java.awt.Point location = SwingUtilities.convertPoint(item, 0, 0, resultScroll.getViewport());
                                check(location.y >= 0 && location.y + item.getHeight() <= resultScroll.getViewport().getHeight(), "Compact result immediately shows important data: " + name);
                            }
                        }
                        String before = GameResultCodec.encode(result);
                        check(panel.getResult() == result, "Result stored without mutating live game state");
                        JLabel headline = (JLabel) named(panel, "result-outcome");
                        check(headlines[i].equals(headline.getText()), "Own WIN/LOSE/DRAW headline");
                        cardData(panel, result);
                        check(((JLabel) named(panel, "result-rank")).getText().equals("+" + result.getPlayer1RankGain() + " \u0111i\u1ec3m x\u1ebfp h\u1ea1ng"), "Own rank gain");
                        JLabel reason = (JLabel) named(panel, "result-reason");
                        check(reason.getText().equals(GameResult.TIME_UP.equals(result.getReason()) ? "H\u1ebft th\u1eddi gian"
                                : GameResult.PLAYER_EXIT.equals(result.getReason()) ? "B\u1ea1n \u0111\u00e3 r\u1eddi tr\u1eadn" : "\u0110\u1ed1i th\u1ee7 m\u1ea5t k\u1ebft n\u1ed1i"), "Reason uses own perspective");
                        BufferedImage image = new BufferedImage(size[0], size[1], BufferedImage.TYPE_INT_RGB);
                        Graphics2D graphics = image.createGraphics();
                        panel.paint(graphics);
                        graphics.dispose();
                        check(before.equals(GameResultCodec.encode(result)), "Result rendering never changes server result");
                        check(java.util.Arrays.stream(((java.awt.image.DataBufferInt) image.getRaster().getDataBuffer()).getData()).distinct().count() > 200, "Nonblank fantasy result screen");
                        ImageIO.write(image, "png", directory.resolve(result.getRoomId() + "-" + size[0] + "x" + size[1] + ".png").toFile());
                        clickLobby(panel);
                        check(returned.get() == 1, "Return to Lobby button works");
                        panel.setCurrentUsername("Nina");
                        check((GameResult.DRAW.equals(result.getWinner()) ? "H\u00d2A"
                                : GameResult.PLAYER2.equals(result.getWinner()) ? "CHI\u1ebeN TH\u1eaeNG!" : "TH\u1ea4T B\u1ea0I")
                                .equals(headline.getText()), "P2 receives correct own outcome");
                        check(((JLabel) named(panel, "result-rank")).getText().equals("+" + result.getPlayer2RankGain() + " \u0111i\u1ec3m x\u1ebfp h\u1ea1ng"), "P2 rank gain");
                        if (GameResult.PLAYER_EXIT.equals(result.getReason())) { check(reason.getText().equals("\u0110\u1ed1i th\u1ee7 r\u1eddi tr\u1eadn"), "P2 sees opponent exit"); }
                        if (GameResult.DISCONNECT.equals(result.getReason())) { check(reason.getText().equals("B\u1ea1n m\u1ea5t k\u1ebft n\u1ed1i"), "Disconnected player perspective"); }
                        JScrollPane scroll = (JScrollPane) named(panel, "result-scroll");
                        scroll.getVerticalScrollBar().setValue(scroll.getVerticalScrollBar().getMaximum()); layout(panel);
                        check(!scroll.getHorizontalScrollBar().isVisible(), "Result remains width-responsive");
                        java.awt.Point point = SwingUtilities.convertPoint(named(panel, "result-player-rank-2"), 0, 0, scroll.getViewport());
                        check(point.y >= 0 && point.y + named(panel, "result-player-rank-2").getHeight() <= scroll.getViewport().getHeight(), "Result bottom data reachable by scrolling");
                    }
                }
                ResultPanel longNames = new ResultPanel(); longNames.setCurrentUsername("Nh\u00e0Th\u00e1mHi\u1ec3mT\u00eanR\u1ea5tD\u00e0i");
                GameResult longResult = new GameResult("a-very-long-room-id-that-must-remain-readable-through-tooltip", "Nh\u00e0Th\u00e1mHi\u1ec3mT\u00eanR\u1ea5tD\u00e0i", "\u0110\u1ed1iTh\u1ee7T\u00eanR\u1ea5tD\u00e0i", GameResult.DRAW,
                        Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, 1, 1);
                longNames.setResult(longResult); longNames.setSize(684, 411); layout(longNames); noOverlap(longNames); checkLabels(longNames); cardData(longNames, longResult);
                for (int[] size : new int[][] {{1100, 700}, {684, 411}, {900, 600}, {700, 450}, {1400, 900}}) {
                    longNames.setSize(size[0], size[1]);
                    for (int pass = 0; pass < 4; pass++) { layout(longNames); }
                    noOverlap(longNames); checkLabels(longNames); cardData(longNames, longResult);
                }
                popup(directory);
            } catch (Exception e) { throw new RuntimeException(e); }
        });
        System.out.println("PASS: fantasy results, WIN/LOSE/DRAW, TIME_UP/EXIT/DISCONNECT, both perspectives, callbacks, scrolling, long names and six sizes");
        System.out.println("PASS: fantasy rematch popup, accept/reject/close exactly once and long-name wrapping");
        System.out.println("ALL RESULT RENDER TESTS PASSED");
    }
}
