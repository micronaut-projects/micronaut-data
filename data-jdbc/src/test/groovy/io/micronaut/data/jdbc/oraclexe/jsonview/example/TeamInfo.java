package io.micronaut.data.jdbc.oraclexe.jsonview.example;

import io.micronaut.data.annotation.Embeddable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.JsonData;
import io.micronaut.data.annotation.JsonViewColumn;

@JsonData(table = "TEAM", permissions = "NOINSERT NOUPDATE NODELETE")
@Embeddable
public class TeamInfo {

    @Id
    private Long teamId;

    @JsonViewColumn(permissions = "NOCHECK")
    private String name;

    public void setTeamId(Long teamId) {
        this.teamId = teamId;
    }

    public void setName(String name) {
        this.name = name;
    }
}
