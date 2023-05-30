package io.micronaut.data.jdbc.oraclexe.jsonview.entities;

import io.micronaut.data.annotation.Embeddable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.JsonData;
import io.micronaut.data.annotation.JsonViewColumn;

@JsonData(table = "DRIVER", permissions = "INSERT UPDATE")
@Embeddable
public class DriverJsonData {

    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    private Long driverId;

    private String name;

    @JsonViewColumn(permissions = "NOCHECK")
    private Integer points;

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
}
