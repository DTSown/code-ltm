package common;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import model.MatchHistory;

public final class HistoryCodec {
    private HistoryCodec() { }

    public static String encode(List<MatchHistory> matches) {
        List<String> rows = new ArrayList<>();
        for (MatchHistory match : matches) {
            rows.add(GameResultCodec.encode(match.getResult()) + ";" + match.getEndedAtEpochMillis()
                    + ";" + match.getDurationSeconds());
        }
        return String.join("/", rows);
    }

    public static List<MatchHistory> decode(String data) {
        if (data == null) { throw new IllegalArgumentException("Missing history"); }
        List<MatchHistory> matches = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        if (data.isEmpty()) { return matches; }
        for (String row : data.split("/", -1)) {
            String[] fields = row.split(";", -1);
            if (fields.length != 13) { throw new IllegalArgumentException("History needs thirteen fields per match"); }
            MatchHistory match = new MatchHistory(GameResultCodec.decode(String.join(";",
                    java.util.Arrays.copyOf(fields, 11))), Long.parseLong(fields[11]), Integer.parseInt(fields[12]));
            if (!ids.add(match.getRoomId())) { throw new IllegalArgumentException("Duplicate history room"); }
            matches.add(match);
        }
        return matches;
    }
}
