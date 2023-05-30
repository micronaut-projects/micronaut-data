package io.micronaut.data.jdbc.oraclexe.jsonview.example;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.JsonView;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.jdbc.annotation.JoinColumn;

import java.util.List;

@JsonView(table = "DRIVER", permissions = "INSERT UPDATE DELETE")
public class DriverView {

    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    private Long driverId;

    private String name;

    private Integer points;

    @JoinColumn(referencedColumnName = "TEAM_ID", name = "TEAM_ID")
    @Relation(Relation.Kind.EMBEDDED)
    private TeamInfo teamInfo;

    @Relation(Relation.Kind.ONE_TO_MANY)
    @JoinColumn(referencedColumnName = "DRIVER_ID", name = "DRIVER_ID")
    private List<DriverRaceJsonData> race;

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

    public TeamInfo getTeamInfo() {
        return teamInfo;
    }

    public void setTeamInfo(TeamInfo teamInfo) {
        this.teamInfo = teamInfo;
    }

    public List<DriverRaceJsonData> getRace() {
        return race;
    }

    public void setRace(List<DriverRaceJsonData> race) {
        this.race = race;
    }
}
