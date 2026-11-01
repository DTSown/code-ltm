# Audio completion checklist

Date: 2026-10-13. PASS means implemented and verified by the named checks.
UNVERIFIED is not a claim of PASS. No required automated check remains pending.

| Requirement | Status | Evidence |
| --- | --- | --- |
| Read sound, screens/dialogs, board, server events, resources and build | PASS | Source review; trap/scoring/protocol unchanged; optional isolated server port added |
| One shared SoundManager per real Client | PASS | MainFrame injection; AudioUiTest and JAR GUI shared-manager assertion |
| Distinct melodic lobby BGM, continuous loop and quieter level | PASS | Original 18.462 s D-major score; PCM/loop checks; subjective listening below |
| Lobby/wait/invitation/statistics updates preserve loop | PASS | Idempotent Music.LOBBY; repeated-request and GUI checks |
| Lobby to match selects game music exclusively | PASS | Worker stop-before-start; AudioResourceTest and real JAR GUI |
| Result/exit/return restores lobby music; rematch selects game music | PASS | AudioResourceTest and real JAR GUI lifecycle |
| Login/disconnect stop BGM; close releases every Clip and worker | PASS | Lifecycle/EDT checks and real JAR GUI |
| Real pickup (0.3-0.8 s) and special gold cues | PASS | treasure_pickup.wav 0.720 s; treasure_gold.wav 0.875 s; PCM decode |
| Reward cue only on collector's Client | PASS | Two independent GamePanels/managers in AudioUiTest |
| Rewards triggered by Server event, never GAME_STATE/TIME_UPDATE | PASS | GamePanel event routing and AudioUiTest |
| Trap has only one cartoon stun cue (0.8-1.5 s), with stars | PASS | trap_stun.wav 1.200 s; event dedup and animation tests |
| Trap never produces reward cue or changes BGM | PASS | AudioUiTest plus unchanged server trap tests |
| Existing penalties, two-second lock, stars and synchronization preserved | PASS | All existing trap mechanics/TCP/render/JAR tests retained |
| Functional buttons click once, including login/invites/rematch/stats/back/exit | PASS | UiSounds, dynamic Swing controls and real JAR GUI flows |
| Disabled/muted buttons silent; movement keys/buttons have no click | PASS | AudioUiTest |
| ui_click.wav is quiet, distinct and 0.1-0.25 s | PASS | Original 0.160 s PCM cue and resource checks |
| Per-Client mute affects both BGM/SFX; unmute follows current scene | PASS | AudioResourceTest, AudioUiTest and JAR GUI |
| Sound controls at login/lobby/game fit 700x450 | PASS | Viewed login-A, lobby-A, trap-A and trap-B from final JAR run |
| Cache effects, bound work/Clips, keep Java Sound off EDT | PASS | One Clip/type, 16-request bound, fake/stalled-device tests |
| Missing device/resource leaves game usable | PASS | AudioResourceTest |
| All five requested new WAV files actually exist | PASS | GenerateAudio execution produced all five files |
| Legal original assets and reproducible generator | PASS | Mathematical synthesis only; resources/audio/ASSETS.md |
| Source classpath and standalone Client JAR load every resource | PASS | AudioUiTest source decoding; isolated-JAR AudioResourceTest |
| Build packages every WAV only in Client, no external directory | PASS | 11/11 source/JAR SHA-256 matches; isolated JAR and standalone launch |
| build.ps1 and test.ps1 actually pass without removing tests | PASS | Both dist JARs built; 19/19 regression suites passed against final runtime |
| Multiple matches and two actual Client JARs remain stable | PASS | JarGuiIntegrationTest on 127.0.0.1:5101 |
| Human speaker listening, balance and subjective music quality | UNVERIFIED | Requires local listening with enabled output device |

Optional enhancements not implemented: disk persistence of the mute setting and
device-dependent fade-in/fade-out. Mute survives screen/match changes within one
Client session; track switches stop the previous BGM before opening the next.

Logs: `out/audio-completion-regression-final.log`,
`out/audio-completion-jar-gui-final.log`. Screenshots/process logs:
`out/jar-test-333434360522446512/`.

The first GUI attempt was blocked by an existing Java server on port 5000.
The final test passed on isolated port 5101; the original process was left running.
See [verification and listening steps](docs/AUDIO_COMPLETION_VERIFICATION.md).
