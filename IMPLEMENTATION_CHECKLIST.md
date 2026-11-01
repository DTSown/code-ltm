# Treasure Rush - Implementation Checklist

Status: `[x]` = implemented and verified by the evidence indicated; `[ ]` = pending
implementation or verification. Physical listening and two-computer LAN checks
must remain pending unless actually performed. No old test is to be removed.

Final audit, 2026-10-12: **Hoan thanh va da kiem chung** = `[x]`;
**Chua hoan thanh hoac chua kiem chung** = `[ ]`. The two unchecked items
are external physical verification limits, not missing runtime features.
Detailed commands, file list, evidence and limits: [verification report](docs/UPGRADE_VERIFICATION.md).

Evidence mapping: server requirements -> MapGenerationTest, TreasureCollectionTest,
server.TrapMechanicsTest (3000 respawns and exact ns boundaries), TrapIntegrationTest
(real TCP and saved statistics); client requirements -> TrapClientRenderTest and
existing render suites; audio -> AudioResourceTest isolated JAR/fake Clip checks;
compatibility -> all 18 regression suites; actual deployment/UI ->
JarGuiIntegrationTest with two standalone JARs at 700x450.
Logs: `out/regression-audio-traps.log`, `out/jar-gui-audio-traps.log`,
`out/jar-gui-final.log`. The report explains the GUI timer test adjustment and
focused final rerun; the exact initial 180-second assertion remains covered.
Visually inspected screenshots: `out/jar-test-8602544170839902210/trap-A.png`,
`trap-B.png`, and `out/test-screenshots/trap-684x411-0-0.png`.

## 1. Analysis and compatibility
- [x] Inspect source structure, README, docs, build scripts and test entry points.
- [x] Inspect GameRoom, ServerManager, MapGenerator and synchronization order.
- [x] Inspect Treasure, Player, GameState, GameResult and nonnegative codecs.
- [x] Inspect GamePanel, BoardPanel, MainFrame and JAR resource packaging.
- [x] Preserve Java 17+, Swing, TCP and server authority without dependencies.
- [x] Preserve symmetric map generation and five initial chest appearances.
- [x] Run existing auth/register/lobby/invite/movement/sync/timer tests.
- [x] Run existing exit/rematch/ranking/history/persistence tests.

## 2. Server traps
- [x] Exactly one randomly selected initial chest is secretly a trap.
- [x] Each replacement independently has 20 percent trap probability.
- [x] NORMAL/SILVER/GOLD can all disguise traps without changing appearance.
- [x] No trap flag/type/value/identifier hint in GAME_STATE or shared models.
- [x] Normal rewards remain 10/20/30 and increment collection statistics.
- [x] Trap gives no reward and deducts min(10, score), clamped to zero.
- [x] Trap does not increment match or lifetime reward collection statistics.
- [x] Trap consumed once, respawn retains five valid nonoverlapping chests.
- [x] Server monotonic stun deadline exactly 2000 ms; no sleep or blocked handler.
- [x] MOVE during stun cannot change coordinates and sends no repeated ERROR.
- [x] Movement works at deadline without an explicit unlock command.
- [x] Opponent movement and match clock continue; EXIT remains allowed.
- [x] Finish/disconnect/cleanup/rematch clear prior stun state.

## 3. TCP events
- [x] TRAP_TRIGGERED contains room, event sequence, player, actual loss, total, duration.
- [x] Both clients receive identical event then identical authoritative GAME_STATE.
- [x] Client cannot forge traps, points or authoritative state.
- [x] Event duplicate and old-room guards prevent repeated effects on rematch.
- [x] GAME_STATE/TIME_UPDATE do not retrigger sound, notice or animation.

## 4. Client
- [x] Java2D draws 3-5 rotating yellow stars above affected player.
- [x] Swing Timer approximately 30 FPS, no EDT blocking or avatar displacement.
- [x] Both clients show the affected avatar and clear stars after two seconds.
- [x] Stars visible at top row and board edges, scaled to current tile.
- [x] WASD, arrows and navigation buttons locked only for local stunned player.
- [x] Controls automatically unlock; exit button remains usable.
- [x] Own/opponent Vietnamese notices are prominent, nonmodal and once per event.
- [x] Display actual deducted amount at scores 0 and 5.
- [x] Score and positions change only from server snapshots.
- [x] Clear UI stun on finish/disconnect/exit/login/new match/disposal.
- [x] Preserve fantasy UI and fit sound control at 700x450 without overlap.

## 5. Audio and packaging
- [x] Dedicated asynchronous Java Sound manager safely handles missing device.
- [x] Actual original PCM WAV lobby/game music and six active effects exist under resources/audio.
- [x] Melodic gentle adventure music loops continuously without duplicate starts.
- [x] Pickup/gold/trap/win/lose effects wired to appropriate events.
- [x] Local sound toggle mutes all audio without affecting opponent or gameplay.
- [x] Stop music at match end/login/close; dispose audio resources.
- [x] Build packages audio directly in client JAR; no outside audio directory needed.
- [x] Decode every WAV through an isolated client-JAR classloader.
- [x] Verify loop PCM boundaries and non-silent melodic content.
- [ ] Human listening on physical speakers confirms musical quality/seamless loop.

## 6. Automated verification and delivery
- [x] Normal rewards and traps at scores 0/5/10/greater tested.
- [x] Exact monotonic boundary before/at 2000 ms tested with deterministic clock.
- [x] Real TCP stun/recovery/opponent/time/event/snapshot tests.
- [x] Trap secrecy, all disguises, initial count and respawn probability tested.
- [x] Respawn occupancy, single trigger, counts and nonnegative result codec tested.
- [x] Finish while stunned and rematch reset tested.
- [x] Client animation/mute/input/duplicate/stale event/render tests.
- [x] Existing regression assertions retained and adapted only for new behavior.
- [x] Run .\build.ps1 successfully; both required dist JARs produced.
- [x] Run .\test.ps1 successfully, recording suite names/count.
- [x] Run real two-client JAR GUI integration with screenshots if environment allows.
- [ ] Actual two-physical-computer LAN verification.
- [x] Update README, protocol, statistics and implementation/verification docs.
- [x] Re-read prompt and audit every checklist item; report remaining limits.
- [x] Final report lists changed source, audio files, results and two-client steps.

## 7. Audio completion (2026-10-13)
- [x] Shared per-Client audio, distinct lobby music, functional UI clicks and collector-only rewards.
- [x] Five requested new WAVs generated, source/JAR decoded and all eleven packaged WAV hashes matched.
- [x] 19/19 regression suites and real two-client JAR GUI checks passed on isolated port 5101.
- [x] Detailed PASS/UNVERIFIED checklist and manual listening steps recorded in AUDIO_IMPLEMENTATION_CHECKLIST.md and docs/AUDIO_COMPLETION_VERIFICATION.md.
- [ ] Physical speaker listening remains unverified; optional preference persistence and crossfade are not implemented.
