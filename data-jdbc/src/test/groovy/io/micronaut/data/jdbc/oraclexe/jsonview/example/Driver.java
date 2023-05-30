package io.micronaut.data.jdbc.oraclexe.jsonview.example;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;

@MappedEntity
public class Driver {

    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    private Long driverId;

    private String name;

    private Integer points;

    @ManyToOne(fetch = FetchType.LAZY)
    private Team team;

    public Long getDriverId() {
        return driverId;
    }

    public void setDriverId(Long driverId) {
        this.driverId = driverId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getPoints() {
        return points;
    }

    public void setPoints(Integer points) {
        this.points = points;
    }

    public Team getTeam() {
        return team;
    }

    public void setTeam(Team team) {
        this.team = team;
    }
}
