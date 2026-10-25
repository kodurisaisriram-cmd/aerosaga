
import type { Drone } from "../types/drone";

const missions: Drone["mission"][] = [
  "TAKEOFF",
  "NAVIGATE",
  "DROP PACKAGE",
  "RETURN TO BASE",
];

export const initialDrones: Drone[] = Array.from({ length: 50 }, (_, i) => {
  const status: Drone["status"] =
    i === 6 ? "OFFLINE" : i % 13 === 0 ? "RETURNING" : "ONLINE";

  const mission: Drone["mission"] =
    status === "RETURNING"
      ? "RETURN TO BASE"
      : status === "OFFLINE"
      ? "NAVIGATE"
      : missions[i % missions.length];

  return {
    id: `DRONE-${String(i + 1).padStart(2, "0")}`,
    latitude: 12.85 + (i % 10) * 0.035,
    longitude: 77.45 + Math.floor(i / 10) * 0.045,
    altitude: 80 + (i % 6) * 20,
    speed: 25 + (i % 5) * 5,
    battery: 35 + ((i * 7) % 66),
    status,
    mission,
  };
});
