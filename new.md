# MeshApp Audio System Report (End-to-End)

This report documents the current audio architecture and implementation in MeshApp from sampling to playback, including voice call, voice message, and loopback paths.

---

## 1) High-Level Architecture

Core modules/classes:

- `voice/AudioController.kt`  
  Shared policy engine for routing, mic profile, AEC/NS/AGC enablement, and DSP gain/limiter decisions.
- `voice/VoiceCodec.kt`  
  Opus encoder/decoder using Android `MediaCodec`.
- `voice/VoiceSessionManager.kt`  
  Real-time full-duplex call pipeline over mesh with encryption, jitter buffer, and PLC.
- `voice/CallCrypto.kt`  
  Per-call ECDH-derived AES-GCM encryption/decryption for live call packets.
- `voicemessage/VoiceMessageRecorder.kt`  
  Capture + encode pipeline for recorded messages written to local file.
- `voicemessage/VoiceMessagePlayer.kt`  
  Decode + playback pipeline for recorded message files.
- `voice/VoiceSimulator.kt`  
  Local loopback test path using the same capture policy as live calls, with 3-second output delay.

All features use `AudioController.startSession(...)` and `AudioController.endSession()` for consistent hardware routing and lifecycle behavior.

---

## 2) Audio Fundamentals Used in Project

### Sampling + PCM format

- Sample rate: **16,000 Hz**
- Channels: **mono**
- PCM: **16-bit signed little-endian**
- Frame size: **20 ms**
- Samples per frame: `16000 * 20 / 1000 = 320`
- Bytes per frame: `320 * 2 = 640`

Constants are defined in `VoiceCodec`:

- `SAMPLE_RATE = 16000`
- `FRAME_MS = 20`
- `SAMPLES_PER_FRAME = 320`
- `BYTES_PER_FRAME = 640`

---

## 3) Encoding / Decoding (Opus via MediaCodec)

Implementation file: `voice/VoiceCodec.kt`

### Encoder

- MIME: `MediaFormat.MIMETYPE_AUDIO_OPUS`
- Channels: 1
- Sample rate: 16 kHz
- Bitrate: **16 kbps** (`OPUS_BITRATE = 16000`)
- Complexity: 10

Flow:

1. `encode(pcm16)` dequeues input buffer.
2. Writes PCM bytes to codec input buffer.
3. Queues input with PTS=0 in current implementation.
4. Dequeues output buffer and returns compressed Opus bytes.
5. If no output buffer is ready in time, returns empty `ByteArray` (frame not produced yet / delayed).

### Decoder

Configured with Opus CSD:

- `csd-0`: OpusHead
- `csd-1`: pre-skip in ns
- `csd-2`: mapping/setup buffer

Flow:

1. `decode(compressed)` dequeues input buffer.
2. Writes compressed Opus bytes.
3. Queues to decoder.
4. Dequeues output buffer and returns PCM16 bytes.
5. If no output available yet, returns empty `ByteArray`.

---

## 4) DSP Stage (Gain + Soft Limiter)

`AudioController.processRecording(...)` and `processPlayback(...)` call:

- `VoiceCodec.applyGainAndLimit(pcm16, gain, threshold)`

Limiter shape:

- Linear below threshold.
- Smooth tanh soft-knee above threshold.
- Prevents hard clipping while keeping perceived loudness.

Important implementation detail:

- If gain is `1.0f`, `applyGainAndLimit` returns input buffer directly (no math pass).

---

## 5) Routing + Session Policy

Implementation file: `voice/AudioController.kt`

### Routing

During active session:

- `AudioManager.MODE_IN_COMMUNICATION`
- Speaker ON:
  - Android 12+: `setCommunicationDevice(TYPE_BUILTIN_SPEAKER)`
  - older: `isSpeakerphoneOn = true`
- Speaker OFF:
  - Android 12+: `clearCommunicationDevice()`
  - older: `isSpeakerphoneOn = false`

No forced max stream-volume call is used now.

### Session types

`AudioSessionType`:

- `VOICE_CALL`
- `VOICE_MESSAGE_RECORD`
- `VOICE_MESSAGE_PLAYBACK`
- `LOOPBACK_TEST` (configured to mirror call policy)

`resolvePolicy()` maps (session type + speaker state) to:

- mic source
- AEC/NS/AGC toggles
- recording gain
- playback gain
- soft-limit threshold

### Current policy matrix

| Session | Earpiece / speaker-off | Speaker / speaker-on |
|---|---|---|
| **VOICE_CALL** | Source `VOICE_COMMUNICATION`, AEC+NS+AGC ON, rec gain 1.0, play gain 1.20, threshold 0.94 | Source `VOICE_RECOGNITION`, AEC ON, NS OFF, AGC OFF, rec gain 1.0, play gain 1.35, threshold 0.90 |
| **VOICE_MESSAGE_RECORD** | Source `VOICE_RECOGNITION`, AEC/NS/AGC OFF, rec gain 1.0, threshold 0.94 | same |
| **VOICE_MESSAGE_PLAYBACK** | play gain 1.20, threshold 0.92 | play gain 1.35, threshold 0.92 |
| **LOOPBACK_TEST** | Same as `VOICE_CALL` earpiece policy | Same as `VOICE_CALL` speaker policy |

---

## 6) Live Voice Call Flow (Full Duplex)

Implementation file: `voice/VoiceSessionManager.kt`

### Tx path (your mic -> mesh)

1. Start session as `VOICE_CALL`.
2. Create `AudioRecord` with source from `AudioController.getMicSource()`.
3. Apply recorder effects with `audioController.configureRecorder(...)`.
4. Read PCM into `readBuffer`.
5. Accumulate bytes until exactly one 20 ms frame (`640 bytes`) is available.
6. `processRecording(frame)` -> gain + limiter.
7. `VoiceCodec.encode(...)` -> Opus.
8. `CallCrypto.encrypt(sequenceNumber, encoded)` -> AES-GCM ciphertext.
9. Wrap in `VoicePacket(callId, sequenceNumber, timestampMs, encodedAudio=ciphertext)`.
10. Send via `meshService.sendVoice(...)`.

### Rx path (mesh -> your speaker/earpiece)

1. Receive `incomingVoiceStream`.
2. Filter by peer and callId.
3. Decrypt using `CallCrypto.decrypt(sequenceNumber, ciphertext)`.
4. Decode Opus -> PCM via `VoiceCodec.decode(...)`.
5. Insert by sequence into jitter buffer map.
6. `drainJitterBuffer(...)`:
   - prebuffer until 4 packets (~80 ms)
   - cap buffer growth at 10 packets (~200 ms)
   - reorder by expected sequence
   - if missing frame, PLC repeats last frame up to 2 frames at 0.8x
7. `processPlayback(frame)` -> gain + limiter.
8. Write to `AudioTrack` configured for `USAGE_VOICE_COMMUNICATION`.

---

## 7) Live Call Crypto Details

Implementation file: `voice/CallCrypto.kt`

1. ECDH shared secret from own ephemeral private + peer ephemeral public key.
2. Directional keys derived with SHA-256(sharedSecret + label):
   - send key label: caller/callee depending on side
   - receive key opposite label
3. Cipher: `AES/GCM/NoPadding`
4. Nonce: 12 bytes = 4-byte big-endian sequence number + 8 zero bytes.
5. Sequence numbers are unique per direction, preventing nonce reuse within a key direction.

---

## 8) Voice Message Record/Play Flow

### Record flow

Implementation file: `voicemessage/VoiceMessageRecorder.kt`

1. Start session as `VOICE_MESSAGE_RECORD` (speaker forced off by policy call).
2. Build `AudioRecord` with source from policy.
3. Apply recorder effects per policy.
4. Read exact 640-byte PCM frames.
5. `processRecording(...)`.
6. `VoiceCodec.encode(...)`.
7. Persist each encoded frame as:
   - 2-byte signed length (`writeShort(encoded.size)`)
   - raw encoded bytes (`write(encoded)`)
8. Duration = `frameCount * FRAME_MS`.
9. File renamed with duration metadata via `VoiceMessageFile.renameWithDuration(...)`.

### Playback flow

Implementation file: `voicemessage/VoiceMessagePlayer.kt`

1. Start session as `VOICE_MESSAGE_PLAYBACK`, speaker forced ON.
2. Read file stream as repeated `[short length][bytes]`.
3. Decode each compressed frame with `VoiceCodec.decode(...)`.
4. `processPlayback(...)`.
5. Write to `AudioTrack`.

File format is length-prefixed binary Opus frame stream.

---

## 9) Loopback Test Flow

Implementation file: `voice/VoiceSimulator.kt`

1. Start session as `LOOPBACK_TEST`.
2. `AudioRecord` uses the shared `AudioController` call-equivalent capture policy for loopback session.
3. Recorder effects are applied by `audioController.configureRecorder(...)`.
4. Captured PCM is processed by `processRecording(...)` then `processPlayback(...)`.
5. Output is buffered with fixed **3-second delay** before writing to `AudioTrack`.

Loopback is diagnostic and intentionally separate from live-call behavior.

---

## 10) Gain / Threshold Configuration Sources

Base values in `meshControl/MeshConfig.kt`:

- `earpieceRecordingGain = 1.0`
- `earpiecePlaybackGain = 1.20`
- `loudspeakerRecordingGain = 1.0`
- `loudspeakerPlaybackGain = 1.35`
- `softLimitThreshold = 0.94`

`AudioController.resolvePolicy()` overrides threshold per session/mode where needed.

---

## 11) Feature-to-Policy Wiring

- `VoiceSessionManager.start()` -> `startSession(VOICE_CALL, forceLoudspeaker=false)`
- `VoiceMessageRecorder.start()` -> `startSession(VOICE_MESSAGE_RECORD, forceLoudspeaker=false)`
- `VoiceMessagePlayer.play()` -> `startSession(VOICE_MESSAGE_PLAYBACK, forceLoudspeaker=true)`
- `VoiceSimulator.start()` -> `startSession(LOOPBACK_TEST, forceLoudspeaker=false)`

This ensures each feature uses its intended capture + DSP profile.

---

## 12) Practical Notes for Quality Tuning

- High speaker-mode gain on phone speakers increases acoustic feedback probability.
- Strong NS/AGC plus high output often causes pumping/rain/static artifacts.
- Device OEM DSP behavior varies; speaker-mode capture may need per-device fallback.
- Loopback speaker testing is a stress test and can sound worse than real two-device calling.

Current implementation intentionally uses:

- moderate playback boost,
- neutral recording gain,
- tighter thresholds on speaker-heavy paths,
- separate loopback diagnostics.
