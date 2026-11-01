import common.HistoryCodec;
import common.Protocol;
import common.RankingCodec;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import model.GameResult;
import model.MatchHistory;
import model.Player;
import server.Account;
import server.DatabaseConfig;
import server.GameRoom;
import server.MySqlDataStore;
import server.ServerDataStore;
import server.ServerManager;

/** Uses a unique disposable database; never writes to configured bt or to the source TSV. */
public class MySqlPersistenceIntegrationTest {
    private static void check(boolean value, String message) {
        if (!value) { throw new AssertionError(message); }
    }

    private static void migration(MySqlDataStore store) throws Exception {
        Map<String, Account> source = new LinkedHashMap<>(), restored = new LinkedHashMap<>();
        Map<String, MatchHistory> history = new LinkedHashMap<>(), reloaded = new LinkedHashMap<>();
        new ServerDataStore(Path.of("data", "server-data.tsv")).load(source, history);
        check(!source.isEmpty(), "Migration fixture must contain accounts");
        store.importEmpty(source, history);
        store.load(restored, reloaded);
        check(source.keySet().equals(restored.keySet()) && history.keySet().equals(reloaded.keySet()), "All TSV records imported");
        for (Account account : source.values()) {
            Account copy = restored.get(account.getUsername());
            check(account.getSalt().equals(copy.getSalt()) && account.getPasswordHash().equals(copy.getPasswordHash()), "Existing credentials preserved");
            check(RankingCodec.encode(java.util.List.of(account.createPlayer()))
                    .equals(RankingCodec.encode(java.util.List.of(copy.createPlayer()))), "Lifetime totals preserved");
        }
        for (MatchHistory match : history.values()) {
            check(HistoryCodec.encode(java.util.List.of(match)).equals(HistoryCodec.encode(java.util.List.of(reloaded.get(match.getRoomId())))),
                    "Match details preserved");
        }
        try { store.importEmpty(source, history); throw new AssertionError("Nonempty database overwritten"); }
        catch (IOException expected) { check(expected.getMessage().contains("empty"), "Refused repeat import"); }
        System.out.println("PASS: TSV migration, original credentials/totals/history, refusal to overwrite nonempty database");
    }

    private static void transactionAndNames(MySqlDataStore store) throws Exception {
        Map<String, Account> accounts = new LinkedHashMap<>();
        Map<String, MatchHistory> history = new LinkedHashMap<>();
        store.load(accounts, history);
        String upper = "DB_Case", lower = "db_case", unicode = "Nguy\u1ec5n \ud83d\ude00";
        for (String name : new String[] {upper, lower, unicode}) { accounts.put(name, Account.register(name, "password")); }
        store.save(accounts, history);
        Map<String, Account> loaded = new LinkedHashMap<>();
        store.load(loaded, new LinkedHashMap<>());
        check(loaded.containsKey(upper) && loaded.containsKey(lower) && loaded.containsKey(unicode), "UTF-8 and case-sensitive usernames");
        check(loaded.get(unicode).verifyPassword("password") && !loaded.get(unicode).verifyPassword("wrong"), "Password verification");
        Player changed = accounts.get(upper).createPlayer(); changed.setTotalRankPoint(99);
        accounts.put(upper, accounts.get(upper).withStatistics(changed));
        accounts.put("rollback_only", Account.register("rollback_only", "secret"));
        GameResult invalid = new GameResult("invalid-fk", upper, "missing_account", GameResult.PLAYER1, 10, 0, 1, 0, 3, 0);
        history.put(invalid.getRoomId(), new MatchHistory(invalid, 123, 1));
        try { store.saveRegistration(accounts, history, "rollback_only", "secret"); throw new AssertionError("Missing foreign key accepted"); }
        catch (IOException expected) { }
        loaded.clear(); Map<String, MatchHistory> after = new LinkedHashMap<>(); store.load(loaded, after);
        check(loaded.get(upper).createPlayer().getTotalRankPoint() == 0 && !loaded.containsKey("rollback_only")
                && !after.containsKey("invalid-fk"), "Failed match write rolls back account updates and inserts");
        System.out.println("PASS: real MySQL foreign-key rollback, case-sensitive and Unicode usernames, salted password verification");
    }

    private static String demoPassword(DatabaseConfig config, String username) throws Exception {
        try (Connection connection = config.connect(true);
                PreparedStatement sql = connection.prepareStatement("SELECT password_plaintext FROM accounts WHERE username=?")) {
            sql.setString(1, username);
            try (ResultSet rows = sql.executeQuery()) {
                check(rows.next(), "Expected account exists");
                return rows.getString(1);
            }
        }
    }

    private static void upgradeSchema(DatabaseConfig config, MySqlDataStore store) throws Exception {
        Map<String, Account> before = new LinkedHashMap<>();
        Map<String, MatchHistory> beforeHistory = new LinkedHashMap<>();
        store.load(before, beforeHistory);
        try (Connection connection = config.connect(true); Statement sql = connection.createStatement()) {
            sql.executeUpdate("ALTER TABLE accounts DROP COLUMN password_plaintext");
            sql.executeUpdate("DROP VIEW matches");
            sql.executeUpdate("RENAME TABLE match_details TO matches");
        }
        store.initialize(); store.initialize();
        Map<String, Account> after = new LinkedHashMap<>();
        Map<String, MatchHistory> afterHistory = new LinkedHashMap<>();
        store.load(after, afterHistory);
        check(before.keySet().equals(after.keySet()) && beforeHistory.keySet().equals(afterHistory.keySet()), "Schema upgrade preserves records");
        for (String username : before.keySet()) {
            check(before.get(username).getPasswordHash().equals(after.get(username).getPasswordHash())
                    && demoPassword(config, username) == null, "Legacy credentials preserved and plaintext initially NULL");
        }
        System.out.println("PASS: automatic legacy-table upgrade, full match data retained, repeat initialization and NULL legacy passwords");
    }

    private static void readableRooms(DatabaseConfig config) throws Exception {
        try (Connection connection = config.connect(true); Statement sql = connection.createStatement();
                ResultSet rows = sql.executeQuery("SELECT * FROM matches")) {
            check(rows.getMetaData().getColumnCount() == 5, "Room view has exactly five columns");
            String[] names = {"ten_phong", "nguoi_tham_gia", "diem_rank", "ruong_da_an", "nguoi_thang"};
            for (int i = 0; i < names.length; i++) {
                check(names[i].equals(rows.getMetaData().getColumnLabel(i + 1)), "Readable room column name");
            }
            boolean found = false;
            while (rows.next()) {
                found = true;
                check(rows.getString(1).startsWith("Ph\u00f2ng "), "Room name has no UUID");
                check(rows.getString(2).contains(" / "), "Both participant names visible");
                check(rows.getString(3).contains(": +") && rows.getString(4).contains(": "), "Rank/treasures identify each participant");
                check(!java.util.List.of("P1", "P2", "DRAW").contains(rows.getString(5)), "Winner uses actual username or Hoa");
            }
            check(found, "Room view contains migrated/new games");
        }
        System.out.println("PASS: exactly five readable room columns, named participants, rank, treasures and actual winner");
    }

    private static boolean hasLegacyCredentials(DatabaseConfig config) throws Exception {
        try (Connection connection = config.connect(true); ResultSet columns = connection.getMetaData()
                .getColumns(connection.getCatalog(), null, "accounts", "salt")) { return columns.next(); }
    }

    private static void completePasswordMigration(DatabaseConfig base) throws Exception {
        DatabaseConfig config = base.withDatabase("treasure_test_" + UUID.randomUUID().toString().replace("-", ""));
        MySqlDataStore store = new MySqlDataStore(config); store.createDatabase();
        try {
            check(!hasLegacyCredentials(config), "New databases have no salt/hash columns");
            Map<String, Account> accounts = new LinkedHashMap<>();
            accounts.put("legacy_A", Account.register("legacy_A", "old-pass-A"));
            accounts.put("legacy_B", Account.register("legacy_B", "old-pass-B"));
            store.importEmpty(accounts, new LinkedHashMap<>());
            check(hasLegacyCredentials(config), "Legacy TSV credentials retained while passwords unknown");
            try (GameLifecycleIntegrationTest.Harness h = new GameLifecycleIntegrationTest.Harness(ServerManager.withDataStore(180, store));
                    GameLifecycleIntegrationTest.Client a = new GameLifecycleIntegrationTest.Client(h.server.getLocalPort());
                    GameLifecycleIntegrationTest.Client b = new GameLifecycleIntegrationTest.Client(h.server.getLocalPort())) {
                a.send(Protocol.LOGIN, "legacy_A;old-pass-A"); a.expect(Protocol.LOGIN_SUCCESS);
                check(hasLegacyCredentials(config), "First login does not discard another account's unknown password");
                b.send(Protocol.LOGIN, "legacy_B;wrong"); b.expect(Protocol.LOGIN_FAIL);
                check(hasLegacyCredentials(config), "Wrong login cannot trigger credential removal");
                b.send(Protocol.LOGIN, "legacy_B;old-pass-B"); b.expect(Protocol.LOGIN_SUCCESS);
                check(!hasLegacyCredentials(config), "Final successful login automatically removes salt/hash columns");
            }
            store.initialize();
            check(!hasLegacyCredentials(config), "Restart does not recreate removed credential columns");
            try (GameLifecycleIntegrationTest.Harness h = new GameLifecycleIntegrationTest.Harness(ServerManager.withDataStore(180, store));
                    GameLifecycleIntegrationTest.Client a = new GameLifecycleIntegrationTest.Client(h.server.getLocalPort())) {
                a.send(Protocol.LOGIN, "legacy_A;wrong"); a.expect(Protocol.LOGIN_FAIL);
                a.send(Protocol.LOGIN, "legacy_A;old-pass-A"); a.expect(Protocol.LOGIN_SUCCESS);
            }
            tcpAndRestart(config, store);
            readableRooms(config);
            check(!hasLegacyCredentials(config), "Registration and match saves do not recreate salt/hash columns");
            System.out.println("PASS: complete password migration, automatic column removal and login/matches after restart without hash columns");
        } finally {
            try (Connection connection = config.connect(false); Statement sql = connection.createStatement()) {
                sql.executeUpdate("DROP DATABASE `" + config.database() + "`");
            }
        }
    }

    private static void demoRegistrationAndLogin(DatabaseConfig config, MySqlDataStore store) throws Exception {
        String legacy = "demo_legacy", fresh = "demo_new";
        String password = "Demo-'\\;\tNguy\u1ec5n \ud83d\ude00";
        Map<String, Account> accounts = new LinkedHashMap<>();
        Map<String, MatchHistory> history = new LinkedHashMap<>();
        store.load(accounts, history);
        accounts.put(legacy, Account.register(legacy, password));
        String originalHash = accounts.get(legacy).getPasswordHash();
        store.save(accounts, history);
        check(demoPassword(config, legacy) == null, "Legacy password not guessed from hash");
        try (GameLifecycleIntegrationTest.Harness h = new GameLifecycleIntegrationTest.Harness(ServerManager.withDataStore(180, store));
                GameLifecycleIntegrationTest.Client a = new GameLifecycleIntegrationTest.Client(h.server.getLocalPort());
                GameLifecycleIntegrationTest.Client b = new GameLifecycleIntegrationTest.Client(h.server.getLocalPort())) {
            a.send(Protocol.LOGIN, legacy + ";wrong"); a.expect(Protocol.LOGIN_FAIL);
            check(demoPassword(config, legacy) == null, "Wrong password does not populate the demo column");
            a.send(Protocol.LOGIN, legacy + ";" + password); a.expect(Protocol.LOGIN_SUCCESS);
            check(password.equals(demoPassword(config, legacy)), "Successful legacy login fills exact original password");
            b.send(Protocol.REGISTER, fresh + ";" + password); b.expect(Protocol.REGISTER_SUCCESS);
            check(password.equals(demoPassword(config, fresh)), "Registration saves plaintext with Unicode and SQL special characters");
            b.send(Protocol.REGISTER, fresh + ";replacement"); b.expect(Protocol.REGISTER_FAIL);
            check(password.equals(demoPassword(config, fresh)), "Duplicate registration does not replace demo password");
            a.send(Protocol.GET_RANKING, "");
            check(!a.expect(Protocol.RANKING).contains(password), "Ranking does not expose the demo password");
        }
        // A Workbench edit to the display column must not change authentication.
        try (Connection connection = config.connect(true);
                PreparedStatement sql = connection.prepareStatement("UPDATE accounts SET password_plaintext=? WHERE username=?")) {
            sql.setString(1, "display_only"); sql.setString(2, legacy); sql.executeUpdate();
        }
        try (GameLifecycleIntegrationTest.Harness h = new GameLifecycleIntegrationTest.Harness(ServerManager.withDataStore(180, store));
                GameLifecycleIntegrationTest.Client a = new GameLifecycleIntegrationTest.Client(h.server.getLocalPort())) {
            a.send(Protocol.LOGIN, legacy + ";display_only"); a.expect(Protocol.LOGIN_FAIL);
            check("display_only".equals(demoPassword(config, legacy)), "Authentication uses hash, not editable plaintext column");
            a.send(Protocol.LOGIN, legacy + ";" + password); a.expect(Protocol.LOGIN_SUCCESS);
        }
        accounts.clear(); history.clear(); store.load(accounts, history); store.save(accounts, history);
        check(originalHash.equals(accounts.get(legacy).getPasswordHash())
                && password.equals(demoPassword(config, legacy)) && password.equals(demoPassword(config, fresh)),
                "Hash and demo passwords survive restart and ordinary snapshot saves");
        System.out.println("PASS: demo registration, successful-login backfill, wrong-login isolation, Unicode, hash authentication and persistence");
    }

    private static void tcpAndRestart(DatabaseConfig config, MySqlDataStore store) throws Exception {
        String nameA = "mysql_Alex", nameB = "mysql_Nina";
        String beforeRanking, beforeHistory;
        try (GameLifecycleIntegrationTest.Harness h = new GameLifecycleIntegrationTest.Harness(ServerManager.withDataStore(180, store));
                GameLifecycleIntegrationTest.Client a = RankingPersistenceIntegrationTest.login(h, nameA, true);
                GameLifecycleIntegrationTest.Client b = RankingPersistenceIntegrationTest.login(h, nameB, true)) {
            GameRoom room = RankingPersistenceIntegrationTest.start(h, a, nameA, b, nameB);
            RankingPersistenceIntegrationTest.seed(h, room, 30, 10, 3, 1);
            a.send(Protocol.EXIT_GAME, "");
            GameResult result = RankingPersistenceIntegrationTest.receive(a, b);
            check(GameResult.PLAYER2.equals(result.getWinner()), "Forfeit result through TCP");
            RankingPersistenceIntegrationTest.stats(RankingPersistenceIntegrationTest.profile(h.manager, nameA), 0, 0, 1, 3);
            RankingPersistenceIntegrationTest.stats(RankingPersistenceIntegrationTest.profile(h.manager, nameB), 3, 1, 1, 1);
            a.send(Protocol.GET_RANKING, ""); beforeRanking = a.expect(Protocol.RANKING);
            a.send(Protocol.GET_HISTORY, ""); beforeHistory = a.expect(Protocol.HISTORY);
            check(HistoryCodec.decode(beforeHistory).size() == 1, "Private match history");
            b.send(Protocol.REGISTER, nameA + ";duplicate"); b.expect(Protocol.REGISTER_FAIL);
        }
        try (GameLifecycleIntegrationTest.Harness h = new GameLifecycleIntegrationTest.Harness(ServerManager.withDataStore(180, store));
                GameLifecycleIntegrationTest.Client a = RankingPersistenceIntegrationTest.login(h, nameA, false);
                GameLifecycleIntegrationTest.Client b = new GameLifecycleIntegrationTest.Client(h.server.getLocalPort())) {
            a.send(Protocol.GET_RANKING, ""); check(beforeRanking.equals(a.expect(Protocol.RANKING)), "Ranking survives restart");
            a.send(Protocol.GET_HISTORY, ""); check(beforeHistory.equals(a.expect(Protocol.HISTORY)), "History survives restart");
            b.send(Protocol.LOGIN, nameB + ";wrong"); b.expect(Protocol.LOGIN_FAIL);
            b.send(Protocol.LOGIN, nameB + ";" + RankingPersistenceIntegrationTest.PASSWORD); b.expect(Protocol.LOGIN_SUCCESS);
        }
        check(RankingPersistenceIntegrationTest.PASSWORD.equals(demoPassword(config, nameA))
                && RankingPersistenceIntegrationTest.PASSWORD.equals(demoPassword(config, nameB)),
                "Match snapshots preserve demo passwords");
        System.out.println("PASS: TCP registration/login, duplicate rejection, match statistics/history, restart and password rejection on MySQL");
    }

    public static void main(String[] args) throws Exception {
        DatabaseConfig config = DatabaseConfig.read().withDatabase("treasure_test_" + UUID.randomUUID().toString().replace("-", ""));
        MySqlDataStore store = new MySqlDataStore(config);
        store.createDatabase();
        try {
            migration(store); upgradeSchema(config, store); transactionAndNames(store);
            demoRegistrationAndLogin(config, store); tcpAndRestart(config, store);
            readableRooms(config); completePasswordMigration(config);
            System.out.println("ALL MYSQL PERSISTENCE TESTS PASSED");
        } finally {
            try (Connection connection = config.connect(false); Statement sql = connection.createStatement()) {
                sql.executeUpdate("DROP DATABASE `" + config.database() + "`");
            }
        }
    }
}
