package common;

public final class RoomName {
    private RoomName() { }

    // Numbered IDs retain their UUID suffix; old IDs remain compatible.
    public static long number(String roomId) {
        if (roomId == null || !roomId.startsWith("ROOM-")) { return 0; }
        int separator = roomId.indexOf('-', 5);
        if (separator < 0) { return 0; }
        try {
            long number = Long.parseLong(roomId.substring(5, separator));
            java.util.UUID.fromString(roomId.substring(separator + 1));
            return number > 0 ? number : 0;
        } catch (IllegalArgumentException invalid) { return 0; }
    }

    public static String display(String roomId) {
        long number = number(roomId);
        return number > 0 ? "Ph\u00f2ng " + number : roomId == null ? "--" : roomId;
    }
}
