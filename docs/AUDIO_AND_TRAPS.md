# Audio and traps

## Runtime design

The server owns trap selection, scores, replacement chests and monotonic stun
timing. Shared state contains only the public chest appearance. See
[GAME_STATE.md](GAME_STATE.md) for the event and ordering contract.

MainFrame owns one SoundManager and passes it to GamePanel and the sound controls.
SoundManager uses Java Sound on a dedicated daemon worker. Loading, opening,
looping, rewinding and closing clips occur away from Swing's EDT. Repeated running snapshots
and lobby updates do not create more music loops. Muting is a local setting; it sends no TCP
message and does not change the other player's audio or game state. Missing
audio hardware/resources degrade to silent gameplay, with no blocking dialog.

Music has explicit LOBBY and GAME tracks. Login has no BGM. A successful login
starts lobby music, which continues through online-list updates, statistics and
invitations. Starting a match selects game music; result/return-to-lobby selects
lobby music again. The old BGM Clip is stopped and closed before the new one is
opened, so at most one background track plays. Win/lose cues play once per result
alongside the lobby track; a draw has no result cue. Logout/disconnect stop BGM,
and application disposal releases all audio resources.

Server-confirmed TREASURE_COLLECTED plays PICKUP/GOLD only when the collector
matches the local username; both clients still display the notice. TRAP_TRIGGERED
plays TRAP once on each client together with the stars, with existing room/sequence
guards. GAME_STATE and TIME_UPDATE never produce these effects. Trap, scoring
and protocol logic are unchanged. GameServer additionally accepts
`-Dtreasure.server.port=5101` for isolated GUI testing while an existing server
occupies the default 5000.

UiSounds binds functional Swing buttons once, including dynamically added controls,
invitations, rematches, feedback and exit-confirmation dialogs. Disabled controls
are silent. Movement buttons and WASD/arrow key actions do not produce clicks.
SoundToggle handles its own click exactly once; muting is silent, unmuting clicks.
The same setting is available at login, lobby and next to the game clock.
Mute stops BGM and SFX; scene changes still update the intended track, so unmuting
resumes the current screen's BGM. The setting is retained within this Client
session, not persisted between application launches.

The worker queue is bounded to 16 effects and there is at most one cached Clip
per effect type. Replays rewind the same Clip rather than opening a new device
line per click. Mute, stopAll and close release the cache. Track changes preserve
ongoing effects. Transitions use stop-before-start without device-specific gain
controls or crossfades; both procedural music buffers have continuous loop tails.

## Resources

The actual PCM WAV assets live in `resources/audio` and are packaged as
`audio/*.wav` in TreasureRushClient.jar. No audio directory is needed next to
the deployed JAR. Build fails on missing required audio instead of producing
a silently incomplete client. No external codec, dependency or download is used.

`tools/GenerateAudio.java` synthesizes original music and effects, so there are
no third-party recording licenses to obtain. The generator and asset manifest
document the exact names, durations and synthesis details. Recreate resources:

```powershell
java --source 17 tools/GenerateAudio.java resources/audio
.\build.ps1
```

When compiling/running source manually, put `resources` on the classpath:

```powershell
java -cp 'out/final-tests;resources' client.GameClient
```

## Verification

Run `powershell -NoProfile -ExecutionPolicy Bypass -File .\test-audio.ps1` for the
three audio suites. The script builds the Client alone when a Server JAR already
exists, so a running Server's locked JDBC library does not prevent a Client update.
The new `client.MainFrameAudioIntegrationTest` loads the actual packaged MainFrame,
injects recording Clips, and keeps its Swing window hidden. It verifies successful
login starts lobby music; ranking/history preserve it; shared login/lobby/game mute
controls agree; game/result transitions select the right track without replay on
duplicate messages; logout stops music; disposal closes every Clip and worker.
It requires a desktop environment, although no test window is shown. This catches
missing wiring between MainFrame and SoundManager that isolated sound tests miss.

`TrapMechanicsTest` verifies exact timing boundaries with an injected monotonic
clock. `TrapIntegrationTest` uses real TCP handlers for event ordering, silent
MOVE rejection, opponent movement, live timer, recovery, exit, rematch,
disconnect and saved statistics. Existing TreasureCollectionTest still exercises
1000 successful rewards, now with additional trap respawns between them.

`TrapClientRenderTest` checks presentation and control lifecycle on the EDT.
`AudioResourceTest` decodes PCM from an isolated JAR classloader, independent of
external audio files. It checks track exclusivity, repeated scene changes, mute
across scene changes, cached effects, worker bounds and unavailable devices.
`client.AudioUiTest` checks source resources, real Swing button callbacks, dynamic
and disabled controls, local-only rewards, trap deduplication, BGM continuity and
two independent client managers. `JarGuiIntegrationTest` opens five original chests through
real Client JAR controls, checks rewards and at least one trap, and captures
both clients' trap screenshots before testing the existing lifecycle flows.

Programmatic decoding and loop-boundary checks do not establish subjective
musical quality or audibility at the user's speakers. That requires listening
with an enabled output device. See IMPLEMENTATION_CHECKLIST.md and the final
verification report for actual executed checks and remaining limits.
The audio completion checklist is [AUDIO_IMPLEMENTATION_CHECKLIST.md](../AUDIO_IMPLEMENTATION_CHECKLIST.md).
