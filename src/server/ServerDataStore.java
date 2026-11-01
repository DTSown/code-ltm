package server;

import common.HistoryCodec;
import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import model.MatchHistory;
import model.Player;

public final class ServerDataStore implements DataStore {
    private static final String HEADER = "TREASURE_SERVER\t1";
    private final Path file;

    public ServerDataStore(Path file) { this.file = file.toAbsolutePath().normalize(); }

    public void initialize() throws IOException { if (Files.notExists(file)) { save(Map.of(), Map.of()); } }

    public void load(Map<String, Account> accounts, Map<String, MatchHistory> history) throws IOException {
        if (Files.notExists(file)) { return; }
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        Map<String, Account> loadedAccounts = new LinkedHashMap<>();
        Map<String, MatchHistory> loadedHistory = new LinkedHashMap<>();
        try {
            if (lines.isEmpty() || !HEADER.equals(lines.get(0))) { throw new IllegalArgumentException("Invalid data header"); }
            if (lines.size() < 2) { throw new IllegalArgumentException("Missing snapshot footer"); }
            for (int i = 1; i < lines.size() - 1; i++) {
                String[] f = lines.get(i).split("\t", -1);
                if (f.length == 8 && "ACCOUNT".equals(f[0])) {
                    Account account = new Account(URLDecoder.decode(f[1], StandardCharsets.UTF_8), f[2], f[3],
                            Integer.parseInt(f[4]), Integer.parseInt(f[5]), Integer.parseInt(f[6]), Integer.parseInt(f[7]));
                    if (loadedAccounts.putIfAbsent(account.getUsername(), account) != null) {
                        throw new IllegalArgumentException("Duplicate account");
                    }
                } else if (f.length == 2 && "MATCH".equals(f[0])) {
                    List<MatchHistory> decoded = HistoryCodec.decode(f[1]);
                    if (decoded.size() != 1 || loadedHistory.putIfAbsent(decoded.get(0).getRoomId(), decoded.get(0)) != null) {
                        throw new IllegalArgumentException("Invalid or duplicate match");
                    }
                } else { throw new IllegalArgumentException("Invalid data row " + (i + 1)); }
            }
            String[] footer = lines.get(lines.size() - 1).split("\t", -1);
            if (footer.length != 3 || !"END".equals(footer[0])
                    || Integer.parseInt(footer[1]) != loadedAccounts.size()
                    || Integer.parseInt(footer[2]) != loadedHistory.size()) {
                throw new IllegalArgumentException("Incomplete snapshot or invalid record counts");
            }
            for (MatchHistory match : loadedHistory.values()) {
                if (!loadedAccounts.containsKey(match.getResult().getPlayer1Username())
                        || !loadedAccounts.containsKey(match.getResult().getPlayer2Username())) {
                    throw new IllegalArgumentException("History references missing account");
                }
            }
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid server data in " + file + ": " + e.getMessage(), e);
        }
        accounts.putAll(loadedAccounts); history.putAll(loadedHistory);
    }

    public void save(Map<String, Account> accounts, Map<String, MatchHistory> history) throws IOException {
        List<String> lines = new ArrayList<>(); lines.add(HEADER);
        List<Account> sorted = new ArrayList<>(accounts.values());
        sorted.sort(Comparator.comparing(Account::getUsername));
        for (Account account : sorted) {
            Player p = account.createPlayer();
            lines.add("ACCOUNT\t" + URLEncoder.encode(account.getUsername(), StandardCharsets.UTF_8)
                    + "\t" + account.getSalt() + "\t" + account.getPasswordHash()
                    + "\t" + p.getTotalRankPoint() + "\t" + p.getWins()
                    + "\t" + p.getTotalTreasureCollected() + "\t" + p.getTotalMatches());
        }
        for (MatchHistory match : history.values()) {
            lines.add("MATCH\t" + HistoryCodec.encode(java.util.Collections.singletonList(match)));
        }
        lines.add("END\t" + accounts.size() + "\t" + history.size());
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "server-data-", ".tmp");
        try {
            byte[] bytes = (String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) { channel.write(buffer); }
                channel.force(true);
            }
            // One atomic snapshot keeps account statistics and history in the same commit.
            for (int attempt = 0; ; attempt++) {
                try {
                    Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                    break;
                } catch (AtomicMoveNotSupportedException e) { throw e; }
                catch (IOException e) {
                    if (attempt >= 4) { throw e; }
                    // Windows sync/antivirus can briefly hold the destination after a write.
                    try { Thread.sleep(50L * (attempt + 1)); }
                    catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Interrupted while committing server data", interrupted);
                    }
                }
            }
        } finally { Files.deleteIfExists(temporary); }
    }
}
