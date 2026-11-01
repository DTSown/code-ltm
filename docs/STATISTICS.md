# Statistics and persistence

Normal GameServer uses `new ServerManager()` and selects a DataStore using
`config/database.properties`. `storage=mysql` uses MySqlDataStore and Connector/J;
`storage=tsv` (also the default without a config file) uses `data/server-data.tsv`.
Configuration and TSV paths are relative to the working directory. Start Server
from the same directory on each run. See [MySQL setup](MYSQL.md).
`ServerManager(seconds, path)` supports a separate test file and short timer;
`ServerManager(seconds)` is memory-only for existing isolated tests.
`ServerManager.withDataStore(seconds, store)` injects MySQL for integration tests.

## MySQL storage

`accounts` stores username (primary key), password_plaintext, rank_points, wins,
total_treasures and total_matches. Legacy databases temporarily retain salt and
password_hash until every plaintext password is known and verified. `match_details` stores room_id (primary key), both
players (foreign keys to accounts), winner, both scores, collected counts, rank
gains, reason, ended_at_epoch_millis and duration_seconds. Tables use InnoDB,
utf8mb4 and utf8mb4_0900_bin so Java's case-sensitive username identity is preserved.
Requires MySQL 8.0.19+ (tested on MySQL 9.6).

`matches` is now a read-only view with exactly five columns: ten_phong,
nguoi_tham_gia, diem_rank, ruong_da_an, nguoi_thang. It shows numbered room names,
participant names alongside each rank gain/treasure count, and the winner's actual
username (or Hòa). UUID-only legacy rooms use Phòng cũ 1, 2, ... ordered by end
time/ID. The underlying match_details table retains all original fields for game
history and restart. Initialization renames an old matches base table before
creating the view, preserving all records; reads support the pre-migration schema.

Each save upserts account snapshots and inserts immutable history in a single
transaction. Repeated saves do not add duplicate matches. Any write failure rolls
back the whole transaction. Reads load both tables in a REPEATABLE_READ transaction
and validate model fields before publishing data to ServerManager. Ranking/history
are still computed in Java from the loaded snapshot. One server process must own
the database; restart the game server after manual database edits.

`server.DatabaseSetup [source.tsv]` creates the database and tables. With a source
TSV it imports only into empty tables, in one transaction, preserving salts/hashes
and all lifetime totals. It refuses to overwrite any nonempty tables. The original
TSV is not modified. Normal server startup never imports TSV implicitly and never
falls back to TSV if the configured MySQL connection fails.

## Account and statistics

Account is immutable and stores username, a unique 16-byte SecureRandom salt,
SHA-256(salt bytes || UTF-8 password), and lifetime rank/wins/treasures/matches.
Salt and hash are lowercase hex (32 and 64 characters). Plaintext passwords are
not stored in Account/TSV snapshots or logged in LOGIN/REGISTER messages.
The requested MySQL demo additionally stores `password_plaintext`: registration
writes it atomically with the new account; a successful login backfills it for
legacy accounts. Wrong passwords and rejected logins do not update it. This column
is never loaded into Account/Player or sent in ranking/history responses; ordinary
snapshot upserts leave it intact. After every account's plaintext matches its
original salted hash, initialization/login/save automatically drops both legacy
credential columns. Until then the old hashes remain usable. Fresh databases have
no salt/hash columns; importing a legacy TSV temporarily adds them. Once migrated,
SQL stores plaintext only; loading creates transient salted Account hashes in RAM
for the unchanged login checks. Snapshot writes and new registrations support
both schemas and never reintroduce dropped credential columns. Initialization
adds the nullable plaintext column to an existing accounts table; old rows stay
NULL until successful login. A failed demo-column write rejects login before
creating a session, so a successful login guarantees the demo value was saved.
Salted SHA-256 is
the simple classroom implementation requested, not production password hardening.
TCP credentials are still sent over the existing unencrypted connection.

Login creates a fresh session Player copied from Account statistics; offline
accounts stay on the ranking. Session status/position/score are not persisted.
Restart restores credentials and lifetime totals, not RUNNING rooms or rematches.
Previous memory-only accounts had no file to migrate; new registrations are saved.

GameRoom.finishGame updates both Players once behind its existing result guard:
WIN +3 rank/+1 win, DRAW +1 rank, LOSE +0; each participant gets +1 totalMatches
and their actual collected count added to totalTreasureCollected. This includes
TIME_UP, PLAYER_EXIT and DISCONNECT, even for the departing player. ServerManager
copies final statistics into Account, inserts immutable MatchHistory keyed by
room UUID, and saves before GAME_OVER. MatchHistory wraps a snapshot GameResult,
so the next match's reset cannot alter old scores/counts. Duration is elapsed
server countdown seconds; endedAt is server epoch milliseconds.

Disguised traps subtract up to ten match points, clamped at zero. They never
increment collectedTreasure or totalTreasureCollected. History stores the actual
final nonnegative score, including trap losses; ranking awards and tie-breaks
remain unchanged. Trap membership and temporary stuns are not persisted.

## File format

One UTF-8 snapshot, tab-separated rows:

```text
TREASURE_SERVER<TAB>1
ACCOUNT<TAB>encodedUsername<TAB>saltHex<TAB>hashHex<TAB>rank<TAB>wins<TAB>totalTreasureCollected<TAB>totalMatches
MATCH<TAB>roomId;p1;p2;winner;score1;score2;collected1;collected2;rankGain1;rankGain2;reason;endedAtEpochMillis;durationSeconds
END<TAB>accountCount<TAB>matchCount
```

`<TAB>` means an actual tab. Names use URL-encoded UTF-8, matching game codecs.
The footer detects incomplete snapshots. Loading rejects malformed fields,
duplicates, wrong version/counts and history referencing unknown accounts; it
does not silently reset or overwrite a corrupt/unreadable file.

All updates and writes hold ServerManager's existing synchronized lock, with
Manager -> Room lock order. A single snapshot commits accounts and history
together, avoiding two-file inconsistencies. Save writes a unique temporary file
in the same directory, flushes using FileChannel.force(true), then replaces the
destination using ATOMIC_MOVE/REPLACE_EXISTING. Brief Windows sharing failures
retry up to five attempts; temporary files are cleaned afterward. Filesystems
without atomic replacement cause an explicit save error, not a partial overwrite.
Use one central Server process per data file; multi-process writers are unsupported.
This paragraph describes the TSV backend; MySQL uses the transaction described above.

Registration reports REGISTER_FAIL and is not committed in memory on save error.
If saving a completed match fails, Server retains updated statistics/history in
memory, sends GAME_OVER followed by ERROR, still cleans the room, and retries
on a subsequent successful save or close(). Rank/history are not applied again.
Persistent disk failure followed by process termination cannot preserve unsaved
memory; restore writable storage before stopping Server.

## Wire formats

Both requests require a logged-in handler. Client-sent RANKING/HISTORY return ERROR.

```text
GET_RANKING|
RANKING|encodedUsername,rank,wins,totalTreasureCollected,totalMatches;encodedUsername2,...

GET_HISTORY|
HISTORY|roomId;p1;p2;winner;score1;score2;collected1;collected2;rankGain1;rankGain2;reason;endedAtEpochMillis;durationSeconds/roomId2;...
```

No entries means `RANKING|` or `HISTORY|`. History rows use `/`; all names are
URL-encoded UTF-8, so row/field delimiters inside names are safe. Winner stays
P1/P2/DRAW and reason stays TIME_UP/PLAYER_EXIT/DISCONNECT. GAME_OVER and GAME_STATE
formats are unchanged by this prompt.

Ranking includes all registered players sorted by rank descending, wins descending,
totalTreasureCollected descending, then username ascending (String.compareTo).
HISTORY contains only the requesting handler's matches, newest endedAt first,
then room UUID for equal timestamps. Request data cannot select another username.

Lobby opens RankingPanel or HistoryPanel. Both tables have Refresh and Return to
Lobby. Rank numbers are row positions in Server order; client does not sort or
calculate rank points. History shows the numbered room name (legacy IDs unchanged), opponent, own outcome/scores,
counts, reason, end time and duration with horizontal scrolling on narrow windows.
Delayed statistics responses do not switch a Client out of gameplay/results.

## Tests

```powershell
javac -encoding UTF-8 -d out src/common/*.java src/model/*.java src/server/*.java src/client/*.java tests/*.java
java -cp out RankingPersistenceIntegrationTest
java '-Djava.awt.headless=true' -cp out StatisticsPanelRenderTest
```

TCP integration covers score wins, draw, exit, disconnect, lifetime totals, duplicate
finish, simultaneous room finishes, two identical ranking responses, private history,
re-login/restart, offline/tie-break order, UTF-8/separators, salted hashes, corrupt
files, failed registration and failed match-write retry. Generated test data stays
in temporary directories under `out`, never in the normal Server data directory.
