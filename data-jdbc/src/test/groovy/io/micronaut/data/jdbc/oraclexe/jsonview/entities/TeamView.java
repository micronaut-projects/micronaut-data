package io.micronaut.data.jdbc.oraclexe.jsonview.entities;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.JsonView;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.jdbc.annotation.JoinColumn;

import java.util.List;

@JsonView(table = "TEAM", permissions = "INSERT UPDATE DELETE")
public class TeamView {

    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    private Long teamId;

    private String name;

    private Integer points;

    @Relation(Relation.Kind.ONE_TO_MANY)
    @JoinColumn(referencedColumnName = "TEAM_ID", name = "TEAM_ID")
    private List<DriverJsonData> driver;

    public Long getTeamId() {
        return teamId;
    }

    public void setTeamId(Long teamId) {
        this.teamId = teamId;
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

    public List<DriverJsonData> getDriver() {
        return driver;
    }

    public void setDriver(List<DriverJsonData> driver) {
        this.driver = driver;
    }
}
