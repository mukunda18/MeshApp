# Voice Module

The voice module manages live voice calls between mesh peers. It handles call signaling and state, then runs the audio session when a call is active.

## Responsibilities

- Start, accept, reject, and end calls.
- Track call states such as dialing, ringing, and active.
- Encode and decode audio for transmission.
- Protect voice sessions and send audio through the mesh service.

The module relies on the messaging, mesh, and audio services to connect callers and access device audio.
