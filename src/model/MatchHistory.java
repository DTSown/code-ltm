package model;

public final class MatchHistory {
    private final GameResult result;
    private final long endedAtEpochMillis;
    private final int durationSeconds;

    public MatchHistory(GameResult result, long endedAtEpochMillis, int durationSeconds) {
        if (result == null || endedAtEpochMillis < 0 || durationSeconds < 0 || durationSeconds > 180) {
            throw new IllegalArgumentException("Invalid match history");
        }
        this.result = result;
        this.endedAtEpochMillis = endedAtEpochMillis;
        this.durationSeconds = durationSeconds;
    }

    public GameResult getResult() { return result; }
    public String getRoomId() { return result.getRoomId(); }
    public long getEndedAtEpochMillis() { return endedAtEpochMillis; }
    public int getDurationSeconds() { return durationSeconds; }
    public boolean containsPlayer(String username) {
        return result.getPlayer1Username().equals(username) || result.getPlayer2Username().equals(username);
    }
}
