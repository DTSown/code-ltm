# Final Review - Prompt 15

Historical review of the pre-trap release. For the current audio/trap upgrade,
see [UPGRADE_VERIFICATION.md](UPGRADE_VERIFICATION.md) and
[IMPLEMENTATION_CHECKLIST.md](../IMPLEMENTATION_CHECKLIST.md).

## Phạm Vi

Giữ project và giao thức hiện tại; không thêm gameplay hay viết lại các màn hình.
Đã rà soát luồng tài khoản, sảnh, mời đấu, game, timer, kết quả, tái đấu, ranking/history/persistence.

## Các Sửa Lỗi Cuối

- Bỏ IP loopback mặc định và API connect localhost không dùng; IP/port lấy từ form.
- TCP connect timeout 5 giây, phản hồi lỗi kết nối dễ đọc, callback disconnect trên EDT.
- Receiver giữ socket riêng để không đóng nhầm kết nối mới; logout không báo lỗi mất mạng.
- Đổi endpoint sau register sẽ kết nối đúng endpoint mới, không dùng socket cũ.
- Chặn ký tự phân cách trong username đăng ký và xuống dòng trong credentials.
- Cleanup login dựa trên Player, không dựa vào chuỗi UNKNOWN.
- Không log payload/password; log sự kiện ngắn và không gửi lại vào handler đã đóng.
- Server tự tạo file data lần đầu, dùng đường dẫn tương đối, không đưa data vào Client.jar.
- Hai JAR tách package runtime, manifest main riêng, bytecode Java 17.

## Bằng Chứng Kiểm Thử

- 13/13 regression suites PASS, gồm 12 bộ test cũ và ClientConnectionTest mới.
- JarGuiIntegrationTest PASS với Server.jar và hai Client.jar chạy bằng java -jar.
- Hai Client trên cùng máy kết nối qua IPv4 LAN 192.168.0.29:5000, không qua loopback.
- Đăng ký/login, thấy nhau, reject/accept, cùng state, WASD/nút MOVE, ăn rương/respawn,
  timer giảm, Exit và Result, room tái đấu mới, disconnect xử thua, ranking/history.
- Server tắt đột ngột: Client báo lỗi và về Login; restart giữ account/rank/history.
- Thư mục client-test chỉ có TreasureRushClient.jar; khởi chạy thuần không agent cũng thành công.
- Kiểm tra manifest, Java 17 bytecode, whitelist package, không có server/test/data trong Client.jar.
- Render tests kiểm tra UTF-8, nhiều kích thước và pixel; ảnh GUI thật nằm trong out/jar-test-*.

Tổng: 14/14 bộ test PASS. Số này là số chương trình test, không phải số assertion hay seed.

## Giới Hạn

Chưa có máy vật lý thứ hai để xác nhận Firewall/router của LAN thực tế.
TCP không heartbeat nên mất mạng im lặng phụ thuộc thời gian phát hiện của hệ điều hành.
Không reconnect tự động/TLS; SHA-256 + salt dùng cho bài tập, chưa phải KDF production.
Server tắt đột ngột không khôi phục trận đang chạy; dữ liệu các trận đã hoàn tất vẫn được giữ.
