import common.GameStateCodec;
import common.Message;
import common.Protocol;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import model.GameState;
import model.Player;
import model.Position;
import model.Treasure;
import server.ClientHandler;
import server.GameRoom;
import server.ServerManager;

public class MoveIntegrationTest {
    private static void check(boolean condition, String description) {
        if (!condition) {
            throw new AssertionError(description);
        }
    }

    private static class Client implements AutoCloseable {
        final Socket socket;
        final BufferedReader reader;
        final BufferedWriter writer;

        Client(int port) throws Exception {
            socket = new Socket("127.0.0.1", port);
            socket.setSoTimeout(3000);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        }

        void send(String type, String data) throws Exception {
            writer.write(new Message(type, data).encode());
            writer.newLine();
            writer.flush();
        }

        String expect(String type) throws Exception {
            for (int i = 0; i < 50; i++) {
                String line = reader.readLine();
                check(line != null, "Unexpected disconnect");
                Message message = Message.decode(line);
                if (type.equals(message.getType())) {
                    return message.getData();
                }
                check(Protocol.ONLINE_LIST.equals(message.getType()) || Protocol.TIME_UPDATE.equals(message.getType()),
                        "Unexpected message: " + line);
            }
            throw new AssertionError("Missing message: " + type);
        }

        void login(String username) throws Exception {
            send(Protocol.REGISTER, username + ";123");
            expect(Protocol.REGISTER_SUCCESS);
            send(Protocol.LOGIN, username + ";123");
            check(username.equals(expect(Protocol.LOGIN_SUCCESS)), "Login identity");
        }

        void barrier() throws Exception {
            send(Protocol.HELLO, "");
            expect(Protocol.WELCOME);
        }

        @Override
        public void close() throws Exception {
            socket.close();
        }
    }

    private static String bothState(Client alex, Client nina) throws Exception {
        String a = alex.expect(Protocol.GAME_STATE);
        String b = nina.expect(Protocol.GAME_STATE);
        check(a.equals(b), "Both clients receive the exact same ordered snapshot");
        GameStateCodec.decode(a);
        return a;
    }

    private static String snapshot(ServerManager manager, GameRoom room) {
        synchronized (manager) {
            synchronized (room) {
                return GameStateCodec.encode(room.getGameState());
            }
        }
    }

    private static boolean matchesCurrentState(ServerManager manager, GameRoom room, String payload) {
        GameState received = GameStateCodec.decode(payload);
        GameState current = GameStateCodec.decode(snapshot(manager, room));
        check(current.getRemainingTime() <= received.getRemainingTime(), "Server time never increases");
        current.setRemainingTime(received.getRemainingTime());
        return payload.equals(GameStateCodec.encode(current));
    }

    // Deterministic fixtures isolate movement rules from random map generation.
    private static String fixture(ServerManager manager, GameRoom room, Client alex, Client nina,
            int row1, int col1, int row2, int col2) throws Exception {
        synchronized (manager) {
            synchronized (room) {
                GameState state = room.getGameState();
                int[][] map = new int[7][7];
                map[2][2] = 1;
                map[4][4] = 1;
                state.setMap(map);
                state.getPlayer1().setPosition(new Position(row1, col1));
                state.getPlayer2().setPosition(new Position(row2, col2));
                state.setTreasures(new ArrayList<>(Arrays.asList(
                        new Treasure("T1", new Position(0, 5), 10, Treasure.NORMAL),
                        new Treasure("T2", new Position(6, 1), 10, Treasure.NORMAL),
                        new Treasure("T3", new Position(3, 1), 20, Treasure.SILVER),
                        new Treasure("T4", new Position(3, 5), 20, Treasure.SILVER),
                        new Treasure("T5", new Position(0, 3), 30, Treasure.GOLD))));
                Message stateMessage = new Message(Protocol.GAME_STATE, GameStateCodec.encode(state));
                manager.sendToPlayer("Alex", stateMessage);
                manager.sendToPlayer("Nina", stateMessage);
            }
        }
        return bothState(alex, nina);
    }

    private static void validMove(ServerManager manager, GameRoom room, Client alex, Client nina,
            Client sender, String direction, int row, int col) throws Exception {
        GameState expected = GameStateCodec.decode(snapshot(manager, room));
        Player moved = sender == alex ? expected.getPlayer1() : expected.getPlayer2();
        moved.setPosition(new Position(row, col));
        sender.send(Protocol.MOVE, direction);
        String received = bothState(alex, nina);
        expected.setRemainingTime(GameStateCodec.decode(received).getRemainingTime());
        check(received.equals(GameStateCodec.encode(expected)), "Only the sender's position changes");
        check(matchesCurrentState(manager, room, received), "Snapshot matches authoritative state");
    }

    private static void rejectedMove(ServerManager manager, GameRoom room, Client alex, Client nina,
            Client sender, String direction) throws Exception {
        String before = snapshot(manager, room);
        sender.send(Protocol.MOVE, direction);
        check(!sender.expect(Protocol.ERROR).isEmpty(), "Rejected MOVE gives a reason");
        check(matchesCurrentState(manager, room, before), "Rejected MOVE changes no state except normal timer ticks");
        alex.barrier();
        nina.barrier();
    }

    private static void collectMove(ServerManager manager, GameRoom room, Client alex, Client nina,
            Client sender, String direction, int row, int col, String treasureId) throws Exception {
        GameState before = GameStateCodec.decode(snapshot(manager, room));
        Treasure old = before.getTreasures().stream().filter(treasure -> treasureId.equals(treasure.getId())).findFirst().get();
        GameState expected = GameStateCodec.decode(GameStateCodec.encode(before));
        Player moving = sender == alex ? expected.getPlayer1() : expected.getPlayer2();
        int value = Treasure.valueForType(old.getType());
        moving.setPosition(new Position(row, col));
        moving.setMatchScore(moving.getMatchScore() + value);
        moving.setCollectedTreasure(moving.getCollectedTreasure() + 1);
        sender.send(Protocol.MOVE, direction);
        String notice = alex.expect(Protocol.TREASURE_COLLECTED);
        check(notice.equals(nina.expect(Protocol.TREASURE_COLLECTED)), "Same collection event for both clients");
        check(notice.equals(moving.getUsername() + ";" + old.getType() + ";" + value + ";" + moving.getMatchScore()),
                "Collection event carries authoritative type, value and total score");
        String received = bothState(alex, nina);
        GameState after = GameStateCodec.decode(received);
        TreasureCollectionTest.validateReplacement(before, after, treasureId);
        expected.setTreasures(after.getTreasures());
        expected.setRemainingTime(after.getRemainingTime());
        check(received.equals(GameStateCodec.encode(expected)) && matchesCurrentState(manager, room, received),
                "Only movement, collector score/count and replacement change");
        alex.barrier();
        nina.barrier();
    }

    private static void forgedMessage(ServerManager manager, GameRoom room, Client alex, Client nina,
            Client sender, String type, String data) throws Exception {
        String before = snapshot(manager, room);
        sender.send(type, data);
        sender.expect(Protocol.ERROR);
        check(matchesCurrentState(manager, room, before), "Forged " + type + " changes no game data");
        alex.barrier();
        nina.barrier();
    }

    private static void together(Client a, String directionA, Client b, String directionB) throws Exception {
        CountDownLatch go = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread[] senders = new Thread[2];
        Client[] clients = { a, b };
        String[] directions = { directionA, directionB };
        for (int i = 0; i < 2; i++) {
            final int index = i;
            senders[i] = new Thread(() -> {
                try {
                    go.await();
                    clients[index].send(Protocol.MOVE, directions[index]);
                } catch (Exception e) {
                    failure.set(e);
                }
            });
            senders[i].start();
        }
        go.countDown();
        for (Thread sender : senders) {
            sender.join(3000);
            check(!sender.isAlive(), "Concurrent sends complete");
        }
        check(failure.get() == null, "Concurrent send failure: " + failure.get());
    }

    public static void main(String[] args) throws Exception {
        ServerManager manager = new ServerManager(180);
        List<Thread> handlers = new ArrayList<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        try (ServerSocket server = new ServerSocket(0)) {
            Thread acceptor = new Thread(() -> {
                try {
                    while (!server.isClosed()) {
                        Thread thread = new Thread(new ClientHandler(server.accept(), manager));
                        thread.setUncaughtExceptionHandler((handler, error) -> failure.set(error));
                        handlers.add(thread);
                        thread.start();
                    }
                } catch (Exception e) {
                    if (!server.isClosed()) {
                        failure.set(e);
                    }
                }
            });
            acceptor.start();
            try (Client alex = new Client(server.getLocalPort());
                    Client nina = new Client(server.getLocalPort());
                    Client outsider = new Client(server.getLocalPort());
                    Client anonymous = new Client(server.getLocalPort())) {
                anonymous.send(Protocol.MOVE, "RIGHT");
                anonymous.expect(Protocol.ERROR);
                alex.login("Alex");
                nina.login("Nina");
                outsider.login("Erin");
                alex.send(Protocol.INVITE, "Nina");
                check("Alex".equals(nina.expect(Protocol.INVITE_RECEIVED)), "Invite sender");
                nina.send(Protocol.INVITE_ACCEPT, "Alex");
                check(alex.expect(Protocol.GAME_START).equals(nina.expect(Protocol.GAME_START)), "Same match");
                bothState(alex, nina);
                GameRoom room = manager.getGameRooms().get(0);
                fixture(manager, room, alex, nina, 1, 1, 5, 5);
                validMove(manager, room, alex, nina, alex, "RIGHT", 1, 2);
                validMove(manager, room, alex, nina, nina, "UP", 4, 5);
                validMove(manager, room, alex, nina, alex, "LEFT", 1, 1);
                validMove(manager, room, alex, nina, alex, "DOWN", 2, 1);
                System.out.println("PASS: valid RIGHT/UP/LEFT/DOWN, sender identity and identical TCP snapshots");

                rejectedMove(manager, room, alex, nina, alex, "RIGHT");
                fixture(manager, room, alex, nina, 0, 0, 5, 5);
                rejectedMove(manager, room, alex, nina, alex, "UP");
                rejectedMove(manager, room, alex, nina, alex, "LEFT");
                fixture(manager, room, alex, nina, 6, 6, 1, 1);
                rejectedMove(manager, room, alex, nina, alex, "DOWN");
                rejectedMove(manager, room, alex, nina, alex, "RIGHT");
                fixture(manager, room, alex, nina, 3, 2, 3, 3);
                rejectedMove(manager, room, alex, nina, alex, "RIGHT");
                rejectedMove(manager, room, alex, nina, nina, "LEFT");
                rejectedMove(manager, room, alex, nina, alex, "Nina;RIGHT");
                rejectedMove(manager, room, alex, nina, alex, "RIGHT|Nina");
                rejectedMove(manager, room, alex, nina, alex, "");
                rejectedMove(manager, room, alex, nina, outsider, "RIGHT");
                synchronized (manager) {
                    room.getPlayer1().setStatus(Player.Status.FREE);
                }
                rejectedMove(manager, room, alex, nina, alex, "LEFT");
                synchronized (manager) {
                    room.getPlayer1().setStatus(Player.Status.PLAYING);
                    room.getGameState().setStatus("WAITING");
                }
                rejectedMove(manager, room, alex, nina, alex, "LEFT");
                synchronized (manager) {
                    room.getGameState().setStatus("RUNNING");
                    Player erin = manager.getOnlinePlayers().stream()
                            .filter(player -> "Erin".equals(player.getUsername())).findFirst().get();
                    erin.setStatus(Player.Status.PLAYING);
                }
                rejectedMove(manager, room, alex, nina, outsider, "RIGHT");
                System.out.println("PASS: obstacles, all boundaries, collision, spoofed payload, login/status/room guards");

                fixture(manager, room, alex, nina, 3, 2, 5, 5);
                validMove(manager, room, alex, nina, alex, "RIGHT", 3, 3);
                check(room.getGameState().getTreasures().size() == 5, "Empty-cell move keeps all treasures");
                validMove(manager, room, alex, nina, alex, "UP", 2, 3);
                System.out.println("PASS: empty-cell movement preserves treasure list, scores, counts and time");

                String initial = fixture(manager, room, alex, nina, 1, 1, 5, 5);
                together(alex, "RIGHT", nina, "LEFT");
                GameState intermediate = GameStateCodec.decode(bothState(alex, nina));
                int moved = (intermediate.getPlayer1().getPosition().getCol() == 2 ? 1 : 0)
                        + (intermediate.getPlayer2().getPosition().getCol() == 4 ? 1 : 0);
                check(moved == 1, "First snapshot includes exactly one move");
                String finalPayload = bothState(alex, nina);
                GameState expected = GameStateCodec.decode(initial);
                expected.getPlayer1().setPosition(new Position(1, 2));
                expected.getPlayer2().setPosition(new Position(5, 4));
                expected.setRemainingTime(GameStateCodec.decode(finalPayload).getRemainingTime());
                check(finalPayload.equals(GameStateCodec.encode(expected))
                        && matchesCurrentState(manager, room, finalPayload), "Both simultaneous moves retained in order");

                fixture(manager, room, alex, nina, 3, 2, 3, 4);
                together(alex, "RIGHT", nina, "LEFT");
                String contestedPayload = bothState(alex, nina);
                GameState contested = GameStateCodec.decode(contestedPayload);
                boolean alexWon = contested.getPlayer1().getPosition().getCol() == 3;
                check(alexWon != (contested.getPlayer2().getPosition().getCol() == 3), "Only one player enters contested cell");
                (alexWon ? nina : alex).expect(Protocol.ERROR);
                check(matchesCurrentState(manager, room, contestedPayload), "Collision cannot overwrite winning move");
                alex.barrier();
                nina.barrier();

                fixture(manager, room, alex, nina, 3, 2, 3, 3);
                String beforeSwap = snapshot(manager, room);
                together(alex, "RIGHT", nina, "LEFT");
                alex.expect(Protocol.ERROR);
                nina.expect(Protocol.ERROR);
                check(matchesCurrentState(manager, room, beforeSwap), "Simultaneous swap is rejected");
                alex.barrier();
                nina.barrier();
                outsider.barrier();

                fixture(manager, room, alex, nina, 0, 4, 5, 5);
                collectMove(manager, room, alex, nina, alex, "RIGHT", 0, 5, "T1");
                fixture(manager, room, alex, nina, 3, 0, 5, 5);
                collectMove(manager, room, alex, nina, alex, "RIGHT", 3, 1, "T3");
                fixture(manager, room, alex, nina, 0, 2, 5, 5);
                collectMove(manager, room, alex, nina, alex, "RIGHT", 0, 3, "T5");
                fixture(manager, room, alex, nina, 1, 1, 6, 2);
                collectMove(manager, room, alex, nina, nina, "LEFT", 6, 1, "T2");
                check(room.getPlayer1().getMatchScore() == 60 && room.getPlayer1().getCollectedTreasure() == 3
                        && room.getPlayer2().getMatchScore() == 10 && room.getPlayer2().getCollectedTreasure() == 1,
                        "NORMAL/SILVER/GOLD totals apply to the correct player");
                System.out.println("PASS: NORMAL +10, SILVER +20, GOLD +30, counts, respawn and equal event/state delivery");

                forgedMessage(manager, room, alex, nina, alex, Protocol.TREASURE_COLLECTED, "Alex;GOLD;30;999999");
                forgedMessage(manager, room, alex, nina, anonymous, Protocol.TREASURE_COLLECTED, "Nina;GOLD;30;999999");
                GameState forged = GameStateCodec.decode(snapshot(manager, room));
                forged.getPlayer1().setMatchScore(999999);
                forged.getPlayer1().setCollectedTreasure(999);
                forgedMessage(manager, room, alex, nina, alex, Protocol.GAME_STATE, GameStateCodec.encode(forged));
                forgedMessage(manager, room, alex, nina, alex, "SCORE", "999999");
                System.out.println("PASS: forged collection, score and GAME_STATE messages have no effect");

                fixture(manager, room, alex, nina, 0, 2, 0, 4);
                GameState beforeRace = GameStateCodec.decode(snapshot(manager, room));
                together(alex, "RIGHT", nina, "LEFT");
                String raceEvent = alex.expect(Protocol.TREASURE_COLLECTED);
                check(raceEvent.equals(nina.expect(Protocol.TREASURE_COLLECTED)), "Exactly one shared collection event");
                String racePayload = bothState(alex, nina);
                GameState afterRace = GameStateCodec.decode(racePayload);
                boolean alexWins = afterRace.getPlayer1().getPosition().getCol() == 3;
                Player scorer = alexWins ? afterRace.getPlayer1() : afterRace.getPlayer2();
                Player oldScorer = alexWins ? beforeRace.getPlayer1() : beforeRace.getPlayer2();
                Player other = alexWins ? afterRace.getPlayer2() : afterRace.getPlayer1();
                Player oldOther = alexWins ? beforeRace.getPlayer2() : beforeRace.getPlayer1();
                check(raceEvent.equals(scorer.getUsername() + ";GOLD;30;" + scorer.getMatchScore()), "Winner named in event");
                check(scorer.getMatchScore() == oldScorer.getMatchScore() + 30
                        && scorer.getCollectedTreasure() == oldScorer.getCollectedTreasure() + 1
                        && other.getMatchScore() == oldOther.getMatchScore()
                        && other.getCollectedTreasure() == oldOther.getCollectedTreasure(), "Only first arrival gets points");
                TreasureCollectionTest.validateReplacement(beforeRace, afterRace, "T5");
                (alexWins ? nina : alex).expect(Protocol.ERROR);
                alex.barrier();
                nina.barrier();
                int exitRow = afterRace.getTreasures().stream().anyMatch(treasure -> treasure.getPosition().getRow() == 1
                        && treasure.getPosition().getCol() == 3) ? 0 : 1;
                int exitCol = exitRow == 1 ? 3 : 2;
                if (exitRow == 0 && afterRace.getPlayer1().getPosition().getCol() == 2) {
                    exitCol = 4;
                }
                validMove(manager, room, alex, nina, alexWins ? alex : nina, exitRow == 1 ? "DOWN"
                        : exitCol == 2 ? "LEFT" : "RIGHT", exitRow, exitCol);
                validMove(manager, room, alex, nina, alexWins ? nina : alex, alexWins ? "LEFT" : "RIGHT", 0, 3);
                outsider.barrier();
                System.out.println("PASS: simultaneous treasure contest scores once; later arrival gets no points from removed chest");
                room.endGame();
                rejectedMove(manager, room, alex, nina, alex, "UP");
                System.out.println("PASS: simultaneous moves, contested cell, swap collision, room-only delivery, stopped room");
            } finally {
                manager.close();
                server.close();
                acceptor.join(3000);
                check(!acceptor.isAlive(), "Acceptor stops");
                for (Thread handler : handlers) {
                    handler.join(3000);
                    check(!handler.isAlive(), "ClientHandler stops without deadlock");
                }
            }
        }
        check(failure.get() == null, "Server failure: " + failure.get());
        System.out.println("ALL MOVE AND TREASURE TCP TESTS PASSED");
    }
}
