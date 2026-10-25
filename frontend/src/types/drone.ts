export type DroneStatus = "ONLINE" | "OFFLINE" | "RETURNING";
export type MissionStep = "TAKEOFF" | "NAVIGATE" | "DROP PACKAGE" | "RETURN TO BASE" |"OFFLINE";

export interface Drone {
  id: string;
  latitude: number;
  longitude: number;
  altitude: number;
  speed: number;
  battery: number;
  status: DroneStatus;
  mission: MissionStep;
}