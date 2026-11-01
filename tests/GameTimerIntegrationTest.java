import common.GameResultCodec;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import model.GameResult;
import model.GameState;
import model.Player;
import model.Position;
import model.Treasure;
import server.ClientHandler;
import server.GameRoom;
import server.ServerManager;

public class GameTimerIntegrationTest {
    private static void check(boolean condition, String description) {
        if (!condition) { throw new AssertionError(description); }
    }

    private static class Client implements AutoCloseable {
        final Socket socket;
        final BufferedReader reader;
        final BufferedWriter writer;

        Client(int port) throws Exception {
            socket = new Socket("127.0.0.1", port);
            socket.setSoTimeout(4000);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        }

        void send(String type, String data) throws Exception {
            writer.write(new Message(type, data).encode());
            writer.newLine();
            writer.flush();
        }

        String expect(String type) throws Exception {
            for (int i = 0; i < 30; i++) {
                String line = reader.readLine();
                check(line != null, "Unexpected disconnect");
                Message message = Message.decode(line);
                if (type.equals(message.getType())) { return message.getData(); }
                check(Protocol.ONLINE_LIST.equals(message.getType()), "Unexpected message: " + line);
            }
            throw new AssertionError("Missing message: " + type);
        }

        void login(String username) throws Exception {
            send(Protocol.REGISTER, username + ";123");
            expect(Protocol.REGISTER_SUCCESS);
            send(Protocol.LOGIN, username + ";123");
            expect(Protocol.LOGIN_SUCCESS);
        }

        @Override
        public void close() throws Exception { socket.close(); }
    }

    private static GameRoom start(ServerManager manager, Client alex, Client nina) throws Exception {
        alex.send(Protocol.INVITE, "Nina");
        check("Alex".equals(nina.expect(Protocol.INVITE_RECEIVED)), "Invitation received");
        nina.send(Protocol.INVITE_ACCEPT, "Alex");
        check(alex.expect(Protocol.GAME_START).equals(nina.expect(Protocol.GAME_START)), "Same start");
        String a = alex.expect(Protocol.GAME_STATE);
        check(a.equals(nina.expect(Protocol.GAME_STATE)), "Identical initial state");
        check(GameStateCodec.decode(a).getRemainingTime() == 2, "Test starts at two seconds");
        GameRoom room = manager.getGameRooms().get(0);
        check(room.isTimerRunning(), "Timer scheduled after initial state");
        return room;
    }

    private static void codecTests() {
        GameResult result = new GameResult("room-test", "Nguy\u1ec5n;+|", "DRAW", GameResult.PLAYER1, 30, 10, 1, 1, 3, 0);
        String data = GameResultCodec.encode(result);
        check(data.equals(GameResultCodec.encode(GameResultCodec.decode(data))), "Result codec UTF-8 and separators");
        String[] fields = data.split(";", -1);
        for (int index : new int[] { 3, 4, 6, 8 }) {
            String[] invalid = fields.clone();
            invalid[index] = index == 3 ? "INVALID" : "-1";
            try {
                GameResultCodec.decode(String.join(";", invalid));
                throw new AssertionError("Malformed result accepted");
            } catch (IllegalArgumentException expected) { }
        }
    }

    private static void finishMoveRace() throws Exception {
        for (int i = 0; i < 20; i++) {
            Player alex = MapGenerationTest.player("Alex");
            Player nina = MapGenerationTest.player("Nina");
            GameRoom room = new GameRoom(alex, nina);
            room.startGame();
            GameState state = room.getGameState();
            state.setMap(new int[7][7]);
            state.setRemainingTime(1);
            alex.setStatus(Player.Status.PLAYING);
            nina.setStatus(Player.Status.PLAYING);
            alex.setPosition(new Position(1, 1));
            nina.setPosition(new Position(5, 5));
            state.setTreasures(new ArrayList<>(Arrays.asList(new Treasure("G", new Position(1, 2), 30, Treasure.GOLD))));
            CountDownLatch go = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread move = new Thread(() -> {
                try {
                    go.await();
                    synchronized (room) {
                        if (room.handleMove("Alex", "RIGHT") == null) { room.collectTreasure(alex); }
                    }
                } catch (Throwable e) { failure.set(e); }
            });
            Thread finish = new Thread(() -> {
                try {
                    go.await();
                    synchronized (room) {
                        state.setRemainingTime(0);
                        room.finishGame();
                    }
                } catch (Throwable e) { failure.set(e); }
            });
            move.start();
            finish.start();
            go.countDown();
            move.join(1000);
            finish.join(1000);
            check(!move.isAlive() && !finish.isAlive() && failure.get() == null, "No finish/move race or deadlock");
            GameResult result = room.getResult();
            check(result != null && result.getPlayer1Score() == alex.getMatchScore()
                    && (alex.getMatchScore() == 0 || alex.getMatchScore() == 30), "Result snapshots final score atomically");
            int rank = alex.getTotalRankPoint();
            int wins = alex.getWins();
            check(room.finishGame() == null && room.handleMove("Alex", "LEFT") != null
                    && rank == alex.getTotalRankPoint() && wins == alex.getWins(), "Finish only once, no post-finish moves");
        }
        System.out.println("PASS: result codec and 20 move/collection versus finish races");
    }

    public static void main(String[] args) throws Exception {
        codecTests();
        ServerManager manager = new ServerManager(2);
        AtomicInteger overCount = new AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        List<Thread> handlers = new ArrayList<>();
        List<GameRoom> endedRooms = new ArrayList<>();
        try (ServerSocket server = new ServerSocket(0)) {
            Thread acceptor = new Thread(() -> {
                try {
                    while (!server.isClosed()) {
                        ClientHandler handler = new ClientHandler(server.accept(), manager) {
                            @Override
                            public synchronized boolean sendMessage(Message message) {
                                if (Protocol.GAME_OVER.equals(message.getType())) {
                                    if (getPlayer().getStatus() != Player.Status.PLAYING || manager.getGameRooms().isEmpty()) {
                                        failure.set(new AssertionError("Cleanup ran before GAME_OVER flush"));
                                    }
                                    overCount.incrementAndGet();
                                }
                                boolean sent = super.sendMessage(message);
                                if (Protocol.GAME_OVER.equals(message.getType()) && !sent) {
                                    failure.set(new AssertionError("GAME_OVER flush failed"));
                                }
                                return sent;
                            }
                        };
                        Thread thread = new Thread(handler);
                        thread.setUncaughtExceptionHandler((t, error) -> failure.set(error));
                        handlers.add(thread);
                        thread.start();
                    }
                } catch (Exception e) { if (!server.isClosed()) { failure.set(e); } }
            });
            acceptor.start();
            try (Client alex = new Client(server.getLocalPort()); Client nina = new Client(server.getLocalPort())) {
                alex.login("Alex");
                nina.login("Nina");
                int rank1 = 10, rank2 = 7, wins1 = 2, wins2 = 1;
                int[][] cases = { { 20, 10, 2, 1, 3, 0 }, { 20, 50, 2, 3, 0, 3 }, { 30, 30, 1, 3, 1, 1 } };
                for (int[] scenario : cases) {
                    GameRoom room = start(manager, alex, nina);
                    endedRooms.add(room);
                    synchronized (manager) {
                        synchronized (room) {
                            if (endedRooms.size() > 1) {
                                check(room.getPlayer1().getTotalRankPoint() == rank1 && room.getPlayer2().getTotalRankPoint() == rank2
                                        && room.getPlayer1().getWins() == wins1 && room.getPlayer2().getWins() == wins2,
                                        "Rank and wins survive a subsequent room's match reset");
                            }
                            room.getPlayer1().setTotalRankPoint(rank1);
                            room.getPlayer2().setTotalRankPoint(rank2);
                            room.getPlayer1().setWins(wins1);
                            room.getPlayer2().setWins(wins2);
                            room.getPlayer1().setMatchScore(scenario[0]);
                            room.getPlayer2().setMatchScore(scenario[1]);
                            room.getPlayer1().setCollectedTreasure(scenario[2]);
                            room.getPlayer2().setCollectedTreasure(scenario[3]);
                        }
                    }
                    for (int remaining : new int[] { 1, 0 }) {
                        check(Integer.toString(remaining).equals(alex.expect(Protocol.TIME_UPDATE))
                                && Integer.toString(remaining).equals(nina.expect(Protocol.TIME_UPDATE)), "Timer decrements equally to zero");
                    }
                    String finalState = alex.expect(Protocol.GAME_STATE);
                    check(finalState.equals(nina.expect(Protocol.GAME_STATE)), "Identical FINISHED state");
                    GameState decodedState = GameStateCodec.decode(finalState);
                    check(decodedState.getRemainingTime() == 0 && "FINISHED".equals(decodedState.getStatus()), "Final state at zero");
                    String resultData = alex.expect(Protocol.GAME_OVER);
                    check(resultData.equals(nina.expect(Protocol.GAME_OVER)), "Identical GAME_OVER result");
                    GameResult result = GameResultCodec.decode(resultData);
                    check(GameResult.TIME_UP.equals(result.getReason()), "Timer result reason TIME_UP");
                    check(result.getRoomId().equals(room.getId()) && result.getPlayer1Score() == scenario[0]
                            && result.getPlayer2Score() == scenario[1] && result.getPlayer1Collected() == scenario[2]
                            && result.getPlayer2Collected() == scenario[3] && result.getPlayer1RankGain() == scenario[4]
                            && result.getPlayer2RankGain() == scenario[5], "Scores, counts and rank gains correct");
                    rank1 += scenario[4];
                    rank2 += scenario[5];
                    wins1 += scenario[4] == 3 ? 1 : 0;
                    wins2 += scenario[5] == 3 ? 1 : 0;
                    String list = alex.expect(Protocol.ONLINE_LIST);
                    check(list.equals(nina.expect(Protocol.ONLINE_LIST)) && list.contains("Alex," + rank1 + "," + wins1 + ",FREE")
                            && list.contains("Nina," + rank2 + "," + wins2 + ",FREE"), "Updated ranks/wins and FREE broadcast after result");
                    check(manager.getGameRooms().isEmpty() && !room.isTimerRunning(), "Active room removed and timer canceled");
                    check(room.finishGame() == null && room.getPlayer1().getTotalRankPoint() == rank1
                            && room.getPlayer2().getTotalRankPoint() == rank2, "No duplicate rank awards");
                    alex.send(Protocol.MOVE, "RIGHT");
                    alex.expect(Protocol.ERROR);
                    nina.send(Protocol.MOVE, "UP");
                    nina.expect(Protocol.ERROR);
                    alex.send(Protocol.GAME_OVER, resultData);
                    alex.expect(Protocol.ERROR);
                    alex.send(Protocol.TIME_UPDATE, "180");
                    alex.expect(Protocol.ERROR);
                }
                check(overCount.get() == 6, "Exactly one GAME_OVER per client per room");
                check(GameResult.PLAYER1.equals(endedRooms.get(0).getResult().getWinner())
                        && endedRooms.get(0).getResult().getPlayer1Score() == 20, "Prior result unaffected by next match reset");
                GameRoom canceled = start(manager, alex, nina);
                manager.close();
                check(!canceled.isTimerRunning() && manager.getGameRooms().isEmpty() && manager.isTimerSchedulerShutdown(),
                        "Server shutdown cancels an active room timer and its scheduler");
                System.out.println("PASS: 2-second timer, P1/P2 win, draw, +3/+1/+0, wins, delivery order, FREE, cleanup and cancellation");
            } finally {
                manager.close();
                server.close();
                acceptor.join(1000);
                for (Thread thread : handlers) {
                    thread.join(1000);
                    check(!thread.isAlive(), "Handler terminates");
                }
            }
        }
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if ("GameTimer".equals(thread.getName())) {
                thread.join(1000);
                check(!thread.isAlive(), "No timer thread leak");
            }
        }
        check(failure.get() == null, "Server failure: " + failure.get());
        finishMoveRace();
        System.out.println("ALL TIMER AND RESULT TESTS PASSED");
    }
}
