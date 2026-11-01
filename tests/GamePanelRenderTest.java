import client.GamePanel;
import client.BoardPanel;
import common.GameStateCodec;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import javax.imageio.ImageIO;
import javax.swing.JLabel;
import javax.swing.JButton;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import java.awt.event.KeyEvent;
import javax.swing.SwingUtilities;
import model.GameState;
import model.Position;
import model.Treasure;
import server.MapGenerator;

public class GamePanelRenderTest {
    private static void layout(Container container) {
        container.doLayout();
        for (Component component : container.getComponents()) {
            if (component instanceof Container) {
                layout((Container) component);
            }
        }
    }

    private static void labels(Container container, List<JLabel> result) {
        for (Component component : container.getComponents()) {
            if (component instanceof JLabel) {
                result.add((JLabel) component);
            } else if (component instanceof Container) {
                labels((Container) component, result);
            }
        }
    }

    private static BufferedImage render(GameState state, int width, int height) {
        GamePanel panel = new GamePanel();
        panel.setSoundEnabled(false);
        String before = GameStateCodec.encode(state);
        panel.setCurrentUsername("Alex");
        panel.setPlayers("Alex", "Nina");
        panel.setGameState(state);
        if (state.getPlayer1().getMatchScore() == 30) {
            panel.showTreasureCollected("Alex", Treasure.GOLD, 30, 30);
        }
        MapGenerationTest.check(panel.getGameState() == state, "Panel stores current state");
        panel.setSize(width, height);
        layout(panel);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        panel.paint(graphics);
        graphics.dispose();

        List<JLabel> allLabels = new ArrayList<>();
        labels(panel, allLabels);
        List<JButton> arrowButtons = new ArrayList<>();
        buttons(panel, arrowButtons);
        for (JButton button : arrowButtons) {
            if (button.getName().startsWith("MOVE_")) {
                MapGenerationTest.check(button.getWidth() == 36 && button.getHeight() == 36, "Stable 36x36 direction buttons");
            } else {
                MapGenerationTest.check(button.getFontMetrics(button.getFont()).stringWidth(button.getText()) + 16 <= button.getWidth(),
                        "Exit button text fits");
            }
            Point point = SwingUtilities.convertPoint(button, 0, 0, panel);
            MapGenerationTest.check(new Rectangle(0, 0, width, height).contains(
                    new Rectangle(point.x, point.y, button.getWidth(), button.getHeight())), "Arrow button is visible");
        }
        List<Rectangle> bounds = new ArrayList<>();
        List<Rectangle> textBounds = new ArrayList<>();
        for (JLabel label : allLabels) {
            if (!visible(label, panel)) { continue; }
            Point point = SwingUtilities.convertPoint(label, 0, 0, panel);
            MapGenerationTest.check(label.getFontMetrics(label.getFont()).stringWidth(label.getText()) <= label.getWidth(),
                    "Label text fits: " + label.getText());
            MapGenerationTest.check(label.getFontMetrics(label.getFont()).getHeight() <= label.getHeight(), "Label height fits: " + label.getText()
                    + " " + label.getFontMetrics(label.getFont()).getHeight() + "/" + label.getHeight());
            Rectangle text = new Rectangle(point.x, point.y, label.getWidth(), label.getHeight());
            MapGenerationTest.check(new Rectangle(0, 0, width, height).contains(text), "HUD/header text stays visible");
            for (Rectangle previous : textBounds) { MapGenerationTest.check(!text.intersects(previous), "HUD/header labels do not overlap"); }
            textBounds.add(text);
        }
        BoardPanel board = (BoardPanel) named(panel, "fantasy-board");
        MapGenerationTest.check(board.getComponentCount() == 0 && board.getWidth() == board.getHeight(), "Custom square board, no Swing tile buttons");
        BufferedImage boardImage = boardImage(panel);
        for (int row = 0; row < 7; row++) {
            for (int col = 0; col < 7; col++) {
                Rectangle rectangle = board.getTileBounds(row, col);
                MapGenerationTest.check(rectangle.width >= 25 && rectangle.height >= 25 && new Rectangle(0, 0, board.getWidth(), board.getHeight()).contains(rectangle),
                        "Each tile visible in square 7x7 board");
                for (Rectangle previous : bounds) { MapGenerationTest.check(!rectangle.intersects(previous), "No overlapping board cells"); }
                bounds.add(rectangle);
                MapGenerationTest.check(Arrays.stream(tilePixels(boardImage, rectangle)).distinct().count() > 8, "Each tile has fantasy artwork");
                String tooltip = tileTip(board, row, col);
                MapGenerationTest.check(tooltip.contains(state.getMap()[row][col] == 1 ? "Obstacle" : "Road"), "Obstacle/road match server map");
            }
        }
        MapGenerationTest.check(bounds.size() == 49, "49 painted cells");
        for (Treasure treasure : state.getTreasures()) {
            Position position = treasure.getPosition();
            MapGenerationTest.check(tileTip(board, position.getRow(), position.getCol()).contains(treasure.getId() + ": " + treasure.getType()), "Correct server treasure type/position");
        }
        for (model.Player player : new model.Player[] {state.getPlayer1(), state.getPlayer2()}) {
            MapGenerationTest.check(tileTip(board, player.getPosition().getRow(), player.getPosition().getCol()).contains(player.getUsername()), "Correct server player positions");
        }
        Rectangle boardBounds = board.getBounds();
        for (String name : new String[] {"player-hud-1", "player-hud-2", "collection-notification", "exit-game"}) {
            Component component = named(panel, name);
            Point point = SwingUtilities.convertPoint(component, 0, 0, panel);
            MapGenerationTest.check(!boardBounds.intersects(new Rectangle(point.x, point.y, component.getWidth(), component.getHeight())), "Board not obscured by " + name);
        }
        MapGenerationTest.check(Arrays.stream(((DataBufferInt) image.getRaster().getDataBuffer()).getData()).distinct().count() > 300, "Nonblank colorful fantasy render");
        MapGenerationTest.check(state.getRemainingTime() == 180, "Rendering does not run a timer");
        MapGenerationTest.check(before.equals(GameStateCodec.encode(state)), "Painting never changes server snapshot");
        panel.setPlayers("Alex", "Nina");
        return image;
    }

    private static Component named(Container parent, String name) {
        for (Component component : parent.getComponents()) {
            if (name.equals(component.getName())) { return component; }
            if (component instanceof Container) {
                Component found = named((Container) component, name); if (found != null) { return found; }
            }
        }
        return null;
    }

    private static boolean visible(Component component, Container root) {
        for (Component current = component; current != root; current = current.getParent()) { if (!current.isVisible()) { return false; } }
        return true;
    }

    private static BufferedImage boardImage(GamePanel panel) {
        BoardPanel board = (BoardPanel) named(panel, "fantasy-board");
        BufferedImage image = new BufferedImage(board.getWidth(), board.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics(); board.paint(g); g.dispose(); return image;
    }

    private static int[] tilePixels(BufferedImage image, Rectangle tile) {
        return image.getRGB(tile.x, tile.y, tile.width, tile.height, null, 0, tile.width);
    }

    private static String tileTip(BoardPanel board, int row, int col) {
        Rectangle tile = board.getTileBounds(row, col);
        return board.getToolTipText(new java.awt.event.MouseEvent(board, 0, 0, 0, tile.x + tile.width / 2, tile.y + tile.height / 2, 0, false));
    }

    private static GameState collectedState(String payload) {
        GameState state = GameStateCodec.decode(payload);
        state.getPlayer1().setPosition(new Position(3, 3));
        state.getPlayer1().setMatchScore(30);
        state.getPlayer1().setCollectedTreasure(1);
        state.getTreasures().removeIf(treasure -> Treasure.GOLD.equals(treasure.getType()));
        for (int row = 0; row < 7; row++) {
            for (int col = 0; col < 7; col++) {
                final int r = row;
                final int c = col;
                boolean occupied = state.getTreasures().stream().anyMatch(treasure -> treasure.getPosition().getRow() == r
                        && treasure.getPosition().getCol() == c);
                Position p1 = state.getPlayer1().getPosition();
                Position p2 = state.getPlayer2().getPosition();
                if (state.getMap()[row][col] == 0 && !occupied && (p1.getRow() != row || p1.getCol() != col)
                        && (p2.getRow() != row || p2.getCol() != col)) {
                    state.getTreasures().add(new Treasure("UI-RESPAWN", new Position(row, col), 10, Treasure.NORMAL));
                    return GameStateCodec.decode(GameStateCodec.encode(state));
                }
            }
        }
        throw new AssertionError("No UI respawn fixture cell");
    }

    private static void buttons(Container container, List<JButton> result) {
        for (Component component : container.getComponents()) {
            if (component instanceof JButton) {
                result.add((JButton) component);
            } else if (component instanceof Container) {
                buttons((Container) component, result);
            }
        }
    }

    private static void testControls(String payload) {
        List<String> sent = new ArrayList<>();
        GamePanel panel = new GamePanel(sent::add);
        panel.setSoundEnabled(false);
        panel.setSize(1100, 700);
        layout(panel);
        panel.setCurrentUsername("Alex");
        List<JButton> arrows = new ArrayList<>();
        buttons(panel, arrows);
        JButton exit = arrows.stream().filter(button -> "exit-game".equals(button.getName())).findFirst().orElseThrow();
        MapGenerationTest.check(!exit.isEnabled(), "Exit disabled before match");
        arrows.remove(exit);
        MapGenerationTest.check(arrows.size() == 4, "Four direction buttons");
        for (JButton button : arrows) {
            MapGenerationTest.check(!button.isEnabled() && !button.isFocusable(), "No pre-match move; buttons keep key focus");
        }
        GameState state = GameStateCodec.decode(payload);
        panel.setGameState(state);
        MapGenerationTest.check(exit.isEnabled(), "Exit available during RUNNING match");
        String before = GameStateCodec.encode(state);
        int[] keys = { KeyEvent.VK_W, KeyEvent.VK_UP, KeyEvent.VK_S, KeyEvent.VK_DOWN,
                KeyEvent.VK_A, KeyEvent.VK_LEFT, KeyEvent.VK_D, KeyEvent.VK_RIGHT };
        String[] directions = { "UP", "UP", "DOWN", "DOWN", "LEFT", "LEFT", "RIGHT", "RIGHT" };
        for (int i = 0; i < keys.length; i++) {
            Object actionKey = panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                    .get(KeyStroke.getKeyStroke(keys[i], 0));
            MapGenerationTest.check(("MOVE_" + directions[i]).equals(actionKey), "Window-level key binding");
            Action action = panel.getActionMap().get(actionKey);
            MapGenerationTest.check(action.isEnabled(), "Movement enabled during RUNNING");
            action.actionPerformed(null);
        }
        MapGenerationTest.check(sent.equals(Arrays.asList(directions)), "Keys send direction only");
        for (JButton button : arrows) {
            MapGenerationTest.check(button.isEnabled() && button.getToolTipText() != null, "Enabled buttons with tooltips");
            button.doClick(0);
        }
        MapGenerationTest.check(sent.size() == 12, "Buttons share key actions");
        MapGenerationTest.check(before.equals(GameStateCodec.encode(panel.getGameState())), "Client never changes position locally");
        panel.setVisible(false);
        panel.getActionMap().get("MOVE_RIGHT").actionPerformed(null);
        MapGenerationTest.check(sent.size() == 12, "Hidden GamePanel cannot send MOVE");
        panel.setVisible(true);
        state.setStatus("WAITING");
        panel.setGameState(state);
        panel.getActionMap().get("MOVE_RIGHT").actionPerformed(null);
        MapGenerationTest.check(sent.size() == 12 && !arrows.get(0).isEnabled(), "Non-running state disables input");
        state.setStatus("RUNNING");
        panel.setCurrentUsername("Erin");
        panel.getActionMap().get("MOVE_RIGHT").actionPerformed(null);
        MapGenerationTest.check(sent.size() == 12, "Outsider cannot send MOVE");
        BufferedImage alexView = boardImage(panel);
        panel.setCurrentUsername("Nina");
        List<JLabel> allLabels = new ArrayList<>();
        labels(panel, allLabels);
        int highlighted = 0;
        for (JLabel label : allLabels) {
            if (label.getName() != null && label.getName().startsWith("hud-own-") && label.getText().contains("C\u1ee6A B\u1ea0N")) {
                MapGenerationTest.check("hud-own-2".equals(label.getName()), "Own player, not fixed P1, gets highlighted");
                highlighted++;
            }
        }
        MapGenerationTest.check(highlighted == 1, "Exactly one highlighted player");
        MapGenerationTest.check(!Arrays.equals(((DataBufferInt) alexView.getRaster().getDataBuffer()).getData(),
                ((DataBufferInt) boardImage(panel).getRaster().getDataBuffer()).getData()), "Board highlight changes with own-player identity");
        String beforeNotice = GameStateCodec.encode(panel.getGameState());
        panel.showTreasureCollected("Alex", Treasure.GOLD, 30, 30);
        MapGenerationTest.check(beforeNotice.equals(GameStateCodec.encode(panel.getGameState())), "Collection notice never awards points locally");
        GameState afterCollection = collectedState(payload);
        panel.setGameState(afterCollection);
        BoardPanel board = (BoardPanel) named(panel, "fantasy-board");
        MapGenerationTest.check(tileTip(board, 3, 3).contains("Alex") && !tileTip(board, 3, 3).contains("GOLD"), "Server position replaces consumed GOLD chest");
        for (JLabel label : allLabels) {
            if ("treasure-notice".equals(label.getName())) {
                MapGenerationTest.check(label.getText().equals("Alex \u0111\u00e3 thu th\u1eadp R\u01b0\u01a1ng v\u00e0ng +30 \u0111i\u1ec3m!"),
                        "Vietnamese collection notice persists after GAME_STATE");
            }
        }
        MapGenerationTest.check(((JLabel) named(panel, "hud-score-1")).getText().equals("30")
                && ((JLabel) named(panel, "hud-collected-1")).getText().equals("R\u01b0\u01a1ng: 1")
                && ((JLabel) named(panel, "hud-score-2")).getText().equals("0"), "Scores and collected counts update only from state");
        Position original = GameStateCodec.decode(payload).getPlayer1().getPosition();
        afterCollection.getPlayer1().setPosition(original);
        panel.setGameState(GameStateCodec.decode(GameStateCodec.encode(afterCollection)));
        MapGenerationTest.check(!tileTip(board, 3, 3).contains("GOLD") && !tileTip(board, 3, 3).contains("Alex"), "Consumed GOLD stays gone after moving away");
        MapGenerationTest.check(sent.size() == 12 && panel.getGameState().getTreasures().size() == 5,
                "Rendering server updates sends no MOVE and keeps the five server treasures");
        MapGenerationTest.check("03:00".equals(GamePanel.formatTime(180)) && "02:59".equals(GamePanel.formatTime(179))
                && "00:00".equals(GamePanel.formatTime(0)), "MM:SS time formatting");
        int scoreBeforeTime = panel.getGameState().getPlayer1().getMatchScore();
        panel.updateTime(179);
        for (JLabel label : allLabels) {
            if ("server-time".equals(label.getName())) {
                MapGenerationTest.check(label.getText().contains("02:59"), "TIME_UPDATE drives displayed time");
            }
        }
        panel.updateTime(0);
        panel.getActionMap().get("MOVE_RIGHT").actionPerformed(null);
        MapGenerationTest.check(sent.size() == 12 && !arrows.get(0).isEnabled(), "Server time zero disables input");
        panel.finishGame();
        MapGenerationTest.check(!exit.isEnabled(), "Exit disabled after GAME_OVER");
        panel.updateTime(10);
        panel.getActionMap().get("MOVE_RIGHT").actionPerformed(null);
        MapGenerationTest.check(sent.size() == 12 && "FINISHED".equals(panel.getGameState().getStatus())
                && panel.getGameState().getRemainingTime() == 0 && panel.getGameState().getPlayer1().getMatchScore() == scoreBeforeTime,
                "GAME_OVER stops input and later clock messages cannot restart it");
        panel.setPlayers("Alex", "Nina");
        for (JLabel label : allLabels) {
            if ("treasure-notice".equals(label.getName())) {
                MapGenerationTest.check(label.getText().trim().isEmpty(), "New match clears previous collection notice");
            }
        }
        System.out.println("PASS: Vietnamese collection notice, authoritative scores/counts, consumed chest and respawn rendering");
        System.out.println("PASS: buttons, WASD/arrows, window focus bindings, no local movement, input guards, own-player highlight");
        panel.close();
    }

    private static void testArtwork(String payload) {
        GamePanel panel = new GamePanel(); panel.setSize(1100, 700); panel.setCurrentUsername("Alex");
        panel.setSoundEnabled(false);
        GameState state = GameStateCodec.decode(payload); panel.setGameState(state); layout(panel);
        BoardPanel board = (BoardPanel) named(panel, "fantasy-board");
        Treasure target = state.getTreasures().get(0); Position position = target.getPosition();
        List<int[]> types = new ArrayList<>();
        for (String type : new String[] {Treasure.NORMAL, Treasure.SILVER, Treasure.GOLD}) {
            target.setType(type); target.setValue(Treasure.valueForType(type)); panel.setGameState(state);
            int[] painted = tilePixels(boardImage(panel), board.getTileBounds(position.getRow(), position.getCol()));
            for (int[] previous : types) { MapGenerationTest.check(!Arrays.equals(previous, painted), "Three visually distinct chest types"); }
            types.add(painted);
        }
        BufferedImage previous = boardImage(panel);
        for (int row = 0; row < 7; row++) {
            for (int col = 0; col < 7; col++) {
                if (!tileTip(board, row, col).equals("(" + row + ", " + col + ") Road")) { continue; }
                state.getMap()[row][col] = 1; panel.setGameState(state);
                MapGenerationTest.check(!Arrays.equals(tilePixels(previous, board.getTileBounds(row, col)),
                        tilePixels(boardImage(panel), board.getTileBounds(row, col))), "Obstacle artwork replaces road");
                row = 7; break;
            }
        }
        System.out.println("PASS: chest types have different artwork and obstacles replace grass paths");
        panel.close();
    }

    private static void testExit(String payload) {
        int[] exits = {0}; boolean[] answer = {false}; boolean[] finishDuringDialog = {false};
        GamePanel panel = new GamePanel(direction -> { }, () -> exits[0]++) {
            @Override protected boolean confirmExit() {
                if (finishDuringDialog[0]) { finishGame(); }
                return answer[0];
            }
        };
        panel.setSoundEnabled(false); panel.setCurrentUsername("Alex"); panel.setGameState(GameStateCodec.decode(payload));
        JButton exit = (JButton) named(panel, "exit-game");
        String before = GameStateCodec.encode(panel.getGameState());
        exit.doClick(0); MapGenerationTest.check(exits[0] == 0, "Cancel exit sends nothing");
        answer[0] = true; exit.doClick(0);
        MapGenerationTest.check(exits[0] == 1 && before.equals(GameStateCodec.encode(panel.getGameState())), "Confirmed exit only invokes sender, no local forfeit");
        finishDuringDialog[0] = true; exit.doClick(0);
        MapGenerationTest.check(exits[0] == 1 && !exit.isEnabled(), "No EXIT after GAME_OVER during confirmation");
        panel.close();
    }

    private static void testNotificationTimer(String payload) throws Exception {
        GamePanel[] holder = new GamePanel[1]; String[] snapshot = new String[1];
        SwingUtilities.invokeAndWait(() -> {
            GamePanel panel = new GamePanel(); holder[0] = panel;
            panel.setSoundEnabled(false);
            panel.setSize(1100, 700); panel.setGameState(GameStateCodec.decode(payload)); layout(panel);
            snapshot[0] = GameStateCodec.encode(panel.getGameState());
            panel.showTreasureCollected("Alex", Treasure.NORMAL, 10, 10);
        });
        Thread.sleep(1500);
        SwingUtilities.invokeAndWait(() -> holder[0].showTreasureCollected("Nina", Treasure.GOLD, 30, 30));
        Thread.sleep(1500);
        SwingUtilities.invokeAndWait(() -> {
            MapGenerationTest.check(named(holder[0], "collection-notification").isVisible()
                    && ((JLabel) named(holder[0], "treasure-notice")).getText().contains("Nina"), "New collection restarts dismissal instead of disappearing with older notice");
        });
        Thread.sleep(1500);
        SwingUtilities.invokeAndWait(() -> {
            GamePanel panel = holder[0];
            MapGenerationTest.check(!named(panel, "collection-notification").isVisible(), "Notification automatically hides after 2.8 seconds");
            MapGenerationTest.check(snapshot[0].equals(GameStateCodec.encode(panel.getGameState()))
                    && ((JLabel) named(panel, "server-time")).getText().equals("03:00"), "UI timer never decrements match clock or changes game state");
            panel.showTreasureCollected("Alex", Treasure.SILVER, 20, 20); panel.finishGame();
            MapGenerationTest.check(!named(panel, "collection-notification").isVisible(), "Finish clears notification timer");
            panel.setPlayers("Alex", "Nina"); panel.setGameState(GameStateCodec.decode(payload));
            panel.showTreasureCollected("Alex", Treasure.GOLD, 30, 30); panel.removeNotify();
            MapGenerationTest.check(!named(panel, "collection-notification").isVisible(), "Removing panel clears notification timer");
            panel.close();
        });
        System.out.println("PASS: nonblocking notification expiry/restart/cleanup with no local countdown or score updates");
    }

    public static void main(String[] args) throws Exception {
        GameState state = new MapGenerator(new Random(42)).generate("render-test-room",
                MapGenerationTest.player("Alex"), MapGenerationTest.player("Nina"));
        String payload = GameStateCodec.encode(state);
        Path directory = Paths.get("out", "test-screenshots");
        Files.createDirectories(directory);
        SwingUtilities.invokeAndWait(() -> {
            try {
                testControls(payload);
                testArtwork(payload);
                testExit(payload);
                for (int[] size : new int[][] { {1100, 700}, {1084, 661}, {900, 600}, {700, 450}, {684, 411}, {1400, 900} }) {
                    BufferedImage first = render(GameStateCodec.decode(payload), size[0], size[1]);
                    BufferedImage second = render(GameStateCodec.decode(payload), size[0], size[1]);
                    int[] a = ((DataBufferInt) first.getRaster().getDataBuffer()).getData();
                    int[] b = ((DataBufferInt) second.getRaster().getDataBuffer()).getData();
                    MapGenerationTest.check(Arrays.equals(a, b), "Same state produces identical rendered boards");
                    ImageIO.write(first, "png", directory.resolve("game-panel-" + size[0] + "x" + size[1] + ".png").toFile());
                    BufferedImage collected = render(collectedState(payload), size[0], size[1]);
                    ImageIO.write(collected, "png", directory.resolve("game-panel-collected-" + size[0] + "x" + size[1] + ".png").toFile());
                }
                GameState longNames = GameStateCodec.decode(payload);
                longNames.getPlayer1().setUsername("Nh\u00e0Th\u00e1mHi\u1ec3mKhoB\u00e1uV\u00e0ngR\u1ea5tD\u00e0i");
                longNames.getPlayer2().setUsername("\u0110\u1ed1iTh\u1ee7S\u0103nKhoB\u00e1uT\u00eanR\u1ea5tD\u00e0i");
                longNames.getPlayer1().setMatchScore(Integer.MAX_VALUE); longNames.getPlayer1().setCollectedTreasure(Integer.MAX_VALUE);
                longNames.getPlayer2().setMatchScore(Integer.MAX_VALUE); longNames.getPlayer2().setCollectedTreasure(Integer.MAX_VALUE);
                ImageIO.write(render(longNames, 684, 411), "png", directory.resolve("game-panel-long-names.png").toFile());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        testNotificationTimer(payload);
        System.out.println("PASS: headless rendering at six sizes, 49 painted cells, long names, HUD, timers, colors and no overlap");
        System.out.println("ALL RENDER TESTS PASSED");
    }
}
