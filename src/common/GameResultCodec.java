package common;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import model.GameResult;

public final class GameResultCodec {
    private GameResultCodec() {
    }

    public static String encode(GameResult result) {
        return result.getRoomId() + ";" + URLEncoder.encode(result.getPlayer1Username(), StandardCharsets.UTF_8)
                + ";" + URLEncoder.encode(result.getPlayer2Username(), StandardCharsets.UTF_8)
                + ";" + result.getWinner() + ";" + result.getPlayer1Score() + ";" + result.getPlayer2Score()
                + ";" + result.getPlayer1Collected() + ";" + result.getPlayer2Collected()
                + ";" + result.getPlayer1RankGain() + ";" + result.getPlayer2RankGain() + ";" + result.getReason();
    }

    public static GameResult decode(String data) {
        if (data == null) {
            throw new IllegalArgumentException("Missing GAME_OVER data");
        }
        String[] fields = data.split(";", -1);
        if (fields.length != 11) {
            throw new IllegalArgumentException("GAME_OVER must contain eleven fields");
        }
        return new GameResult(fields[0], URLDecoder.decode(fields[1], StandardCharsets.UTF_8),
                URLDecoder.decode(fields[2], StandardCharsets.UTF_8), fields[3],
                Integer.parseInt(fields[4]), Integer.parseInt(fields[5]),
                Integer.parseInt(fields[6]), Integer.parseInt(fields[7]),
                Integer.parseInt(fields[8]), Integer.parseInt(fields[9]), fields[10]);
    }
}
