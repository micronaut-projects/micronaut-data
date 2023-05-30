package io.micronaut.data.jdbc.oraclexe.jsonview.entities;

import io.micronaut.data.annotation.Embeddable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.JsonData;

@JsonData(table = "RACE", permissions = "NOINSERT NOUPDATE NODELETE")
@Embeddable
public class RaceInfo {

    @Id
    private Long raceId;

    private String name;

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
}
