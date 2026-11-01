import common.GameStateCodec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import model.GameState;
import model.Player;
import model.Position;
import model.Treasure;
import server.GameRoom;

public class TreasureCollectionTest {
    private static void check(boolean condition, String description) {
        if (!condition) {
            throw new AssertionError(description);
        }
    }

    static void validate(GameState state) {
        check(state.getTreasures().size() == 5, "Exactly five treasures after respawn");
        Set<Integer> cells = new HashSet<>();
        Set<String> ids = new HashSet<>();
        for (Player player : new Player[] { state.getPlayer1(), state.getPlayer2() }) {
            Position p = player.getPosition();
            check(cells.add(p.getRow() * 7 + p.getCol()), "Players do not collide");
        }
        for (Treasure treasure : state.getTreasures()) {
            Position p = treasure.getPosition();
            check(p.getRow() >= 0 && p.getRow() < 7 && p.getCol() >= 0 && p.getCol() < 7, "Treasure bounds");
            check(state.getMap()[p.getRow()][p.getCol()] == 0, "Treasure not on obstacle");
            check(cells.add(p.getRow() * 7 + p.getCol()), "Treasure not on a player or another treasure");
            check(!treasure.getId().isEmpty() && ids.add(treasure.getId()), "Unique treasure ids");
            check(treasure.getValue() == Treasure.valueForType(treasure.getType()), "Type matches value");
        }
        check(state.getRemainingTime() >= 0 && state.getRemainingTime() <= 180 && "RUNNING".equals(state.getStatus()),
                "Valid server remaining time");
    }

    static void validateReplacement(GameState before, GameState after, String collectedId) {
        validate(after);
        Set<String> oldIds = new HashSet<>();
        for (Treasure old : before.getTreasures()) {
            oldIds.add(old.getId());
            Treasure remaining = after.getTreasures().stream()
                    .filter(treasure -> old.getId().equals(treasure.getId())).findFirst().orElse(null);
            if (old.getId().equals(collectedId)) {
                check(remaining == null, "Collected treasure id disappears");
            } else {
                check(remaining != null && old.getType().equals(remaining.getType())
                        && old.getValue() == remaining.getValue()
                        && old.getPosition().getRow() == remaining.getPosition().getRow()
                        && old.getPosition().getCol() == remaining.getPosition().getCol(), "Other treasures remain unchanged");
            }
        }
        long replacements = after.getTreasures().stream().filter(treasure -> !oldIds.contains(treasure.getId())).count();
        check(replacements == 1, "Exactly one new treasure id");
    }

    private static boolean occupied(GameState state, int row, int col) {
        if (state.getMap()[row][col] == 1) {
            return true;
        }
        Position p2 = state.getPlayer2().getPosition();
        if (p2.getRow() == row && p2.getCol() == col) {
            return true;
        }
        return state.getTreasures().stream().anyMatch(treasure -> treasure.getPosition().getRow() == row
                && treasure.getPosition().getCol() == col);
    }

    public static void main(String[] args) throws Exception {
        Player alex = MapGenerationTest.player("Alex");
        Player nina = MapGenerationTest.player("Nina");
        var clock = new java.util.concurrent.atomic.AtomicLong();
        var constructor = GameRoom.class.getDeclaredConstructor(Player.class, Player.class,
                java.util.function.LongSupplier.class, java.util.Random.class);
        constructor.setAccessible(true);
        GameRoom room = constructor.newInstance(alex, nina, (java.util.function.LongSupplier) clock::get, new java.util.Random(710));
        room.startGame();
        alex.setStatus(Player.Status.PLAYING);
        nina.setStatus(Player.Status.PLAYING);
        GameState state = room.getGameState();
        int[][] map = new int[7][7];
        map[2][2] = 1;
        map[4][4] = 1;
        state.setMap(map);
        alex.setPosition(new Position(1, 1));
        nina.setPosition(new Position(6, 6));
        state.setTreasures(new ArrayList<>(Arrays.asList(
                new Treasure("T1", new Position(1, 2), 10, Treasure.NORMAL),
                new Treasure("T2", new Position(2, 1), 20, Treasure.SILVER),
                new Treasure("T3", new Position(3, 3), 30, Treasure.GOLD),
                new Treasure("T4", new Position(0, 5), 10, Treasure.NORMAL),
                new Treasure("T5", new Position(6, 1), 20, Treasure.SILVER))));
        int total = 0;
        int rewards = 0, traps = 0;
        Set<String> allIds = new HashSet<>();
        state.getTreasures().forEach(treasure -> allIds.add(treasure.getId()));
        int[][] directions = { { -1, 0 }, { 1, 0 }, { 0, -1 }, { 0, 1 } };
        String[] moves = { "DOWN", "UP", "RIGHT", "LEFT" };
        for (int i = 0; rewards < 1000; i++) {
            check(i < 2000, "Bounded run still covers 1000 successful reward collections");
            clock.addAndGet(2_000_000_000L);
            synchronized (room) {
                Treasure target = null;
                Position from = null;
                String move = null;
                for (Treasure treasure : state.getTreasures()) {
                    for (int d = 0; d < directions.length; d++) {
                        int row = treasure.getPosition().getRow() + directions[d][0];
                        int col = treasure.getPosition().getCol() + directions[d][1];
                        if (row >= 0 && row < 7 && col >= 0 && col < 7 && !occupied(state, row, col)) {
                            target = treasure;
                            from = new Position(row, col);
                            move = moves[d];
                            break;
                        }
                    }
                    if (target != null) {
                        break;
                    }
                }
                check(target != null, "A treasure can be approached");
                alex.setPosition(from);
                GameState before = GameStateCodec.decode(GameStateCodec.encode(state));
                check(room.handleMove("Alex", move) == null, "Move into treasure is valid");
                Treasure collected = room.collectTreasure(alex);
                common.TrapEvent trap = room.pollTrapEvent();
                if (trap == null) {
                    check(collected == target, "Collect the reward at the moved position");
                    total += Treasure.valueForType(target.getType()); rewards++;
                } else {
                    check(collected == null && trap.pointsLost() == Math.min(10, total), "Trap cannot also award treasure");
                    total = Math.max(0, total - 10); traps++;
                    check(trap.totalScore() == total, "Trap event reflects exact deduction");
                }
                check(alex.getMatchScore() == total && alex.getCollectedTreasure() == rewards, "Exact accumulated score and reward count");
                check(nina.getMatchScore() == 0 && nina.getCollectedTreasure() == 0, "Opponent does not gain points");
                String payload = GameStateCodec.encode(state);
                GameState after = GameStateCodec.decode(payload);
                check(after.getRemainingTime() == 180, "Collection alone never decrements time");
                validateReplacement(before, after, target.getId());
                for (Treasure treasure : after.getTreasures()) {
                    if (!before.getTreasures().stream().anyMatch(old -> old.getId().equals(treasure.getId()))) {
                        check(allIds.add(treasure.getId()), "Respawn id has never been used before");
                    }
                }
                check(payload.equals(GameStateCodec.encode(after)), "Codec preserves respawn, score and count");
                check(room.collectTreasure(alex) == null && payload.equals(GameStateCodec.encode(state)), "Same treasure cannot be scored twice");
                check(room.pollTrapEvent() == null, "Trap cannot trigger twice");
            }
        }
        check(rewards == 1000 && traps > 0, "All original reward coverage plus trap respawns");
        String before = GameStateCodec.encode(state);
        check(room.collectTreasure(MapGenerationTest.player("Alex")) == null, "Username alone cannot claim treasure");
        check(before.equals(GameStateCodec.encode(state)), "Invalid claimant has no effect");
        room.endGame();
        before = GameStateCodec.encode(state);
        check(room.collectTreasure(alex) == null && before.equals(GameStateCodec.encode(state)), "Stopped room cannot collect");
        System.out.println("PASS: 1000 rewards plus " + traps + " traps, exact score/count, valid respawn, unique ids, codec and double-collection guard");
        System.out.println("ALL TREASURE TESTS PASSED");
    }
}
