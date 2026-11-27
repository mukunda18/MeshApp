# File Transfer Module

The file transfer module manages sending and receiving files between mesh peers. It tracks each transfer from the initial offer through completion or cancellation.

## Responsibilities

- Create and handle file transfer offers and responses.
- Split file data into chunks for transfer and check file data with checksums.
- Track transfer state and report progress, completion, failure, or cancellation.
- Keep transfer records through the module's file transfer store.

The module uses the mesh and messaging services to exchange transfer data and signals. It does not provide the chat screens or general network routing.
