
import { useCallback, useEffect, useMemo, useState } from "react";
import Header from "./components/Header";
import CesiumMap from "./components/CesiumMap";
import DroneList from "./components/DroneList";
import DroneDetails from "./components/DroneDetails";
import WorkflowPanel from "./components/WorkflowPanel";
import { initialDrones } from "./data/mockDrones";
import type { Drone } from "./types/drone";

interface Mission {
  missionId: string;
  droneId: string;
  status: string;
}

interface TelemetryMessage {
  droneId: string;
  latitude: number;
  longitude: number;
  altitude: number;
  speed: number;
  battery: number;
  status: string;
  mission: string;
  timestamp: number;
}

export default function App() {
  const [drones, setDrones] = useState<Drone[]>(initialDrones);
  const [selectedId, setSelectedId] = useState<string | null>("DRONE-07");

  const [missions, setMissions] = useState<Mission[]>([]);
  const [loadingMissions, setLoadingMissions] = useState(true);
  const [missionError, setMissionError] = useState("");

  const selectedDrone = useMemo(
    () => drones.find((d) => d.id === selectedId) ?? null,
    [drones, selectedId]
  );

  const selectDrone = useCallback((id: string) => {
    setSelectedId(id);
  }, []);

  // Fetch missions from the Spring Boot REST API.
  useEffect(() => {
    const controller = new AbortController();

    async function loadMissions() {
      try {
        const response = await fetch(
          "http://localhost:8080/api/missions",
          { signal: controller.signal }
        );

        if (!response.ok) {
          throw new Error(`Request failed: ${response.status}`);
        }

        const data: Mission[] = await response.json();

        setMissions(data);

        setDrones((current) =>
          current.map((drone) => {
            const backendMission = data.find(
              (mission) => mission.droneId === drone.id
            );

            if (!backendMission) {
              return drone;
            }

            const missionStep: Drone["mission"] =
              backendMission.status === "RETURNING"
                ? "RETURN TO BASE"
                : backendMission.status === "NAVIGATE"
                ? "NAVIGATE"
                : drone.mission;

            const nextStatus: Drone["status"] =
              backendMission.status === "RETURNING"
                ? "RETURNING"
                : drone.status;

            return {
              ...drone,
              status: nextStatus,
              mission: missionStep,
            };
          })
        );

        setMissionError("");
      } catch (error) {
        if (controller.signal.aborted) return;

        setMissionError(
          error instanceof Error
            ? error.message
            : "Unable to load missions"
        );
      } finally {
        if (!controller.signal.aborted) {
          setLoadingMissions(false);
        }
      }
    }

    loadMissions();

    return () => controller.abort();
  }, []);

  // Receive live telemetry from the Spring Boot WebSocket.
  useEffect(() => {
    let stopped = false;
    let reconnectTimer: number | undefined;
    let socket: WebSocket | undefined;

    function connect() {
      if (stopped) return;

      socket = new WebSocket("ws://localhost:8080/ws/telemetry");

      socket.onopen = () => {
        console.log("Connected to live drone telemetry");
      };

      socket.onmessage = (event: MessageEvent) => {
        try {
          const telemetry: TelemetryMessage = JSON.parse(event.data);

          if (
            !telemetry.droneId ||
            !Number.isFinite(telemetry.latitude) ||
            !Number.isFinite(telemetry.longitude) ||
            !Number.isFinite(telemetry.altitude) ||
            !Number.isFinite(telemetry.speed) ||
            !Number.isFinite(telemetry.battery)
          ) {
            console.warn("Ignoring invalid telemetry message:", event.data);
            return;
          }

          setDrones((current) =>
            current.map((drone) => {
              if (drone.id !== telemetry.droneId) {
                return drone;
              }

              const nextStatus: Drone["status"] =
                telemetry.status === "OFFLINE"
                  ? "OFFLINE"
                  : telemetry.battery <= 10
                  ? "RETURNING"
                  : "ONLINE";

              const nextMission: Drone["mission"] =
  nextStatus === "OFFLINE"
    ? "OFFLINE"
    : telemetry.mission === "RETURN TO BASE" ||
        telemetry.mission === "RETURNING"
      ? "RETURN TO BASE"
      : telemetry.mission === "TAKEOFF"
        ? "TAKEOFF"
        : telemetry.mission === "DROP PACKAGE" ||
            telemetry.mission === "DROP"
          ? "DROP PACKAGE"
          : "NAVIGATE";
          
              return {
                ...drone,
                latitude: telemetry.latitude,
                longitude: telemetry.longitude,
                altitude: telemetry.altitude,
                speed: telemetry.speed,
                battery: telemetry.battery,
                status: nextStatus,
                mission: nextMission,
              };
            })
          );
        } catch (error) {
          console.error("Invalid telemetry message:", error);
        }
      };

      socket.onerror = () => {
        console.error("Drone telemetry WebSocket error");
      };

      socket.onclose = () => {
        if (!stopped) {
          console.log("Telemetry disconnected. Reconnecting in 3 seconds...");
          reconnectTimer = window.setTimeout(connect, 3000);
        }
      };
    }

    connect();

    return () => {
      stopped = true;

      if (reconnectTimer !== undefined) {
        window.clearTimeout(reconnectTimer);
      }

      socket?.close();
    };
  }, []);

  // Keep the Backend Missions section synchronized with displayed drone statuses.
  useEffect(() => {
    setMissions((currentMissions) =>
      currentMissions.map((mission) => {
        const drone = drones.find((d) => d.id === mission.droneId);

        if (!drone) return mission;

        let status = mission.status;

        if (drone.status === "OFFLINE") {
          status = "OFFLINE";
        } else if (drone.status === "RETURNING") {
          status = "RETURNING";
        } else {
          status = drone.mission;
        }

        return {
          ...mission,
          status,
        };
      })
    );
  }, [drones]);

  const online = drones.filter((d) => d.status === "ONLINE").length;
  const offline = drones.filter((d) => d.status === "OFFLINE").length;
  const returning = drones.filter((d) => d.status === "RETURNING").length;

  return (
    <div className="app">
      <Header online={online} total={drones.length} />

      {/* Fleet status summary */}
      <section className="fleet-summary">
        <div className="fleet-summary-card online-card">
          <span>ONLINE</span>
          <strong>{online}</strong>
        </div>

        <div className="fleet-summary-card offline-card">
          <span>OFFLINE</span>
          <strong>{offline}</strong>
        </div>

        <div className="fleet-summary-card returning-card">
          <span>RETURNING</span>
          <strong>{returning}</strong>
        </div>

        <div className="fleet-summary-card total-card">
          <span>TOTAL DRONES</span>
          <strong>{drones.length}</strong>
        </div>
      </section>

      <main className="dashboard">
        <DroneList
          drones={drones}
          selectedId={selectedId}
          onSelect={selectDrone}
        />

        <section className="map-card">
          <div className="map-heading">
            <div>
              <span className="globe-icon">🌍</span>
              <strong>3D EARTH</strong>
            </div>
            <span className="live-pill">LIVE TELEMETRY</span>
          </div>

          <CesiumMap
            drones={drones}
            selectedId={selectedId}
            onSelect={selectDrone}
          />
        </section>

        <DroneDetails drone={selectedDrone} />
      </main>

      <WorkflowPanel drone={selectedDrone} />

      <section style={{ padding: "16px 24px" }}>
        <h3>Backend Missions</h3>

        {loadingMissions && <p>Loading missions...</p>}

        {missionError && (
          <p style={{ color: "tomato" }}>
            Could not load missions: {missionError}
          </p>
        )}

        {!loadingMissions && !missionError && (
          <div>
            {missions.length === 0 && <p>No missions found.</p>}

            {missions.map((mission) => (
              <div key={mission.missionId} style={{ marginBottom: 10 }}>
                <strong>{mission.missionId}</strong>
                {" — "}
                {mission.droneId}
                {" — "}
                {mission.status}
              </div>
            ))}
          </div>
        )}
      </section>

      <footer>
        AeroSaga frontend prototype • React + CesiumJS •
        Mission API connected • WebSocket telemetry enabled
      </footer>
    </div>
  );
}
