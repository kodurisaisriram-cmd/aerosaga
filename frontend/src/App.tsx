import { useCallback, useEffect, useMemo, useState } from "react";
import Header from "./components/Header";
import CesiumMap from "./components/CesiumMap";
import DroneList from "./components/DroneList";
import DroneDetails from "./components/DroneDetails";
import WorkflowPanel from "./components/WorkflowPanel";
import { initialDrones } from "./data/mockDrones";
import type { Drone } from "./types/drone";

export default function App() {
  const [drones, setDrones] = useState<Drone[]>(initialDrones);
  const [selectedId, setSelectedId] = useState<string | null>("DRONE-07");

  const selectedDrone = useMemo(
    () => drones.find((d) => d.id === selectedId) ?? null,
    [drones, selectedId]
  );

  const selectDrone = useCallback((id: string) => {
    setSelectedId(id);
  }, []);

  // Mock "live telemetry": moves online drones a tiny amount every 2 seconds.
  useEffect(() => {
    const timer = window.setInterval(() => {
      setDrones((current) =>
        current.map((drone) => {
          if (drone.status === "OFFLINE") return drone;

          const direction = drone.id.charCodeAt(drone.id.length - 1) % 2 === 0 ? 1 : -1;
          const nextBattery = Math.max(1, drone.battery - (Math.random() > 0.75 ? 1 : 0));

          return {
            ...drone,
            latitude: drone.latitude + direction * 0.001,
            longitude: drone.longitude + 0.001,
            battery: nextBattery,
          };
        })
      );
    }, 2000);

    return () => window.clearInterval(timer);
  }, []);

  const online = drones.filter((d) => d.status !== "OFFLINE").length;

  return (
    <div className="app">
      <Header online={online} total={drones.length} />

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
            <span className="live-pill">LIVE MOCK TELEMETRY</span>
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

      <footer>
        AeroSaga frontend prototype • React + CesiumJS • Backend/WebSocket integration comes next
      </footer>
    </div>
  );
}