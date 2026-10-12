package com.aerosaga.backend.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface DroneActivity {

    @ActivityMethod
    void takeoff(String droneId);

    @ActivityMethod
    void navigate(String droneId, String destination);

    @ActivityMethod
    void dropPackage(String droneId);

    @ActivityMethod
    void returnToBase(String droneId);
}
```