import common.Message;
import common.Protocol;
import common.GameStateCodec;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import server.ClientHandler;
import server.GameRoom;
import server.ServerManager;

public class InviteIntegrationTest {
    private static void check(boolean condition, String description) {
        if (!condition) {
            throw new AssertionError(description);
        }
    }

    private static class Client implements AutoCloseable {
        final Socket socket;
        final BufferedReader reader;
        final BufferedWriter writer;
        String lastOnlineList;

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
                check(line != null, "Connection unexpectedly closed");
                Message message = Message.decode(line);
                if (Protocol.ONLINE_LIST.equals(message.getType())) {
                    lastOnlineList = message.getData();
                }
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
            check(username.equals(expect(Protocol.LOGIN_SUCCESS)), "Login username");
        }

        @Override
        public void close() throws Exception {
            socket.close();
        }
    }

    public static void main(String[] args) throws Exception {
        ServerManager manager = new ServerManager(180);
        List<Thread> handlers = new ArrayList<>();
        List<Client> clients = new ArrayList<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        try (ServerSocket server = new ServerSocket(0)) {
            Thread acceptor = new Thread(() -> {
                try {
                    while (!server.isClosed()) {
                        Socket socket = server.accept();
                        Thread handler = new Thread(new ClientHandler(socket, manager));
                        handler.setUncaughtExceptionHandler((thread, error) -> failure.set(error));
                        handlers.add(handler);
                        handler.start();
                    }
                } catch (Exception e) {
                    if (!server.isClosed()) {
                        failure.set(e);
                    }
                }
            });
            acceptor.start();
            try {
                Client alex = new Client(server.getLocalPort());
                Client nina = new Client(server.getLocalPort());
                Client erin = new Client(server.getLocalPort());
                clients.add(alex);
                clients.add(nina);
                clients.add(erin);

                alex.send(Protocol.INVITE, "Nina");
                alex.expect(Protocol.ERROR);
                alex.login("Alex");
                nina.login("Nina");
                erin.login("Erin");
                check(manager.getOnlinePlayers().size() == 3, "Exactly three online players");
                alex.send(Protocol.LOGIN, "Erin;123");
                alex.expect(Protocol.LOGIN_FAIL);
                alex.send(Protocol.INVITE, "Alex");
                alex.expect(Protocol.ERROR);
                alex.send(Protocol.INVITE, "Missing");
                alex.expect(Protocol.ERROR);
                nina.send(Protocol.INVITE_ACCEPT, "Alex");
                nina.expect(Protocol.ERROR);

                alex.send(Protocol.INVITE, "Nina");
                check("Alex".equals(nina.expect(Protocol.INVITE_RECEIVED)), "Invitation sender");
                alex.send(Protocol.INVITE, "Nina");
                alex.expect(Protocol.ERROR);
                nina.send(Protocol.INVITE_REJECT, "Alex");
                check("Nina".equals(alex.expect(Protocol.INVITE_REJECT)), "Reject identifies Nina");
                check(manager.getGameRooms().isEmpty(), "Reject creates no room");
                nina.send(Protocol.INVITE_ACCEPT, "Alex");
                nina.expect(Protocol.ERROR);
                System.out.println("PASS: login, invitation validation, duplicate invite, reject, unsolicited accept");

                alex.send(Protocol.INVITE, "Nina");
                nina.expect(Protocol.INVITE_RECEIVED);
                alex.send(Protocol.INVITE, "Erin");
                erin.expect(Protocol.INVITE_RECEIVED);
                CountDownLatch go = new CountDownLatch(1);
                Thread acceptNina = acceptTogether(nina, go, failure);
                Thread acceptErin = acceptTogether(erin, go, failure);
                go.countDown();
                acceptNina.join(3000);
                acceptErin.join(3000);
                check(!acceptNina.isAlive() && !acceptErin.isAlive(), "Accept sends complete");
                String start = alex.expect(Protocol.GAME_START);
                check(manager.getGameRooms().size() == 1, "Concurrent accept creates exactly one room");
                GameRoom room = manager.getGameRooms().get(0);
                check(room.isStarted() && !room.getId().isEmpty(), "Room has id and started state");
                check("Alex".equals(room.getPlayer1().getUsername()), "Player1 is inviter");
                boolean ninaWon = "Nina".equals(room.getPlayer2().getUsername());
                Client winner = ninaWon ? nina : erin;
                Client observer = ninaWon ? erin : nina;
                check(start.equals(winner.expect(Protocol.GAME_START)), "Both players receive same GAME_START");
                String alexState = alex.expect(Protocol.GAME_STATE);
                String winnerState = winner.expect(Protocol.GAME_STATE);
                check(alexState.equals(winnerState), "Both TCP clients receive identical GAME_STATE payloads");
                synchronized (manager) {
                    model.GameState current = GameStateCodec.decode(GameStateCodec.encode(room.getGameState()));
                    current.setRemainingTime(GameStateCodec.decode(alexState).getRemainingTime());
                    check(alexState.equals(GameStateCodec.encode(current)), "Payload matches authoritative room state");
                }
                MapGenerationTest.validate(GameStateCodec.decode(alexState));
                System.out.println("PASS: identical roomId, map, spawns, treasures, scores, counts and time over TCP");
                observer.expect(Protocol.ERROR);
                String list = observer.lastOnlineList;
                check(list.contains("Alex,0,0,PLAYING")
                        && list.contains(room.getPlayer2().getUsername() + ",0,0,PLAYING"),
                        "Lobby observer receives PLAYING broadcast");
                winner.send(Protocol.INVITE_ACCEPT, "Alex");
                winner.expect(Protocol.ERROR);
                observer.send(Protocol.INVITE, "Alex");
                observer.expect(Protocol.ERROR);
                alex.send(Protocol.INVITE, ninaWon ? "Erin" : "Nina");
                alex.expect(Protocol.ERROR);
                check(manager.getGameRooms().size() == 1, "Repeat accept creates no extra room");
                System.out.println("PASS: concurrent accept, one started room, GAME_START for both, PLAYING broadcast");

                Client departing = new Client(server.getLocalPort());
                clients.add(departing);
                departing.login("Leaving");
                departing.send(Protocol.INVITE, observer == nina ? "Nina" : "Erin");
                check("Leaving".equals(observer.expect(Protocol.INVITE_RECEIVED)), "Disconnect invitation");
                departing.close();
                String updated;
                do {
                    updated = observer.expect(Protocol.ONLINE_LIST);
                } while (updated.contains("Leaving,"));
                observer.send(Protocol.INVITE_ACCEPT, "Leaving");
                observer.expect(Protocol.ERROR);
                observer.send(Protocol.GET_ONLINE_LIST, "");
                check(!observer.expect(Protocol.ONLINE_LIST).contains("Leaving,"), "Disconnected player removed");
                check(manager.getGameRooms().size() == 1, "Disconnected invitation creates no room");
                System.out.println("PASS: disconnect before accept, online list cleanup, server still responds");
            } finally {
                manager.close();
                for (Client client : clients) {
                    client.close();
                }
                server.close();
                acceptor.join(3000);
                for (Thread handler : handlers) {
                    handler.join(3000);
                    check(!handler.isAlive(), "ClientHandler shuts down without deadlock");
                }
            }
        }
        check(failure.get() == null, "Server thread failure: " + failure.get());
        System.out.println("ALL TESTS PASSED");
    }

    private static Thread acceptTogether(Client client, CountDownLatch go,
            AtomicReference<Throwable> failure) {
        Thread thread = new Thread(() -> {
            try {
                go.await();
                client.send(Protocol.INVITE_ACCEPT, "Alex");
            } catch (Exception e) {
                failure.set(e);
            }
        });
        thread.start();
        return thread;
    }
}
