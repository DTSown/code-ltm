package client;

import common.Message;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

public class ClientConnection {
    // Chờ thiết lập TCP tối đa 5 giây; không phải timeout tự ngắt một phiên đang chơi.
    private static final int CONNECT_TIMEOUT_MS = 5000;
    // volatile giúp thread mạng và thread giao diện nhìn thấy phiên hiện tại khi đọc biến này.
    private volatile Session session;
    private volatile MessageListener messageListener;
    private volatile Consumer<String> disconnectListener;
    private volatile String connectionError = "";

    // Each receiver owns its socket: a stale receiver cannot close a later connection.
    // ===== LƯU SOCKET VÀ LUỒNG ĐỌC / GHI CỦA MỘT PHIÊN KẾT NỐI =====
    private static final class Session {
        // Một phiên giữ cùng socket và hai luồng UTF-8; thread nhận luôn gắn với đúng phiên đó.
        final Socket socket;
        final BufferedReader reader;
        final BufferedWriter writer;
        Session(Socket socket) throws IOException {
            this.socket = socket;
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        }
    }

    // ===== KẾT NỐI TCP TỚI SERVER VÀ XỬ LÝ TIMEOUT =====
    public synchronized void connect(String ip, int port) {
        // Dọn kết nối cũ rồi mở TCP tới IP/port người dùng chọn; synchronized tuần tự hóa việc kết nối.
        disconnect(); connectionError = "";
        Socket socket = new Socket();
        try {
            if (ip == null || ip.trim().isEmpty() || port < 1 || port > 65535) { throw new IllegalArgumentException("Invalid address"); }
            socket.connect(new InetSocketAddress(ip.trim(), port), CONNECT_TIMEOUT_MS);
            Session connected = new Session(socket); session = connected;
            // Nhận bản tin ở thread nền để không chặn EDT; daemon không giữ JVM sống khi Client đã đóng.
            Thread receiver = new Thread(() -> listenServer(connected), "ClientReceiver");
            receiver.setDaemon(true); receiver.start();
        } catch (IOException | IllegalArgumentException e) {
            // Phân biệt lỗi hostname, hết thời gian chờ và lỗi kết nối khác để form hiển thị đúng nguyên nhân.
            connectionError = e instanceof java.net.UnknownHostException ? "Kh\u00f4ng t\u00ecm th\u1ea5y Server IP/t\u00ean m\u00e1y."
                    : e instanceof java.net.SocketTimeoutException ? "K\u1ebft n\u1ed1i qu\u00e1 th\u1eddi gian. Ki\u1ec3m tra IP, Port v\u00e0 Firewall."
                    : "Kh\u00f4ng k\u1ebft n\u1ed1i \u0111\u01b0\u1ee3c. Ki\u1ec3m tra IP, Port v\u00e0 Server \u0111ang ch\u1ea1y.";
            try { socket.close(); } catch (IOException ignored) { }
        }
    }

    // ===== GỬI BẢN TIN VĂN BẢN TỚI SERVER =====
    public synchronized void sendMessage(String rawMessage) {
        // Mỗi bản tin chiếm một dòng; chặn xuống dòng trong nội dung để không bị tách thành hai yêu cầu.
        Session active = session;
        if (active == null || rawMessage == null) { return; }
        if (rawMessage.indexOf('\n') >= 0 || rawMessage.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Messages must contain only one line");
        }
        // Thêm dấu kết thúc dòng cho readLine() phía Server và flush để đẩy bộ đệm ra luồng gửi.
        try { active.writer.write(rawMessage); active.writer.newLine(); active.writer.flush(); }
        catch (IOException e) { closeSession(active, true); }
    }

    // ===== CHUYỂN MESSAGE THÀNH CHUỖI TRƯỚC KHI GỬI =====
    public synchronized void sendMessage(Message message) { if (message != null) { sendMessage(message.encode()); } }

    // ===== NHẬN PHẢN HỒI SERVER TRÊN THREAD NỀN =====
    private void listenServer(Session active) {
        // readLine() trả null khi bên kia đóng luồng; lỗi socket cũng đi qua finally để dọn phiên.
        // Chưa có heartbeat: mất mạng im lặng có thể phải chờ hệ điều hành phát hiện.
        try {
            String line;
            while ((line = active.reader.readLine()) != null) {
                Message message = Message.decode(line);
                MessageListener listener = messageListener;
                if (listener != null) {
                    // Đưa phản hồi lên EDT; bỏ phản hồi cũ nếu Client đã chuyển sang một phiên mới.
                    SwingUtilities.invokeLater(() -> { if (session == active) { listener.onMessage(message); } });
                }
            }
        } catch (IOException ignored) {
            // EOF and socket errors share the same single disconnect notification.
        } finally { closeSession(active, true); }
    }

    // ===== ĐÓNG PHIÊN MẠNG VÀ THÔNG BÁO MẤT KẾT NỐI =====
    private void closeSession(Session expected, boolean notify) {
        synchronized (this) {
            // Thread nhận của phiên cũ không được đóng nhầm kết nối mới; chỉ một bên nhận quyền dọn phiên.
            if (session != expected) { return; }
            session = null;
        }
        // Đóng socket trước để đánh thức thread đang chờ đọc, rồi đóng các luồng gắn với socket.
        try { expected.socket.close(); } catch (IOException ignored) { }
        try { expected.reader.close(); } catch (IOException ignored) { }
        try { expected.writer.close(); } catch (IOException ignored) { }
        Consumer<String> listener = disconnectListener;
        if (notify && listener != null) {
            // Chỉ báo mất kết nối khi cần; giao diện nhận callback và chuyển về màn hình đăng nhập.
            SwingUtilities.invokeLater(() -> listener.accept("M\u1ea5t k\u1ebft n\u1ed1i Server. Vui l\u00f2ng ki\u1ec3m tra m\u1ea1ng v\u00e0 \u0111\u0103ng nh\u1eadp l\u1ea1i."));
        }
    }

    public void setMessageListener(MessageListener listener) { messageListener = listener; }
    public void setDisconnectListener(Consumer<String> listener) { disconnectListener = listener; }
    // Chủ động đăng xuất/đổi kết nối dùng notify=false để không bật thông báo lỗi mất mạng.
    // ===== CHỦ ĐỘNG NGẮT KẾT NỐI KHI ĐĂNG XUẤT =====
    public void disconnect() { Session active = session; if (active != null) { closeSession(active, false); } }
    // Kiểm tra có phiên TCP trong bộ nhớ; đây không phải lệnh ping để thử mạng tức thời.
    // ===== KIỂM TRA CLIENT CÓ PHIÊN TCP HIỆN TẠI HAY KHÔNG =====
    public boolean isConnected() { return session != null; }
    public String getConnectionError() { return connectionError; }
}
