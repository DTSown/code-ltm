# Audio completion verification

Date: 2026-10-13. Windows, JDK 24.0.2, Java 17 target, no third-party dependencies.

## Results

- PASS: `powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1`.
  Both required JARs are present in dist. The final `test.ps1` also rebuilt them.
- PASS: `powershell -NoProfile -ExecutionPolicy Bypass -File .\test.ps1`,
  19/19 suites, including every existing regression suite and new client.AudioUiTest.
  Log: `out/audio-completion-regression-final.log`.
- PASS: `java -cp out/final-tests JarGuiIntegrationTest 127.0.0.1 5101`.
  Real Server JAR and two standalone Client JARs exercised register/login,
  invitations, shared audio ownership, lobby mute across match start, scene music
  selection, five initial chests including a trap, score/count/respawn, timer,
  exit, result, rematch, disconnect, rankings/history, logout and restart persistence.
  Log: `out/audio-completion-jar-gui-final.log`.
- PASS: all eleven packaged WAV entries match resources/audio byte-for-byte by
  SHA-256. Source classpath decoding and isolated-JAR decoding passed. Every WAV
  is signed little-endian 22050 Hz, 16-bit mono PCM, non-silent and unclipped.
- PASS: fake Java Sound backends verify at most one BGM, stop-before-start,
  eight repeated scene transitions, no restarts for 100 repeated requests,
  SFX alongside BGM, cache reuse, mute/unmute following the current scene,
  bounded work, absent/stalled devices, cleanup and no Java Sound calls on EDT.
- PASS: two GamePanels with independent managers verify collector-only regular,
  silver and gold rewards, exactly one trap cue per valid event, no reward on
  traps, no replay from state/time messages, result cue deduplication and no
  click from movement. Actual Swing controls verify dynamic binding, disabled
  buttons, self-disabling callbacks and single toggle-on click.
- PASS: visually reviewed login-A, lobby-A, trap-A and trap-B screenshots in
  `out/jar-test-333434360522446512/`. All are 700x450, with sound controls clear
  of headings, HUD and clock. Both clients show Alex's stun stars and correct
  notices. Only Alex's navigation is disabled; exit remains available.
  Login/lobby retain their existing scrollable layout at this small size.
- No Exception/ERROR/FAIL matches in the final real-JAR process logs.

The first GUI run could not bind 5000 because an existing Java process owned it.
That process was not stopped. An optional `treasure.server.port` system property
was added, retaining the normal default 5000, and the complete GUI suite passed
on 5101. `test.ps1 -JarGui -ServerPort 5101` supports the same isolation.

No trap selection, score calculation, stun timing, map generation, state codec
or protocol logic changed. Existing resource tests now assert the new active
filenames and cache reuse instead of per-play Clip disposal; final disposal and
all prior failure/lifecycle checks remain. No test was deleted or disabled.

## Files

Runtime changed: src/client/SoundManager.java, GamePanel.java, MainFrame.java,
LoginPanel.java, LobbyPanel.java, AuthFeedback.java; src/server/GameServer.java
(optional port only). New runtime files: src/client/UiSounds.java and SoundToggle.java.

Build/generation changed: tools/GenerateAudio.java, tools/BuildJars.java, test.ps1.
The existing build.ps1 already passes the resources path and needed no changes.

New resources: resources/audio/lobby_bgm.wav (18.462 s), treasure_pickup.wav
(0.720 s), treasure_gold.wav (0.875 s), trap_stun.wav (1.200 s), ui_click.wav
(0.160 s). Existing adventure.wav, win.wav and lose.wav remain active. Original
pickup.wav/gold.wav/trap.wav remain packaged for compatibility. All are original
procedural synthesis, with no downloaded recordings or licensing ambiguity.

Tests: tests/AudioUiTest.java (new); updates to AudioResourceTest.java,
JarGuiAgent.java and JarGuiIntegrationTest.java.

Docs: README.md, resources/audio/ASSETS.md, docs/AUDIO_AND_TRAPS.md,
IMPLEMENTATION_CHECKLIST.md, docs/UPGRADE_VERIFICATION.md;
new AUDIO_IMPLEMENTATION_CHECKLIST.md and this report.

## Manual listening

Physical speaker audibility, subjective music quality and perceived balance have
not been verified. Java 17 bytecode is checked; actual runtime tests used JDK 24.
The mute setting lasts for the current Client process, not across launches.
Optional device-specific fades are not implemented; track changes stop the old
track first and both original music scores have continuous PCM loop boundaries.

1. Use three terminals at the project root. Start the freshly built Server with
   `java -jar dist/TreasureRushServer.jar`. If another Server occupies 5000, close
   it yourself or use `java -Dtreasure.server.port=5101 -jar dist/TreasureRushServer.jar`.
2. In the other two terminals run `java -jar dist/TreasureRushClient.jar` once
   per terminal. Use 127.0.0.1 and the Server's chosen port; sign in as different users.
3. Enable the speaker control. Clicking register/login, lobby actions and invitation
   controls should produce a short click. Login has no BGM; lobby should play
   the brighter "Harbor of Maps" score. Wait through two loops (about 37 seconds).
4. Invite/accept a match. Lobby music should stop and "Lantern Path" should play.
   Collect a real regular/silver chest and a gold chest: only the collector's
   Client should produce the corresponding reward sound. Test each Client in turn.
5. Open original chests until the guaranteed trap is found. Hear one boing/stun
   cue with the stars; there must be no reward cue, no repeated cue from updates,
   and no BGM interruption. The two-second server lock still applies.
6. Mute while playing, then exit to results/lobby while muted. Unmute: lobby music,
   rather than game music, should resume. Confirm the other Client stays audible.
7. Rematch to hear game music again, then return to lobby, open ranking/history,
   log out and close. Lobby music should survive statistics/invites but stop at
   logout/close. Adjust OS output routing/volume if the application is silent.

For an individual PCM asset, open any WAV in resources/audio with the OS player.
This checks the files themselves; the standalone Client test above checks the
packaged resources and event wiring.
