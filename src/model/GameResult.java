package model;

public final class GameResult {
    public static final String PLAYER1 = "P1";
    public static final String PLAYER2 = "P2";
    public static final String DRAW = "DRAW";
    public static final String TIME_UP = "TIME_UP";
    public static final String PLAYER_EXIT = "PLAYER_EXIT";
    public static final String DISCONNECT = "DISCONNECT";
    private final String reason;
    private final String roomId;
    private final String player1Username;
    private final String player2Username;
    private final String winner;
    private final int player1Score;
    private final int player2Score;
    private final int player1Collected;
    private final int player2Collected;
    private final int player1RankGain;
    private final int player2RankGain;

    public GameResult(String roomId, String player1Username, String player2Username, String winner,
            int player1Score, int player2Score, int player1Collected, int player2Collected,
            int player1RankGain, int player2RankGain) {
        this(roomId, player1Username, player2Username, winner, player1Score, player2Score,
                player1Collected, player2Collected, player1RankGain, player2RankGain, TIME_UP);
    }

    public GameResult(String roomId, String player1Username, String player2Username, String winner,
            int player1Score, int player2Score, int player1Collected, int player2Collected,
            int player1RankGain, int player2RankGain, String reason) {
        if (roomId == null || roomId.isEmpty() || player1Username == null || player1Username.trim().isEmpty()
                || player2Username == null || player2Username.trim().isEmpty() || player1Username.equals(player2Username)
                || player1Score < 0 || player2Score < 0 || player1Collected < 0 || player2Collected < 0) {
            throw new IllegalArgumentException("Invalid result fields");
        }
        String expected = player1Score > player2Score ? PLAYER1 : player2Score > player1Score ? PLAYER2 : DRAW;
        if (!TIME_UP.equals(reason) && !PLAYER_EXIT.equals(reason) && !DISCONNECT.equals(reason)) {
            throw new IllegalArgumentException("Invalid end reason");
        }
        if (!TIME_UP.equals(reason)) {
            if (!PLAYER1.equals(winner) && !PLAYER2.equals(winner)) {
                throw new IllegalArgumentException("Forfeit requires a winner");
            }
            expected = winner;
        }
        int gain1 = DRAW.equals(expected) ? 1 : PLAYER1.equals(expected) ? 3 : 0;
        int gain2 = DRAW.equals(expected) ? 1 : PLAYER2.equals(expected) ? 3 : 0;
        if (!expected.equals(winner) || gain1 != player1RankGain || gain2 != player2RankGain) {
            throw new IllegalArgumentException("Winner and rank gains must match scores");
        }
        this.roomId = roomId;
        this.reason = reason;
        this.player1Username = player1Username;
        this.player2Username = player2Username;
        this.winner = winner;
        this.player1Score = player1Score;
        this.player2Score = player2Score;
        this.player1Collected = player1Collected;
        this.player2Collected = player2Collected;
        this.player1RankGain = player1RankGain;
        this.player2RankGain = player2RankGain;
    }

    public static GameResult fromState(GameState state) {
        Player p1 = state.getPlayer1();
        Player p2 = state.getPlayer2();
        String winner = p1.getMatchScore() > p2.getMatchScore() ? PLAYER1
                : p2.getMatchScore() > p1.getMatchScore() ? PLAYER2 : DRAW;
        return new GameResult(state.getRoomId(), p1.getUsername(), p2.getUsername(), winner,
                p1.getMatchScore(), p2.getMatchScore(), p1.getCollectedTreasure(), p2.getCollectedTreasure(),
                DRAW.equals(winner) ? 1 : PLAYER1.equals(winner) ? 3 : 0,
                DRAW.equals(winner) ? 1 : PLAYER2.equals(winner) ? 3 : 0);
    }

    public String getRoomId() { return roomId; }
    public String getReason() { return reason; }
    public String getPlayer1Username() { return player1Username; }
    public String getPlayer2Username() { return player2Username; }
    public String getWinner() { return winner; }
    public int getPlayer1Score() { return player1Score; }
    public int getPlayer2Score() { return player2Score; }
    public int getPlayer1Collected() { return player1Collected; }
    public int getPlayer2Collected() { return player2Collected; }
    public int getPlayer1RankGain() { return player1RankGain; }
    public int getPlayer2RankGain() { return player2RankGain; }
}
