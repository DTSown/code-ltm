import client.BoardPanel;
import client.GamePanel;
import common.GameStateCodec;
import common.TrapEvent;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import model.GameResult;
import model.GameState;
import model.Player;
import model.Position;

public class TrapClientRenderTest {
    private static void check(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
    }

    private static Component named(Container parent, String name) {
        for (Component child : parent.getComponents()) {
            if (name.equals(child.getName())) { return child; }
            if (child instanceof Container nested) {
                Component result = named(nested, name); if (result != null) { return result; }
            }
        }
        return null;
    }

    private static void layout(Container parent) {
        parent.doLayout();
        for (Component child : parent.getComponents()) { if (child instanceof Container nested) { layout(nested); } }
    }

    private static GameState state(String room, int row, int col) {
        GameState state = new GameState(); state.setRoomId(room); state.setStatus("RUNNING"); state.setRemainingTime(180);
        state.setPlayer1(new Player("Alex", 0, 0, Player.Status.PLAYING, new Position(row, col), 15, 1));
        state.setPlayer2(new Player("Nina", 0, 0, Player.Status.PLAYING, new Position(3, 3), 5, 1));
        return state;
    }

    private static TrapEvent trap(String room, long sequence, String username, int lost) {
        return TrapEvent.decode(new TrapEvent(room, sequence, username, lost, 0, 2000).encode());
    }

    private static GamePanel panel() {
        GamePanel panel = new GamePanel(); panel.setSoundEnabled(false); panel.setCurrentUsername("Alex");
        return panel;
    }

    private static BoardPanel board(GamePanel panel) { return (BoardPanel) named(panel, "fantasy-board"); }
    private static AbstractButton button(GamePanel panel, String name) { return (AbstractButton) named(panel, name); }
    private static String notice(GamePanel panel) { return ((JLabel) named(panel, "treasure-notice")).getToolTipText(); }

    private static BufferedImage paint(JComponent component) {
        BufferedImage image = new BufferedImage(component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics(); component.paint(graphics); graphics.dispose(); return image;
    }

    private static boolean starPixel(BufferedImage image, int x, int y) {
        return (image.getRGB(x, y) & 0xffffff) == 0xffeb28;
    }

    private static int stars(BufferedImage image, Rectangle area) {
        int count = 0;
        for (int y = Math.max(0, area.y); y < Math.min(image.getHeight(), area.y + area.height); y++) {
            for (int x = Math.max(0, area.x); x < Math.min(image.getWidth(), area.x + area.width); x++) {
                if (starPixel(image, x, y)) { count++; }
            }
        }
        return count;
    }

    private static void checkControls(GamePanel panel, boolean enabled) {
        for (int key : new int[] {KeyEvent.VK_W, KeyEvent.VK_A, KeyEvent.VK_S, KeyEvent.VK_D,
                KeyEvent.VK_UP, KeyEvent.VK_LEFT, KeyEvent.VK_DOWN, KeyEvent.VK_RIGHT}) {
            Object binding = panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(KeyStroke.getKeyStroke(key, 0));
            Action action = panel.getActionMap().get(binding);
            check(action != null && action.isEnabled() == enabled, "Keyboard stun guard: " + key);
        }
        for (String direction : new String[] {"UP", "LEFT", "DOWN", "RIGHT"}) {
            check(button(panel, "MOVE_" + direction).isEnabled() == enabled, "Button stun guard");
        }
    }

    private static void controlsAndLifecycle() {
        AtomicInteger moves = new AtomicInteger(), exits = new AtomicInteger();
        GamePanel panel = new GamePanel(direction -> moves.incrementAndGet(), exits::incrementAndGet) {
            @Override protected boolean confirmExit() { return true; }
        };
        panel.setSoundEnabled(false); panel.setCurrentUsername("Alex"); panel.setSize(700, 450);
        try {
            panel.setGameState(state("room-a", 0, 0)); layout(panel);
            String before = GameStateCodec.encode(panel.getGameState());
            panel.showTrapTriggered(trap("room-a", 2, "Alex", 5));
            checkControls(panel, false);
            for (String direction : new String[] {"UP", "LEFT", "DOWN", "RIGHT"}) {
                panel.getActionMap().get("MOVE_" + direction).actionPerformed(null);
                button(panel, "MOVE_" + direction).doClick(0);
            }
            check(moves.get() == 0, "No movement sent during stun, even directly invoked actions");
            check(button(panel, "exit-game").isEnabled(), "Exit stays enabled during stun");
            button(panel, "exit-game").doClick(0); check(exits.get() == 1, "Stunned player can exit");
            check(before.equals(GameStateCodec.encode(panel.getGameState())), "Trap and exit never mutate authoritative state");
            check(notice(panel).contains("-5 ") && notice(panel).contains("B\u1ea1n"), "Actual five-point loss and own perspective");
            String original = notice(panel);
            panel.showTrapTriggered(trap("room-a", 2, "Nina", 10));
            panel.showTrapTriggered(trap("room-a", 1, "Alex", 0));
            panel.showTrapTriggered(trap("other-room", 999, "Alex", 10));
            panel.showTrapTriggered(trap("room-a", 999, "Stranger", 10));
            check(original.equals(notice(panel)) && !board(panel).isStunned("Nina"), "Duplicate, old, wrong-room and nonmember events ignored");
            panel.showTrapTriggered(trap("room-a", 3, "Nina", 0));
            check(notice(panel).equals("Nina \u0111\u00e3 tr\u00fang b\u1eaby v\u00e0 b\u1ecb cho\u00e1ng!"), "Opponent trap notice");
            check(board(panel).isStunned("Alex") && board(panel).isStunned("Nina"), "Independent simultaneous stuns");
            panel.showTrapTriggered(trap("room-a", 4, "Alex", 10));
            check(notice(panel).contains("-10 "), "Full deduction shown");
            GameState updated = state("room-a", 0, 6); updated.getPlayer1().setMatchScore(5);
            panel.setGameState(updated); panel.updateTime(179);
            checkControls(panel, false); check(board(panel).isStunned("Alex"), "State and time updates preserve stun");
            check(panel.getGameState().getPlayer1().getMatchScore() == 5, "Server snapshot owns score");
            panel.finishGame(); checkControls(panel, false);
            check(!board(panel).isStunned("Alex") && !named(panel, "collection-notification").isVisible(), "Finish clears effects");
            panel.showTrapTriggered(trap("room-a", 5, "Alex", 10));
            check(!board(panel).isStunned("Alex"), "Finished room rejects delayed event");
            panel.setPlayers("Alex", "Nina"); panel.setGameState(state("room-b", 6, 6));
            panel.showTrapTriggered(trap("room-a", 50, "Alex", 10)); checkControls(panel, true);
            panel.showTrapTriggered(trap("room-b", 1, "Nina", 5)); checkControls(panel, true);
            check(board(panel).isStunned("Nina"), "New room resets sequence; opponent stun does not block local movement");
            panel.setGameState(state("room-c", 6, 0));
            check(!board(panel).isStunned("Nina"), "Room change clears old animation");
            panel.showTrapTriggered(trap("room-c", 1, "Alex", 0)); panel.setPlayers("Alex", "Nina");
            check(!board(panel).isStunned("Alex"), "Rematch preparation clears stun");
            panel.setGameState(state("room-d", 0, 0)); panel.showTrapTriggered(trap("room-d", 1, "Alex", 10));
            panel.resetSession(); panel.showTrapTriggered(trap("room-d", 2, "Alex", 10));
            checkControls(panel, false); check(!board(panel).isStunned("Alex"), "Disconnect/login reset rejects late trap");
            panel.setGameState(state("room-e", 0, 0)); panel.showTrapTriggered(trap("room-e", 1, "Alex", 10));
            panel.setCurrentUsername("Nina"); check(!board(panel).isStunned("Alex"), "Account change clears stun");
            panel.setCurrentUsername("Spectator"); panel.setGameState(state("room-f", 0, 0));
            panel.showTrapTriggered(trap("room-f", 1, "Alex", 10)); check(!board(panel).isStunned("Alex"), "Local account must be a member");
            panel.setCurrentUsername("Alex"); panel.setGameState(state("room-g", 0, 0));
            panel.showTrapTriggered(trap("room-g", 1, "Alex", 10)); panel.updateTime(0);
            check(!board(panel).isStunned("Alex"), "Zero server time clears stun and disables movement"); checkControls(panel, false);
            panel.setGameState(state("room-h", 0, 0)); panel.showTrapTriggered(trap("room-h", 1, "Alex", 10));
            panel.finishGame(GameResult.fromState(panel.getGameState()));
            panel.finishGame(GameResult.fromState(panel.getGameState()));
            check(!board(panel).isStunned("Alex"), "Repeated results remain terminal");
            panel.setGameState(state("room-i", 0, 0)); panel.showTrapTriggered(trap("room-i", 1, "Alex", 10));
            panel.removeNotify(); check(!board(panel).isStunned("Alex"), "Removing panel stops animation");
        } finally { panel.close(); }
        panel.setGameState(state("closed", 0, 0)); panel.showTrapTriggered(trap("closed", 1, "Alex", 10));
        checkControls(panel, false); check(!panel.isSoundEnabled(), "Dispose is terminal and closes audio");
    }

    private static void renderCorners(Path directory) throws Exception {
        for (int[] size : new int[][] {{700, 450}, {684, 411}, {1100, 700}, {1400, 900}}) {
            for (int[] corner : new int[][] {{0, 0}, {0, 6}, {6, 0}, {6, 6}}) {
                GamePanel panel = panel();
                try {
                    panel.setSize(size[0], size[1]); panel.setGameState(state("render", corner[0], corner[1])); layout(panel);
                    String before = GameStateCodec.encode(panel.getGameState());
                    panel.showTrapTriggered(trap("render", 1, "Alex", 5)); layout(panel);
                    BoardPanel board = board(panel); BufferedImage image = paint(board);
                    Rectangle tile = board.getTileBounds(corner[0], corner[1]);
                    Rectangle halo = new Rectangle(tile.x - 4, tile.y - 12, tile.width + 8, 26);
                    ImageIO.write(paint(panel), "png", directory.resolve("trap-" + size[0] + "x" + size[1]
                            + "-" + corner[0] + "-" + corner[1] + ".png").toFile());
                    check(stars(image, halo) > 8, "Yellow stars visible at " + size[0] + "x" + size[1]
                            + " corner " + corner[0] + "," + corner[1] + ": " + stars(image, halo));
                    check(stars(image, new Rectangle(0, 0, image.getWidth(), 1)) == 0
                            && stars(image, new Rectangle(0, 0, 1, image.getHeight())) == 0
                            && stars(image, new Rectangle(image.getWidth() - 1, 0, 1, image.getHeight())) == 0,
                            "Star orbit stays inside board edges");
                    check(stars(image, new Rectangle(tile.x, tile.y + tile.height - 14, tile.width, 14)) == 0,
                            "Stars do not overlap player name");
                    Component sound = named(panel, "sound-toggle");
                    check(sound.getWidth() == 32 && sound.getHeight() == 32, "Compact stable sound toggle");
                    for (Component sibling : sound.getParent().getComponents()) {
                        if (sibling != sound && sibling.isVisible()) { check(!sound.getBounds().intersects(sibling.getBounds()), "Sound toggle does not overlap header"); }
                    }
                    check(!button(panel, "sound-toggle").isSelected() && button(panel, "sound-toggle").getToolTipText() != null,
                            "Muted icon has tooltip and state");
                    JLabel notice = (JLabel) named(panel, "treasure-notice");
                    check(notice.getFontMetrics(notice.getFont()).stringWidth(notice.getText()) <= notice.getWidth(), "Trap notice fits");
                    check(before.equals(GameStateCodec.encode(panel.getGameState())), "Animation does not change server state");
                } finally { panel.close(); }
            }
        }
        GamePanel toggle = panel();
        try {
            button(toggle, "sound-toggle").doClick(0); check(toggle.isSoundEnabled(), "Speaker unmutes before active game");
            button(toggle, "sound-toggle").doClick(0); check(!toggle.isSoundEnabled(), "Speaker mutes again");
        } finally { toggle.close(); }
    }

    private static void animationAndExpiry() throws Exception {
        List<GamePanel> panels = new ArrayList<>(); List<BufferedImage> initial = new ArrayList<>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                for (int[] corner : new int[][] {{0, 0}, {0, 6}, {6, 0}, {6, 6}}) {
                    GamePanel panel = panel(); panels.add(panel); panel.setSize(700, 450);
                    panel.setGameState(state("timed", corner[0], corner[1])); layout(panel);
                    panel.showTrapTriggered(trap("timed", 1, "Alex", 5)); initial.add(paint(board(panel)));
                }
            });
            Thread.sleep(180);
            SwingUtilities.invokeAndWait(() -> {
                for (int i = 0; i < panels.size(); i++) {
                    BufferedImage now = paint(board(panels.get(i))), before = initial.get(i); int changed = 0;
                    for (int y = 0; y < now.getHeight(); y++) {
                        for (int x = 0; x < now.getWidth(); x++) {
                            if (starPixel(before, x, y) != starPixel(now, x, y)) { changed++; }
                        }
                    }
                    check(changed > 8, "33ms timer moves yellow star pixels at each corner");
                }
            });
            Thread.sleep(950);
            SwingUtilities.invokeAndWait(() -> {
                for (GamePanel panel : panels) {
                    GameState next = GameStateCodec.decode(GameStateCodec.encode(panel.getGameState()));
                    next.getPlayer1().setMatchScore(5); panel.setGameState(next); panel.updateTime(179);
                    panel.showTrapTriggered(trap("timed", 1, "Alex", 5)); checkControls(panel, false);
                }
            });
            Thread.sleep(1150);
            SwingUtilities.invokeAndWait(() -> {
                for (GamePanel panel : panels) {
                    checkControls(panel, true);
                    check(!board(panel).isStunned("Alex"), "Expiry automatically reenables movement without another network message");
                    BufferedImage image = paint(board(panel));
                    check(stars(image, new Rectangle(0, 0, image.getWidth(), image.getHeight())) == 0, "Expiry removes stars");
                    check(panel.getGameState().getRemainingTime() == 179 && panel.getGameState().getPlayer1().getMatchScore() == 5,
                            "UI timer never changes time or score");
                    panel.showTrapTriggered(trap("timed", 1, "Alex", 5)); checkControls(panel, true);
                }
            });
        } finally { SwingUtilities.invokeAndWait(() -> panels.forEach(GamePanel::close)); }
    }

    public static void main(String[] args) throws Exception {
        Path directory = Path.of("out", "test-screenshots"); Files.createDirectories(directory);
        SwingUtilities.invokeAndWait(() -> {
            controlsAndLifecycle();
            try { renderCorners(directory); } catch (Exception error) { throw new RuntimeException(error); }
        });
        animationAndExpiry();
        System.out.println("PASS: muted EDT trap controls, exit, authority, sequence/room guards, lifecycle, corner renders and automatic animated expiry");
    }
}
