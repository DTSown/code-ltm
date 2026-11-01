# Audio and trap upgrade verification

For the subsequent 2026-10-13 lobby/click/audio completion and its 19-suite
verification, see [AUDIO_COMPLETION_VERIFICATION.md](AUDIO_COMPLETION_VERIFICATION.md).
The results below record the earlier trap/audio upgrade.

Date: 2026-10-12. Environment: Windows, JDK 24.0.2, compilation with
`--release 17`; no third-party dependencies. Actual execution on a separate
JDK 17 installation has not been performed; class-file major version 61 is
checked by the JAR test.

## Fresh recheck requested by user

Rechecked on 2026-10-12 against freshly built artifacts:

- `powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1`: PASS,
  exit code 0; both required dist JARs produced.
- `powershell -NoProfile -ExecutionPolicy Bypass -File .\test.ps1`: PASS,
  18/18 regression suites. Log: `out/recheck-regression.log`.
- `java -cp out/final-tests JarGuiIntegrationTest 127.0.0.1`: PASS,
  real Server JAR and two standalone Client JARs, including trap gameplay,
  mute controls and existing full lifecycle. Log: `out/recheck-jar-gui.log`.
- All six ZIP entries match their source WAV files byte-for-byte by SHA-256:
  adventure (940844 bytes), pickup (31354), gold (41056), trap (39732),
  win (70604), lose (71044). Isolated JAR decoding also passed again.
- Visually reviewed `trap-A.png` and `trap-B.png` in
  `out/jar-test-2803976608339775213/`: both clients show Alex's stars,
  matching scores and appropriate notices at 700x450. Only Alex's navigation
  is disabled; exit remains enabled. No HUD/clock/notice overlap was observed.
- No Exception/ERROR/FAIL matches in the new GUI run's process logs.

No new failure was found, so no production source or test changes were needed
for this recheck. Physical speaker listening and separate-machine LAN checks
remain unverified; the limitations below still apply.

## Executed checks

- `powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1`: PASS,
  both `dist/TreasureRushServer.jar` and `dist/TreasureRushClient.jar` produced.
- `powershell -NoProfile -ExecutionPolicy Bypass -File .\test.ps1`: 18/18 PASS.
  Full output: `out/regression-audio-traps.log`.
- Final runtime code: `test.ps1 -JarGui` rebuilt both JARs and passed 18/18
  regression suites, including the Unicode username fix. Its GUI phase exposed
  an existing timing assumption: the live screen may already read 179 rather
  than 180 seconds. The test agent now records the first wire GAME_STATE and
  still asserts exactly 180 there, while verifying the live countdown separately.
  After recompiling the two GUI test files, the focused command
  `java -cp out/final-tests JarGuiIntegrationTest 127.0.0.1` passed against the
  final JARs. Thus all 19 suite programs passed with the final implementation;
  the last GUI harness adjustment required only rerunning its affected suite.
  Logs: `out/jar-gui-audio-traps.log` (18 suites), `out/jar-gui-final.log` (GUI).
  Real Server and two standalone Client JAR processes connected through
  `127.0.0.1:5000`, with both windows resized to 700x450. The test checked local
  mute/unmute, all five original chests, exact reward/trap score/count changes,
  controls/exit while stunned, automatic recovery, and the existing authentication,
  lobby, invitations, timer, result, rematch, disconnect, ranking/history,
  logout, abrupt server stop and persistence after restart flows.
  Final screenshots and process logs: `out/jar-test-8602544170839902210/`.
  `trap-A.png` and `trap-B.png` were visually inspected: both show Alex's stars;
  only Alex's movement controls are disabled. No HUD/clock/notice overlap.
  The independent plain Client JAR launch also passed without a test agent.

Existing suites retained: MapGenerationTest, TreasureCollectionTest,
InviteIntegrationTest, MoveIntegrationTest, GameTimerIntegrationTest,
GameLifecycleIntegrationTest, RankingPersistenceIntegrationTest,
GamePanelRenderTest, ResultPanelRenderTest, StatisticsPanelRenderTest,
LoginPanelRenderTest, LobbyPanelRenderTest, ClientConnectionTest, RoomNameTest.

New suites: server.TrapMechanicsTest, TrapIntegrationTest,
TrapClientRenderTest, AudioResourceTest.

Notable coverage:
- 1000 map seeds retain connected symmetric layouts and fair visible placement.
- 1000 successful reward collections plus 247 trap collections retain all
  prior score/count, respawn occupancy, identifier and codec assertions.
- Exact 1,999,999,999 / 2,000,000,000 ns stun boundaries, including signed
  nanoTime values and overflow; all disguises and 3000 respawns.
- Scores 0, 5, 10 and 37, trap secrecy, single consumption, fake client event
  rejection, independent opponent, clock, event/state ordering and finish races.
- Existing accepted Unicode-space usernames remain valid through trap events
  and results; a queued disconnect callback cannot reopen a disposed frame.
- Real TCP flood during stun, automatic recovery, exit during stun, rematch,
  disconnect, timeout, history and lifetime statistics reload.
- EDT input, duplicate/stale events, animation pixel changes and all four
  board corners at 700x450, 684x411, 1100x700 and 1400x900. Render screenshots
  are in `out/test-screenshots/trap-*.png`.
- All six WAV files decode from a client-only isolated classloader; PCM is
  non-silent and music has continuous sample boundaries. Fake Java Sound clips
  verify music idempotence, all effects, local mute, bounded requests, cleanup,
  stalled/unavailable devices and no clip operations on the EDT.

The original reward test was expanded to account for traps without reducing
its 1000 successful rewards. Existing UI tests mute their local test panels and
close resources. The GUI test whitelist explicitly accepts client WAV resources
and opens all five original chests, retaining normal reward assertions and
adding trap assertions. The initial timer assertion is now made against the
captured initial network message instead of a later mutable screen snapshot.
No test was removed or disabled.

## Changed files

Runtime: `src/server/GameRoom.java`, `src/server/ServerManager.java`,
`src/server/ClientHandler.java`, `src/common/Protocol.java`,
`src/common/TrapEvent.java` (new), `src/client/GamePanel.java`,
`src/client/BoardPanel.java`, `src/client/MainFrame.java`,
`src/client/SoundManager.java` (new).

Build: `tools/BuildJars.java`, `tools/GenerateAudio.java` (new), `build.ps1`,
`test.ps1`. Resources: `resources/audio/adventure.wav`, `pickup.wav`,
`gold.wav`, `trap.wav`, `win.wav`, `lose.wav`, and `ASSETS.md` (all new).

Tests: four new suites listed above; updates to `TreasureCollectionTest.java`,
`GamePanelRenderTest.java`, `RoomNameTest.java`, `JarGuiIntegrationTest.java`,
`JarGuiAgent.java`.

Documentation: `README.md`, `docs/GAME_STATE.md`, `docs/STATISTICS.md`,
`docs/FINAL_REVIEW.md`; new `docs/AUDIO_AND_TRAPS.md`, this report,
and `IMPLEMENTATION_CHECKLIST.md`.

MapGenerator, shared Treasure/Player/GameState/GameResult and GAME_STATE wire
schema are unchanged. Existing saved data needs no migration. Ship the updated
Server and Client together so both support the new trap event; old clients do
not provide the stun presentation or local input lock.

## Limits

No physical speaker listening assessment or two-physical-computer LAN test has
been performed. Programmatic WAV decoding and loop-boundary checks do not prove
subjective musical quality or the user's OS output routing. Client animation
expiry is based on event receipt; network/EDT delivery adds a small delay relative
to the authoritative server interval. The server always uses monotonic elapsed
time; Swing paints on its next approximately 33 ms frame.

An unavailable audio device leaves gameplay usable. A native audio driver stalled
inside its own call can delay that worker's release, while the Swing thread stays
responsive. Failed audio resources remain silent for that SoundManager instance.

One development run of the fake-Clip startup test hit its five-second wait for
"Music starts once". Its cause was not captured; five immediate repeats and
subsequent complete regression runs passed without relaxing that assertion or
timeout. Treat a recurrence as a diagnostic issue, not as proof of missing audio
hardware (that test uses no physical device).

The pre-existing server uses synchronous socket writes under its manager lock.
A peer that stops reading indefinitely can cause TCP backpressure to delay all
rooms. This upgrade preserves that transport design; the stun implementation
itself introduces no sleeps or blocking waits.
