import common.GameResultCodec;
import common.GameStateCodec;
import common.Message;
import common.Protocol;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import model.GameResult;
import model.Player;
import server.ClientHandler;
import server.GameRoom;
import server.ServerManager;

public class GameLifecycleIntegrationTest {
    static void check(boolean ok, String message) { if (!ok) { throw new AssertionError(message); } }

    static class Client implements AutoCloseable {
        final Socket socket;
        final BufferedReader in;
        final BufferedWriter out;
        Client(int port) throws Exception {
            socket = new Socket("127.0.0.1", port);
            socket.setSoTimeout(4000);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        }
        void send(String type, String data) throws Exception {
            out.write(new Message(type, data).encode()); out.newLine(); out.flush();
        }
        String expect(String type) throws Exception {
            for (int i = 0; i < 80; i++) {
                String line = in.readLine();
                check(line != null, "Unexpected EOF");
                Message message = Message.decode(line);
                if (type.equals(message.getType())) { return message.getData(); }
                check(Arrays.asList(Protocol.ONLINE_LIST, Protocol.TIME_UPDATE, Protocol.GAME_STATE,
                        Protocol.PLAY_AGAIN).contains(message.getType()), "Unexpected: " + line);
            }
            throw new AssertionError("Missing " + type);
        }
        void login(String name) throws Exception {
            send(Protocol.REGISTER, name + ";123"); expect(Protocol.REGISTER_SUCCESS);
            send(Protocol.LOGIN, name + ";123"); expect(Protocol.LOGIN_SUCCESS);
        }
        public void close() throws Exception { socket.close(); }
    }

    static class Harness implements AutoCloseable {
        final ServerManager manager;
        final ServerSocket server = new ServerSocket(0);
        final List<Thread> handlers = new CopyOnWriteArrayList<>();
        final List<ClientHandler> connections = new CopyOnWriteArrayList<>();
        final ConcurrentHashMap<String, Integer> results = new ConcurrentHashMap<>();
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final Thread acceptor;
        Harness() throws Exception {
            this(180, null);
        }
        Harness(int duration, java.nio.file.Path file) throws Exception {
            this(new ServerManager(duration, file));
        }
        Harness(ServerManager manager) throws Exception {
            this.manager = manager;
            acceptor = new Thread(() -> {
                try {
                    while (!server.isClosed()) {
                        ClientHandler handler = new ClientHandler(server.accept(), manager) {
                            public synchronized boolean sendMessage(Message message) {
                                if (Protocol.GAME_OVER.equals(message.getType())) {
                                    GameResult r = GameResultCodec.decode(message.getData());
                                    results.merge(r.getRoomId(), 1, Integer::sum);
                                    if (getPlayer().getStatus() != Player.Status.PLAYING) {
                                        failure.set(new AssertionError("Cleanup before result send"));
                                    }
                                }
                                return super.sendMessage(message);
                            }
                        };
                        connections.add(handler);
                        Thread thread = new Thread(handler, "LifecycleHandler");
                        thread.setUncaughtExceptionHandler((t, e) -> failure.set(e));
                        handlers.add(thread); thread.start();
                    }
                } catch (IOException e) { if (!server.isClosed()) { failure.set(e); } }
            });
            acceptor.start();
        }
        Client client(String name) throws Exception {
            Client client = new Client(server.getLocalPort()); client.login(name); return client;
        }
        public void close() throws Exception {
            manager.close(); server.close();
            for (ClientHandler handler : connections) { handler.closeConnection(); }
            acceptor.join(1000);
            for (Thread thread : handlers) { thread.join(1000); check(!thread.isAlive(), "Handler leak"); }
            check(failure.get() == null, "Handler failure: " + failure.get());
        }
    }

    static GameRoom readStart(Harness h, Client a, Client b) throws Exception {
        check(a.expect(Protocol.GAME_START).equals(b.expect(Protocol.GAME_START)), "Same start");
        String state = a.expect(Protocol.GAME_STATE);
        check(state.equals(b.expect(Protocol.GAME_STATE)), "Identical state");
        check(GameStateCodec.decode(state).getRemainingTime() == 180, "Fresh 180-second timer");
        GameRoom room = h.manager.getGameRooms().get(0);
        check(room.isTimerRunning(), "Timer started");
        return room;
    }
    static GameRoom invite(Harness h, Client a, Client b) throws Exception {
        a.send(Protocol.INVITE, "Nina"); b.expect(Protocol.INVITE_RECEIVED);
        b.send(Protocol.INVITE_ACCEPT, "Alex"); return readStart(h, a, b);
    }
    static GameResult exit(Harness h, Client a, Client b, GameRoom room) throws Exception {
        a.send(Protocol.EXIT_GAME, "");
        String result = a.expect(Protocol.GAME_OVER);
        check(result.equals(b.expect(Protocol.GAME_OVER)), "Same result for both");
        a.expect(Protocol.ONLINE_LIST); b.expect(Protocol.ONLINE_LIST);
        check(h.manager.getGameRooms().isEmpty() && !room.isTimerRunning(), "Cleanup and cancel");
        check(h.results.get(room.getId()) == 2, "Exactly one result per connected participant");
        return GameResultCodec.decode(result);
    }

    static void rematches() throws Exception {
        try (Harness h = new Harness(); Client a = h.client("Alex"); Client b = h.client("Nina")) {
            GameRoom old = invite(h, a, b);
            synchronized (h.manager) { synchronized (old) { old.getPlayer1().setMatchScore(100); } }
            GameResult result = exit(h, a, b, old);
            check(GameResult.PLAYER2.equals(result.getWinner()) && GameResult.PLAYER_EXIT.equals(result.getReason())
                    && result.getPlayer1Score() == 100 && result.getPlayer1RankGain() == 0
                    && result.getPlayer2RankGain() == 3, "Higher-scoring quitter loses");
            check(old.getPlayer1().getTotalRankPoint() == 0 && old.getPlayer2().getTotalRankPoint() == 3
                    && old.getPlayer2().getWins() == 1, "Rank/wins exactly once");
            a.send(Protocol.EXIT_GAME, ""); a.expect(Protocol.ERROR);
            a.send(Protocol.PLAY_AGAIN, "Nina"); check("Alex".equals(b.expect(Protocol.PLAY_AGAIN)), "Rematch prompt");
            b.send(Protocol.PLAY_AGAIN_ACCEPT, "Alex");
            GameRoom fresh = readStart(h, a, b);
            check(!old.getId().equals(fresh.getId()) && old.getGameState() != fresh.getGameState()
                    && !Arrays.deepEquals(old.getGameState().getMap(), fresh.getGameState().getMap()), "New room/state/map");
            check(fresh.getPlayer1().getMatchScore() == 0 && fresh.getPlayer2().getMatchScore() == 0
                    && fresh.getPlayer1().getCollectedTreasure() == 0 && fresh.getGameState().getTreasures().size() == 5
                    && fresh.getPlayer2().getTotalRankPoint() == 3, "Reset match, preserve rank");
            exit(h, a, b, fresh);
            a.send(Protocol.PLAY_AGAIN, "Nina"); b.expect(Protocol.PLAY_AGAIN);
            b.send(Protocol.PLAY_AGAIN_REJECT, "Alex");
            a.expect(Protocol.PLAY_AGAIN_REJECT); b.expect(Protocol.PLAY_AGAIN_REJECT);
            check(h.manager.getGameRooms().isEmpty(), "Reject creates no room");
            b.send(Protocol.PLAY_AGAIN_ACCEPT, "Alex"); b.expect(Protocol.ERROR);
            GameRoom third = invite(h, a, b); exit(h, a, b, third);
            CountDownLatch go = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread ta = new Thread(() -> { try { go.await(); a.send(Protocol.PLAY_AGAIN, "Nina"); } catch (Throwable e) { failure.set(e); } });
            Thread tb = new Thread(() -> { try { go.await(); b.send(Protocol.PLAY_AGAIN, "Alex"); } catch (Throwable e) { failure.set(e); } });
            ta.start(); tb.start(); go.countDown(); ta.join(); tb.join();
            check(failure.get() == null, "Concurrent request failure");
            GameRoom simultaneous = readStart(h, a, b);
            check(h.manager.getGameRooms().size() == 1, "Only one simultaneous rematch room");
            b.send(Protocol.PLAY_AGAIN_ACCEPT, "Alex"); b.expect(Protocol.ERROR);
            check(h.manager.getGameRooms().size() == 1, "Late accept cannot create second room");
            exit(h, a, b, simultaneous);
            a.send(Protocol.PLAY_AGAIN, "Nina"); b.expect(Protocol.PLAY_AGAIN);
            b.send(Protocol.RETURN_TO_LOBBY, "");
            a.expect(Protocol.PLAY_AGAIN_REJECT); b.expect(Protocol.PLAY_AGAIN_REJECT);
            a.send(Protocol.PLAY_AGAIN, "Nina"); a.expect(Protocol.ERROR);
            b.send(Protocol.PLAY_AGAIN_ACCEPT, "Alex"); b.expect(Protocol.ERROR);
            check(h.manager.getGameRooms().isEmpty(), "Lobby cancels stale requests");
            GameRoom fifth = invite(h, a, b); exit(h, a, b, fifth);
            b.send(Protocol.RETURN_TO_LOBBY, "");
            a.expect(Protocol.PLAY_AGAIN_REJECT); b.expect(Protocol.PLAY_AGAIN_REJECT);
            a.send(Protocol.PLAY_AGAIN, "Nina"); a.expect(Protocol.ERROR);
            check(h.manager.getGameRooms().isEmpty(), "Lobby without pending request also expires context");
            GameRoom sixth = invite(h, a, b); exit(h, a, b, sixth);
            a.send(Protocol.PLAY_AGAIN, "Nina"); b.expect(Protocol.PLAY_AGAIN);
            b.close(); a.expect(Protocol.PLAY_AGAIN_REJECT);
            a.send(Protocol.PLAY_AGAIN, "Nina"); a.expect(Protocol.ERROR);
            check(h.manager.getGameRooms().isEmpty(), "Result disconnect cancels rematch");
            check(sixth.getPlayer2().getTotalRankPoint() == 18 && sixth.getPlayer2().getWins() == 6,
                    "Result disconnect awards no extra rank or win");
        }
        System.out.println("PASS: EXIT, normal/simultaneous rematch, new state/map/timer, reject, lobby and result disconnect");
    }

    static void disconnect() throws Exception {
        try (Harness h = new Harness(); Client a = h.client("Alex"); Client b = h.client("Nina")) {
            GameRoom room = invite(h, a, b);
            a.close();
            GameResult r = GameResultCodec.decode(b.expect(Protocol.GAME_OVER));
            String list = b.expect(Protocol.ONLINE_LIST);
            check(GameResult.DISCONNECT.equals(r.getReason()) && GameResult.PLAYER2.equals(r.getWinner())
                    && r.getPlayer1RankGain() == 0 && r.getPlayer2RankGain() == 3, "Disconnect forfeit");
            check(list.contains("Nina,3,1,FREE") && !list.contains("Alex,"), "Survivor wins and FREE, quitter offline");
            check(h.results.get(room.getId()) == 1 && !room.isTimerRunning() && h.manager.getGameRooms().isEmpty(), "Single survivor result and cancellation");
            b.send(Protocol.MOVE, "RIGHT"); b.expect(Protocol.ERROR);
            try (Client another = h.client("Other")) { check(h.manager.getOnlinePlayers().size() == 2, "Server still accepts clients"); }
        }
        System.out.println("PASS: RUNNING disconnect, survivor +3/wins, one result, cancel, Server alive");
    }

    static void races() throws Exception {
        for (int i = 0; i < 20; i++) {
            Player a = MapGenerationTest.player("Alex"), b = MapGenerationTest.player("Nina");
            GameRoom room = new GameRoom(a, b); room.startGame();
            a.setMatchScore(100);
            CountDownLatch go = new CountDownLatch(1);
            Thread[] threads = {
                new Thread(() -> { try { go.await(); room.finishGame(); } catch (InterruptedException e) { throw new RuntimeException(e); } }),
                new Thread(() -> { try { go.await(); room.finishGame(a, GameResult.PLAYER_EXIT); } catch (InterruptedException e) { throw new RuntimeException(e); } }),
                new Thread(() -> { try { go.await(); room.finishGame(b, GameResult.DISCONNECT); } catch (InterruptedException e) { throw new RuntimeException(e); } })
            };
            for (Thread t : threads) { t.start(); } go.countDown();
            for (Thread t : threads) { t.join(1000); check(!t.isAlive(), "No finish deadlock"); }
            check(a.getTotalRankPoint() + b.getTotalRankPoint() == 3 && a.getWins() + b.getWins() == 1
                    && room.finishGame() == null && room.handleMove("Alex", "UP") != null, "Race awards exactly once");
            String encoded = GameResultCodec.encode(room.getResult());
            check(encoded.equals(GameResultCodec.encode(GameResultCodec.decode(encoded))), "Reason codec roundtrip");
            try {
                GameResultCodec.decode(encoded.substring(0, encoded.lastIndexOf(';')) + ";UNKNOWN");
                throw new AssertionError("Invalid reason accepted");
            } catch (IllegalArgumentException expected) { }
        }
        System.out.println("PASS: 20 TIME_UP/EXIT/DISCONNECT finish races");
    }
    public static void main(String[] args) throws Exception {
        rematches(); disconnect(); races();
        System.out.println("ALL LIFECYCLE TESTS PASSED");
    }
}
