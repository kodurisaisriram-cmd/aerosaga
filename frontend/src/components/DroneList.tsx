import type { Drone } from "../types/drone";

interface Props {
  drones: Drone[];
  selectedId: string | null;
  onSelect: (id: string) => void;
}

export default function DroneList({ drones, selectedId, onSelect }: Props) {
  return (
    <aside className="drone-list">
      <div className="panel-title">Fleet ({drones.length})</div>
      <div className="list-scroll">
        {drones.map((drone) => (
          <button
            key={drone.id}
            className={`drone-row ${selectedId === drone.id ? "selected" : ""}`}
            onClick={() => onSelect(drone.id)}
          >
            <span className={`status-dot ${drone.status.toLowerCase()}`} />
            <span className="drone-name">{drone.id}</span>
            <span className="battery-mini">{drone.battery}%</span>
          </button>
        ))}
      </div>
    </aside>
  );
}