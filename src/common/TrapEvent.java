package common;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public record TrapEvent(String roomId, long eventId, String username,
        int pointsLost, int totalScore, int stunMillis) {
    public TrapEvent {
        if (roomId == null || roomId.isBlank() || roomId.indexOf(';') >= 0 || roomId.indexOf('|') >= 0
                || roomId.indexOf('\r') >= 0 || roomId.indexOf('\n') >= 0
                || eventId <= 0 || username == null || username.trim().isEmpty()
                || pointsLost < 0 || pointsLost > 10 || totalScore < 0
                || stunMillis < 0 || stunMillis > 2000) {
            throw new IllegalArgumentException("Invalid trap event fields");
        }
    }

    // roomId;eventId;URLencodedUsername;pointsLost;totalScore;stunMillis
    public String encode() {
        return roomId + ";" + eventId + ";" + URLEncoder.encode(username, StandardCharsets.UTF_8)
                + ";" + pointsLost + ";" + totalScore + ";" + stunMillis;
    }

    public static TrapEvent decode(String data) {
        if (data == null) {
            throw new IllegalArgumentException("Missing TRAP_TRIGGERED data");
        }
        String[] fields = data.split(";", -1);
        if (fields.length != 6) {
            throw new IllegalArgumentException("TRAP_TRIGGERED must contain six fields");
        }
        return new TrapEvent(fields[0], Long.parseLong(fields[1]),
                URLDecoder.decode(fields[2], StandardCharsets.UTF_8),
                Integer.parseInt(fields[3]), Integer.parseInt(fields[4]), Integer.parseInt(fields[5]));
    }
}
