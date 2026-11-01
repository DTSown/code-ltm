package model;

public class Player {
    public enum Status {
        FREE,
        PLAYING
    }

    private String username;
    private int totalRankPoint;
    private int wins;
    private int totalMatches;
    private int totalTreasureCollected;
    private Status status;
    private Position position;
    private int matchScore;
    private int collectedTreasure;

    public Player() {
        this("", 0, 0, Status.FREE, new Position(), 0, 0);
    }

    public Player(String username, int totalRankPoint, int wins, Status status,
                  Position position, int matchScore, int collectedTreasure) {
        this.username = username;
        this.totalRankPoint = totalRankPoint;
        this.wins = wins;
        this.status = status;
        this.position = position;
        this.matchScore = matchScore;
        this.collectedTreasure = collectedTreasure;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public int getTotalRankPoint() {
        return totalRankPoint;
    }

    public void setTotalRankPoint(int totalRankPoint) {
        this.totalRankPoint = totalRankPoint;
    }

    public int getWins() {
        return wins;
    }

    public void setWins(int wins) {
        this.wins = wins;
    }

    public int getTotalMatches() { return totalMatches; }
    public void setTotalMatches(int totalMatches) { this.totalMatches = totalMatches; }
    public int getTotalTreasureCollected() { return totalTreasureCollected; }
    public void setTotalTreasureCollected(int total) { this.totalTreasureCollected = total; }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Position getPosition() {
        return position;
    }

    public void setPosition(Position position) {
        this.position = position;
    }

    public int getMatchScore() {
        return matchScore;
    }

    public void setMatchScore(int matchScore) {
        this.matchScore = matchScore;
    }

    public int getCollectedTreasure() {
        return collectedTreasure;
    }

    public void setCollectedTreasure(int collectedTreasure) {
        this.collectedTreasure = collectedTreasure;
    }
}
