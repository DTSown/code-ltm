# MySQL cho Treasure Rush

## Cấu hình trên máy hiện tại

| Thuộc tính | Giá trị |
| --- | --- |
| MySQL Server | 9.6 |
| Host / port | `127.0.0.1:3306` |
| Database | `bt` |
| User / password | `root` / để trống |
| Cấu hình Java | `config/database.properties` |
| Dữ liệu MySQL cục bộ | `data/mysql-local` |
| Log MySQL | `data/mysql-local/server.log` |

MySQL được khởi chạy bằng chương trình đã cài tại
`C:\Program Files\MySQL\MySQL Server 9.6\bin\mysqld.exe`. Instance này chỉ nghe
trên `127.0.0.1`. Đây là tiến trình cục bộ, chưa đăng ký Windows service để tự chạy
khi bật máy. Máy Client chỉ kết nối game server TCP, không kết nối MySQL trực tiếp.

## Chạy lại dự án

Mở terminal tại thư mục dự án:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\start-mysql-local.ps1
java -jar dist/TreasureRushServer.jar
```

Mở terminal khác để chạy Client:

```powershell
java -jar dist/TreasureRushClient.jar
```

Script khởi động MySQL kiểm tra cổng 3306 trước; nếu đã có tiến trình nghe, script
không tạo thêm instance. Nếu MySQL đã được cài và chạy theo cách khác, chỉ cần sửa
cấu hình kết nối; không cần dùng script instance cục bộ.

Trong MySQL Workbench, mở kết nối `127.0.0.1:3306` với user `root`, để trống mật
khẩu. Nhấn Refresh ở **SCHEMAS**, mở database **bt**, mục **Tables**.

```sql
USE bt;
SHOW TABLES;
SELECT username, rank_points, wins, total_treasures, total_matches FROM accounts;
SELECT * FROM matches;
```

## Cấu trúc dữ liệu

| Bảng | Khóa chính | Nội dung |
| --- | --- | --- |
| `accounts` | `username` | `password_plaintext`, `rank_points`, `wins`, `total_treasures`, `total_matches`; salt/hash chỉ giữ tạm khi còn tài khoản cũ chưa chuyển mật khẩu |
| `match_details` | `room_id` | Bảng nội bộ giữ đầy đủ người chơi, điểm, rương, rank, kết quả và thời gian để lịch sử game tiếp tục hoạt động |
| `matches` (view) | — | Chỉ 5 cột: `ten_phong`, `nguoi_tham_gia`, `diem_rank`, `ruong_da_an`, `nguoi_thang` |

`match_details.player1` và `match_details.player2` tham chiếu `accounts.username` bằng khóa
ngoại. `username` tối đa 255 ký tự; tên có phân biệt chữ hoa/thường. Hai bảng dùng
InnoDB và utf8mb4. `ended_at_epoch_millis` là thời gian Unix tính bằng mili giây.

Trong Workbench, xem **SCHEMAS → bt → Views → matches**, hoặc chạy
`SELECT * FROM bt.matches;`. Tên phòng hiển thị `Phòng 1`, `Phòng 2`, ...; hai
phòng chỉ có UUID từ phiên bản cũ hiển thị `Phòng cũ 1`, `Phòng cũ 2`. `room_id`
là định danh nội bộ, không phải dữ liệu mã hóa, và không xuất hiện trong view.
Người thắng hiển thị tên tài khoản hoặc `Hòa`. Rank và rương ghi kèm tên từng
người, ví dụ `vinhdc: +3 / test: +0` và `vinhdc: 50 / test: 10`. `diem_rank` là
điểm hạng được cộng trong trận, không phải tổng điểm trọn đời hay thứ tự xếp hạng.
View tự cập nhật khi có trận mới, không cần chép dữ liệu thủ công.

Server đọc dữ liệu vào RAM lúc khởi động. Đăng ký hoặc kết thúc trận sẽ ghi MySQL
bằng prepared statement trong transaction. Thống kê và lịch sử được commit cùng
nhau. Bảng xếp hạng vẫn sắp xếp bằng Java. Nếu sửa dữ liệu thủ công bằng Workbench,
cần tắt game server trước và khởi động lại sau khi sửa.

## Hiển thị mật khẩu trong MySQL để demo

Theo cấu hình bài demo hiện tại, cột `accounts.password_plaintext` lưu mật khẩu
dạng rõ. Người xem bảng hoặc bản sao lưu có thể đọc được cột này.

- Tài khoản mới: ghi mật khẩu rõ cùng transaction đăng ký tài khoản.
- Tài khoản cũ: ban đầu là `NULL`; sau lần đăng nhập đúng mật khẩu tiếp theo,
  server bổ sung mật khẩu vào cột này. Đăng nhập sai không ghi dữ liệu.
- Trong thời gian chuyển đổi, tài khoản cũ vẫn được xác thực bằng salt/hash gốc.
  Sau khi toàn bộ mật khẩu rõ đã được đối chiếu đúng với hash, chương trình tự
  xóa hai cột `salt` và `password_hash` khỏi MySQL. Nếu thiếu hoặc có mật khẩu rõ
  không khớp, chương trình giữ nguyên hai cột và chờ lần đăng nhập đúng tiếp theo.
- Sau khi xóa hai cột, server nạp mật khẩu rõ từ MySQL và chỉ tạo hash tạm trong
  RAM để kiểm tra đăng nhập. Không tạo lại cột salt/hash khi đăng ký hay lưu trận.
  Khi đó sửa mật khẩu rõ trong MySQL sẽ đổi mật khẩu dùng sau lần restart server.
- Cột này không được đưa vào TSV, log hay phản hồi bảng xếp hạng/lịch sử cho Client.
  Các lần lưu kết quả trận không xóa giá trị đã bổ sung.

Database mới không có salt/hash. Nhập TSV cũ sẽ thêm tạm hai cột để giữ mật khẩu
cũ cho tới khi mọi tài khoản đã đăng nhập lại. Không tự đặt hoặc đoán mật khẩu.
Để hoàn tất trên database hiện tại: restart game server bằng JAR mới, đăng nhập
lần lượt các tài khoản còn NULL (có thể đăng xuất rồi đổi tài khoản). Sau lần đăng
nhập cuối cùng, Refresh bảng `accounts` để thấy hai cột salt/hash được xóa.

Server tự cập nhật cấu trúc khi khởi động. Bảng `matches` cũ được đổi tên thành
`match_details`; view `matches` được tạo thay thế nên chi tiết trận không mất.
Có thể cập nhật cấu trúc ngay bằng
`java -cp dist/TreasureRushServer.jar server.DatabaseSetup` sau khi build, không
cần nhập lại TSV. Sau khi cập nhật JAR, khởi động lại game server để dùng luồng
đăng nhập mới, đăng nhập bằng tài khoản cần xem rồi chạy trong Workbench:

```sql
SELECT username, password_plaintext
FROM bt.accounts;
```

`NULL` nghĩa là tài khoản đó chưa đăng nhập thành công kể từ khi bật tính năng
demo. Không thể giải ngược hash cũ để tự điền mật khẩu. Bản demo này lưu mật khẩu
rõ theo yêu cầu demo.

## Thiết lập trên máy mới hoặc nhập TSV

Cần JDK 17+, MySQL Server 8.0.19+ và quyền tạo database/bảng. Sao chép
`config/database.example.properties` thành `config/database.properties` rồi chỉnh
host, port, database, user, password. File cấu hình thật được bỏ qua trong Git.
Có thể dùng biến môi trường `TREASURE_DB_STORAGE`, `TREASURE_DB_HOST`,
`TREASURE_DB_PORT`, `TREASURE_DB_DATABASE`, `TREASURE_DB_USER`, `TREASURE_DB_PASSWORD`,
`TREASURE_DB_SSLMODE` để ghi đè cấu hình file.

Tạo database/bảng và kiểm tra kết nối:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\setup-mysql.ps1
```

Để nhập dữ liệu cũ, chỉ chạy lệnh sau **một lần khi hai bảng còn trống**, với game
server đã tắt:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\setup-mysql.ps1 -ImportTsv
```

Script tải Connector/J 9.7.0 từ Maven Central, kiểm tra SHA-256, build JAR, tạo bảng
và nhập `data/server-data.tsv`. Nếu đã có bản ghi trong MySQL, thao tác nhập bị từ
chối. Salt/hash mật khẩu, tổng điểm và lịch sử được giữ nguyên. File TSV gốc vẫn
còn nhưng không được cập nhật khi chọn MySQL. Không nhập lại TSV cũ sau khi đã chơi
thêm trận trong MySQL.

## Đóng gói và kiểm thử

`build.ps1` dùng JDBC chuẩn trong JDK để compile. Khi có driver trong `lib`, build
sao chép driver sang `dist/lib` và thêm Class-Path vào manifest Server JAR. Khi gửi
Server sang máy khác, gửi cả `dist/lib` và cấu hình kết nối. Client vẫn chỉ cần JAR.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\test.ps1 -MySql
```

Lệnh `-MySql` chạy các bộ kiểm thử map, vòng đời trận, lưu trữ TSV và MySQL;
không chạy các bộ UI/bẫy. Kiểm thử MySQL dùng database riêng tên `treasure_test_<uuid>` và xóa database test
sau khi hoàn tất; không ghi vào database `bt`. Kiểm thử bao gồm nhập TSV, giữ nguyên
hash/thống kê/lịch sử, khóa ngoại và rollback, Unicode, phân biệt hoa/thường, đăng
ký/đăng nhập qua TCP, lưu kết quả trận và nạp lại sau khi khởi động lại server.

## Sao lưu

Cần giữ cả thư mục `data/mysql-local` nếu dùng instance cục bộ. TSV cũ chỉ chứa dữ
liệu trước khi chuyển đổi. Có thể xuất database bằng **Server → Data Export** trong
Workbench; nên xuất `bt` trước khi chuyển máy hoặc thay đổi cấu trúc bảng.

Tài liệu JDBC: [MySQL Connector/J](https://dev.mysql.com/doc/connector-j/en/),
[DriverManager](https://dev.mysql.com/doc/connector-j/en/connector-j-usagenotes-connect-drivermanager.html).
