package io.micronaut.data.jdbc.oraclexe.jsonview.entities;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

import java.util.Date;

@MappedEntity
public class Race {

    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    private Long raceId;

    private String name;

    private Integer laps;

    private Date raceDate;

    private String podium;

    public Long getRaceId() {
        return raceId;
    }

    public void setRaceId(Long raceId) {
        this.raceId = raceId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getLaps() {
        return laps;
    }

    public void setLaps(Integer laps) {
        this.laps = laps;
    }

    public Date getRaceDate() {
        return raceDate;
    }

    public void setRaceDate(Date raceDate) {
        this.raceDate = raceDate;
    }

    public String getPodium() {
        return podium;
    }

    public void setPodium(String podium) {
        this.podium = podium;
    }
}
