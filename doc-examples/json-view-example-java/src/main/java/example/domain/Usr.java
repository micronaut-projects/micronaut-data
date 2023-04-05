package example.domain;

import example.repository.DsIntervalToDurationConverter;
import example.repository.YmIntervalToPeriodConverter;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;

@MappedEntity
public final class Usr {
    @Id
    private Long id;
    private String name;
    @TypeDef(type = DataType.OBJECT, converter = YmIntervalToPeriodConverter.class)
    private Period ym;
    @TypeDef(type = DataType.OBJECT, converter = DsIntervalToDurationConverter.class)
    private Duration ds;
    private Double bd;
    @MappedProperty("fdate")
    private LocalDate date;
    private LocalDateTime ts;

    public Usr(
        @Id
        Long id,
        String name,
        @TypeDef(type = DataType.OBJECT, converter = YmIntervalToPeriodConverter.class)
        Period ym,
        @TypeDef(type = DataType.OBJECT, converter = DsIntervalToDurationConverter.class)
        Duration ds,
        Double bd,
        LocalDate date,
        LocalDateTime ts
    ) {
        this.id = id;
        this.name = name;
        this.ym = ym;
        this.ds = ds;
        this.bd = bd;
        this.date = date;
        this.ts = ts;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @TypeDef(type = DataType.OBJECT, converter = YmIntervalToPeriodConverter.class)
    public Period getYm() {
        return ym;
    }

    public void setYm(Period ym) {
        this.ym = ym;
    }

    @TypeDef(type = DataType.OBJECT, converter = DsIntervalToDurationConverter.class)
    public Duration getDs() {
        return ds;
    }

    public void setDs(Duration ds) {
        this.ds = ds;
    }

    public Double getBd() {
        return bd;
    }

    public void setBd(Double bd) {
        this.bd = bd;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public LocalDateTime getTs() {
        return ts;
    }

    public void setTs(LocalDateTime ts) {
        this.ts = ts;
    }
}
