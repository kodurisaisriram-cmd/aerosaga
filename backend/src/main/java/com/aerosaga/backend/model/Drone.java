package com.aerosaga.backend.model;

public class Drone {

    private String droneId;
    private String name;
    private double batteryLevel;
    private boolean online;
	public Drone(String droneId, String name, double batteryLevel, boolean online) {
		super();
		this.droneId = droneId;
		this.name = name;
		this.batteryLevel = batteryLevel;
		this.online = online;
	}
	public Drone() {
		super();
		// TODO Auto-generated constructor stub
	}
	public String getDroneId() {
		return droneId;
	}
	public void setDroneId(String droneId) {
		this.droneId = droneId;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public double getBatteryLevel() {
		return batteryLevel;
	}
	public void setBatteryLevel(double batteryLevel) {
		this.batteryLevel = batteryLevel;
	}
	public boolean isOnline() {
		return online;
	}
	public void setOnline(boolean online) {
		this.online = online;
	}
    

}