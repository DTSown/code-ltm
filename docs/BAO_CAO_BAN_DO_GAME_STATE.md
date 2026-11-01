# Báo cáo phần bản đồ, di chuyển, va chạm, rương và đồng bộ GAME_STATE

## 1. Phần em phụ trách

Phần này gồm hai nhóm chức năng liên quan trực tiếp với nhau:

1. **Gameplay:** sinh bản đồ, điều khiển di chuyển, kiểm tra va chạm, nhặt rương,
   cộng điểm và sinh rương mới.
2. **Mạng:** thiết kế theo kiểu **Server authoritative**, đồng bộ toàn bộ trận bằng
   `GAME_STATE` và bảo vệ dữ liệu khi hai client thao tác đồng thời.

Mục tiêu quan trọng nhất là: **client chỉ gửi ý định, server mới là nơi quyết định
kết quả**. Client không tự sửa tọa độ, điểm, rương hoặc thời gian.

## 2. Kiến trúc tổng quát

Luồng của một lần bấm di chuyển:

```text
Người chơi bấm WASD / phím mũi tên
        |
        v
Client gửi MOVE|UP/DOWN/LEFT/RIGHT
        |
        v
ClientHandler nhận và chuyển cho ServerManager
        |
        v
ServerManager xác thực phiên đăng nhập và phòng
        |
        v
GameRoom kiểm tra biên, tường, đối thủ và trạng thái trận
        |
        +--> sai: gửi ERROR, không đổi dữ liệu
        |
        v
Cập nhật vị trí -> kiểm tra rương -> cộng điểm -> sinh rương bù
        |
        v
Server encode một GAME_STATE và gửi cùng dữ liệu cho hai client
        |
        v
Hai client decode, cập nhật HUD và vẽ lại bàn chơi
```

Các lớp chính:

| File | Trách nhiệm |
| --- | --- |
| `src/server/MapGenerator.java` | Sinh map 7x7, tường, vị trí xuất phát và 5 rương ban đầu |
| `src/server/GameRoom.java` | Giữ trạng thái phòng; xử lý di chuyển, va chạm, nhặt rương, điểm |
| `src/server/ServerManager.java` | Xác thực client, điều phối thao tác và phát trạng thái cho hai người |
| `src/common/GameStateCodec.java` | Mã hóa/giải mã snapshot `GAME_STATE` qua TCP |
| `src/model/GameState.java` | Model chứa toàn bộ trạng thái cần đồng bộ |
| `src/client/GamePanel.java` | Gửi hướng điều khiển và cập nhật giao diện từ snapshot server |
| `src/client/BoardPanel.java` | Vẽ map, tường, người chơi và rương bằng Java2D |
| `src/client/MainFrame.java` | Nhận bản tin mạng, decode rồi chuyển state cho màn hình game |

## 3. Bản đồ được sinh như thế nào

### 3.1. Quy ước dữ liệu

- Map có kích thước `7 x 7`, tổng cộng 49 ô.
- `0` là ô đường đi; `1` là vật cản.
- Ô được đổi sang một số bằng công thức `cell = row * 7 + col`.
- Ô đối xứng qua tâm được tính bằng `opposite = 48 - cell`.

### 3.2. Sinh vật cản

`MapGenerator.generate()` chọn ngẫu nhiên 4 ô ở nửa đầu bản đồ. Với mỗi ô, server
đặt thêm ô đối xứng qua tâm, vì vậy có đúng 8 vật cản. Bản đồ đối xứng làm giảm lợi
thế thiên vị giữa hai người chơi.

### 3.3. Kiểm tra bản đồ liên thông

Sau khi đặt tường, hàm `isConnected()` dùng BFS từ ô trung tâm. BFS chỉ đi bốn hướng
giống luật di chuyển thật. Bản đồ chỉ được chấp nhận khi BFS tới đủ `49 - 8 = 41`
ô trống. Nếu bị chia thành vùng kín, server bỏ map đó và sinh lại; tối đa 1000 lần.

### 3.4. Vị trí người chơi và rương

- Hai người chơi xuất phát ở hai ô đối xứng, cách nhau ít nhất 5 bước Manhattan.
- Hai rương thường `NORMAL` đối xứng nhau, mỗi rương 10 điểm.
- Hai rương bạc `SILVER` đối xứng nhau, mỗi rương 20 điểm.
- Một rương vàng `GOLD` ở tâm, có giá trị 30 điểm.
- Rương không đè lên tường/người chơi và cách vị trí xuất phát ít nhất 2 bước.

Khi tạo trận mới, server reset riêng `matchScore` và `collectedTreasure` về 0; dữ
liệu lâu dài như rank hoặc số trận thắng không bị reset.

## 4. Di chuyển và va chạm

Client tạo bốn action `UP`, `DOWN`, `LEFT`, `RIGHT`. Nút bấm, WASD và phím mũi tên
dùng chung action. Khi thao tác, client chỉ gửi:

```text
MOVE|RIGHT
```

Client **không** tự cộng/trừ hàng cột. Tại `GameRoom.handleMove()`, server thực hiện:

1. Kiểm tra phòng đã bắt đầu, trạng thái là `RUNNING` và thời gian còn lớn hơn 0.
2. Xác định người gửi có đúng là một trong hai người của phòng không.
3. Kiểm tra Player đang ở trạng thái `PLAYING` và không bị choáng.
4. Tính tọa độ dự kiến trong biến cục bộ.
5. Từ chối nếu tọa độ ra ngoài map.
6. Từ chối nếu `map[row][col] == 1` (đụng tường).
7. Từ chối nếu ô đích đang có đối thủ.
8. Chỉ sau khi qua toàn bộ kiểm tra mới gán `Position` mới.

Việc tính vào biến cục bộ trước khi gán rất quan trọng: một bước đi sai không để
lại trạng thái trung gian hoặc làm nhân vật xuyên tường.

## 5. Nhặt rương, tăng điểm và hồi sinh rương

Sau một bước hợp lệ, `ServerManager.move()` gọi ngay
`GameRoom.collectTreasure(player)` trong cùng critical section.

Server duyệt danh sách rương và so sánh cả `row` lẫn `col`. Nếu vị trí trùng:

1. Giá trị được lấy từ loại rương bằng `Treasure.valueForType()`; server không nhận
   con số điểm do client khai báo.
2. Rương cũ bị xóa khỏi danh sách.
3. Điểm được cộng 10/20/30 và số rương đã nhặt tăng 1 đối với rương thưởng.
4. Server liệt kê các ô đường còn trống, loại ô có người chơi hoặc rương khác.
5. Server chọn ngẫu nhiên một ô hợp lệ và loại rương mới, tạo ID mới rồi thêm vào.

Số rương vì thế vẫn là 5. Rương đã ăn không thể được tính lần hai vì object cũ đã
bị xóa trước khi server nhả khóa phòng.

Dự án còn có rương bẫy ngụy trang. Cờ bẫy chỉ tồn tại riêng trong `GameRoom`, không
nằm trong `GameState` và không gửi cho client. Đây cũng là một ví dụ của server
authoritative: client không biết trước và không thể tự chọn kết quả rương.

## 6. Server authoritative

`ServerManager.move()` không tin dữ liệu gameplay do client gửi. Server tự suy ra
Player từ `ClientHandler` đã đăng nhập và chỉ chấp nhận một chuỗi hướng hợp lệ.

Các bản tin sau là bản tin **chỉ server được gửi**:

- `GAME_STATE`
- `TREASURE_COLLECTED`
- `TIME_UPDATE`
- `GAME_OVER`

Nếu client giả mạo `GAME_STATE` có điểm rất lớn hoặc tự gửi
`TREASURE_COLLECTED`, `ClientHandler` trả `ERROR`; state trong phòng không đổi.

Ví dụ tấn công bị chặn:

```text
Client giả gửi: GAME_STATE|...matchScore=999999...
Server xử lý:   ERROR|Message này chỉ do Server gửi
Kết quả:        điểm thật trên Server không thay đổi
```

## 7. Đồng bộ GAME_STATE

Định dạng payload:

```text
GAME_STATE|roomId;map;player1;player2;treasures;remainingTime;status
```

Trong đó:

- `map`: đúng 49 ký tự `0` hoặc `1`, ghi lần lượt theo hàng.
- Mỗi player: `username,row,col,matchScore,collectedTreasure`.
- Mỗi rương: `id,type,value,row,col`; các rương cách nhau bằng `/`.
- `remainingTime`: số giây còn lại do server giữ.
- `status`: `WAITING`, `RUNNING` hoặc `FINISHED` tùy giai đoạn.

Ví dụ rút gọn:

```text
GAME_STATE|ROOM-1-...;000100...;Alex,0,0,10,1;Nina,6,6,0,0;T1,NORMAL,10,2,3/...;175;RUNNING
```

`GameStateCodec.encode()` tạo một snapshot từ state gốc trên server. Cùng một object
`Message` chứa snapshot đó được gửi cho cả hai client, nên hai máy nhận cùng map,
tọa độ, rương và điểm.

Ở client, `GameStateCodec.decode()` kiểm tra:

- đủ đúng 7 trường;
- map có đúng 49 số 0/1;
- tọa độ nằm trong map, không nằm trên tường và không trùng nhau;
- ID rương không trùng;
- giá trị khớp loại rương;
- điểm, số rương và thời gian không âm.

Sau khi decode thành công, `GamePanel.setGameState()` thay snapshot local và yêu
cầu `BoardPanel` vẽ lại. Giao diện không dự đoán trước bước đi, do đó trạng thái
hiển thị luôn lấy từ quyết định cuối cùng của server.

## 8. Xử lý thao tác đồng thời

Mỗi kết nối TCP chạy ở một `ClientHandler`, nên hai người có thể gửi MOVE gần như
cùng lúc. Để tránh race condition, code dùng thứ tự khóa cố định:

```text
ServerManager lock -> GameRoom lock -> ClientHandler.sendMessage lock
```

- `ServerManager.move()` là `synchronized`: xác thực phiên và tìm phòng nhất quán.
- `synchronized(room)` bao toàn bộ di chuyển, nhặt, cộng điểm, sinh rương và encode.
- Các method thay đổi dữ liệu trong `GameRoom` cũng là `synchronized`.
- `ClientHandler.sendMessage()` là `synchronized`, tránh hai luồng ghi xen ký tự
  lên cùng socket.
- Luồng timer cũng lấy khóa Manager rồi khóa Room, cùng thứ tự với MOVE, nên không
  xảy ra deadlock do đảo thứ tự khóa.

Tình huống hai người cùng lao vào một rương:

1. Luồng A lấy khóa trước, di chuyển vào ô rương, nhận điểm, xóa rương và sinh rương mới.
2. Luồng B chỉ chạy sau khi A nhả khóa.
3. B thấy ô đã có A nên bị va chạm người chơi và không thể nhận lại rương cũ.
4. Chỉ có đúng một `TREASURE_COLLECTED`; cả hai nhận snapshot theo cùng thứ tự.

Đây là tính **atomic**: chuỗi thao tác hoặc hoàn tất trọn vẹn, hoặc bị từ chối mà
không làm thay đổi một phần state.

## 9. Những đoạn code cần mở khi thuyết trình

Nên mở theo thứ tự sau để giảng viên dễ theo dõi:

1. `MapGenerator.generate()` và `isConnected()` — cách sinh map và BFS.
2. `GamePanel.createMoveButton()` — client chỉ gửi hướng.
3. `ServerManager.move()` — điểm vào authoritative, khóa và phát state.
4. `GameRoom.handleMove()` — kiểm tra biên/tường/người chơi.
5. `GameRoom.collectTreasure()` và `createReplacementTreasure()` — điểm và rương bù.
6. `GameStateCodec.encode()/decode()` — giao thức đồng bộ.
7. `MainFrame` nhánh `Protocol.GAME_STATE` và `GamePanel.setGameState()` — client nhận và vẽ.

Trong source đã có comment bắt đầu bằng `[PHẦN PHỤ TRÁCH: ...]`; có thể tìm nhanh:

```powershell
rg -n "PHẦN PHỤ TRÁCH" src
```

## 10. Kịch bản nói ngắn 3–5 phút

> Phần em phụ trách là bản đồ, di chuyển, va chạm, nhặt rương tăng điểm và đồng bộ
> trạng thái trận. Em thiết kế theo mô hình server authoritative. Client chỉ gửi
> hướng UP, DOWN, LEFT, RIGHT chứ không tự sửa tọa độ hay điểm.
>
> Bản đồ 7x7 được sinh hoàn toàn ở server, gồm 8 vật cản đối xứng. Sau khi sinh,
> em dùng BFS kiểm tra 41 ô đường đều liên thông để không tạo vùng bị kẹt. Hai người
> xuất phát đối xứng và có 5 rương gồm hai thường, hai bạc, một vàng.
>
> Khi server nhận MOVE, server xác thực người gửi, tính ô dự kiến rồi kiểm tra biên,
> tường và ô đối thủ. Chỉ bước hợp lệ mới cập nhật Position. Sau đó server kiểm tra
> nhặt rương, tự lấy giá trị chuẩn 10, 20 hoặc 30, cộng điểm, xóa rương cũ và sinh
> rương mới tại một ô trống hợp lệ.
>
> Toàn bộ bước di chuyển, nhặt rương, cộng điểm, sinh rương và tạo snapshot nằm
> trong cùng khóa phòng. Vì vậy nếu hai client thao tác đồng thời, server xử lý lần
> lượt và một rương chỉ được tính cho một người. Luồng timer cũng dùng cùng thứ tự
> khóa nên dữ liệu không bị race condition.
>
> Cuối cùng server encode toàn bộ map, hai player, rương, thời gian và trạng thái
> thành GAME_STATE rồi gửi cùng payload cho hai client. Client decode, kiểm tra dữ
> liệu và chỉ render snapshot này. Do đó sửa client để tự tăng điểm cũng không làm
> thay đổi dữ liệu thật trên server.

## 11. Kịch bản demo trực tiếp

### Chuẩn bị

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1
```

Mở ba terminal:

```powershell
# Terminal 1
java -jar dist\TreasureRushServer.jar

# Terminal 2 và 3
java -jar dist\TreasureRushClient.jar
```

Hai client kết nối `127.0.0.1`, port `5000`, đăng nhập hai tài khoản khác nhau.

### Thứ tự demo

1. Mời đấu và chỉ ra hai client có cùng map/rương.
2. Bấm vào tường hoặc biên để chứng minh tọa độ không đổi.
3. Cho hai nhân vật tiến vào nhau để demo va chạm người chơi.
4. Nhặt ba loại rương để thấy điểm tăng 10/20/30 và rương mới xuất hiện.
5. Di chuyển trên một client và chỉ ra client còn lại cập nhật từ `GAME_STATE`.
6. Nếu có thời gian, chạy test TCP để chứng minh thao tác đồng thời và chống giả mạo.

## 12. Kiểm thử và bằng chứng

Kết quả kiểm tra ngày **01/11/2026**: build thành công hai JAR và
**19/19 regression tests PASS**.

Chạy toàn bộ regression:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\test.ps1
```

Các bộ test liên quan trực tiếp:

- `MapGenerationTest`: 1000 seed, số tường, liên thông BFS, spawn, rương và codec.
- `TreasureCollectionTest`: điểm chính xác, rương hồi sinh, ID mới, không ăn hai lần.
- `MoveIntegrationTest`: TCP thật, MOVE hợp lệ/sai, va chạm và snapshot giống nhau.
- `MoveIntegrationTest` còn cho hai thread gửi đồng thời vào một rương, kiểm tra chỉ
  một người được +30 và chỉ một event được phát.
- Test giả gửi `TREASURE_COLLECTED`, `GAME_STATE` và `SCORE=999999`, kiểm tra state
  server hoàn toàn không đổi.
- `GameTimerIntegrationTest`: timer và MOVE dùng chung state an toàn.
- `GamePanelRenderTest`: phím/nút chỉ gửi MOVE, render từ state server.

## 13. Câu hỏi phản biện thường gặp

### Tại sao không cho client tự cập nhật vị trí để mượt hơn?

Vì phạm vi bài dùng snapshot authoritative và bàn 7x7 nên ưu tiên nhất quán/chống
gian lận. Client-side prediction có thể thêm sau, nhưng vẫn phải rollback theo server.

### Tại sao cần BFS sau khi sinh tường?

Đối xứng không bảo đảm bản đồ liên thông. BFS loại map có vùng kín, bảo đảm mọi ô
đường và rương đều có thể tiếp cận bằng luật đi bốn hướng.

### Vì sao dùng `synchronized` hai cấp?

Manager bảo vệ quan hệ phiên–người chơi–phòng ở cấp toàn server; Room bảo vệ dữ liệu
một trận. Critical section của Room giúp MOVE, nhặt, điểm, respawn và snapshot là
một thao tác nguyên tử. Thứ tự khóa luôn cố định để tránh deadlock.

### Nếu hai người cùng nhặt một rương thì sao?

Khóa phòng làm hai request chạy tuần tự. Người đầu tiên xóa rương và chiếm ô; người
thứ hai thấy va chạm hoặc không còn rương đó, nên không được cộng điểm.

### Client sửa gói tin điểm thì sao?

Server không có API nhận điểm. Client chỉ gửi hướng; điểm được server suy ra từ
loại rương đang nằm ở vị trí thật. Các message state/kết quả giả mạo bị trả `ERROR`.

### Hai client có thể nhận state khác nhau không?

Server encode một payload sau khi cập nhật xong, rồi gửi chính payload đó cho cả
hai trong cùng critical section. Mỗi socket còn khóa riêng để bản tin không ghi xen.

## 14. Hạn chế và hướng phát triển

- `synchronized` ở `ServerManager` đơn giản và an toàn cho bài tập, nhưng khóa chung
  có thể hạn chế số phòng khi hệ thống rất lớn. Có thể tách room executor hoặc lock
  theo room để tăng khả năng mở rộng.
- TCP hiện chưa có sequence number cho mỗi `GAME_STATE`; có thể bổ sung `stateVersion`
  để client phát hiện snapshot trùng/cũ khi giao thức phức tạp hơn.
- Client chưa có prediction/interpolation; có thể thêm để mượt trên Internet, nhưng
  server vẫn phải là nguồn dữ liệu cuối cùng.
- Trò chơi hiện phù hợp LAN, chưa có TLS và heartbeat đầy đủ cho môi trường production.

## 15. Kết luận

Phần cài đặt đạt ba yêu cầu cốt lõi: **đúng luật chơi**, **không tin client** và
**nhất quán khi đồng thời**. Bản đồ được kiểm tra liên thông, mọi bước đi/va chạm/
điểm/rương do server quyết định, còn `GAME_STATE` là snapshot duy nhất để hai client
hiển thị cùng một trận đấu.
