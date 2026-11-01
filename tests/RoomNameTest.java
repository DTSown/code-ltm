import client.GamePanel;
import client.HistoryPanel;
import client.ResultPanel;
import common.GameResultCodec;
import common.GameStateCodec;
import common.RoomName;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.imageio.ImageIO;
import model.GameResult;
import model.GameState;
import model.MatchHistory;
import server.GameRoom;

public class RoomNameTest {
    static void check(boolean value, String reason) { if (!value) { throw new AssertionError(reason); } }

    static Component named(Container parent, String name) {
        for (Component child : parent.getComponents()) {
            if (name.equals(child.getName())) { return child; }
            if (child instanceof Container) {
                Component match = named((Container) child, name);
                if (match != null) { return match; }
            }
        }
        return null;
    }

    static void render(GameState state, GameResult result, MatchHistory history, Path screenshot) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            GamePanel game = new GamePanel(); game.setSoundEnabled(false); game.setCurrentUsername("Alex"); game.setGameState(state);
            ResultPanel panel = new ResultPanel(); panel.setCurrentUsername("Alex"); panel.setResult(result);
            HistoryPanel past = new HistoryPanel(() -> { }, () -> { });
            past.setCurrentUsername("Alex"); past.setHistory(List.of(history));
            String name = "Ph\u00f2ng " + RoomName.number(state.getRoomId());
            check(name.equals(((JLabel) named(game, "game-room")).getToolTipText()), "Game uses readable server room number");
            game.setSize(1100, 700); game.doLayout();
            check(name.equals(((JLabel) named(game, "game-room")).getText()), "Visible game room label is not truncated");
            check(name.equals(((JLabel) named(panel, "result-room")).getText()), "Result uses same room number");
            JTable table = (JTable) named(past, "history-table");
            check(name.equals(table.getModel().getValueAt(0, 0)), "History uses same room number");
            check(state.getRoomId().equals(result.getRoomId()) && result.getRoomId().equals(history.getRoomId()), "Internal ID remains intact");
            BufferedImage image = new BufferedImage(1100, 700, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics(); game.paint(graphics); graphics.dispose();
            try { ImageIO.write(image, "png", screenshot.toFile()); }
            catch (java.io.IOException error) { throw new RuntimeException(error); }
            game.finishGame();
            game.close();
        });
    }

    public static void main(String[] args) throws Exception {
        String uuid = "87ec77d3-89e6-4800-aa56-b5ad715c902f";
        check(RoomName.display("ROOM-1-" + uuid).equals("Ph\u00f2ng 1"), "Numeric room name");
        check(RoomName.display(uuid).equals(uuid) && RoomName.display("old-room").equals("old-room"), "Legacy IDs supported");
        for (String invalid : new String[] {"ROOM-0-" + uuid, "ROOM--1-" + uuid, "ROOM-X-" + uuid,
                "ROOM-999999999999999999999-" + uuid, "ROOM-1-not-a-uuid"}) {
            check(RoomName.number(invalid) == 0 && RoomName.display(invalid).equals(invalid), "Invalid ID has safe fallback");
        }
        Path file = Files.createTempDirectory(Path.of("out"), "room-name-test-").resolve("data.tsv");
        try (GameLifecycleIntegrationTest.Harness h = new GameLifecycleIntegrationTest.Harness(180, file);
                GameLifecycleIntegrationTest.Client a = h.client("Alex");
                GameLifecycleIntegrationTest.Client b = h.client("Nina")) {
            for (long number = 1; number <= 2; number++) {
                GameRoom room;
                if (number == 1) {
                    room = GameLifecycleIntegrationTest.invite(h, a, b);
                } else {
                    a.send(common.Protocol.PLAY_AGAIN, "Nina"); b.expect(common.Protocol.PLAY_AGAIN);
                    b.send(common.Protocol.PLAY_AGAIN_ACCEPT, "Alex");
                    room = GameLifecycleIntegrationTest.readStart(h, a, b);
                }
                check(RoomName.number(room.getId()) == number, "Rooms numbered in creation order");
                String payload = GameStateCodec.encode(room.getGameState());
                check(payload.equals(GameStateCodec.encode(GameStateCodec.decode(payload))), "Numbered state codec roundtrip");
                GameResult result = GameLifecycleIntegrationTest.exit(h, a, b, room);
                check(GameResultCodec.encode(result).equals(GameResultCodec.encode(GameResultCodec.decode(GameResultCodec.encode(result)))), "Numbered result codec roundtrip");
                render(GameStateCodec.decode(payload), result, h.manager.getMatchHistory("Alex").stream()
                        .filter(match -> room.getId().equals(match.getRoomId())).findFirst().orElseThrow(), file.getParent().resolve("game-" + number + ".png"));
            }
        }
        try (GameLifecycleIntegrationTest.Harness h = new GameLifecycleIntegrationTest.Harness(180, file);
                GameLifecycleIntegrationTest.Client a = new GameLifecycleIntegrationTest.Client(h.server.getLocalPort());
                GameLifecycleIntegrationTest.Client b = new GameLifecycleIntegrationTest.Client(h.server.getLocalPort())) {
            a.send(common.Protocol.LOGIN, "Alex;123"); a.expect(common.Protocol.LOGIN_SUCCESS);
            b.send(common.Protocol.LOGIN, "Nina;123"); b.expect(common.Protocol.LOGIN_SUCCESS);
            GameRoom room = GameLifecycleIntegrationTest.invite(h, a, b);
            check(RoomName.number(room.getId()) == 3 && h.manager.getMatchHistory("Alex").size() == 2, "Restart resumes saved numbering and preserves history");
            GameLifecycleIntegrationTest.exit(h, a, b, room);
        }
        System.out.println("PASS: numbered rooms over TCP, same Client labels, codec, old IDs, fresh/rematch numbering and restart persistence");
    }
}
