# Summary of Improvements: File Transfer & Voice Features

This document summarizes the core technical improvements and bug fixes applied to the MeshApp for handling file transfers and audio services.

---

## 1. File Transfer System
**Goal:** Fix video/file corruption and prevent memory-related crashes.

### Key Fixes:
*   **Chunk Integrity**: Fixed a bug where the sender would transmit partial, corrupted chunks if the disk read didn't fill the buffer immediately. The new logic ensures every chunk (except the last one) is exactly the expected size.
*   **Streaming Architecture**: Migrated from a "Load-All-at-Once" approach to a **Streaming approach**. The app now reads and sends files chunk-by-chunk from disk.
    *   **Impact**: You can now send large files (like 50s+ MP4 videos) without the app running out of memory (OOM) or crashing.
*   **Precision Reassembly**: The receiver now uses `RandomAccessFile` to write incoming chunks at exact offsets.
    *   **Impact**: Even if the last chunk is smaller, the file is reconstructed with byte-perfect accuracy, ensuring the video remains playable and the checksum matches.

---

## 2. Voice & Audio Standardization
**Goal:** Centralize hardware management, enforce feature priority, and unify audio quality.

### Key Fixes:
*   **Audio Controller (Priority Brain)**: Introduced a central manager that enforces a strict priority hierarchy:
    1.  **Voice Call** (Highest)
    2.  **Voice Message** (Medium)
    3.  **Loopback Test** (Lowest)
    *   **Impact**: Starting a Call will now automatically "preempt" (stop) a Loopback or Voice Message, preventing hardware conflicts.
*   **Lifecycle Management**: Wired the `AudioController` to the `MeshService`.
    *   **Impact**: Turning off the Mesh now explicitly kills all audio processes. This fixes the issue where the green mic indicator stayed on after the app was "closed."
*   **Unified Configuration**: Moved all audio settings (Gain, NS, AEC, AGC) into `MeshConfig.kt`.
    *   **Impact**: Live calls, loopbacks, and voice messages now share consistent, high-quality audio processing logic.

### Standardized Processing:
| Feature | Default Source | Enhancements |
| :--- | :--- | :--- |
| **Voice Call** | `VOICE_COMMUNICATION` | AEC, NS, AGC enabled |
| **Voice Message** | `VOICE_RECOGNITION` | NS enabled, High-fidelity capture |
| **Loopback** | `VOICE_COMMUNICATION` | Mirrors call quality for testing |

---

## 3. Results
*   **Reliability**: Files no longer fail at the last second due to checksum errors.
*   **Stability**: Large video transfers no longer cause the app to crash.
*   **Professionalism**: Hardware (Mic/Speaker) is managed predictably, respecting system privacy indicators.
