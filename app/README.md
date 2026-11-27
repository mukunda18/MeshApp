# App Module

The app module is the Android entry point for MeshApp. It brings the application modules together and manages the Android components that start and display the app.

## Responsibilities

- Create shared application services and dependencies, including identity, security, messaging, file transfer, and voice services.
- Provide Android network sockets to the mesh service.
- Set up the main activity and app theme.
- Run the mesh service as an Android foreground service so it can keep working while the app is not visible.

The app module connects platform-specific Android code to the reusable feature modules.
