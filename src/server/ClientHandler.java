package server;

import common.Message;
import common.Protocol;
import java.io.Closeable;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import model.Player;

public class ClientHandler implements Runnable {
    // Mỗi đối tượng phục vụ một socket; Runnable.run() được GameServer chạy trên thread riêng.
    private final Socket socket;
    private final ServerManager serverManager;
    private BufferedReader in;
    private BufferedWriter out;
    private String clientUsername;
    private Player player;
    private boolean closed;

    public ClientHandler(Socket socket, ServerManager serverManager) {
        this.socket = socket;
        this.serverManager = serverManager;
        this.clientUsername = "UNKNOWN";
    }

    @Override
    // ===== ĐỌC YÊU CẦU TCP TỪ MỘT CLIENT =====
    public void run() {
        try {
            // Hai đầu đọc/ghi dùng cùng UTF-8 để truyền đúng username và thông báo tiếng Việt.
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

            String line;
            // Đọc từng dòng, tách TYPE|DATA rồi phân nhánh xử lý; readLine() có thể chờ ngay trên thread Client này.
            while ((line = in.readLine()) != null) {
                Message message = Message.decode(line);
                handleMessage(message);
            }

        } catch (IOException e) {
            // Cleanup below logs the disconnect once and preserves the other clients.
        } finally {
            // EOF hoặc lỗi socket đều phải dọn kết nối và trạng thái online của người chơi.
            closeConnection();
        }
    }

    // ===== PHÂN LOẠI THÔNG ĐIỆP VÀ GỌI ĐÚNG CHỨC NĂNG SERVER =====
    public void handleMessage(Message message) {
        // switch chọn nghiệp vụ theo loại bản tin; không tạo thêm thread cho mỗi nhánh REGISTER/LOGIN.
        String type = message.getType();

        if (type == null || type.trim().isEmpty()) {
            return;
        }

        switch (type) {
            case Protocol.HELLO:
                sendMessage(new Message(Protocol.WELCOME, "Connected successfully"));
                break;
            case Protocol.REGISTER:
                handleRegister(message.getData());
                break;
            case Protocol.LOGIN:
                handleLogin(message.getData());
                break;
            case Protocol.GET_ONLINE_LIST:
                serverManager.sendOnlineList(this);
                break;
            case Protocol.GET_RANKING:
                serverManager.sendRanking(this);
                break;
            case Protocol.GET_HISTORY:
                serverManager.sendHistory(this);
                break;
            case Protocol.INVITE:
                serverManager.invite(this, message.getData());
                break;
            case Protocol.INVITE_ACCEPT:
                serverManager.acceptInvite(this, message.getData());
                break;
            case Protocol.INVITE_REJECT:
                serverManager.rejectInvite(this, message.getData());
                break;
            case Protocol.MOVE:
                serverManager.move(this, message.getData());
                break;
            case Protocol.EXIT_GAME:
                serverManager.exitGame(this);
                break;
            case Protocol.PLAY_AGAIN:
                serverManager.requestRematch(this, message.getData());
                break;
            case Protocol.PLAY_AGAIN_ACCEPT:
                serverManager.acceptRematch(this, message.getData());
                break;
            case Protocol.PLAY_AGAIN_REJECT:
                serverManager.rejectRematch(this, message.getData());
                break;
            case Protocol.RETURN_TO_LOBBY:
                serverManager.returnToLobby(this);
                break;
            case Protocol.TREASURE_COLLECTED:
                // Các thông điệp kết quả/trạng thái thuộc quyền Server, không chấp nhận Client tự gửi để sửa game.
            case Protocol.TRAP_TRIGGERED:
            case Protocol.GAME_STATE:
            case Protocol.TIME_UPDATE:
            case Protocol.GAME_OVER:
            case Protocol.RANKING:
            case Protocol.HISTORY:
                sendMessage(new Message(Protocol.ERROR, "Message này chỉ do Server gửi"));
                break;
            case Protocol.DISCONNECT:
                System.out.println("Client requested disconnect: " + clientUsername);
                closeConnection();
                break;
            case Protocol.LOGOUT:
                // Đăng xuất đóng phiên mạng; dữ liệu tài khoản và lịch sử lâu dài không bị xóa.
                System.out.println("Client logged out: " + clientUsername);
                closeConnection();
                break;
            default:
                sendMessage(new Message(Protocol.ERROR, "Unknown message type: " + type));
                break;
        }
    }

    // ===== NHẬN YÊU CẦU ĐĂNG KÝ VÀ TRẢ KẾT QUẢ CHO CLIENT =====
    private void handleRegister(String data) {
        // Tách thông tin, nhờ ServerManager xử lý; null là thành công, còn chuỗi lỗi thành REGISTER_FAIL.
        String[] credentials = parseCredentials(data);
        String error = serverManager.registerAccount(credentials[0], credentials[1]);
        if (error == null) {
            sendMessage(new Message(Protocol.REGISTER_SUCCESS, "Đăng ký thành công"));
        } else {
            sendMessage(new Message(Protocol.REGISTER_FAIL, error));
        }
    }

    // ===== NHẬN YÊU CẦU ĐĂNG NHẬP VÀ CẬP NHẬT DANH SÁCH ONLINE =====
    private void handleLogin(String data) {
        // Xác thực và chống đăng nhập trùng trong ServerManager; đúng thì báo Client và phát lại danh sách online.
        String[] credentials = parseCredentials(data);
        String error = serverManager.loginClient(this, credentials[0], credentials[1]);
        if (error == null) {
            sendMessage(new Message(Protocol.LOGIN_SUCCESS, clientUsername));
            serverManager.broadcastOnlineList();
        } else {
            sendMessage(new Message(Protocol.LOGIN_FAIL, error));
        }
    }

    // ===== TÁCH USERNAME VÀ MẬT KHẨU TỪ NỘI DUNG BẢN TIN =====
    private String[] parseCredentials(String data) {
        // Chỉ tách tại dấu ; đầu tiên để phần mật khẩu còn lại được giữ; hiện code trim hai đầu cả hai trường.
        String[] parts = data == null ? new String[0] : data.split(";", 2);
        String username = parts.length > 0 ? parts[0].trim() : "";
        String password = parts.length > 1 ? parts[1].trim() : "";
        return new String[] { username, password };
    }

    // ===== GỬI PHẢN HỒI TỪ SERVER VỀ CLIENT =====
    public synchronized boolean sendMessage(Message message) {
        // Ghi nguyên một bản tin có dấu xuống dòng rồi flush; synchronized tránh các nguồn gửi ghi xen kẽ.
        if (closed || message == null || out == null) {
            return false;
        }

        try {
            out.write(message.encode() + "\n");
            out.flush();
            return true;
        } catch (IOException e) {
            System.out.println("Error sending message to client: " + e.getMessage());
            return false;
        }
    }

    // ===== ĐÓNG SOCKET VÀ DỌN PHIÊN KHI LOGOUT / MẤT MẠNG =====
    public void closeConnection() {
        synchronized (this) {
            // Cờ closed bảo đảm các đường EOF, LOGOUT và lỗi chỉ dọn cùng kết nối một lần.
            if (closed) {
                return;
            }
            closed = true;
        }

        // Đóng socket/luồng, xóa phiên online và thông báo danh sách mới nếu trước đó đã đăng nhập.
        boolean wasLoggedIn = player != null;
        closeQuietly(socket);
        closeQuietly(in);
        closeQuietly(out);

        serverManager.removeClient(this);
        if (wasLoggedIn) {
            serverManager.broadcastOnlineList();
        }
    }

    private void closeQuietly(Closeable closeable) {
        if (closeable == null) {
            return;
        }

        try {
            closeable.close();
        } catch (IOException e) {
            System.out.println("Error while closing client resource");
        }
    }

    public String getClientUsername() {
        return clientUsername;
    }

    public void setClientUsername(String clientUsername) {
        this.clientUsername = clientUsername;
    }

    public Player getPlayer() {
        return player;
    }

    public void setPlayer(Player player) {
        this.player = player;
    }
}
