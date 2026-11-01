package model;

public class Treasure {
    public static final String NORMAL = "NORMAL";
    public static final String SILVER = "SILVER";
    public static final String GOLD = "GOLD";
    private String id;
    private Position position;
    private int value;
    private String type;

    public static int valueForType(String type) {
        switch (type) {
            case NORMAL: return 10;
            case SILVER: return 20;
            case GOLD: return 30;
            default: throw new IllegalArgumentException("Invalid treasure type: " + type);
        }
    }

    public Treasure() {
        this(new Position(), 0, "NORMAL");
    }

    public Treasure(Position position, int value, String type) {
        this("", position, value, type);
    }

    public Treasure(String id, Position position, int value, String type) {
        this.id = id;
        this.position = position;
        this.value = value;
        this.type = type;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Position getPosition() {
        return position;
    }

    public void setPosition(Position position) {
        this.position = position;
    }

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
