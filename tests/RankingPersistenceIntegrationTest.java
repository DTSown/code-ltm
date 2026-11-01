import common.GameResultCodec;
import common.HistoryCodec;
import common.Protocol;
import common.RankingCodec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import model.GameResult;
import model.MatchHistory;
import model.Player;
import server.Account;
import server.GameRoom;
import server.ServerDataStore;
import server.ServerManager;

public class RankingPersistenceIntegrationTest {
    static final String PASSWORD = "Secret-PTIT-42!";
    static void check(boolean ok, String message) { if (!ok) { throw new AssertionError(message); } }

    static GameLifecycleIntegrationTest.Client login(GameLifecycleIntegrationTest.Harness h, String name, boolean register) throws Exception {
        GameLifecycleIntegrationTest.Client c = new GameLifecycleIntegrationTest.Client(h.server.getLocalPort());
        try {
            if (register) { c.send(Protocol.REGISTER, name + ";" + PASSWORD); c.expect(Protocol.REGISTER_SUCCESS); }
            c.send(Protocol.LOGIN, name + ";" + PASSWORD); c.expect(Protocol.LOGIN_SUCCESS); return c;
        } catch (Exception | AssertionError e) { c.close(); throw e; }
    }
    static GameRoom start(GameLifecycleIntegrationTest.Harness h, GameLifecycleIntegrationTest.Client a, String nameA,
            GameLifecycleIntegrationTest.Client b, String nameB) throws Exception {
        a.send(Protocol.INVITE, nameB); check(nameA.equals(b.expect(Protocol.INVITE_RECEIVED)), "Invite");
        b.send(Protocol.INVITE_ACCEPT, nameA);
        String start = a.expect(Protocol.GAME_START); check(start.equals(b.expect(Protocol.GAME_START)), "Same start");
        String state = a.expect(Protocol.GAME_STATE); check(state.equals(b.expect(Protocol.GAME_STATE)), "Same initial state");
        for (GameRoom room : h.manager.getGameRooms()) {
            if (room.getPlayer1().getUsername().equals(nameA)) { return room; }
        }
        throw new AssertionError("No new room");
    }
    static void seed(GameLifecycleIntegrationTest.Harness h, GameRoom room, int score1, int score2, int count1, int count2) {
        synchronized (h.manager) { synchronized (room) {
            room.getPlayer1().setMatchScore(score1); room.getPlayer2().setMatchScore(score2);
            room.getPlayer1().setCollectedTreasure(count1); room.getPlayer2().setCollectedTreasure(count2);
        } }
    }
    static GameResult receive(GameLifecycleIntegrationTest.Client a, GameLifecycleIntegrationTest.Client b) throws Exception {
        String data = a.expect(Protocol.GAME_OVER); check(data.equals(b.expect(Protocol.GAME_OVER)), "Identical result");
        a.expect(Protocol.ONLINE_LIST); b.expect(Protocol.ONLINE_LIST); return GameResultCodec.decode(data);
    }
    static Player profile(ServerManager manager, String username) {
        return manager.getRanking().stream().filter(p -> username.equals(p.getUsername())).findFirst().orElseThrow();
    }
    static void stats(Player p, int rank, int wins, int matches, int treasures) {
        check(p.getTotalRankPoint() == rank && p.getWins() == wins && p.getTotalMatches() == matches
                && p.getTotalTreasureCollected() == treasures, "Wrong lifetime statistics for " + p.getUsername());
    }
    static void allFlows(Path file) throws Exception {
        String beforeRanking, beforeHistory;
        try (GameLifecycleIntegrationTest.Harness h = new GameLifecycleIntegrationTest.Harness(2, file);
                GameLifecycleIntegrationTest.Client a = login(h, "Alex", true);
                GameLifecycleIntegrationTest.Client b = login(h, "Nina", true);
                GameLifecycleIntegrationTest.Client c = login(h, "Cora", true);
                GameLifecycleIntegrationTest.Client d = login(h, "Dana", true)) {
            try (GameLifecycleIntegrationTest.Client unauth = new GameLifecycleIntegrationTest.Client(h.server.getLocalPort())) {
                unauth.send(Protocol.GET_RANKING, ""); unauth.expect(Protocol.ERROR);
                unauth.send(Protocol.GET_HISTORY, "Alex"); unauth.expect(Protocol.ERROR);
            }
            GameRoom win = start(h, a, "Alex", b, "Nina"); seed(h, win, 30, 10, 3, 1);
            check(GameResult.PLAYER1.equals(receive(a, b).getWinner()), "Normal score winner");
            stats(profile(h.manager, "Alex"), 3, 1, 1, 3); stats(profile(h.manager, "Nina"), 0, 0, 1, 1);
            GameRoom draw = start(h, a, "Alex", b, "Nina"); seed(h, draw, 30, 30, 2, 4);
            check(GameResult.DRAW.equals(receive(a, b).getWinner()), "Draw");
            stats(profile(h.manager, "Alex"), 4, 1, 2, 5); stats(profile(h.manager, "Nina"), 1, 0, 2, 5);
            GameRoom exit = start(h, a, "Alex", b, "Nina"); seed(h, exit, 90, 0, 5, 0);
            a.send(Protocol.EXIT_GAME, ""); check(GameResult.PLAYER_EXIT.equals(receive(a, b).getReason()), "Exit history reason");
            a.send(Protocol.EXIT_GAME, ""); a.expect(Protocol.ERROR);
            check(exit.finishGame() == null, "No second statistics update");
            stats(profile(h.manager, "Alex"), 4, 1, 3, 10); stats(profile(h.manager, "Nina"), 4, 1, 3, 5);
            GameRoom disconnect = start(h, a, "Alex", b, "Nina"); seed(h, disconnect, 10, 20, 1, 2);
            b.close();
            check(GameResult.DISCONNECT.equals(GameResultCodec.decode(a.expect(Protocol.GAME_OVER)).getReason()), "Disconnect history reason");
            a.expect(Protocol.ONLINE_LIST);
            stats(profile(h.manager, "Alex"), 7, 2, 4, 11); stats(profile(h.manager, "Nina"), 4, 1, 4, 7);
            try (GameLifecycleIntegrationTest.Client nina = login(h, "Nina", false)) {
                stats(h.manager.getOnlinePlayers().stream().filter(p -> "Nina".equals(p.getUsername())).findFirst().orElseThrow(), 4, 1, 4, 7);
                GameRoom room1 = start(h, a, "Alex", nina, "Nina");
                GameRoom room2 = start(h, c, "Cora", d, "Dana");
                CountDownLatch go = new CountDownLatch(1);
                AtomicReference<Throwable> failure = new AtomicReference<>();
                Thread t1 = new Thread(() -> { try { go.await(); a.send(Protocol.EXIT_GAME, ""); } catch (Throwable e) { failure.set(e); } });
                Thread t2 = new Thread(() -> { try { go.await(); c.send(Protocol.EXIT_GAME, ""); } catch (Throwable e) { failure.set(e); } });
                t1.start(); t2.start(); go.countDown(); t1.join(); t2.join();
                check(failure.get() == null, "Concurrent sends"); receive(a, nina); receive(c, d);
                check(h.manager.getGameRooms().isEmpty() && !room1.isTimerRunning() && !room2.isTimerRunning(), "Concurrent cleanup");
                stats(profile(h.manager, "Alex"), 7, 2, 5, 11); stats(profile(h.manager, "Nina"), 7, 2, 5, 7);
                stats(profile(h.manager, "Cora"), 0, 0, 1, 0); stats(profile(h.manager, "Dana"), 3, 1, 1, 0);
                a.send(Protocol.GET_RANKING, ""); c.send(Protocol.GET_RANKING, "");
                beforeRanking = a.expect(Protocol.RANKING); check(beforeRanking.equals(c.expect(Protocol.RANKING)), "Two Clients receive identical ranking");
                List<Player> rank = RankingCodec.decode(beforeRanking);
                check(Arrays.asList("Alex", "Nina", "Dana", "Cora").equals(rank.stream().map(Player::getUsername).toList()), "Treasure tie-break");
                a.send(Protocol.GET_HISTORY, "Cora"); beforeHistory = a.expect(Protocol.HISTORY);
                List<MatchHistory> history = HistoryCodec.decode(beforeHistory);
                check(history.size() == 5 && history.stream().allMatch(m -> m.containsPlayer("Alex")), "History derives identity from handler");
                c.send(Protocol.GET_HISTORY, "Alex"); check(HistoryCodec.decode(c.expect(Protocol.HISTORY)).size() == 1, "Private history");
                check(history.stream().anyMatch(m -> GameResult.TIME_UP.equals(m.getResult().getReason()))
                        && history.stream().anyMatch(m -> GameResult.PLAYER_EXIT.equals(m.getResult().getReason()))
                        && history.stream().anyMatch(m -> GameResult.DISCONNECT.equals(m.getResult().getReason())), "All end reasons stored");
                check(h.results.get(win.getId()) == 2 && h.results.get(draw.getId()) == 2
                        && h.results.get(exit.getId()) == 2 && h.results.get(disconnect.getId()) == 1, "Single GAME_OVER per participant");
                a.send(Protocol.RANKING, "Alex,9999,9999,9999,9999"); a.expect(Protocol.ERROR);
                a.send(Protocol.HISTORY, beforeHistory); a.expect(Protocol.ERROR);
            }
        }
        String text = Files.readString(file, StandardCharsets.UTF_8);
        check(!text.contains(PASSWORD) && text.lines().filter(line -> line.startsWith("MATCH\t")).count() == 6, "No plaintext and six unique persisted matches");
        try (GameLifecycleIntegrationTest.Harness restarted = new GameLifecycleIntegrationTest.Harness(2, file);
                GameLifecycleIntegrationTest.Client a = login(restarted, "Alex", false);
                GameLifecycleIntegrationTest.Client c = login(restarted, "Cora", false);
                GameLifecycleIntegrationTest.Client bad = new GameLifecycleIntegrationTest.Client(restarted.server.getLocalPort())) {
            bad.send(Protocol.LOGIN, "Nina;wrong"); bad.expect(Protocol.LOGIN_FAIL);
            bad.send(Protocol.LOGIN, "Nina;" + PASSWORD); bad.expect(Protocol.LOGIN_SUCCESS);
            bad.send(Protocol.REGISTER, "Alex;" + PASSWORD); bad.expect(Protocol.REGISTER_FAIL);
            a.send(Protocol.GET_RANKING, ""); c.send(Protocol.GET_RANKING, "");
            check(beforeRanking.equals(a.expect(Protocol.RANKING)) && beforeRanking.equals(c.expect(Protocol.RANKING)), "Ranking survives restart unchanged");
            a.send(Protocol.GET_HISTORY, ""); check(beforeHistory.equals(a.expect(Protocol.HISTORY)), "History survives restart unchanged");
            stats(profile(restarted.manager, "Nina"), 7, 2, 5, 7);
            check(restarted.manager.getGameRooms().isEmpty()
                    && restarted.manager.getOnlinePlayers().stream().allMatch(p -> p.getStatus() == Player.Status.FREE), "Restart does not restore stale rooms/status");
        }
        System.out.println("PASS: real TCP score/draw/exit/disconnect statistics, concurrent finishes, private history, identical ranking and restart");
    }

    static void sortingAndCodecs(Path dir) throws Exception {
        Map<String, Account> accounts = new LinkedHashMap<>();
        String[] names = {"Low", "Wins", "GoldA", "GoldB", "High", "Nguy\u1ec5n;,/|+"};
        int[][] values = {{10, 1, 99}, {10, 2, 1}, {10, 2, 8}, {10, 2, 8}, {11, 0, 0}, {0, 0, 0}};
        for (int i = 0; i < names.length; i++) {
            Account account = Account.register(names[i], PASSWORD); Player p = account.createPlayer();
            p.setTotalRankPoint(values[i][0]); p.setWins(values[i][1]); p.setTotalTreasureCollected(values[i][2]);
            p.setTotalMatches(10); accounts.put(names[i], account.withStatistics(p));
        }
        check(!accounts.get("Low").getSalt().equals(accounts.get("Wins").getSalt())
                && !accounts.get("Low").getPasswordHash().equals(accounts.get("Wins").getPasswordHash()), "Per-account salt");
        Path file = dir.resolve("sort.tsv"); new ServerDataStore(file).save(accounts, new LinkedHashMap<>());
        try (ServerManager manager = new ServerManager(180, file)) {
            List<String> ordered = manager.getRanking().stream().map(Player::getUsername).toList();
            check(ordered.equals(Arrays.asList("High", "GoldA", "GoldB", "Wins", "Low", names[5])), "All four sorting criteria and offline accounts");
            String wire = RankingCodec.encode(manager.getRanking());
            check(wire.equals(RankingCodec.encode(RankingCodec.decode(wire))), "Ranking UTF-8/separator roundtrip");
            Player snapshot = manager.getRanking().get(0); snapshot.setTotalRankPoint(99999);
            check(profile(manager, "High").getTotalRankPoint() == 11, "Ranking returns detached snapshots");
        }
        GameResult r = new GameResult("codec-room", names[5], "Low", GameResult.PLAYER1, 30, 10, 1, 1, 3, 0);
        String wire = HistoryCodec.encode(List.of(new MatchHistory(r, 123456789L, 180)));
        check(wire.equals(HistoryCodec.encode(HistoryCodec.decode(wire))), "History UTF-8/separator roundtrip");
        for (String invalid : Arrays.asList("A,1,2", "A,-1,0,0,0", "A,0,0,0,0;A,0,0,0,0")) {
            try { RankingCodec.decode(invalid); throw new AssertionError("Malformed ranking accepted"); }
            catch (IllegalArgumentException expected) { }
        }
        try { HistoryCodec.decode(wire + "/" + wire); throw new AssertionError("Duplicate history accepted"); }
        catch (IllegalArgumentException expected) { }
        Path corrupt = dir.resolve("corrupt.tsv"); Files.writeString(corrupt, "BROKEN\n", StandardCharsets.UTF_8);
        try { new ServerManager(180, corrupt); throw new AssertionError("Corrupt file silently reset"); }
        catch (IllegalStateException expected) { }
        check("BROKEN\n".equals(Files.readString(corrupt)), "Corrupt file left untouched");
        Path blocked = dir.resolve("not-a-directory"); Files.writeString(blocked, "existing");
        try (ServerManager manager = new ServerManager(180, blocked.resolve("data.tsv"))) {
            check(manager.registerAccount("CannotSave", PASSWORD) != null && manager.getRanking().isEmpty(), "Registration rollback on disk failure");
        }
        System.out.println("PASS: sorting, salted passwords, UTF-8 codecs, offline ranking, malformed data and failed-write rollback");
    }

    static void failedMatchWrite(Path dir) throws Exception {
        Path file = dir.resolve("retry.tsv"), backup = dir.resolve("retry-backup.tsv");
        try (GameLifecycleIntegrationTest.Harness h = new GameLifecycleIntegrationTest.Harness(180, file);
                GameLifecycleIntegrationTest.Client a = login(h, "Alex", true);
                GameLifecycleIntegrationTest.Client b = login(h, "Nina", true)) {
            GameRoom room = start(h, a, "Alex", b, "Nina"); seed(h, room, 10, 20, 1, 2);
            Files.move(file, backup); Files.createDirectory(file);
            Path blocker = file.resolve("blocker"); Files.writeString(blocker, "block replacement");
            a.send(Protocol.EXIT_GAME, "");
            String result = a.expect(Protocol.GAME_OVER); check(result.equals(b.expect(Protocol.GAME_OVER)), "Result still delivered on disk error");
            a.expect(Protocol.ERROR); b.expect(Protocol.ERROR);
            a.expect(Protocol.ONLINE_LIST); b.expect(Protocol.ONLINE_LIST);
            stats(profile(h.manager, "Alex"), 0, 0, 1, 1); stats(profile(h.manager, "Nina"), 3, 1, 1, 2);
            check(h.manager.getMatchHistory("Alex").size() == 1 && !room.isTimerRunning(), "Failed write preserves one in-memory history and cancels timer");
            a.send(Protocol.EXIT_GAME, ""); a.expect(Protocol.ERROR);
            Files.delete(blocker); Files.delete(file); Files.move(backup, file);
            h.manager.close();
        }
        try (ServerManager restored = new ServerManager(180, file)) {
            stats(profile(restored, "Alex"), 0, 0, 1, 1); stats(profile(restored, "Nina"), 3, 1, 1, 2);
            check(restored.getMatchHistory("Alex").size() == 1, "Close retries pending snapshot without duplicate statistics/history");
        }
        System.out.println("PASS: failed match write, client notification, memory retained and close retry without duplication");
    }
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory(Path.of("out"), "persistence-test-");
        allFlows(dir.resolve("server-data.tsv")); sortingAndCodecs(dir); failedMatchWrite(dir);
        System.out.println("ALL RANKING/PERSISTENCE TESTS PASSED");
    }
}
