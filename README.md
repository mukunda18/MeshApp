# MeshApp

MeshApp is a decentralized, peer-to-peer (P2P) messaging and real-time media streaming system engineered for the Android ecosystem. It facilitates secure, resilient communication in environments devoid of centralized infrastructure, cellular service, or internet connectivity. By synthesizing an ad-hoc mesh network over local Wi-Fi, MeshApp orchestrates multi-hop packet propagation, enabling a self-organizing and self-healing autonomous network topology.

---

## Core Pillars

*   **Reliability**: Enforced via an AODV (Ad-hoc On-demand Distance Vector) routing engine and reliable TCP unicast primitives for deterministic data delivery.
*   **Security**: Cryptographically secured through End-to-End Encryption (E2EE) utilizing Elliptic Curve Diffie-Hellman (ECDH) key exchange, AES-256-GCM for authenticated confidentiality, and ECDSA for digital signatures.
*   **Low-Latency Media**: Optimized real-time voice streaming using the Opus audio codec over UDP with dynamic jitter buffering and packet loss concealment.
*   **Transparency**: Protocol semantics and network state are exposed via reactive Kotlin Flows for real-time introspection and UI reconciliation.
*   **Decentralization**: An architecturally flat network topology where every node acts as both a host and a router/relay, eliminating single points of failure (SPOFs).

---

## Architecture & Modular Design

The system adheres to a **Layered Modular Architecture** ensuring strict separation of concerns, testability, and decoupled domains.

| Module | Responsibility |
| :--- | :--- |
| **`:app`** | Host application environment, Android `ForegroundService` lifecycle management, and manual **Inversion of Control (IoC / Pure DI)**. |
| **`:ui`** | Declarative Jetpack Compose interface (Material 3), MVVM state management, animated transitions, and reactive UI reconciliation. |
| **`:meshControl`** | Central orchestration engine managing the convergence of transport, routing, audio coordination, and cryptographic layers. |
| **`:messaging`** | Domain-specific chat logic, message deduplication, cryptographic delivery tracking, and transactional SQLite persistence. |
| **`:routing`** | Reactive path discovery (AODV), peer adjacency management, routing table partitioning, and recursive route error invalidation. |
| **`:security`** | Cryptographic core: Android Keystore P-256 keypair management, shared secret derivation (ECDH), AES-GCM encryption, and ECDSA signatures. |
| **`:transport`** | Network I/O abstraction: UDP broadcast for discovery/voice and TCP unicast for reliable session delivery. |
| **`:packetProcessor`** | Binary serialization engine: Deterministic wire-format encoding and decoding. |
| **`:model`** | Unified domain primitives: `NodeId`, `MessageId`, and strongly-typed protocol packet structures. |
| **`:logger`** | Centralized diagnostics utility for protocol event logging, network telemetry, and debugging. |
| **`:filetransfer`** | Encrypted file offers, chunked streaming over TCP, adaptive timeouts, SHA-256 checksums, and transfer state tracking. |
| **`:voice`** | Real-time full-duplex encrypted voice calls, Opus 16 kHz codec, jitter buffering, PLC, DSP soft limiting, and loopback diagnostics. |
| **`:voicemessage`** | Voice-message recording, Opus compression, local storage, audio playback, and chat conversation integration. |

---

## Features & Capabilities

### 1. Encrypted Text Messaging
- **One-to-One Chat**: Direct peer-to-peer messaging across single-hop or multi-hop mesh routes.
- **End-to-End Encryption**: Every message is encapsulated in an authenticated `SecureEnvelope` with ephemeral keys and forward secrecy.
- **Delivery State Tracking**: UI displays live message delivery states (`QUEUED`, `SENT`, `DELIVERED`, `FAILED`).
- **Cryptographic ACKs**: Digitally signed ACK packets confirm remote delivery and update persistent state.
- **Local Persistence**: Full conversation history stored locally in SQLite with automatic deduplication.
- **Peer Directory**: Displays discovered peers, online statuses, custom display names, and route hop counts.

### 2. Real-Time Encrypted Voice Calls
- **Full-Duplex Audio**: Low-latency interactive voice calling over UDP.
- **High-Fidelity Opus Codec**: 16 kHz mono sampling, 20 ms frames (320 samples / 640 bytes PCM), compressed at 16 kbps via Android `MediaCodec`.
- **Per-Call Key Agreement**: Ephemeral ECDH key agreement during call setup deriving directional AES-GCM encryption keys.
- **Jitter Buffer & Packet Loss Concealment (PLC)**: Adaptive jitter buffering (pre-buffer ~80 ms, cap ~200 ms) with repetition-decay PLC for smooth playback over lossy links.
- **Latency & Backpressure Management**: Bounded audio stream queues with drop-oldest overflow policy and sequence tracking to prevent latency buildup.
- **Hardware & DSP Optimizations**: Hardware Acoustic Echo Cancellation (AEC), Noise Suppression (NS), Automatic Gain Control (AGC), and a software soft-knee limiter (`tanh`) to eliminate clipping distortion.
- **In-Band Signaling**: Encrypted call setup and teardown (`OFFER`, `RINGING`, `ACCEPT`, `REJECT`, `BUSY`, `CANCEL`, `HANGUP`).
- **Diagnostic Loopback**: Local mic-to-speaker loopback mode with 3-second delay to test capture, DSP, and playback paths.

### 3. Voice Messages
- **Microphone Recording**: Push-to-record 16 kHz mono voice notes.
- **Opus File Storage**: Frames compressed and saved locally in a length-prefixed binary format with duration metadata.
- **Integrated Playback**: Dedicated audio player streaming decoded frames to communication audio tracks.
- **Chat Integration**: Voice messages are sent and rendered seamlessly in chat threads alongside text messages.

### 4. File Transfer
- **Structured Signaling**: Encrypted negotiation (`OFFER`, `ACCEPT`, `REJECT`, `CANCEL`, `COMPLETE`) carried within secure message envelopes.
- **TCP Chunk Streaming**: Binary `FILE_CHUNK (0x08)` packets streamed sequentially over reliable TCP connections.
- **Adaptive Timeouts**: Dynamic transfer completion timeouts scaled according to file payload size and mesh throughput.
- **Integrity Verification**: Per-chunk and full-file SHA-256 checksum verification.
- **Progress Tracking & Sharing**: Live UI progress bars and system-level file sharing via Android `FileProvider`.

### 5. Audio Session Policy Management
- **Hardware Coordinator**: `AudioController` provides centralized synchronization of microphone and speaker hardware.
- **Session Priority Hierarchy**: `VOICE_CALL` > `VOICE_MESSAGE` > `LOOPBACK` (higher-priority sessions preempt lower-priority audio).
- **Modern Device Routing**: Full support for Android 12+ `setCommunicationDevice` for earpiece and speakerphone toggling.

### 6. Android Application & User Interface
- **Foreground Service**: Background mesh persistence and socket listeners managed via Android `ForegroundService` with notification channels.
- **Modern Jetpack Compose UI**: Clean Material 3 design featuring animated state transitions, dark theme support, and responsive layouts.
- **Diagnostics & Network Telemetry**: Dedicated UI screens for inspecting network interfaces, viewing peer routing tables, and monitoring live protocol logs.

---

## Communication Protocol Specification

MeshApp uses a compact binary wire protocol designed for high throughput and low overhead. Multi-byte integers are serialized in **Big-Endian** byte order.

### 1. Fixed Header (122 Bytes)
Every packet transmitted over the mesh begins with a mandatory 122-byte header:

```
+---------------+---------------+---------------+---------------+
|  Magic (2B)   |  Version (1B) |   Type (1B)   |   Flags (1B)  |
+---------------+---------------+---------------+---------------+
| Hopcount (1B) |    TTL (1B)   |  Reserved(1B) |               |
+---------------+---------------+---------------+               |
|                   Immediate Sender NodeId (32B)               |
+---------------------------------------------------------------+
|                      Source NodeId (32B)                      |
+---------------------------------------------------------------+
|                    Destination NodeId (32B)                   |
+-----------------------------------------------+---------------+
|                Message ID (8B)                |Timestamp (8B) |
+-------------------------------+---------------+---------------+
|       Payload Length (2B)     |                               |
+-------------------------------+-------------------------------+
```

| Offset | Field | Size | Description |
| :--- | :--- | :--- | :--- |
| 0 | Magic | 2B | Protocol identifier: `0x4D45` (ASCII `'ME'`) |
| 2 | Version | 1B | Protocol version (current: `1`) |
| 3 | Type | 1B | Packet type code (see table below) |
| 4 | Flags | 1B | Control bitmask: `0x01: BROADCAST`, `0x02: ENCRYPTED`, `0x04: ACK_REQUESTED` |
| 5 | Hopcount | 1B | Incremented at each forwarding hop |
| 6 | TTL | 1B | Time-To-Live; decremented at each hop (default: `15`) |
| 7 | Reserved | 1B | Alignment byte (`0x00`) |
| 8 | Immediate Sender | 32B | `NodeId` of the transmitting direct neighbor |
| 40 | Source ID | 32B | `NodeId` of the packet originator |
| 72 | Destination ID | 32B | `NodeId` of the target destination node |
| 104 | Message ID | 8B | Globally unique packet identifier |
| 112 | Timestamp | 8B | Unix epoch creation time in milliseconds |
| 120 | Payload Length | 2B | Byte length of encapsulated payload (max 65535) |

### 2. Packet Types

| Type Code | Name | Transport | Description |
| :--- | :--- | :--- | :--- |
| `0x01` | **HELLO** | UDP Broadcast | Neighbor discovery, heartbeat, and partitioned route table synchronization. |
| `0x02` | **MESSAGE** | TCP Unicast | Encrypted application payload (text chat, call signals, file signals). |
| `0x03` | **RREQ** | UDP Broadcast | Route Request flooded to discover an on-demand path to a destination. |
| `0x04` | **RREP** | UDP / TCP | Route Reply sent back along the reverse path with public key and hop count. |
| `0x05` | **ACK** | TCP Unicast | Cryptographically signed delivery acknowledgement. |
| `0x06` | **RERR** | UDP Broadcast | Route Error packet propagating broken link notifications. |
| `0x07` | **VOICE** | UDP Unicast | Encrypted Opus audio frames for interactive calls. |
| `0x08` | **FILE_CHUNK** | TCP Unicast | Binary chunk packets for file transfer assembly. |

### 3. Secure Envelope Layout
Used for all `0x02: MESSAGE` packets:

| Field | Size | Description |
| :--- | :--- | :--- |
| `envVersion` | 1B | Envelope specification version (`1`) |
| `senderNodeId` | 32B | NodeId of the sender |
| `encSymKey` | 91B | P-256 DER ephemeral public key for ECDH |
| `nonce` | 12B | Cryptographic AES-GCM nonce |
| `ciphertextLen` | 4B | Length of encrypted inner block |
| `ciphertext` | Var | AES-GCM encrypted inner plaintext block |
| `signature` | 64B | ECDSA digital signature ($r \parallel s$) over envelope fields |

---

## Cryptographic Security Model

MeshApp implements zero-trust end-to-end security designed for open wireless environments:

1. **Identity & Key Generation**:
   - Each node generates a **NIST P-256 (secp256r1)** keypair stored securely in the hardware-backed **Android Keystore**.
   - A node's permanent identifier (`NodeId`) is derived from the **SHA-256 hash** of its static public key.
2. **End-to-End Encryption (E2EE)**:
   - Senders generate an ephemeral P-256 keypair for each message transmission.
   - An ECDH shared secret is derived with the recipient's static public key and used to derive an AES-256 key.
   - Authenticated encryption is performed using **AES-256-GCM** with a unique 12-byte nonce.
3. **Voice Call Encryption**:
   - Call setup exchanges ephemeral public keys via `CallOffer` and `CallAccept`.
   - Directional transmission and reception keys are derived from the shared secret, ensuring forward secrecy and replay protection via per-packet sequence numbers.
4. **Authenticity & Non-Repudiation**:
   - All message envelopes and delivery ACKs carry **ECDSA digital signatures** verified against the sender's known public key.

---

## Routing Engine (AODV)

The `:routing` module implements a reactive mesh routing protocol:
- **Neighbor Discovery**: Continuous HELLO broadcasts advertise direct-link presence and exchange routing snapshots.
- **Routing Table Partitioning**: HELLO packets automatically fragment large route lists across multiple frames to stay within network MTU limits.
- **Reactive Path Discovery**: When a node attempts to reach an unknown destination, it initiates controlled **RREQ** flooding. The destination or intermediate nodes with fresh routes reply with unicast **RREP** packets.
- **Path Optimization**: Evaluates route freshness timestamps and selects paths minimizing total hop counts.
- **Topology Self-Healing**: Upstream nodes detecting broken TCP connections or unreachable neighbors broadcast **RERR** packets to purge stale routes.

---

## Technical Stack

- **Language**: Kotlin (100% Type-Safe)
- **UI Framework**: Jetpack Compose with Material 3
- **Concurrency & Streams**: Kotlin Coroutines, StateFlow, SharedFlow, Channels
- **Dependency Injection**: **Manual Dependency Injection (Pure DI)** via centralized Composition Root (`AppContainer`)
- **Audio Processing**: Android `MediaCodec` (Opus), `AudioRecord`, `AudioTrack`, `AcousticEchoCanceler`, `NoiseSuppressor`, `AutomaticGainControl`
- **Network Primitives**: Low-level socket orchestration using `DatagramSocket` (UDP broadcast/voice) and `ServerSocket`/`Socket` (TCP unicast/files)
- **Persistence**: Transactional SQLite with asynchronous DAO abstractions
- **Build System**: Gradle Kotlin DSL with multi-module isolation

---

## Software Development Model (SDM)

The project utilizes an **Incremental Process Model** characterized by iterative functional decomposition and asynchronous integration:
1.  **Platform & Shell**: Host environment instantiation, Foreground Service lifecycle, and declarative UI synthesis.
2.  **Orchestration Plane**: State management, reactive messaging logic, and audio session coordination.
3.  **Foundation Layer**: Transport primitives, TCP/UDP sockets, and deterministic binary framing.
4.  **Topology Synthesis**: Autonomous AODV routing, route table partitioning, and proactive peer discovery.
5.  **Security Integration**: Hardware-backed identity, ephemeral ECDH key agreement, and end-to-end cryptographic encapsulation.
6.  **Media Streaming**: Real-time Opus voice calling, jitter handling, and chunked file transfer pipelines.

---

## Getting Started

### Prerequisites
- Android Studio Ladybug (2024.2.1) or newer
- JDK 17+
- Android SDK 34+
- Two or more physical Android devices running Android 8.0 (API 26) or higher

### Installation & Setup

1. **Clone the repository**:
   ```bash
   git clone https://github.com/mukunda18/MeshApp.git
   cd MeshApp
   ```

2. **Open in Android Studio**:
   - Open the project directory in Android Studio.
   - Allow Gradle to sync dependencies and build modules.

3. **Deploy to Devices**:
   - Connect at least two Android devices to the same local Wi-Fi network (or connect devices to a portable Wi-Fi hotspot hosted on one of the phones).
   - Build and run the `:app` module on both devices.

4. **Mesh Operation**:
   - Grant necessary permissions (Audio recording, Notifications, Nearby Wi-Fi devices).
   - Nodes will autonomously discover each other via HELLO broadcasts.
   - Start 1-on-1 encrypted text chats, stream live voice calls, record audio notes, or transfer files across the mesh.

---

## License

This project is licensed under the Apache License 2.0. See the `LICENSE` file for details.
