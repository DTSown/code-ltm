package common;

public final class Protocol {
    // Tên thông điệp dùng chung cho Client/Server để tránh viết sai chuỗi; lớp này không gửi socket.
    private Protocol() {
    }

    // Lời chào ở tầng ứng dụng sau khi TCP đã kết nối; đây không phải bước xác thực tài khoản.
    // ===== QUY ƯỚC THÔNG ĐIỆP CHÀO VÀ XÁC NHẬN KẾT NỐI =====
    public static final String HELLO = "HELLO";
    public static final String WELCOME = "WELCOME";

    // Client gửi LOGIN/REGISTER; Server trả loại SUCCESS hoặc FAIL tương ứng.
    // ===== QUY ƯỚC THÔNG ĐIỆP ĐĂNG NHẬP, ĐĂNG KÝ VÀ ĐĂNG XUẤT =====
    public static final String LOGIN = "LOGIN";
    public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String LOGIN_FAIL = "LOGIN_FAIL";
    public static final String REGISTER = "REGISTER";
    public static final String REGISTER_SUCCESS = "REGISTER_SUCCESS";
    public static final String REGISTER_FAIL = "REGISTER_FAIL";
    public static final String LOGOUT = "LOGOUT";

    // GET_ONLINE_LIST yêu cầu danh sách; ONLINE_LIST là dữ liệu Server gửi về để cập nhật sảnh.
    // ===== QUY ƯỚC THÔNG ĐIỆP LẤY DANH SÁCH ONLINE =====
    public static final String GET_ONLINE_LIST = "GET_ONLINE_LIST";
    public static final String ONLINE_LIST = "ONLINE_LIST";

    // ===== QUY ƯỚC THÔNG ĐIỆP MỜI NGƯỜI CHƠI VÀO TRẬN =====
    public static final String INVITE = "INVITE";
    public static final String INVITE_RECEIVED = "INVITE_RECEIVED";
    public static final String INVITE_ACCEPT = "INVITE_ACCEPT";
    public static final String INVITE_REJECT = "INVITE_REJECT";

    // ===== QUY ƯỚC THÔNG ĐIỆP ĐIỀU KHIỂN VÀ CẬP NHẬT TRẬN =====
    public static final String GAME_START = "GAME_START";
    public static final String MOVE = "MOVE";
    public static final String GAME_STATE = "GAME_STATE";
    public static final String TIME_UPDATE = "TIME_UPDATE";
    public static final String TREASURE_COLLECTED = "TREASURE_COLLECTED";
    public static final String TRAP_TRIGGERED = "TRAP_TRIGGERED";
    public static final String GAME_OVER = "GAME_OVER";

    // ===== QUY ƯỚC THÔNG ĐIỆP TÁI ĐẤU =====
    public static final String PLAY_AGAIN = "PLAY_AGAIN";
    public static final String PLAY_AGAIN_ACCEPT = "PLAY_AGAIN_ACCEPT";
    public static final String PLAY_AGAIN_REJECT = "PLAY_AGAIN_REJECT";

    public static final String EXIT_GAME = "EXIT_GAME";
    public static final String RETURN_TO_LOBBY = "RETURN_TO_LOBBY";
    // ===== QUY ƯỚC THÔNG ĐIỆP LẤY XẾP HẠNG VÀ LỊCH SỬ =====
    public static final String GET_RANKING = "GET_RANKING";
    public static final String RANKING = "RANKING";
    public static final String GET_HISTORY = "GET_HISTORY";
    public static final String HISTORY = "HISTORY";
    // LOGOUT là yêu cầu đăng xuất; EOF/lỗi mạng vẫn được phát hiện dù Client không gửi DISCONNECT.
    public static final String DISCONNECT = "DISCONNECT";
    public static final String ERROR = "ERROR";
}
