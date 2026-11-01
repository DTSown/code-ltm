import common.GameResultCodec;
import common.GameStateCodec;
import common.Message;
import common.Protocol;
import common.TrapEvent;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import model.GameResult;
import model.GameState;
import model.Position;
import model.Treasure;
import server.GameRoom;

/** Real sockets exercise the normal ClientHandler -> ServerManager path. */
public class TrapIntegrationTest {
    static void check(boolean ok, String description) {
        if (!ok) { throw new AssertionError(description); }
    }

    static GameRoom start(GameLifecycleIntegrationTest.Harness h,
            GameLifecycleIntegrationTest.Client a, GameLifecycleIntegrationTest.Client b, boolean rematch) throws Exception {
        a.send(rematch ? Protocol.PLAY_AGAIN : Protocol.INVITE, "Nina");
        b.expect(rematch ? Protocol.PLAY_AGAIN : Protocol.INVITE_RECEIVED);
        b.send(rematch ? Protocol.PLAY_AGAIN_ACCEPT : Protocol.INVITE_ACCEPT, "Alex");
        check(a.expect(Protocol.GAME_START).equals(b.expect(Protocol.GAME_START)), "Same start");
        state(a, b);
        return h.manager.getGameRooms().get(0);
    }

    static GameState state(GameLifecycleIntegrationTest.Client a, GameLifecycleIntegrationTest.Client b) throws Exception {
        String payload = a.expect(Protocol.GAME_STATE);
        check(payload.equals(b.expect(Protocol.GAME_STATE)), "Identical TCP GAME_STATE");
        return GameStateCodec.decode(payload);
    }

    @SuppressWarnings("unchecked")
    static void fixture(GameLifecycleIntegrationTest.Harness h, GameRoom room, int score) throws Exception {
        synchronized (h.manager) { synchronized (room) {
            Field field = GameRoom.class.getDeclaredField("trapTreasures"); field.setAccessible(true);
            Set<Treasure> traps = (Set<Treasure>) field.get(room);
            check(traps.size() == 1, "Exactly one initial secret trap");
            Treasure trap = traps.iterator().next();
            GameState s = room.getGameState(); s.setMap(new int[7][7]);
            s.getPlayer1().setPosition(new Position(1, 1)); s.getPlayer1().setMatchScore(score);
            s.getPlayer2().setPosition(new Position(6, 6));
            int col = 0;
            for (Treasure t : s.getTreasures()) {
                t.setPosition(t == trap ? new Position(1, 2) : new Position(5, col++));
            }
            String hidden = GameStateCodec.encode(s);
            traps.clear();
            check(hidden.equals(GameStateCodec.encode(s)), "Wire state identical with or without secret trap");
            traps.add(trap);
            check(hidden.equals(GameStateCodec.encode(GameStateCodec.decode(hidden))), "Unchanged public codec roundtrip");
        } }
    }

    static TrapEvent trigger(GameLifecycleIntegrationTest.Harness h, GameRoom room,
            GameLifecycleIntegrationTest.Client a, GameLifecycleIntegrationTest.Client b, int score) throws Exception {
        fixture(h, room, score);
        GameState before = GameStateCodec.decode(GameStateCodec.encode(room.getGameState()));
        String removed = before.getTreasures().stream().filter(t -> t.getPosition().getRow() == 1).findFirst().orElseThrow().getId();
        a.send(Protocol.MOVE, "RIGHT");
        String payload = a.expect(Protocol.TRAP_TRIGGERED);
        check(payload.equals(b.expect(Protocol.TRAP_TRIGGERED)), "Both clients receive the same single trap event");
        TrapEvent event = TrapEvent.decode(payload);
        check(event.roomId().equals(room.getId()) && event.username().equals("Alex")
                && event.pointsLost() == Math.min(10, score) && event.totalScore() == Math.max(0, score - 10)
                && event.stunMillis() == 2000, "Authoritative trap fields and actual deduction");
        GameState after = state(a, b);
        check(after.getPlayer1().getMatchScore() == Math.max(0, score - 10)
                && after.getPlayer1().getCollectedTreasure() == 0, "Trap is not a reward");
        check(after.getPlayer1().getPosition().getCol() == 2, "Moved onto trap before stun");
        TreasureCollectionTest.validateReplacement(before, after, removed);
        return event;
    }

    static void barrier(GameLifecycleIntegrationTest.Client client) throws Exception {
        client.send(Protocol.HELLO, ""); client.expect(Protocol.WELCOME);
    }

    static void gameplayAndRematch() throws Exception {
        var file = Files.createTempDirectory(java.nio.file.Path.of("out"), "trap-persistence-").resolve("data.tsv");
        try (var h = new GameLifecycleIntegrationTest.Harness(180, file);
                var a = h.client("Alex"); var b = h.client("Nina")) {
            GameRoom room = start(h, a, b, false);
            TrapEvent event = trigger(h, room, a, b, 25);
            long receivedAt = System.nanoTime();
            for (int i = 0; i < 80; i++) { a.send(Protocol.MOVE, "LEFT"); }
            barrier(a);
            check(room.getPlayer1().getPosition().getCol() == 2, "Flooded MOVE rejected silently while stunned");
            a.send(Protocol.TRAP_TRIGGERED, event.encode()); a.expect(Protocol.ERROR); barrier(a);
            check(room.getPlayer1().getMatchScore() == 15, "Forged trap event has no effect");
            b.send(Protocol.MOVE, "LEFT");
            GameState opponentMoved = state(a, b);
            check(opponentMoved.getPlayer2().getPosition().getCol() == 5
                    && opponentMoved.getPlayer1().getPosition().getCol() == 2, "Opponent moves during stun");
            String tick = a.expect(Protocol.TIME_UPDATE);
            check(tick.equals(b.expect(Protocol.TIME_UPDATE)) && Integer.parseInt(tick) < 180, "Clock keeps running");
            long remaining = TimeUnit.MILLISECONDS.toNanos(2050) - (System.nanoTime() - receivedAt);
            if (remaining > 0) { TimeUnit.NANOSECONDS.sleep(remaining); }
            a.send(Protocol.MOVE, "LEFT");
            check(state(a, b).getPlayer1().getPosition().getCol() == 1, "MOVE automatically resumes after two seconds");
            GameLifecycleIntegrationTest.exit(h, a, b, room);
            for (int score : new int[] {0, 5}) {
                room = start(h, a, b, true);
                trigger(h, room, a, b, score);
                GameResult result = GameLifecycleIntegrationTest.exit(h, a, b, room);
                check(result.getPlayer1Score() == 0 && result.getPlayer1Collected() == 0,
                        "Exit during stun keeps nonnegative result and no reward count");
            }
            room = start(h, a, b, true);
            check(room.getPlayer1().getMatchScore() == 0 && room.getPlayer1().getCollectedTreasure() == 0, "Fresh rematch score/count");
            fixture(h, room, 0);
            a.send(Protocol.MOVE, "LEFT");
            check(state(a, b).getPlayer1().getPosition().getCol() == 0, "Rematch has no previous stun");
            a.send(Protocol.MOVE, "RIGHT"); state(a, b);
            a.send(Protocol.MOVE, "RIGHT");
            a.expect(Protocol.TRAP_TRIGGERED); b.expect(Protocol.TRAP_TRIGGERED); state(a, b);
            a.close();
            GameResult disconnected = GameResultCodec.decode(b.expect(Protocol.GAME_OVER));
            check(GameResult.DISCONNECT.equals(disconnected.getReason()), "Disconnect during stun safely finishes");
            b.expect(Protocol.ONLINE_LIST);
            check(h.manager.getRanking().stream().allMatch(p -> p.getTotalTreasureCollected() == 0), "Traps excluded from lifetime statistics");
        }
        try (var reloaded = new server.ServerManager(180, file)) {
            check(reloaded.getMatchHistory("Alex").size() == 4, "Trap matches persisted once and reload");
            check(reloaded.getRanking().stream().allMatch(p -> p.getTotalTreasureCollected() == 0), "Persisted reward counts exclude traps");
        }
    }

    static void timeoutDuringStun() throws Exception {
        try (var h = new GameLifecycleIntegrationTest.Harness(1, null);
                var a = h.client("Alex"); var b = h.client("Nina")) {
            GameRoom room = start(h, a, b, false);
            trigger(h, room, a, b, 0);
            String result = a.expect(Protocol.GAME_OVER);
            check(result.equals(b.expect(Protocol.GAME_OVER)), "Identical timeout result during stun");
            check(GameResult.TIME_UP.equals(GameResultCodec.decode(result).getReason()) && !room.isTimerRunning(), "Timer ends stunned game safely");
        }
    }

    public static void main(String[] args) throws Exception {
        gameplayAndRematch(); timeoutDuringStun();
        System.out.println("ALL TRAP TCP TESTS PASSED: loss, secrecy, silent MOVE flood, recovery, opponent, timer, exit, rematch, disconnect, persistence");
    }
}
