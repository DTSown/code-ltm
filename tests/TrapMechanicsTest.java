package server;

import common.GameStateCodec;
import common.Message;
import common.Protocol;
import common.TrapEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import model.GameResult;
import model.GameState;
import model.Player;
import model.Position;
import model.Treasure;

public class TrapMechanicsTest {
    private static final long STUN_NANOS = 2_000_000_000L;

    private static void check(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
    }

    private static void rejects(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Malformed trap event accepted");
        } catch (IllegalArgumentException expected) { }
    }

    private static Object field(GameRoom room, String name) throws Exception {
        Field field = GameRoom.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(room);
    }

    @SuppressWarnings("unchecked")
    private static Set<Treasure> traps(GameRoom room) throws Exception {
        return (Set<Treasure>) field(room, "trapTreasures");
    }

    private static Player player(String name) {
        Player player = new Player();
        player.setUsername(name);
        player.setStatus(Player.Status.PLAYING);
        return player;
    }

    private static class ControlledRandom extends Random {
        int initialIndex;
        int typeIndex;
        int integerCalls;
        int probabilityCalls;
        double probability = 0.9;

        @Override public int nextInt(int bound) {
            if (integerCalls++ == 0) {
                check(bound == 5, "Initial trap samples all five chests");
                return initialIndex;
            }
            return integerCalls % 2 == 0 ? typeIndex : 0;
        }

        @Override public double nextDouble() {
            probabilityCalls++;
            return probability;
        }
    }

    private static GameRoom room(AtomicLong clock, ControlledRandom random) {
        GameRoom room = new GameRoom(player("Alex"), player("Nina"), clock::get, random);
        room.startGame();
        layout(room);
        return room;
    }

    private static void layout(GameRoom room) {
        GameState state = room.getGameState();
        state.setMap(new int[7][7]);
        state.getMap()[4][4] = 1;
        room.getPlayer1().setPosition(new Position(0, 0));
        room.getPlayer2().setPosition(new Position(6, 6));
        for (int i = 0; i < state.getTreasures().size(); i++) {
            state.getTreasures().get(i).setPosition(new Position(2, i + 1));
        }
    }

    private static Treasure collect(GameRoom room, Player player, Treasure target) {
        player.setPosition(target.getPosition());
        return room.collectTreasure(player);
    }

    private static void validState(GameRoom room) {
        String encoded = GameStateCodec.encode(room.getGameState());
        GameState decoded = GameStateCodec.decode(encoded);
        check(decoded.getTreasures().size() == 5, "Exactly five nonoverlapping chests");
        check(encoded.equals(GameStateCodec.encode(decoded)), "Existing state codec roundtrip");
        check(encoded.split(";", -1).length == 7, "No extra state fields");
        for (String chest : encoded.split(";", -1)[4].split("/")) {
            check(chest.split(",", -1).length == 5, "No secret treasure fields");
        }
    }

    private static void codec() {
        TrapEvent event = new TrapEvent("ROOM-7-example", Long.MAX_VALUE,
                "Nguy\u1ec5n ;,+|/%\r\n", 5, 0, 2000);
        check(event.equals(TrapEvent.decode(event.encode())), "UTF-8 and separators roundtrip");
        check(!event.encode().contains("|") && !event.encode().contains("\n"), "Safe wire encoding");
        check(event.encode().split(";", -1).length == 6, "Six event fields");
        TrapEvent zero = new TrapEvent("room", 1, "A", 0, 0, 0);
        check(zero.equals(TrapEvent.decode(zero.encode())), "Nonnegative lower bounds");
        for (String bad : new String[] { null, "", "r;1;A;0;0", "r;1;A;0;0;2000;extra",
                "r;0;A;0;0;2000", "r;-1;A;0;0;2000", "r;9223372036854775808;A;0;0;2000",
                ";1;A;0;0;2000", "r;1;;0;0;2000", "r;1;%20;0;0;2000", "r;1;%ZZ;0;0;2000",
                "r;1;A;-1;0;2000", "r;1;A;11;0;2000", "r;1;A;0;-1;2000",
                "r;1;A;0;2147483648;2000", "r;1;A;0;0;-1", "r;1;A;0;0;2001" }) {
            rejects(() -> TrapEvent.decode(bad));
        }
        rejects(() -> new TrapEvent("r;injected", 1, "A", 0, 0, 2000));
        rejects(() -> new TrapEvent("r\nmessage", 1, "A", 0, 0, 2000));
        rejects(() -> new TrapEvent("r\nmessage\nextra", 1, "A", 0, 0, 2000));
        rejects(() -> new TrapEvent("r", 1, null, 0, 0, 2000));
    }

    private static void initialAndIdentity() throws Exception {
        for (int selected = 0; selected < 5; selected++) {
            ControlledRandom random = new ControlledRandom();
            random.initialIndex = selected;
            GameRoom room = room(new AtomicLong(), random);
            Treasure trap = room.getGameState().getTreasures().get(selected);
            check(traps(room).size() == 1 && traps(room).contains(trap), "Exactly one random initial trap");
            check(random.probabilityCalls == 0, "Initial trap is guaranteed");
            String before = GameStateCodec.encode(room.getGameState());
            traps(room).clear();
            check(before.equals(GameStateCodec.encode(room.getGameState())), "Trap membership is secret");
            traps(room).add(trap);
            room.startGame();
            check(random.integerCalls == 1 && traps(room).contains(trap), "Repeated start does not reroll");
            Treasure sameId = new Treasure(trap.getId(), trap.getPosition(), trap.getValue(), trap.getType());
            room.getGameState().getTreasures().set(selected, sameId);
            check(!traps(room).contains(sameId), "Same T1-style id is not the same trap");
            check(collect(room, room.getPlayer1(), sameId) == sameId, "Replacement fixture remains a reward");
            check(room.pollTrapEvent() == null && room.getPlayer1().getCollectedTreasure() == 1,
                    "Identity-isolated reward counts normally");
            validState(room);
        }
    }

    private static void penaltyAndBoundary() throws Exception {
        for (int score : new int[] { 0, 5, 10, 37 }) {
            // Includes negative nanoTime values and wraparound of the signed long counter.
            for (long origin : new long[] { 0, -9_000_000_000L, Long.MAX_VALUE - 1_000_000_000L }) {
                AtomicLong clock = new AtomicLong(origin);
                GameRoom room = room(clock, new ControlledRandom());
                Player a = room.getPlayer1();
                a.setMatchScore(score);
                a.setCollectedTreasure(3);
                a.setTotalTreasureCollected(11);
                Treasure trap = traps(room).iterator().next();
                check(collect(room, a, trap) == null, "Trap is not a reward");
                TrapEvent event = room.pollTrapEvent();
                check(event != null && event.eventId() == 1 && event.roomId().equals(room.getId())
                        && event.username().equals(a.getUsername()) && event.pointsLost() == Math.min(10, score)
                        && event.totalScore() == Math.max(0, score - 10) && event.stunMillis() == 2000,
                        "Exact deduction and first event");
                check(a.getCollectedTreasure() == 3 && a.getTotalTreasureCollected() == 11,
                        "Trap never counts as a collection");
                check(!room.getGameState().getTreasures().contains(trap) && traps(room).isEmpty(),
                        "Consumed trap removed and replaced");
                validState(room);
                check(room.pollTrapEvent() == null, "Event is consumed exactly once");
                String state = GameStateCodec.encode(room.getGameState());
                check(room.collectTreasure(player("Alex")) == null, "Other session cannot collect");
                for (int i = 0; i < 100; i++) {
                    check(GameRoom.MOVE_STUNNED.equals(room.handleMove("Alex", "RIGHT")), "Flood is blocked");
                }
                check(state.equals(GameStateCodec.encode(room.getGameState())), "Blocked moves preserve state");
                a.setPosition(room.getGameState().getTreasures().get(0).getPosition());
                check(room.collectTreasure(a) == null && a.getCollectedTreasure() == 3
                        && room.pollTrapEvent() == null, "Stun also blocks direct collection");
                a.setPosition(new Position(0, 0));
                clock.set(origin + STUN_NANOS - 1);
                check(GameRoom.MOVE_STUNNED.equals(room.handleMove("Alex", "RIGHT")),
                        "Still stunned at exactly 1999.999999ms");
                check(room.handleMove("Nina", "LEFT") == null, "Opponent can move during stun");
                clock.set(origin + STUN_NANOS);
                check(room.handleMove("Alex", "RIGHT") == null, "Recovered at exactly 2000ms");
                check(room.getGameState().getRemainingTime() == 180, "Stun does not advance countdown");
            }
        }
    }

    private static void independentStunsAndSequence() throws Exception {
        AtomicLong clock = new AtomicLong();
        ControlledRandom random = new ControlledRandom();
        random.probability = 0;
        GameRoom room = room(clock, random);
        collect(room, room.getPlayer1(), traps(room).iterator().next());
        clock.set(500_000_000L);
        collect(room, room.getPlayer2(), traps(room).iterator().next());
        TrapEvent first = room.pollTrapEvent(), second = room.pollTrapEvent();
        check(first.eventId() == 1 && second.eventId() == 2
                && first.username().equals("Alex") && second.username().equals("Nina")
                && room.pollTrapEvent() == null, "Room sequence and FIFO one-time delivery");
        room.getPlayer1().setPosition(new Position(0, 0));
        room.getPlayer2().setPosition(new Position(6, 6));
        clock.set(STUN_NANOS);
        check(room.handleMove("Alex", "RIGHT") == null, "First stun expires independently");
        check(GameRoom.MOVE_STUNNED.equals(room.handleMove("Nina", "LEFT")), "Second stun remains active");
        clock.set(STUN_NANOS + 500_000_000L);
        check(room.handleMove("Nina", "LEFT") == null, "Second stun expires independently");
    }

    private static void respawnProbability() throws Exception {
        String[] types = { Treasure.NORMAL, Treasure.SILVER, Treasure.GOLD };
        double[] draws = { 0, Math.nextDown(0.2), 0.2, Math.nextUp(0.2), Math.nextDown(1.0) };
        for (int type = 0; type < types.length; type++) {
            AtomicLong clock = new AtomicLong();
            ControlledRandom random = new ControlledRandom();
            random.typeIndex = type;
            GameRoom room = room(clock, random);
            int expectedScore = 0, expectedCount = 0, trapRespawns = 0;
            long expectedEvent = 0;
            Treasure target = traps(room).iterator().next();
            for (int i = 0; i < 1000; i++) {
                clock.addAndGet(STUN_NANOS);
                random.probability = i < draws.length ? draws[i] : (i % 10) / 10.0;
                boolean wasTrap = traps(room).contains(target);
                Treasure reward = collect(room, room.getPlayer1(), target);
                TrapEvent event = room.pollTrapEvent();
                if (wasTrap) {
                    expectedScore = Math.max(0, expectedScore - 10);
                    check(reward == null && event != null && event.eventId() == ++expectedEvent,
                            "Only trap collections emit sequenced events");
                } else {
                    expectedScore += target.getValue();
                    expectedCount++;
                    check(reward == target && event == null, "Reward signature preserved");
                }
                check(room.getPlayer1().getMatchScore() == expectedScore
                        && room.getPlayer1().getCollectedTreasure() == expectedCount, "Exact mixed collection totals");
                List<Treasure> treasures = room.getGameState().getTreasures();
                Treasure replacement = treasures.get(treasures.size() - 1);
                check(!replacement.getId().equals(target.getId()) && replacement.getType().equals(types[type]),
                        "Fresh id and independently selected appearance");
                boolean expectedTrap = random.probability < 0.2;
                check(traps(room).contains(replacement) == expectedTrap, "Exact 20% probability boundary");
                if (i >= 10 && expectedTrap) { trapRespawns++; }
                validState(room);
                target = replacement;
            }
            check(trapRespawns == 198 && random.probabilityCalls == 1000,
                    "20% of 990 evenly spaced draws, one independent draw per respawn");
        }
    }

    private static void finishedState(GameRoom room) throws Exception {
        check(traps(room).isEmpty() && ((Map<?, ?>) field(room, "stunStartedAt")).isEmpty()
                && room.pollTrapEvent() == null, "Finish clears traps, stuns and pending events");
        String state = GameStateCodec.encode(room.getGameState());
        check(room.handleMove("Alex", "RIGHT") != null && room.collectTreasure(room.getPlayer1()) == null
                && state.equals(GameStateCodec.encode(room.getGameState())), "Finished room cannot mutate");
    }

    private static void finishesAndRematch() throws Exception {
        for (String reason : new String[] { GameResult.TIME_UP, GameResult.PLAYER_EXIT, GameResult.DISCONNECT }) {
            AtomicLong clock = new AtomicLong();
            GameRoom room = room(clock, new ControlledRandom());
            Player a = room.getPlayer1(), b = room.getPlayer2();
            a.setMatchScore(25);
            a.setCollectedTreasure(2);
            a.setTotalTreasureCollected(7);
            collect(room, a, traps(room).iterator().next());
            if (reason.equals(GameResult.TIME_UP)) { room.getGameState().setRemainingTime(0); }
            GameResult result = room.finishGame(reason.equals(GameResult.TIME_UP) ? null : a, reason);
            check(result.getPlayer1Score() == 15 && result.getPlayer1Collected() == 2
                    && a.getTotalTreasureCollected() == 9 && a.getTotalMatches() == 1,
                    "Finish snapshots post-trap scores and real reward statistics");
            check(result.getWinner().equals(reason.equals(GameResult.TIME_UP) ? GameResult.PLAYER1 : GameResult.PLAYER2),
                    "Stun never prevents timeout or forfeit");
            check(room.finishGame() == null && a.getTotalMatches() == 1, "Finish statistics applied once");
            finishedState(room);
            GameRoom fresh = new GameRoom(a, b, clock::get, new ControlledRandom());
            fresh.startGame(room.getGameState().getMap());
            layout(fresh);
            check(!fresh.getId().equals(room.getId()) && traps(fresh).size() == 1 && fresh.pollTrapEvent() == null
                    && a.getMatchScore() == 0 && a.getCollectedTreasure() == 0, "Fresh rematch state");
            check(fresh.handleMove("Alex", "RIGHT") == null, "No inherited rematch stun");
            collect(fresh, a, traps(fresh).iterator().next());
            check(fresh.pollTrapEvent().eventId() == 1, "Sequence restarts only in a new room");
            fresh.endGame();
            finishedState(fresh);
        }
    }

    private static class RecordingClient extends ClientHandler {
        final ServerManager manager;
        final List<String> messages = new ArrayList<>();
        GameRoom room;

        RecordingClient(ServerManager manager, String name) {
            super(null, manager);
            this.manager = manager;
            check(manager.registerAccount(name, "test") == null, "Register fixture");
            check(manager.loginClient(this, name, "test") == null, "Login fixture");
        }

        @Override public synchronized boolean sendMessage(Message message) {
            if (room != null && (Protocol.TRAP_TRIGGERED.equals(message.getType())
                    || Protocol.GAME_STATE.equals(message.getType()))) {
                check(Thread.holdsLock(manager) && Thread.holdsLock(room), "Event and state sent under both locks");
            }
            messages.add(message.encode());
            return true;
        }
    }

    private static void managerDelivery() throws Exception {
        try (ServerManager manager = new ServerManager(180)) {
            RecordingClient a = new RecordingClient(manager, "Alex"), b = new RecordingClient(manager, "Nina");
            GameRoom room = manager.createGameRoom(a.getPlayer(), b.getPlayer());
            a.room = b.room = room;
            layout(room);
            Treasure trap = traps(room).iterator().next();
            trap.setPosition(new Position(0, 1));
            a.getPlayer().setMatchScore(5);
            manager.move(a, "RIGHT");
            check(a.messages.equals(b.messages) && a.messages.size() == 2
                    && a.messages.get(0).startsWith(Protocol.TRAP_TRIGGERED + "|")
                    && a.messages.get(1).startsWith(Protocol.GAME_STATE + "|"), "Identical event then state to both");
            check(room.pollTrapEvent() == null, "Manager drains event once");
            int count = a.messages.size();
            for (int i = 0; i < 100; i++) { manager.move(a, "DOWN"); }
            check(a.messages.size() == count && b.messages.size() == count, "Stunned moves silently ignored");
            manager.move(b, "LEFT");
            check(a.messages.size() == count + 1 && a.messages.equals(b.messages), "Opponent broadcasts while stunned");
            Method tick = ServerManager.class.getDeclaredMethod("tickRoom", GameRoom.class);
            tick.setAccessible(true);
            tick.invoke(manager, room);
            check(room.getGameState().getRemainingTime() == 179
                    && a.messages.get(a.messages.size() - 1).equals("TIME_UPDATE|179"), "Timer advances during stun");
            String before = GameStateCodec.encode(room.getGameState());
            a.handleMessage(new Message(Protocol.TRAP_TRIGGERED, "forged"));
            check(a.messages.get(a.messages.size() - 1).startsWith("ERROR|")
                    && before.equals(GameStateCodec.encode(room.getGameState())), "Client trap forgery rejected");
            manager.exitGame(a);
            check(room.getResult().getReason().equals(GameResult.PLAYER_EXIT), "Stunned player may exit");
            finishedState(room);
        }
    }

    private static void finishRace() throws Exception {
        for (int i = 0; i < 20; i++) {
            GameRoom room = room(new AtomicLong(), new ControlledRandom());
            Player a = room.getPlayer1();
            a.setMatchScore(25);
            a.setPosition(traps(room).iterator().next().getPosition());
            CountDownLatch go = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread collect = new Thread(() -> {
                try { go.await(); room.collectTreasure(a); }
                catch (Throwable error) { failure.set(error); }
            });
            Thread finish = new Thread(() -> {
                try { go.await(); room.finishGame(); }
                catch (Throwable error) { failure.set(error); }
            });
            collect.start(); finish.start(); go.countDown();
            collect.join(2000); finish.join(2000);
            check(!collect.isAlive() && !finish.isAlive() && failure.get() == null, "Trap/finish race completes");
            int score = room.getResult().getPlayer1Score();
            check((score == 15 || score == 25) && a.getMatchScore() == score
                    && a.getCollectedTreasure() == 0 && a.getTotalMatches() == 1, "Atomic trap/finish result");
            finishedState(room);
        }
    }

    public static void main(String[] args) throws Exception {
        codec();
        acceptedUsernameCompatibility();
        initialAndIdentity();
        penaltyAndBoundary();
        independentStunsAndSequence();
        respawnProbability();
        finishesAndRematch();
        managerDelivery();
        finishRace();
        System.out.println("ALL TRAP MECHANICS TESTS PASSED (codec, identity, nanosecond boundaries, 3000 respawns, lifecycle, delivery, races)");
    }

    private static void acceptedUsernameCompatibility() throws Exception {
        String username = "\u2003";
        try (ServerManager manager = new ServerManager(180)) {
            check(manager.registerAccount(username, "password") == null, "Existing registration accepts Unicode space username");
        }
        GameRoom room = room(new AtomicLong(), new ControlledRandom());
        room.getPlayer1().setUsername(username);
        check(collect(room, room.getPlayer1(), traps(room).iterator().next()) == null, "Accepted username can trigger trap");
        TrapEvent event = room.pollTrapEvent();
        check(event != null && event.username().equals(username) && event.equals(TrapEvent.decode(event.encode())),
                "Trap codec preserves existing username contract");
        validState(room); room.finishGame();
        check(room.getResult().getPlayer1Username().equals(username), "Result retains accepted username");
    }
}
