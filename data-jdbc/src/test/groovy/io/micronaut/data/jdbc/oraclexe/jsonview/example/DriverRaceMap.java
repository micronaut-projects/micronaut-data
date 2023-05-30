package io.micronaut.data.jdbc.oraclexe.jsonview.example;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;

@MappedEntity
public class DriverRaceMap {

    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    private Long driverRaceMapId;

    @ManyToOne(fetch = FetchType.LAZY)
    private Race race;

    @ManyToOne(fetch = FetchType.LAZY)
    private Driver driver;

    private Integer position;

    public Long getDriverRaceMapId() {
        return driverRaceMapId;
    }

    public void setDriverRaceMapId(Long driverRaceMapId) {
        this.driverRaceMapId = driverRaceMapId;
    }

    public Race getRace() {
        return race;
    }

    public void setRace(Race race) {
        this.race = race;
    }

    public Driver getDriver() {
        return driver;
    }

    public void setDriver(Driver driver) {
        this.driver = driver;
    }

    public Integer getPosition() {
        return position;
    }

    public void setPosition(Integer position) {
        this.position = position;
    }
}
