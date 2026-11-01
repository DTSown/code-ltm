package common;

public class Message {
    // type là loại yêu cầu/phản hồi; data chứa phần dữ liệu theo quy ước của từng loại.
    private String type;
    private String data;

    public Message() {
        this("", "");
    }

    public Message(String type, String data) {
        this.type = type;
        this.data = data;
    }

    // ===== ĐÓNG GÓI THÔNG ĐIỆP THÀNH TYPE|DATA =====
    public String encode() {
        // Đóng gói thành TYPE|DATA; đây là định dạng văn bản, không phải mã hóa bảo mật.
        return type + "|" + data;
    }

    // ===== TÁCH CHUỖI NHẬN ĐƯỢC THÀNH LOẠI VÀ NỘI DUNG =====
    public static Message decode(String raw) {
        // Bản tin rỗng được đổi thành ERROR để nơi gọi vẫn nhận một đối tượng Message.
        if (raw == null || raw.trim().isEmpty()) {
            return new Message(Protocol.ERROR, "Empty message");
        }

        // Bỏ ký tự BOM đầu chuỗi nếu có, tránh làm sai tên loại thông điệp.
        if (!raw.isEmpty() && raw.charAt(0) == '\uFEFF') {
            raw = raw.substring(1);
        }

        // Chỉ tách tại dấu | đầu tiên; các dấu | còn lại vẫn thuộc phần data.
        String[] parts = raw.split("\\|", 2);
        if (parts.length == 2) {
            return new Message(parts[0], parts[1]);
        }

        return new Message(parts[0], "");
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return encode();
    }
}
