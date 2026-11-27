# MeshApp

MeshApp is an Android peer-to-peer messaging application that communicates over a local Wi-Fi mesh. It combines UDP discovery, TCP packet delivery, reactive routing, encrypted chat, live voice calls, voice messages, file transfer, and a Compose user interface without requiring an internet connection.

## Core Pillars

*   **Reliability**: AODV-inspired route discovery, peer management, ACK handling, and TCP unicast delivery.
*   **Security**: Persistent P-256 identities, ECDSA packet signatures, ECDH key agreement, and AES-256-GCM payload encryption.
*   **Transparency**: Kotlin coroutines and Flows expose mesh state, delivery status, peer changes, routing events, conversations, and logs.
*   **Decentralization**: Nodes discover nearby peers and can relay packets for other nodes; the app has no required mesh server.

---

## Architecture & Modules

The system adheres to a **Layered Modular Architecture** ensuring strict separation of concerns and architectural decoupling.

| Module | Responsibility |
| :--- | :--- |
| **`:app`** | Host environment, Android `Service` lifecycle management, and manual **Inversion of Control (IoC)**. |
| **`:ui`** | Declarative Jetpack Compose interface, MVVM state management, and reactive UI reconciliation. |
| **`:meshControl`** | Central orchestrator managing the convergence of transport, routing, and cryptographic layers. |
| **`:messaging`** | Domain-specific chat logic, message deduplication, and transactional SQLite persistence. |
| **`:routing`** | Reactive path discovery (AODV), peer management, and recursive route invalidation. |
| **`:security`** | Cryptographic core: P-256 key management, shared secret derivation, and signature verification. |
| **`:transport`** | Network I/O abstraction: UDP broadcast for discovery and TCP unicast for reliable sessions. |
| **`:packetProcessor`** | Binary serialization engine: Deterministic wire-format encoding and decoding. |
| **`:model`** | Unified domain primitives: `NodeId`, `MessageId`, and strongly-typed packet structures. |
| **`:logger`** | Centralized diagnostics utility for protocol event logging and system telemetry. |
| **`:voice`** | Live voice call signaling, call state, audio processing, and encrypted voice frames. |
| **`:voicemessage`** | Microphone recording, local voice-message files, playback, and shared audio sessions. |
| **`:filetransfer`** | File offers, chunk transfer, checksums, retry handling, and transfer persistence. |

---

## Communication Protocol

MeshApp utilizes a bespoke binary protocol optimized for low-latency overhead and high-density packet throughput in autonomous environments.

### 1. Fixed Header (122 Bytes)
Every packet starts with a mandatory 122-byte deterministic header. Multi-byte integers use network byte order.

| Offset | Field | Size | Description |
| :--- | :--- | :--- | :--- |
| 0 | Magic | 2B | Protocol ID: `0x4D45` ('ME') |
| 2 | Version | 1B | Semantic Protocol Versioning |
| 3 | Type | 1B | 01:HELLO, 02:MSG, 03:RREQ, 04:RREP, 05:ACK, 06:RERR |
| 4 | Flags | 1B | 0x01:Broadcast, 0x02:Encrypted, 0x04:Ack-Requested |
| 5 | Hopcount | 1B | Incremental relay traversal counter |
| 6 | TTL | 1B | Maximum propagation depth (Default: 15) |
| 8 | Imm. Sender | 32B | NodeId of the immediate hop transmitter |
| 40 | Source ID | 32B | NodeId of the packet originator |
| 72 | Dest ID | 32B | NodeId of the terminal recipient |
| 104 | Message ID | 8B | Globally unique identifier for deduplication and ACKs |
| 112 | Timestamp | 8B | Unix epoch creation time for temporal verification |
| 120 | Payload Len | 2B | Byte-count of the encapsulated payload |

The implementation also reserves header type `0x07` for voice packets and
`0x08` for file chunks.

### 2. Packet Types
*   **HELLO (0x01)**: Peer discovery and topology synchronization. Encapsulates identity and routing vector snapshots.
*   **MSG (0x02)**: Encrypted messaging payload utilizing the Secure Envelope format.
*   **RREQ / RREP (0x03/0x04)**: Reactive path discovery via controlled flooding and targeted response.
*   **ACK (0x05)**: Positive acknowledgment of successful packet delivery.
*   **RERR (0x06)**: Asynchronous route error propagation for topology invalidation.
*   **VOICE (0x07)**: An encrypted live-call voice frame.
*   **FILE_CHUNK (0x08)**: A chunk of an ongoing file transfer.

---

## Security Implementation

The cryptographic security model ensures Confidentiality, Integrity, Authenticity, and Non-repudiation with Anti-Replay mechanisms.

### 1. Identity & Key Management
- **Asymmetric Cryptography**: Utilizing **NIST P-256 (secp256r1)** for persistent identity generation and packet signatures.
- **NodeId Derivation**: A node's immutable identifier is synthesized via a **SHA-256 hash** of its Static Public Key.
- **Identity Persistence**: Identity data is AES-GCM encrypted by an Android Keystore-backed master key before being stored in SharedPreferences.

### 2. End-to-End Encryption (E2EE)
Payloads are encapsulated within a multi-layered **Secure Envelope**:
1.  **Key Encapsulation Mechanism (KEM)**: Utilizes an **Ephemeral P-256 Keypair** per transmission.
2.  **Shared Secret Derivation**: Derived via ECDH using the sender's ephemeral and recipient's static keys.
3.  **Authenticated Encryption**: Utilizes **AES-256-GCM** with a cryptographically secure 12-byte random nonce.
4.  **Forward Secrecy**: The use of ephemeral key material ensures that long-term key compromise does not affect past session security.

---

## Routing Engine (AODV-Inspired)

The `:routing` module implements a reactive protocol optimized for highly dynamic ad-hoc network topologies:
- **Neighbor Discovery**: Continuous HELLO heartbeats maintain direct-link adjacency lists.
- **On-Demand Path Synthesis**: Destination-specific routes are synthesized via **RREQ** flooding only when required.
- **Route Selection**: Path optimization is achieved through hop-count minimization and sequence number freshness.
- **Topology Recovery**: Upstream nodes propagate **RERR** packets to flush stale routing entries upon link failure detection.

---

## Technical Stack

- **Language**: Kotlin (100% Type-safe)
- **UI Architecture**: Jetpack Compose (Declarative Reconciliation)
- **Concurrency Model**: Kotlin Coroutines & Structured Concurrency (Flow)
- **Dependency Injection**: **Manual Dependency Injection (Pure DI)** via a centralized **Composition Root** (`AppContainer`).
- **Persistence Layer**: Transactional SQLite with asynchronous persistence abstractions.
- **Network Primitives**: Low-level socket orchestration using `DatagramSocket` (UDP) and `ServerSocket`/`Socket` (TCP).
- **Build Orchestration**: Gradle Kotlin DSL with multi-module encapsulation.

---

## Software Development Model (SDM)

The project utilized an **Incremental Process Model** characterized by iterative functional decomposition and asynchronous integration:
1.  **Platform & Shell**: Host environment instantiation and declarative UI synthesis.
2.  **Orchestration Plane**: State management and reactive messaging logic.
3.  **Foundation Layer**: Transport primitives and deterministic binary framing.
4.  **Topology Synthesis**: Autonomous routing and proactive peer discovery.
5.  **Security Integration**: Hardware-backed identity and end-to-end cryptographic encapsulation.

---

## Getting Started

1.  **Clone the repository**.
2.  **Install JDK 21** and open the project in Android Studio.
3.  **Build with Gradle**: `./gradlew assembleDebug`
4.  **Deploy** to at least two Android devices on the same Wi-Fi subnet.
5.  **Grant permissions** for local networking and, for voice features, microphone access.
6.  **Discovery**: Nodes will autonomously discover peers via HELLO broadcasts.
7.  **Communication**: Establish a conversation with any discovered node.

---

*Note: This project is currently in v1.0.0-alpha. Protocol specifications are subject to architectural evolution.*
