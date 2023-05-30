package io.micronaut.data.jdbc.oraclexe.jsonview.example;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.JsonData;
import io.micronaut.data.annotation.JsonViewColumn;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.jdbc.annotation.JoinColumn;

@JsonData(table = "DRIVER_RACE_MAP", permissions = "INSERT UPDATE NODELETE")
public class DriverRaceJsonData {

    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    private Long driverRaceMapId;

    @JoinColumn(referencedColumnName = "RACE_ID", name = "RACE_ID")
    @Relation(Relation.Kind.EMBEDDED)
    private RaceInfo raceInfo;

    @JsonViewColumn(field = "position")
    private Integer finalPosition;

    public Long getDriverRaceMapId() {
        return driverRaceMapId;
    }

    public void setDriverRaceMapId(Long driverRaceMapId) {
        this.driverRaceMapId = driverRaceMapId;
    }

    public RaceInfo getRaceInfo() {
        return raceInfo;
    }

    public void setRaceInfo(RaceInfo raceInfo) {
        this.raceInfo = raceInfo;
    }

    public Integer getFinalPosition() {
        return finalPosition;
    }

    public void setFinalPosition(Integer finalPosition) {
        this.finalPosition = finalPosition;
    }
}
