package common;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import model.Player;

public final class RankingCodec {
    private RankingCodec() { }

    public static String encode(List<Player> players) {
        List<String> rows = new ArrayList<>();
        for (Player p : players) {
            rows.add(URLEncoder.encode(p.getUsername(), StandardCharsets.UTF_8) + "," + p.getTotalRankPoint()
                    + "," + p.getWins() + "," + p.getTotalTreasureCollected() + "," + p.getTotalMatches());
        }
        return String.join(";", rows);
    }

    public static List<Player> decode(String data) {
        if (data == null) { throw new IllegalArgumentException("Missing ranking"); }
        List<Player> players = new ArrayList<>();
        Set<String> names = new HashSet<>();
        if (data.isEmpty()) { return players; }
        for (String row : data.split(";", -1)) {
            String[] f = row.split(",", -1);
            if (f.length != 5) { throw new IllegalArgumentException("Ranking needs five fields per player"); }
            Player p = new Player();
            p.setUsername(URLDecoder.decode(f[0], StandardCharsets.UTF_8));
            if (p.getUsername().trim().isEmpty() || !names.add(p.getUsername())) {
                throw new IllegalArgumentException("Invalid or duplicate ranking username");
            }
            p.setTotalRankPoint(number(f[1])); p.setWins(number(f[2]));
            p.setTotalTreasureCollected(number(f[3])); p.setTotalMatches(number(f[4]));
            players.add(p);
        }
        return players;
    }

    private static int number(String text) {
        int value = Integer.parseInt(text);
        if (value < 0) { throw new IllegalArgumentException("Negative statistics"); }
        return value;
    }
}
