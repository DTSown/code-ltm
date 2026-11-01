import common.GameResultCodec;
import common.GameStateCodec;
import client.ClientConnection;
import client.MainFrame;
import java.awt.Window;
import java.lang.reflect.Field;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.StringReader;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import model.GameResult;
import model.GameState;
import model.Player;
import model.Position;
import model.Treasure;

public class JarGuiIntegrationTest {
    private static final String PASSWORD = "JarSmokeSecret15";
    private static final List<Process> owned = new ArrayList<>();
    private static Path root, directory, agent, serverJar;
    private static String host, java;
    private static int serverPort = 5000;
    static void check(boolean value, String reason) { if (!value) { throw new AssertionError(reason); } }

    static final class Client implements AutoCloseable {
        final Process process; final Socket socket; final DataInputStream input; final DataOutputStream output;
        Client(Path folder, String name, ServerSocket broker) throws Exception {
            Files.createDirectories(folder);
            Path jar = folder.resolve("TreasureRushClient.jar");
            Files.copy(root.resolve("dist/TreasureRushClient.jar"), jar, StandardCopyOption.REPLACE_EXISTING);
            process = new ProcessBuilder(java, "-javaagent:" + agent + "=" + broker.getLocalPort(), "-jar", jar.toString())
                    .directory(folder.toFile()).redirectErrorStream(true).redirectOutput(directory.resolve(name + ".log").toFile()).start();
            owned.add(process); socket = broker.accept(); socket.setSoTimeout(10000);
            input = new DataInputStream(socket.getInputStream()); output = new DataOutputStream(socket.getOutputStream());
            check(input.readUTF().equals("TreasureGuiAgent"), "Agent handshake");
            await(p -> "true".equals(p.getProperty("ready")), "Standalone JAR opens Login");
        }
        Properties command(String... values) throws Exception {
            output.writeUTF(String.join("\t", values)); output.flush();
            Properties result = new Properties(); result.load(new StringReader(input.readUTF()));
            check(!result.containsKey("error"), "GUI command failed: " + result.getProperty("error")); return result;
        }
        Properties state() throws Exception { return command("SNAPSHOT"); }
        Properties await(Predicate<Properties> predicate, String description) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12); Properties state = new Properties();
            while (System.nanoTime() < deadline) {
                check(process.isAlive(), "Client process exited during " + description);
                state = state(); if (predicate.test(state)) { return state; } Thread.sleep(80);
            }
            throw new AssertionError(description + " timed out; screen=" + state.getProperty("screen") + ", user=" + state.getProperty("user")
                    + ", feedback=" + state.getProperty("auth-dialog-message"));
        }
        void set(String name, String value) throws Exception { command("SET", name, value); }
        void click(String name) throws Exception { command("CLICK", name); }
        void clickClose() throws Exception {
            try { command("CLOSE"); } catch (java.io.EOFException closing) { }
            check(process.waitFor(5, TimeUnit.SECONDS), "Client window closes process");
        }
        void screenshot(String name) throws Exception { command("SCREENSHOT", directory.resolve(name + ".png").toString()); }
        void credentials(String name, String ip, int port) throws Exception {
            set("auth-username", name); set("auth-password", PASSWORD); set("auth-host", ip); set("auth-port", Integer.toString(port));
        }
        void register(String name) throws Exception {
            credentials(name, host, serverPort); click("auth-secondary"); set("auth-confirm", PASSWORD); click("auth-submit");
            await(p -> p.getProperty("auth-dialog-message", "").contains("\u0110\u0103ng k\u00fd th\u00e0nh c\u00f4ng"), "Register " + name); click("auth-dialog-close");
        }
        void login(String name) throws Exception {
            credentials(name, host, serverPort); click("auth-submit");
            await(p -> "lobbyPanel".equals(p.getProperty("screen")) && name.equals(p.getProperty("user")) && "FREE".equals(p.getProperty("status")), "Login " + name);
        }
        @Override public void close() throws Exception { socket.close(); stop(process); }
    }

    static void stop(Process process) throws Exception {
        if (process != null && process.isAlive()) {
            process.destroy(); if (!process.waitFor(5, TimeUnit.SECONDS)) { process.destroyForcibly(); check(process.waitFor(5, TimeUnit.SECONDS), "Owned process stopped"); }
        }
    }

    static Process server(String name) throws Exception {
        Path folder = directory.resolve("server"); Files.createDirectories(folder);
        Process server = new ProcessBuilder(java, "-Dtreasure.server.port=" + serverPort, "-jar", serverJar.toString()).directory(folder.toFile())
                .redirectErrorStream(true).redirectOutput(directory.resolve(name + ".log").toFile()).start(); owned.add(server);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            check(server.isAlive(), "Server JAR failed to start; see " + directory.resolve(name + ".log"));
            try (Socket probe = new Socket()) {
                probe.connect(new InetSocketAddress(host, serverPort), 300);
                check(Files.exists(folder.resolve("data/server-data.tsv")), "Server creates data relative to working directory"); return server;
            } catch (java.io.IOException unavailable) { Thread.sleep(100); }
        }
        throw new AssertionError("Server did not bind port " + serverPort + " on " + host);
    }

    static void inspectJar(Path file, String main, String ownPackage, String excluded) throws Exception {
        try (JarFile jar = new JarFile(file.toFile())) {
            check(main.equals(jar.getManifest().getMainAttributes().getValue(Attributes.Name.MAIN_CLASS)), "Main-Class for " + file.getFileName());
            check(jar.getJarEntry(main.replace('.', '/') + ".class") != null, "Entry point included");
            for (JarEntry entry : jar.stream().toList()) {
                String name = entry.getName();
                check(!name.startsWith(excluded + "/") && !name.contains(".tsv") && !name.startsWith("tests/"), "No server/client cross-package or account data in JAR");
                check(name.startsWith("META-INF/") || name.startsWith(ownPackage + "/") || name.startsWith("common/") || name.startsWith("model/")
                        || ownPackage.equals("client") && name.matches("audio/[a-z_-]+\\.wav"), "Only runtime packages and client audio included");
                if (name.endsWith(".class")) {
                    try (DataInputStream bytes = new DataInputStream(jar.getInputStream(entry))) {
                        check(bytes.readInt() == 0xCAFEBABE, "Valid class"); bytes.readUnsignedShort(); check(bytes.readUnsignedShort() == 61, "Java 17 bytecode");
                    }
                }
            }
        }
    }

    static void agentJar() throws Exception {
        agent = directory.resolve("gui-test-agent.jar"); Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue("Premain-Class", "JarGuiAgent");
        Path classes = Path.of(JarGuiIntegrationTest.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(agent), manifest)) {
            out.putNextEntry(new JarEntry("JarGuiAgent.class")); Files.copy(classes.resolve("JarGuiAgent.class"), out); out.closeEntry();
        }
    }

    static GameState synchronizedState(Client a, Client b) throws Exception {
        a.await(p -> "gamePanel".equals(p.getProperty("screen")) && p.containsKey("state"), "GamePanel state A");
        b.await(p -> "gamePanel".equals(p.getProperty("screen")) && p.containsKey("state"), "GamePanel state B");
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12);
        while (System.nanoTime() < deadline) {
            Properties left = a.state(), right = b.state();
            if (left.getProperty("state").equals(right.getProperty("state"))) {
                return GameStateCodec.decode(left.getProperty("state"));
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Both JAR clients receive identical state");
    }

    static void invite(Client a, Client b) throws Exception {
        a.click("opponent-Nina"); a.click("lobby-invite");
        b.await(p -> p.getProperty("invitation-message", "").contains("Alex"), "Invite popup");
    }

    static List<String> treasurePath(GameState state) {
        return treasurePath(state, state.getTreasures().stream().map(Treasure::getId).collect(Collectors.toSet()));
    }

    static List<String> treasurePath(GameState state, java.util.Set<String> targetIds) {
        Position start = state.getPlayer1().getPosition(), other = state.getPlayer2().getPosition();
        int[] previous = new int[49]; Arrays.fill(previous, -1); String[] direction = new String[49];
        int first = start.getRow() * 7 + start.getCol(); previous[first] = first; ArrayDeque<Integer> queue = new ArrayDeque<>(); queue.add(first);
        int[] dr = {-1, 1, 0, 0}, dc = {0, 0, -1, 1}; String[] names = {"UP", "DOWN", "LEFT", "RIGHT"};
        while (!queue.isEmpty()) {
            int cell = queue.remove();
            int target = cell;
            if (state.getTreasures().stream().anyMatch(t -> targetIds.contains(t.getId()) && t.getPosition().getRow() * 7 + t.getPosition().getCol() == target)) {
                List<String> path = new ArrayList<>();
                while (cell != first) { path.add(0, direction[cell]); cell = previous[cell]; } return path;
            }
            for (int i = 0; i < 4; i++) {
                int row = cell / 7 + dr[i], col = cell % 7 + dc[i];
                if (row < 0 || row >= 7 || col < 0 || col >= 7 || state.getMap()[row][col] == 1
                        || row == other.getRow() && col == other.getCol() || previous[row * 7 + col] != -1) { continue; }
                previous[row * 7 + col] = cell; direction[row * 7 + col] = names[i]; queue.add(row * 7 + col);
            }
        }
        throw new AssertionError("No path to treasure");
    }

    static void run() throws Exception {
        disposedDisconnect();
        inspectJar(root.resolve("dist/TreasureRushClient.jar"), "client.GameClient", "client", "server");
        inspectJar(serverJar, "server.GameServer", "server", "client"); agentJar();
        try (ServerSocket available = new ServerSocket(serverPort)) { check(available.getInetAddress().isAnyLocalAddress(), "Wildcard bind available"); }
        try (ServerSocket broker = new ServerSocket(0)) {
            broker.setSoTimeout(15000);
            try (Client a = new Client(directory.resolve("client-A"), "client-A", broker);
                    Client b = new Client(root.resolve("client-test"), "client-B", broker)) {
                check(a.state().getProperty("title").equals("Treasure Rush - \u0110ua S\u0103n Kho B\u00e1u Online"), "Vietnamese title in real JAR");
                a.command("RESIZE", "700", "450"); a.screenshot("login-A");
                a.credentials("Alex", host, serverPort); a.click("auth-submit");
                a.await(p -> p.containsKey("auth-dialog-message") && "false".equals(p.getProperty("busy")), "Server absent error"); a.click("auth-dialog-close");
                a.credentials("Alex", "bad-treasure-host.invalid", serverPort); a.click("auth-submit");
                a.await(p -> p.getProperty("auth-dialog-message", "").contains("Server IP"), "Invalid IP error"); a.click("auth-dialog-close");
                int wrongPort; try (ServerSocket wrong = new ServerSocket(0)) { wrongPort = wrong.getLocalPort(); }
                a.credentials("Alex", host, wrongPort); a.click("auth-submit");
                a.await(p -> p.containsKey("auth-dialog-message") && "false".equals(p.getProperty("connected")), "Wrong port error"); a.click("auth-dialog-close");
                Process server = server("server-first");
                a.register("Alex"); b.register("Nina"); a.login("Alex"); b.login("Nina");
                for (Client client : new Client[] {a, b}) {
                    client.await(p -> "LOBBY".equals(p.getProperty("music")) && "true".equals(p.getProperty("sound-shared")), "Shared manager selects lobby BGM");
                    client.command("RESIZE", "700", "450");
                }
                a.click("lobby-sound-toggle"); a.await(p -> "false".equals(p.getProperty("sound-enabled")), "Lobby mute");
                check("true".equals(b.state().getProperty("sound-enabled")), "Lobby mute is client-local");
                a.await(p -> "true".equals(p.getProperty("opponent-Nina")), "A sees Nina FREE");
                b.await(p -> "true".equals(p.getProperty("opponent-Alex")), "B sees Alex FREE"); a.screenshot("lobby-A"); b.screenshot("lobby-B");
                invite(a, b); b.click("invitation-reject");
                a.await(p -> p.getProperty("auth-dialog-message", "").contains("t\u1eeb ch\u1ed1i"), "Invite rejected"); a.click("auth-dialog-close");
                invite(a, b); b.click("invitation-accept"); GameState initial = synchronizedState(a, b);
                verifyInitialState(a, b, initial);
                a.command("RESIZE", "700", "450"); b.command("RESIZE", "700", "450");
                for (Client client : new Client[] {a, b}) { client.await(p -> "GAME".equals(p.getProperty("music")), "Lobby to game BGM"); }
                check("false".equals(a.state().getProperty("sound-enabled")), "Mute survives lobby to game transition");
                a.click("sound-toggle"); a.await(p -> "true".equals(p.getProperty("sound-enabled")), "Unmute in current game scene");
                a.click("sound-toggle"); a.await(p -> "false".equals(p.getProperty("sound-enabled")), "Local mute");
                check("true".equals(b.state().getProperty("sound-enabled")), "Mute does not affect opponent");
                a.click("sound-toggle"); a.await(p -> "true".equals(p.getProperty("sound-enabled")), "Local unmute");
                collectInitialChests(a, b, initial);
                GameState collected = synchronizedState(a, b);
                check(collected.getPlayer1().getCollectedTreasure() >= 4 && collected.getPlayer1().getMatchScore() >= 0 && collected.getTreasures().size() == 5, "Four original rewards plus trap, valid score/collection/respawn");
                check(collected.getTreasures().stream().anyMatch(t -> initial.getTreasures().stream().noneMatch(old -> old.getId().equals(t.getId()))), "New server treasure appears");
                b.click("MOVE_" + validDirection(collected, collected.getPlayer2(), collected.getPlayer1()));
                a.await(p -> GameStateCodec.decode(p.getProperty("state")).getPlayer2().getPosition().getRow() != collected.getPlayer2().getPosition().getRow()
                        || GameStateCodec.decode(p.getProperty("state")).getPlayer2().getPosition().getCol() != collected.getPlayer2().getPosition().getCol(), "B movement seen by A");
                a.await(p -> GameStateCodec.decode(p.getProperty("state")).getRemainingTime() <= 178, "Real JAR server timer ticks");
                GameState ticking = synchronizedState(a, b);
                check(a.state().getProperty("server-time").equals(client.GamePanel.formatTime(ticking.getRemainingTime())), "Server-driven MM:SS"); a.screenshot("game-A"); b.screenshot("game-B");
                a.click("exit-game"); a.command("CONFIRM");
                Properties result = a.await(p -> "resultPanel".equals(p.getProperty("screen")), "Exit result A");
                b.await(p -> result.getProperty("result").equals(p.getProperty("result")) && "resultPanel".equals(p.getProperty("screen")), "Identical exit result B");
                for (Client client : new Client[] {a, b}) { client.await(p -> "LOBBY".equals(p.getProperty("music")), "Game end restores lobby BGM"); }
                GameResult ended = GameResultCodec.decode(result.getProperty("result"));
                check(ended.getReason().equals(GameResult.PLAYER_EXIT) && ended.getPlayer2RankGain() == 3 && ended.getPlayer1RankGain() == 0 && "false".equals(result.getProperty("MOVE_UP")), "Exit forfeit/rank/input stop");
                a.screenshot("result-A"); b.screenshot("result-B");
                a.await(p -> "FREE".equals(p.getProperty("status")), "Result FREE"); b.await(p -> "FREE".equals(p.getProperty("status")), "Opponent FREE");
                a.click("play-again"); b.await(p -> p.getProperty("invitation-message", "").contains("t\u00e1i \u0111\u1ea5u"), "Rematch popup"); b.screenshot("rematch-B");
                b.click("invitation-accept"); GameState rematch = synchronizedState(a, b);
                verifyInitialState(a, b, rematch);
                for (Client client : new Client[] {a, b}) { check("GAME".equals(client.state().getProperty("music")), "Rematch selects game BGM"); }
                check(!rematch.getRoomId().equals(initial.getRoomId()) && !Arrays.deepEquals(rematch.getMap(), initial.getMap())
                        && rematch.getPlayer1().getMatchScore() == 0 && rematch.getPlayer2().getMatchScore() == 0, "Fresh rematch room/map/score/timer");
                b.clickClose();
                Properties disconnected = a.await(p -> "resultPanel".equals(p.getProperty("screen")) && p.getProperty("result", "").contains("DISCONNECT"), "Disconnect forfeit");
                check(GameResultCodec.decode(disconnected.getProperty("result")).getPlayer1RankGain() == 3, "Survivor rank +3");
                try (Client b2 = new Client(root.resolve("client-test"), "client-B2", broker)) {
                    b2.login("Nina"); a.click("return-lobby");
                    a.await(p -> "true".equals(p.getProperty("opponent-Nina")), "Lobby returns and B reconnects with new login");
                    rankingAndHistory(a, b2, "2");
                    b2.click("lobby-logout"); b2.await(p -> "loginPanel".equals(p.getProperty("screen")) && !p.containsKey("auth-dialog-message"), "Intentional logout quiet");
                    check("null".equals(b2.state().getProperty("music")), "Logout stops BGM");
                    a.await(p -> p.getProperty("lobby-online-count", "").endsWith("1"), "Online list removes logout"); b2.login("Nina");
                    a.await(p -> "true".equals(p.getProperty("opponent-Nina")), "Re-login restores opponent"); invite(a, b2); b2.click("invitation-accept"); synchronizedState(a, b2);
                    stop(server);
                    for (Client client : new Client[] {a, b2}) {
                        client.await(p -> "loginPanel".equals(p.getProperty("screen")) && "false".equals(p.getProperty("connected"))
                                && "false".equals(p.getProperty("MOVE_UP")) && p.getProperty("auth-dialog-message", "").contains("M\u1ea5t k\u1ebft n\u1ed1i"), "Server killed: readable error, disabled movement, Login");
                        check("null".equals(client.state().getProperty("music")), "Disconnect stops BGM");
                        client.click("auth-dialog-close");
                    }
                    server = server("server-restarted"); a.login("Alex"); b2.login("Nina"); rankingAndHistory(a, b2, "2"); stop(server);
                }
            }
        }
        try (java.util.stream.Stream<Path> files = Files.list(root.resolve("client-test"))) {
            check(files.map(p -> p.getFileName().toString()).toList().equals(List.of("TreasureRushClient.jar")), "Standalone client-test contains only Client.jar");
        }
        Process plain = new ProcessBuilder(java, "-jar", "TreasureRushClient.jar")
                .directory(root.resolve("client-test").toFile()).redirectErrorStream(true)
                .redirectOutput(directory.resolve("client-plain.log").toFile()).start();
        owned.add(plain); Thread.sleep(2000);
        check(plain.isAlive(), "Client.jar starts without test agent or sources"); stop(plain);
        check(!Files.readString(directory.resolve("client-plain.log")).contains("Exception"), "Plain standalone launch has no exceptions");
        String data = Files.readString(directory.resolve("server/data/server-data.tsv"), StandardCharsets.UTF_8);
        check(!data.contains(PASSWORD) && !Files.readString(directory.resolve("server-first.log")).contains(PASSWORD), "Passwords neither plaintext persisted nor logged");
        System.out.println("PASS: real java -jar Server and two standalone Client JAR processes via " + host + ":" + serverPort);
        System.out.println("PASS: network errors, register/login, online, reject/accept, equal map/MOVE/treasure/score/time, Exit, Result, rematch, disconnect, ranking/history, logout, abrupt server loss and restart persistence");
        System.out.println("PASS: Java 17 manifests/packages, client-only folder, no server sources/account data in Client.jar; screenshots/logs: " + directory);
    }

    static void rankingAndHistory(Client a, Client b, String matches) throws Exception {
        for (Client c : new Client[] {a, b}) {
            c.await(p -> "lobbyPanel".equals(p.getProperty("screen")), "Lobby for statistics"); c.click("lobby-ranking");
            c.await(p -> "rankingPanel".equals(p.getProperty("screen")) && p.getProperty("ranking-table", "").contains("Alex\t3"), "Updated rank");
        }
        check(a.state().getProperty("ranking-table").equals(b.state().getProperty("ranking-table")), "Both JAR clients identical ranking");
        for (Client c : new Client[] {a, b}) {
            c.screenshot(c == a ? "ranking-A" : "ranking-B"); c.click("ranking-lobby");
            c.await(p -> matches.equals(p.getProperty("profile-matches")), "Lifetime matches persisted"); c.click("lobby-history");
            c.await(p -> "historyPanel".equals(p.getProperty("screen")) && matches.equals(p.getProperty("history-table-rows")), "Exactly one history per completed room");
            c.screenshot(c == a ? "history-A" : "history-B"); c.click("history-lobby");
        }
    }

    static void verifyInitialState(Client a, Client b, GameState current) throws Exception {
        String first = a.state().getProperty("initial-state");
        check(first != null && first.equals(b.state().getProperty("initial-state")), "Both clients receive identical first wire state");
        GameState initial = GameStateCodec.decode(first);
        check(initial.getMap().length == 7 && initial.getTreasures().size() == 5 && initial.getRemainingTime() == 180,
                "First server state has map, five chests and exactly 180 seconds");
        check(initial.getRoomId().equals(current.getRoomId()) && current.getRemainingTime() > 0 && current.getRemainingTime() <= 180,
                "Live GUI time advances independently after the exact initial state");
    }

    @SuppressWarnings("unchecked")
    static void disposedDisconnect() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            ClientConnection connection = new ClientConnection(); MainFrame frame = new MainFrame(connection);
            try {
                Field callbackField = ClientConnection.class.getDeclaredField("disconnectListener"); callbackField.setAccessible(true);
                java.util.function.Consumer<String> callback = (java.util.function.Consumer<String>) callbackField.get(connection);
                frame.dispose();
                callback.accept("Queued EOF after dispose");
                Field active = MainFrame.class.getDeclaredField("connection"); active.setAccessible(true);
                check(active.get(frame) == connection, "Disposed frame ignores queued disconnect without creating a new session");
                for (Window window : Window.getWindows()) { check(!window.isVisible(), "Queued disconnect does not reopen UI"); }
            } catch (ReflectiveOperationException failure) { throw new RuntimeException(failure); }
            finally { frame.dispose(); }
        });
    }

    static void collectInitialChests(Client a, Client b, GameState initial) throws Exception {
        java.util.Set<String> originals = initial.getTreasures().stream().map(Treasure::getId).collect(Collectors.toSet());
        boolean sawTrap = false;
        int moves = 0;
        while (true) {
            GameState current = synchronizedState(a, b);
            originals.retainAll(current.getTreasures().stream().map(Treasure::getId).toList());
            if (originals.isEmpty()) { break; }
            for (String direction : treasurePath(current, originals)) {
                check(++moves < 150, "Original chests reachable with bounded movement");
                a.await(p -> "true".equals(p.getProperty("MOVE_UP")), "Automatic stun recovery");
                GameState before = synchronizedState(a, b); Position old = before.getPlayer1().getPosition();
                a.command("KEY", direction.equals("UP") ? "W" : direction.equals("DOWN") ? "S" : direction.equals("LEFT") ? "A" : "D");
                a.await(p -> {
                    Position next = GameStateCodec.decode(p.getProperty("state")).getPlayer1().getPosition();
                    return next.getRow() != old.getRow() || next.getCol() != old.getCol();
                }, "Authoritative movement " + direction);
                GameState after = synchronizedState(a, b);
                Treasure opened = before.getTreasures().stream().filter(t -> t.getPosition().getRow() == after.getPlayer1().getPosition().getRow()
                        && t.getPosition().getCol() == after.getPlayer1().getPosition().getCol()).findFirst().orElse(null);
                if (opened == null) { continue; }
                TreasureCollectionTest.validateReplacement(before, after, opened.getId());
                int count = before.getPlayer1().getCollectedTreasure(), score = before.getPlayer1().getMatchScore();
                if (after.getPlayer1().getCollectedTreasure() == count + 1) {
                    check(after.getPlayer1().getMatchScore() == score + opened.getValue(), "Exact normal reward in real JAR");
                } else {
                    check(after.getPlayer1().getCollectedTreasure() == count && after.getPlayer1().getMatchScore() == Math.max(0, score - 10), "Exact trap loss and no reward count");
                    check("false".equals(a.state().getProperty("MOVE_UP")) && "true".equals(a.state().getProperty("exit-game")), "Stun locks controls but allows exit");
                    check(b.state().getProperty("treasure-notice", "").contains("Alex"), "Opponent receives trap notice");
                    if (!sawTrap) { a.screenshot("trap-A"); b.screenshot("trap-B"); }
                    sawTrap = true;
                    a.await(p -> "true".equals(p.getProperty("MOVE_UP")), "Stun automatically clears in real JAR");
                }
            }
        }
        check(sawTrap, "One original chest triggers a real trap on both JAR clients");
    }

    static String validDirection(GameState state, Player player, Player other) {
        String[] names = {"UP", "DOWN", "LEFT", "RIGHT"}; int[] dr = {-1, 1, 0, 0}, dc = {0, 0, -1, 1};
        for (int i = 0; i < 4; i++) {
            int row = player.getPosition().getRow() + dr[i], col = player.getPosition().getCol() + dc[i];
            if (row >= 0 && row < 7 && col >= 0 && col < 7 && state.getMap()[row][col] == 0
                    && (row != other.getPosition().getRow() || col != other.getPosition().getCol())) { return names[i]; }
        }
        throw new AssertionError("No valid B movement");
    }

    public static void main(String[] args) throws Exception {
        root = Path.of("").toAbsolutePath(); directory = Files.createTempDirectory(root.resolve("out"), "jar-test-");
        serverJar = root.resolve("dist/TreasureRushServer.jar"); host = args.length == 0 ? "127.0.0.1" : args[0];
        serverPort = args.length < 2 ? 5000 : Integer.parseInt(args[1]);
        check(serverPort > 0 && serverPort <= 65535, "Valid isolated GUI test port");
        java = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        try { run(); System.out.println("ALL REAL JAR GUI TESTS PASSED"); }
        finally { for (Process process : owned) { stop(process); } }
    }
}
