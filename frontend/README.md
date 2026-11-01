# AeroSaga Frontend

Beginner-friendly React + CesiumJS frontend prototype for the AeroSaga drone mission project.

## What this prototype already demonstrates

- React + TypeScript dashboard
- CesiumJS 3D globe
- 50 mock drones
- Drone selection
- Drone ID, status, battery, altitude, latitude, longitude and mission
- Mock live GPS movement every 2 seconds
- Mission workflow panel:
  TAKEOFF -> NAVIGATE -> DROP PACKAGE -> RETURN TO BASE
- Offline drone state

## Run in VS Code

1. Install Node.js (LTS).
2. Open this folder in VS Code.
3. Open Terminal.
4. Run:

```bash
npm install
npm run dev
```

5. Open the localhost URL shown by Vite, normally:

http://localhost:5173

## Important

This is the FRONTEND prototype. It uses mock data so you can demonstrate the UI before the backend is ready.

Later, replace the mock telemetry with your team's Spring Boot WebSocket/API.

Do not put Temporal.io code in this frontend. The backend/Temporal teammate should expose workflow state to the frontend.
