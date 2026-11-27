# Security Module

The security module manages local node identity and the public keys of known peers. It provides cryptographic operations that help protect messages exchanged over the mesh.

## Responsibilities

- Create and store the local node identity.
- Store public keys associated with known nodes.
- Encrypt message content and sign message envelopes with P-256 ECDSA.
- Establish ephemeral P-256 ECDH shared secrets and encrypt payloads with AES-GCM.
- Verify signatures on received messages and acknowledgements, and reject messages that fail security checks.

Messaging and other feature modules use this module's security services rather than implementing these operations themselves.
