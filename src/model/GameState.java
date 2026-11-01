package model;

import java.util.ArrayList;
import java.util.List;

/**
 * Snapshot authoritative của một phòng tại một thời điểm: map, hai người chơi,
 * danh sách rương, thời gian và trạng thái trận. Server sở hữu object gốc; client
 * chỉ nhận bản được giải mã từ GAME_STATE để hiển thị.
 */
public class GameState {
    public static final int MAP_SIZE = 7;
    private String roomId;
    private int[][] map;
    private Player player1;
    private Player player2;
    private List<Treasure> treasures;
    private int remainingTime;
    private String status;

    public GameState() {
        this.roomId = "";
        this.map = new int[MAP_SIZE][MAP_SIZE];
        this.player1 = new Player();
        this.player2 = new Player();
        this.treasures = new ArrayList<>();
        this.remainingTime = 0;
        this.status = "WAITING";
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public int[][] getMap() {
        return map;
    }

    public void setMap(int[][] map) {
        this.map = map;
    }

    public Player getPlayer1() {
        return player1;
    }

    public void setPlayer1(Player player1) {
        this.player1 = player1;
    }

    public Player getPlayer2() {
        return player2;
    }

    public void setPlayer2(Player player2) {
        this.player2 = player2;
    }

    public List<Treasure> getTreasures() {
        return treasures;
    }

    public void setTreasures(List<Treasure> treasures) {
        this.treasures = treasures;
    }

    public int getRemainingTime() {
        return remainingTime;
    }

    public void setRemainingTime(int remainingTime) {
        this.remainingTime = remainingTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
