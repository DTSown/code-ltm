import client.ClientConnection;
import common.Message;
import common.Protocol;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import server.ServerDataStore;
import server.ServerManager;

public class ClientConnectionTest {
    static void check(boolean value, String reason) { if (!value) { throw new AssertionError(reason); } }
    public static void main(String[] args) throws Exception {
        ClientConnection connection = new ClientConnection();
        AtomicInteger disconnected = new AtomicInteger(); CountDownLatch lost = new CountDownLatch(1);
        connection.setDisconnectListener(reason -> {
            check(SwingUtilities.isEventDispatchThread(), "Disconnect delivered on EDT");
            check(reason.contains("\u0111\u0103ng nh\u1eadp l\u1ea1i"), "Readable Vietnamese disconnect notice");
            disconnected.incrementAndGet(); lost.countDown();
        });
        connection.connect("", 0); check(!connection.isConnected() && !connection.getConnectionError().isEmpty(), "Invalid endpoint fails without crash");
        int absentPort;
        try (ServerSocket absent = new ServerSocket(0)) { absentPort = absent.getLocalPort(); }
        long started = System.nanoTime(); connection.connect("127.0.0.1", absentPort);
        check(!connection.isConnected() && !connection.getConnectionError().isEmpty()
                && TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 7000, "Wrong port/server absent has bounded failure");
        connection.connect("invalid-treasure-host.invalid", 5000);
        check(!connection.isConnected() && connection.getConnectionError().contains("Server IP"), "Wrong hostname fails readably");
        ArrayBlockingQueue<Message> messages = new ArrayBlockingQueue<>(4); ArrayBlockingQueue<String> sent = new ArrayBlockingQueue<>(4);
        connection.setMessageListener(message -> { check(SwingUtilities.isEventDispatchThread(), "Messages delivered on EDT"); messages.add(message); });
        CountDownLatch closeFirst = new CountDownLatch(1); AtomicReference<Throwable> failure = new AtomicReference<>();
        try (ServerSocket server = new ServerSocket(0)) {
            Thread handler = new Thread(() -> {
                try {
                    try (Socket first = server.accept()) {
                        first.getOutputStream().write("WELCOME|K\u1ebft n\u1ed1i th\u00e0nh c\u00f4ng\n".getBytes(StandardCharsets.UTF_8));
                        first.getOutputStream().flush();
                        sent.add(new BufferedReader(new InputStreamReader(first.getInputStream(), StandardCharsets.UTF_8)).readLine());
                        check(closeFirst.await(5, TimeUnit.SECONDS), "EOF trigger arrived");
                    }
                    try (Socket second = server.accept()) {
                        second.getOutputStream().write("WELCOME|session2\n".getBytes(StandardCharsets.UTF_8)); second.getOutputStream().flush();
                        while (second.getInputStream().read() != -1) { }
                    }
                } catch (Throwable e) { failure.set(e); }
            }, "ConnectionTestServer"); handler.setDaemon(true); handler.start();
            connection.connect("127.0.0.1", server.getLocalPort()); check(connection.isConnected(), "Connection established");
            Message welcome = messages.poll(5, TimeUnit.SECONDS);
            check(welcome != null && welcome.getData().equals("K\u1ebft n\u1ed1i th\u00e0nh c\u00f4ng"), "UTF-8 receive");
            try { connection.sendMessage("MOVE|UP\nSCORE|999"); throw new AssertionError("Multiline injection accepted"); }
            catch (IllegalArgumentException expected) { }
            connection.sendMessage(new Message(Protocol.LOGIN, "Nguy\u1ec5nAn;m\u1eadtKh\u1ea9u"));
            check("LOGIN|Nguy\u1ec5nAn;m\u1eadtKh\u1ea9u".equals(sent.poll(5, TimeUnit.SECONDS)), "UTF-8 send, no injected line");
            closeFirst.countDown(); check(lost.await(5, TimeUnit.SECONDS), "EOF detected");
            check(!connection.isConnected() && disconnected.get() == 1, "Unexpected disconnect exactly once");
            connection.connect("127.0.0.1", server.getLocalPort());
            Message next = messages.poll(5, TimeUnit.SECONDS);
            check(next != null && next.getData().equals("session2") && connection.isConnected(), "Reconnect session not closed by stale receiver");
            connection.disconnect(); handler.join(5000); SwingUtilities.invokeAndWait(() -> { });
            check(!handler.isAlive() && failure.get() == null && disconnected.get() == 1, "Intentional disconnect quiet and threads stop");
        } finally { connection.disconnect(); }
        java.nio.file.Path directory = Files.createTempDirectory(java.nio.file.Path.of("out"), "data-init-test-");
        java.nio.file.Path file = directory.resolve("data/server-data.tsv"); ServerDataStore store = new ServerDataStore(file); store.initialize();
        check(Files.exists(file) && Files.readString(file, StandardCharsets.UTF_8).startsWith("TREASURE_SERVER\t1"), "Data auto-created");
        String snapshot = Files.readString(file); store.initialize(); check(snapshot.equals(Files.readString(file)), "Initialization never overwrites existing data");
        try (ServerManager manager = new ServerManager(180)) {
            check(manager.registerAccount("A,B", "password") != null && manager.registerAccount("A|B", "password") != null
                    && manager.registerAccount("Valid", "password\nSCORE|99") != null && manager.getRanking().isEmpty(), "Unsafe credentials rejected server-side");
        }
        System.out.println("PASS: invalid IP/port, absent server, UTF-8, socket EOF exactly once, EDT, intentional close, reconnect isolation, data initialization and delimiter validation");
        System.out.println("ALL CLIENT CONNECTION TESTS PASSED");
    }
}
