# Voice Message Module

The voice message module records and plays back voice messages stored as local audio files. It uses the shared voice codec so recorded audio can be handled in a format compatible with the app's audio features.

## Responsibilities

- Capture audio from the device microphone and encode it into a local file.
- Stop or cancel a recording and report its file and duration.
- Play back a recorded voice message.
- Use the shared audio controller to manage audio sessions.

The module creates and plays local recordings; sending them to another peer is handled by the file transfer module.
