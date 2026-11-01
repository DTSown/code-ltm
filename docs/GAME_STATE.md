# GAME_STATE wire format

One UTF-8 line, using the existing TYPE|DATA protocol:

```text
GAME_STATE|roomId;map;player1;player2;treasures;remainingTime;status
```

- `roomId`: `ROOM-number-UUID` for Server-created rooms. The UUID keeps the ID
  unique; Clients display only `Phong number`. Old UUID-only IDs still parse.
  Numbers increase within a Server run and resume after the largest saved number.
- `map`: exactly 49 digits, each `0` (road) or `1` (obstacle), row by row.
- Each player: `username,row,col,matchScore,collectedTreasure`.
- `username`: URL-encoded UTF-8, decoded with URLDecoder. Separators and Vietnamese names are preserved.
- Each treasure: `id,type,value,row,col`; treasure entries are separated by `/`.
- Treasure types/values: `NORMAL/10`, `SILVER/20`, `GOLD/30`.
- Coordinates: zero-based, row and column from 0 to 6.
- `remainingTime`: server-owned seconds, initially `180` in normal matches.
- `status`: initially `RUNNING`.
- Both player scores and collection counts initially equal zero.
- An empty treasure section is supported; there are five treasures at match start.

ServerManager sends GAME_START first, then encodes the room state once and sends
the exact same GAME_STATE payload to both players. Clients only decode and render.

# Generation

The Server places four random obstacle pairs related by a 180-degree rotation:
`(row, col)` maps to `(6-row, 6-col)`. Eight obstacles occupy 16.3% of the board.
The center stays open. BFS from the center must visit all 41 walkable cells;
otherwise the Server generates another map.

Player 1 spawns at a random open cell with Manhattan distance at least 5 from
its rotated cell. Player 2 spawns at that rotated cell. On this odd-sized board,
the actual minimum separation is 6.

Two NORMAL treasures form one rotated pair, two SILVER treasures form another,
and GOLD occupies the center. Treasure cells cannot overlap obstacles or players;
all treasures are at least two Manhattan steps from either spawn. Rotation
preserves shortest-path distances, so both players have equal aggregate distance
to treasures of each type, and equal distance to GOLD.

Generation retries are capped at 1000. Initialization failure does not insert a
room or change either player's online status.

# Movement

Clients send only `MOVE|UP`, `MOVE|DOWN`, `MOVE|LEFT` or `MOVE|RIGHT`.
ServerManager derives the player from the logged-in ClientHandler, checks PLAYING
and room membership, then GameRoom checks RUNNING, bounds, obstacles and collision
with the other player. Invalid movement sends ERROR without changing the state,
except MOVE during a trap stun, which is silently ignored to avoid repeated errors.

Movement, serialization and delivery to both room participants occur under the
same room lock. Each valid move produces one payload shared by both clients,
and concurrent moves produce snapshots in the same order on both connections.
ServerManager also uses its existing synchronized methods for player/room lookup.

After valid movement, the Server collects a treasure at the new position and
respawns it before sending GAME_STATE. No treasure in the transmitted state can
overlap either player, an obstacle or another treasure. Movement does not reduce time;
the Server's timer does.

Swing uses window-level WASD/arrow key bindings and four arrow buttons. Input
only sends a direction; rendering and positions change only upon GAME_STATE.

# Collection and respawn

The entire valid move, collection, score/count update, respawn and message delivery
runs under the existing room lock. For a reward chest, the moving player's score increases according
to the treasure type: NORMAL = 10, SILVER = 20, GOLD = 30. The collection count
increases by one. The old treasure is removed and replaced immediately, keeping
exactly five treasures throughout normal play.

Respawn samples a random type and one random cell from the list of all free road
cells, excluding both players and all remaining treasures. Each replacement has
a new UUID-based id. There is no client-side random generation or score update.

Both room participants receive these messages in order:

```text
TREASURE_COLLECTED|username;type;value;totalScore
GAME_STATE|roomId;map;player1;player2;treasures;remainingTime;status
```

The collection message displays a nonblocking notice. Only the following state
updates the displayed scores, counts and treasure locations. If two players try
to enter the same treasure cell concurrently, the first valid move collects it;
the other move fails player collision and cannot collect the removed chest.

Client-sent TREASURE_COLLECTED, TRAP_TRIGGERED and GAME_STATE messages return ERROR without
changing game state. No incoming score payload is accepted.

# Disguised traps

Exactly one of the five initial chests is uniformly selected as a trap after
MapGenerator finishes. The symmetric map, spawn, chest positions and displayed
types/values are unchanged. Every replacement independently has probability
0.20 of being a trap, independent of NORMAL/SILVER/GOLD appearance.

Trap membership is an identity set of Treasure objects private to GameRoom.
Neither Treasure, Player, GameState nor the seven-field GAME_STATE codec contains
trap metadata. IDs, values, artwork and tooltips cannot reveal which chest is a
trap before opening. The server never sends its random seed or trap set.

Opening a trap consumes and replaces it once, awards no points and no collected
count, and subtracts min(10, current score). Scores remain nonnegative, preserving
GameStateCodec, GameResult, ranking and persistence constraints. The server records
System.nanoTime() and rejects movement while elapsed time is less than
2,000,000,000 ns. At that boundary, the next valid MOVE succeeds without any
unlock request or scheduled sleep. The other player, match timer and EXIT_GAME
continue normally. Room finish clears private stun/event/trap state.

Both connections receive the same event followed by the same state under the
existing Manager -> Room lock order:

```text
TRAP_TRIGGERED|roomId;eventId;encodedUsername;pointsLost;totalScore;stunMillis
GAME_STATE|roomId;map;player1;player2;treasures;remainingTime;status
```

eventId is a positive increasing sequence scoped to the room. Username is
URL-encoded UTF-8; pointsLost is 0..10, totalScore is nonnegative, stunMillis is
2000 for server-generated events. Example for a player with five points:

```text
TRAP_TRIGGERED|ROOM-1-example;1;Alex;5;0;2000
```

Client accepts events only for an active matching room and participant, and
ignores repeated/older sequence numbers. It plays the trap effect and displays
one nonmodal notice, using the actual deduction. Points and positions are still
updated only by GAME_STATE. A 33 ms Swing timer animates four stars and refreshes
local movement availability. Each client measures the event duration from receipt
with its own monotonic clock; the small difference is TCP/EDT delivery latency,
not a cross-machine wall-clock assumption. Server validation remains authoritative.
GAME_STATE and TIME_UPDATE never restart this duration. Finish, disconnect,
login, disposal and new match clear the presentation state.

# Timer and results

Lifetime statistics, Ranking/History protocols and disk persistence are documented
in [STATISTICS.md](STATISTICS.md).

Each ServerManager owns one ScheduledThreadPoolExecutor shared by its rooms.
Each accepted room stores one ScheduledFuture scheduled at a one-second fixed
rate after both initial GAME_STATE messages are flushed. Normal duration is 180
seconds; tests can construct ServerManager with a shorter positive duration.

The callback acquires ServerManager, then GameRoom, matching MOVE's lock order.
Each tick decrements remainingTime, clamped at zero, and sends both participants:

```text
TIME_UPDATE|remainingTime
```

Clients display the received number as MM:SS, e.g. 03:00, 02:59, 00:00. There is
no client countdown or client decision about the winner. At zero the Server
creates an immutable GameResult once, marks the state FINISHED and cancels the
room's future. MOVE and collection are rejected once time is zero or state is
FINISHED.

The higher matchScore wins. WIN adds 3 totalRankPoint and one win; DRAW adds 1
rank point to each player, with no win; LOSE adds 0. Treasure scores are never
copied into rank points. The result snapshots scores/counts so a later match's
reset cannot change the previous result.

Wire format, eleven semicolon-separated fields (both Client and Server must use this codec):

```text
GAME_OVER|roomId;player1;player2;winner;score1;score2;collected1;collected2;rankGain1;rankGain2;reason
```

Player names are URL-encoded UTF-8, like GAME_STATE. Winner is the role token
`P1`, `P2` or `DRAW`; this avoids ambiguity when a username itself is `DRAW`.
Example:

```text
GAME_OVER|room-123;Alex;Nina;P1;70;40;4;2;3;0;TIME_UP
```

At expiry, both connections receive TIME_UPDATE|0, a final FINISHED GAME_STATE,
and identical GAME_OVER data. ServerManager writes/flushes GAME_OVER before
returning both players to FREE, removing the active room and broadcasting the
updated ONLINE_LIST. A disconnected participant cannot receive a flush.
Messages from an old room never go to a different Player session that
logged in later with the same username.

ResultPanel renders names, treasure scores, counts, WIN/LOSE/DRAW and rank gains.
The result screen disables movement and displays the end reason. It offers Play
Again and Return to Lobby.

Cancellation occurs on finish, room cleanup and ServerManager.close(). Canceled
tasks are removed from the executor queue. Server shutdown closes the manager
through a shutdown hook; its executor is shut down and timers cannot leak.
Client-sent TIME_UPDATE and GAME_OVER messages return ERROR without changing
state or ranks.

# Exit and disconnect

`reason` is `TIME_UP`, `PLAYER_EXIT` or `DISCONNECT`. Only TIME_UP compares scores.
For the other reasons, the exiting/disconnected player loses even with a higher
treasure score; the opponent wins +3 rank and +1 win, the loser gains +0 rank.
Scores and counts remain actual match values; forfeit never rewrites them.

GamePanel asks confirmation before sending `EXIT_GAME|`. Server checks the logged-in
handler owns a RUNNING room. On TCP EOF, I/O failure, DISCONNECT or LOGOUT, the
handler removes its online session and Server immediately adjudicates the active
room. Silent network failures are adjudicated when TCP detects them, not necessarily
at the instant a cable/network is lost; there is no reconnect or heartbeat yet.

Timer, MOVE, EXIT and disconnect share Manager -> Room lock order. A single
finish function snapshots the result and awards ranks once. It sends final FINISHED
GAME_STATE and GAME_OVER to still-connected original sessions before cancel/cleanup
and FREE ONLINE_LIST. Only TIME_UP necessarily sets the clock to zero. A duplicate
EXIT after finish returns ERROR. A closed ServerManager cancels rooms without forfeit.

# Play Again

Server retains one previous result-room context per participant, only if both are
still online at finish. This is separate from active rooms. Rematch messages:

```text
Client -> Server: PLAY_AGAIN|opponentUsername
Server -> opponent: PLAY_AGAIN|requesterUsername
Opponent -> Server: PLAY_AGAIN_ACCEPT|requesterUsername
Opponent -> Server: PLAY_AGAIN_REJECT|requesterUsername
Client -> Server: RETURN_TO_LOBBY|
```

Server requires both original participants online and FREE, a shared previous
result context, and a pending request before accept/reject. Reciprocal PLAY_AGAIN
requests count as both consenting, so simultaneous clicks produce exactly one room.
Repeated same-direction requests do not generate extra popups. Starting the new
room consumes both old contexts and requests atomically before GAME_START/GAME_STATE.
The new room has a new UUID, separately generated map (different obstacle layout),
spawn and treasures, zero scores/counts and a new 180-second timer. Rank/wins survive
match resets. No old GameState is reused.

Reject clears both contexts/requests and sends PLAY_AGAIN_REJECT to both clients,
which return to Lobby while FREE. Return to Lobby also explicitly invalidates
rematch on Server, even without a pending request; the other client is notified.
Disconnect at Results clears requests and notifies the surviving client without
another GAME_OVER or rank award. Starting another match or Server shutdown also
clears obsolete contexts. Delayed requests/accepts fail after context invalidation.

Lifecycle regression test (real TCP sockets, normal 180-second initial duration,
matches ended by EXIT/disconnect without waiting three minutes):

```powershell
javac -encoding UTF-8 -d out src/common/*.java src/model/*.java src/server/*.java src/client/*.java tests/*.java
java -cp out GameLifecycleIntegrationTest
```
