import type { Drone } from "../types/drone";

interface Props {
  drone: Drone | null;
}

export default function DroneDetails({ drone }: Props) {
  if (!drone) {
    return (
      <aside className="details-panel empty-panel">
        <div className="panel-title">Drone Details</div>
        <p>Select a drone marker or drone from the list.</p>
      </aside>
    );
  }

  return (
    <aside className="details-panel">
      <div className="panel-title">Drone Details</div>
      <h2>{drone.id}</h2>

      <div className="status-row">
        <span>Status</span>
        <span className={`badge ${drone.status.toLowerCase()}`}>{drone.status}</span>
      </div>

      <div className="metric-grid">
        <div><span>Battery</span><strong>{drone.battery}%</strong></div>
        <div><span>Altitude</span><strong>{drone.altitude} m</strong></div>
        <div><span>Latitude</span><strong>{drone.latitude.toFixed(4)}</strong></div>
        <div><span>Longitude</span><strong>{drone.longitude.toFixed(4)}</strong></div>
        <div><span>Speed</span><strong>{drone.speed} km/h</strong></div>
        <div><span>Mission</span><strong>{drone.mission}</strong></div>
      </div>

      <div className="battery-track">
        <div style={{ width: `${drone.battery}%` }} />
      </div>
    </aside>
  );
}