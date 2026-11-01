# Treasure Rush - Đua Săn Kho Báu Online

Java Swing + TCP Socket, Server đa luồng. Hỗ trợ lưu trữ MySQL qua JDBC hoặc file TSV.
Cần Java 17 trở lên để chạy; cần **JDK 17+** (có `java`, `javac`) để build.
Build dùng API JAR chuẩn của Java, không yêu cầu lệnh `jar` có trong PATH.

## Build Và Test

Trong thư mục project, chạy `build.bat` hoặc:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\test.ps1
```

Output: `dist/TreasureRushServer.jar`, `dist/TreasureRushClient.jar`.
Khi chỉ sửa Client và Server đang chạy, build riêng Client bằng
`powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1 -ClientOnly`.
Đóng rồi mở lại Client để dùng JAR mới.

Kiểm thử riêng âm thanh (bao gồm luồng đăng nhập → sảnh → trận → kết quả trên
Client JAR vừa build, cửa sổ test được giữ ẩn):

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\test-audio.ps1
```

Test GUI JAR thật, tự mở hai Client và một Server riêng, dùng dữ liệu test trong `out/jar-test-*`:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\test.ps1 -JarGui
# Để test qua IPv4 LAN của chính máy Server, thay IP bên dưới bằng IP hiện tại:
powershell -NoProfile -ExecutionPolicy Bypass -File .\test.ps1 -JarGui -ServerIp 192.168.1.10
# Nếu đã có Server khác đang dùng cổng 5000, chạy test GUI trên cổng riêng:
powershell -NoProfile -ExecutionPolicy Bypass -File .\test.ps1 -JarGui -ServerPort 5101
```

Test agent chỉ dùng để điều khiển Swing trong kiểm thử, không nằm trong hai JAR phát hành.
Các thư mục `out/build-*` là class trung gian, không cần gửi cho người chơi.

## Máy Server

1. Mở terminal tại thư mục muốn lưu dữ liệu và chạy:
   ```powershell
   java -jar dist/TreasureRushServer.jar
   ```
2. Nếu Windows Firewall hỏi, cho phép Java trên mạng **Private** đáng tin cậy; không tắt Firewall.
   Nếu cần cấu hình thủ công, cho phép TCP inbound port **5000** trên profile Private.
3. Chạy `ipconfig`, lấy IPv4 của adapter Wi-Fi/Ethernet đang dùng, ví dụ `192.168.1.10`.
4. Muốn chơi trên máy Server, mở thêm:
   ```powershell
   java -jar dist/TreasureRushClient.jar
   ```
   Nhập IPv4 máy Server và port `5000`.

Server bind mọi interface, không giới hạn loopback. Chỉ chạy một Server trên port 5000.
Có thể chọn cổng khác bằng `java -Dtreasure.server.port=5101 -jar dist/TreasureRushServer.jar`;
Client phải nhập cùng cổng đó. Nếu không truyền tùy chọn, cổng vẫn là 5000.
Với `config/database.properties` có `storage=mysql`, tài khoản, salt/hash SHA-256,
thống kê và lịch sử được lưu trong MySQL. Cấu hình hiện tại: `127.0.0.1:3306`,
database `bt`, user `root`, mật khẩu trống. Xem [hướng dẫn MySQL](docs/MYSQL.md).
Server JAR cần đi kèm `dist/lib/mysql-connector-j-9.7.0.jar`.
Theo yêu cầu demo, MySQL lưu mật khẩu rõ trong `accounts.password_plaintext`.
Salt/hash của tài khoản cũ chỉ giữ tạm cho đến khi tất cả tài khoản đã đăng nhập
đúng mật khẩu; chương trình tự xóa hai cột sau khi đối chiếu đủ mật khẩu gốc.
Mục `matches` là view chỉ hiển thị 5 cột dễ đọc; chi tiết trận nằm ở `match_details`.
Xem [hướng dẫn MySQL](docs/MYSQL.md) để xem phòng và hoàn tất chuyển mật khẩu cũ.

Nếu không có cấu hình hoặc chọn `storage=tsv`, dữ liệu tự tạo/load tại
`data/server-data.tsv`, **tương đối với thư mục terminal chạy Server**.
MySQL bị lỗi sẽ được báo lỗi, không tự chuyển sang TSV. File TSV cũ được giữ lại
để tham chiếu sau khi nhập dữ liệu, không tiếp tục cập nhật khi dùng MySQL.

## Máy Người Chơi Khác

1. Chỉ nhận `TreasureRushClient.jar`, đặt trong thư mục bất kỳ.
2. Chạy `java -jar TreasureRushClient.jar`.
3. Nhập IPv4 máy Server, ví dụ `192.168.1.10`, port `5000`.
4. Nhập username/password, đăng ký, rồi đăng nhập.

Client không cần `src`, `out`, `docs`, Server.jar hoặc file dữ liệu tài khoản.
Hai máy cùng Wi-Fi/LAN dùng IPv4 nội bộ. Không nhập `127.0.0.1` trên máy thứ hai:
địa chỉ đó chỉ trỏ về chính máy đang chạy Client. Không cần mở port inbound cho Client.
Wi-Fi guest/client isolation có thể chặn các máy nhìn thấy nhau.

## Chơi Và Lỗi Mạng

Sảnh → chọn đối thủ FREE → Mời thi đấu → đối thủ chấp nhận/từ chối.
WASD/phím mũi tên gửi MOVE lên Server. Trận 180 giây; thoát hoặc mất kết nối bị xử thua.
Kết quả → Chơi tiếp hoặc Về sảnh; Ranking/History lấy dữ liệu Server.

## Rương Bẫy Và Âm Thanh

Mỗi trận bắt đầu với đúng 5 rương, trong đó có 1 rương bẫy ngẫu nhiên. Rương
hồi sinh có xác suất 20% là bẫy. Bẫy ngụy trang giống hệt rương thường/bạc/vàng:
không có dấu hiệu trong hình vẽ, tooltip hoặc dữ liệu GAME_STATE.

Rương thưởng cộng 10/20/30 điểm như trước. Rương bẫy không cộng thưởng và không
tính vào số rương đã thu thập; nó trừ tối đa 10 điểm (không xuống dưới 0) và
gây choáng 2000 ms. Server chặn MOVE bằng đồng hồ monotonic; hết hạn tự đi lại
được. Cả hai Client nhìn thấy vòng sao vàng quay và thông báo, trong khi đối thủ
và đồng hồ trận vẫn hoạt động. Nút thoát trận vẫn dùng được khi đang choáng.

Nhạc sảnh fantasy tự phát sau đăng nhập, tiếp tục khi chờ lời mời hoặc xem thống kê.
Vào trận chuyển sang nhạc phiêu lưu; hết trận hoặc về sảnh chuyển lại nhạc sảnh.
Chỉ có một nhạc nền chạy; các bản tin cập nhật không khởi động lại nhạc.
Về đăng nhập/mất kết nối dừng nhạc, đóng ứng dụng giải phóng âm thanh.
Nút loa có ở đăng nhập, sảnh và bên cạnh đồng hồ trận đấu, bật/tắt cả nhạc lẫn
hiệu ứng riêng trên Client đó. Bật lại sẽ phát đúng nhạc của màn hình hiện tại.
Lựa chọn được giữ trong phiên đang mở, chưa lưu qua lần khởi động ứng dụng.

Nút chức năng có tiếng click nhẹ; nút bị vô hiệu hóa và thao tác di chuyển không
phát click. Rương thường/bạc và vàng có tiếng thưởng riêng, chỉ người nhặt nghe.
Bẫy chỉ phát tiếng choáng một lần cùng vòng sao, không phát tiếng thưởng. Có cả
tiếng thắng/thua. Nếu không có thiết bị âm thanh, game tiếp tục chạy bình thường.

Mười một WAV PCM thật được tự tổng hợp bằng `tools/GenerateAudio.java`, nằm trong
`resources/audio` và được đóng gói trực tiếp vào Client.jar. Máy khác chỉ cần
Client.jar, không cần thư mục audio bên ngoài hay thư viện bổ sung. Xem
[âm thanh và bẫy](docs/AUDIO_AND_TRAPS.md), [giao thức](docs/GAME_STATE.md) và
[checklist triển khai](IMPLEMENTATION_CHECKLIST.md) và
[checklist âm thanh](AUDIO_IMPLEMENTATION_CHECKLIST.md).

Thử nhanh trên một máy: mở ba terminal tại thư mục project, chạy Server một lần
và Client hai lần:

```powershell
java -jar dist/TreasureRushServer.jar
# Terminal 2 và terminal 3, mỗi cửa sổ chạy lệnh sau:
java -jar dist/TreasureRushClient.jar
```

Cả hai Client nhập `127.0.0.1`, port `5000`, đăng ký hai tài khoản khác nhau,
đăng nhập rồi mời đấu. Mở các rương ban đầu để gặp bẫy; kiểm tra vòng sao trên
cả hai cửa sổ, thử di chuyển trong/sau 2 giây, thử nút loa, thoát và tái đấu.
Với hai máy khác nhau, Client nhập IPv4 LAN của máy Server như hướng dẫn ở trên.

Sai IP/port hoặc Server chưa chạy: kiểm tra IP, port 5000, Firewall và Server console.
Server tắt/mất socket: Client báo mất kết nối, dừng điều khiển và về Login; đăng nhập lại sau khi Server hoạt động.
Không có reconnect tự động hay TLS cho kết nối TCP giữa Client và Server.
SHA-256 + salt phù hợp bản bài tập, chưa thay thế KDF mật khẩu cho production.
Username không chứa `,`, `;`, `|`, tab hoặc xuống dòng; mật khẩu không chứa xuống dòng.
TCP không có heartbeat: socket EOF/error được xử lý ngay, nhưng mất mạng kiểu im lặng có thể cần hệ điều hành phát hiện.
Kết nối Internet ngoài LAN cần cấu hình mạng/NAT riêng, chưa thuộc phạm vi bài tập này.

## Kết Quả Rà Soát Cuối

Runner có 18 bộ regression: 14 bộ cũ và 4 bộ mới về logic bẫy, TCP bẫy,
Client/hoạt ảnh và tài nguyên âm thanh. `-JarGui` chạy thêm bộ kiểm thử Server JAR
với hai Client JAR, bao gồm mở đủ 5 rương ban đầu và chụp ảnh khi trúng bẫy.
Kết quả thực thi và giới hạn kiểm chứng của bản nâng cấp được ghi trong
[báo cáo kiểm thử](docs/UPGRADE_VERIFICATION.md). Ảnh và log nằm trong `out`.
