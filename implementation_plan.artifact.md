# Implementation Plan: Increase Voice Call Volume and Add Speaker Mode

The user reports that the voice call volume is too low and requests a way to increase it using gain and a new "speaker mode".

## User Review Required

> [!IMPORTANT]
> To increase volume, I will increase the default digital gain. This may introduce some slight saturation/warmth to the audio due to the soft-clipping protection, but it will be significantly louder.

> [!NOTE]
> Speaker mode requires toggling the `AudioManager.setSpeakerphoneOn` state. On some Android devices, this also requires a permission or specific audio mode settings, which are already partially handled in the current implementation.

## Proposed Changes

### [voice] module

#### [MODIFY] [VoiceSessionManager.kt](file:///C:/Users/Lenovo/AndroidStudioProjects/MeshApp_/voice/src/main/java/com/meshapp/voice/VoiceSessionManager.kt)
- Add `isSpeakerOn` and `playbackGain` variables that can be updated dynamically.
- Implement a method to toggle the device speakerphone using `AudioManager`.
- Update the default `playbackGain` to `2.0f` for louder output.
- Apply a `1.5f` gain during recording in `sendFrame` to boost outgoing volume as well.

#### [MODIFY] [VoiceCallManager.kt](file:///C:/Users/Lenovo/AndroidStudioProjects/MeshApp_/voice/src/main/java/com/meshapp/voice/VoiceCallManager.kt)
- Add a method to set the speaker mode, which delegates to the active `VoiceSessionManager`.
- Ensure the speaker state is preserved if a session restarts.

### [ui] module

#### [MODIFY] [HomeUiState.kt](file:///C:/Users/Lenovo/AndroidStudioProjects/MeshApp_/ui/src/main/java/com/meshapp/ui/state/HomeUiState.kt)
- Add `isSpeakerOn: Boolean = false` to `HomeUiState`.

#### [MODIFY] [HomeViewModel.kt](file:///C:/Users/Lenovo/AndroidStudioProjects/MeshApp_/ui/src/main/java/com/meshapp/ui/viewmodel/HomeViewModel.kt)
- Add `toggleSpeaker()` function to update the UI state and notify `VoiceCallManager`.

#### [MODIFY] [VoiceCallOverlay.kt](file:///C:/Users/Lenovo/AndroidStudioProjects/MeshApp_/ui/src/main/java/com/meshapp/ui/screens/voice/VoiceCallOverlay.kt)
- Add a speaker toggle button (e.g., using `Icons.Default.VolumeUp` or `Icons.Default.Speaker`).
- Connect the button to the `toggleSpeaker()` action.

## Verification Plan

### Automated Tests
- I will verify the code compiles after changes.
- Since this involves hardware audio, manual verification is primary.

### Manual Verification
- Deploy the app to a device.
- Start a voice call.
- Toggle the "Speaker" button and verify the audio routes to the main speaker.
- Confirm that the overall volume is louder than before due to increased gain.
- Verify that the `tanh` soft-clipping prevents harsh distortion at high volumes.
