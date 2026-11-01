package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class GameServer {
    // Cổng TCP mặc định của game; đây là cổng Client kết nối, khác cổng MySQL.
    public static final int PORT = 5000;

    private ServerSocket serverSocket;
    private ServerManager serverManager;

    // ===== KHỞI TẠO BỘ QUẢN LÝ VÀ NẠP DỮ LIỆU SERVER =====
    public GameServer() {
        // Tạo bộ quản lý chung và nạp tài khoản/lịch sử trước khi nhận người chơi.
        serverManager = new ServerManager();
    }

    // ===== MỞ CỔNG TCP VÀ CHỜ KẾT NỐI CLIENT =====
    public void start() {
        // Cho phép chọn cổng qua -Dtreasure.server.port; không truyền thì dùng 5000.
        int listenPort = Integer.getInteger("treasure.server.port", PORT);
        if (listenPort < 1 || listenPort > 65535) { throw new IllegalStateException("Invalid server port: " + listenPort); }
        // Khi JVM tắt bình thường, dọn bộ đếm giờ và thử lưu dữ liệu còn đang chờ.
        Runtime.getRuntime().addShutdownHook(new Thread(serverManager::close, "ServerTimerShutdown"));
        try {
            // ServerSocket lắng nghe kết nối trên các giao diện mạng của máy Server.
            serverSocket = new ServerSocket(listenPort);
            System.out.println("Server started on port " + listenPort);

            // ===== NHẬN MỖI CLIENT VÀ TẠO THREAD XỬ LÝ RIÊNG =====
            while (true) {
                // accept() chờ một Client; Socket trả về chỉ phục vụ kết nối vừa nhận.
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected: " + clientSocket.getRemoteSocketAddress());

                // Mỗi Client có một handler/thread riêng nhưng dùng chung ServerManager.
                ClientHandler clientHandler = new ClientHandler(clientSocket, serverManager);

                Thread thread = new Thread(clientHandler, "ClientHandler-" + clientSocket.getPort());
                thread.start();
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot listen on port " + listenPort + ": " + e.getMessage(), e);
        } finally {
            // Dọn tài nguyên khi vòng lặp Server kết thúc, kể cả khi gặp lỗi mở/nhận kết nối.
            serverManager.close();
            if (serverSocket != null) {
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    System.out.println("Error closing server socket: " + e.getMessage());
                }
            }
        }
    }

    // ===== ĐIỂM KHỞI CHẠY SERVER VÀ BÁO LỖI KHỞI ĐỘNG =====
    public static void main(String[] args) {
        // Điểm chạy đầu tiên: tạo Server, mở cổng; lỗi khởi động thì báo và thoát.
        try { new GameServer().start(); }
        catch (IllegalStateException e) {
            System.err.println("Server cannot start: " + e.getMessage());
            System.exit(1);
        }
    }
}
