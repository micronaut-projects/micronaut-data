package example.domain.view;

import com.fasterxml.jackson.annotation.JsonProperty;
import example.Metadata;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.Period;

@Serdeable
public class UsrView {
    private Long usrId;
    private String name;

    private Period ym;

    private Duration ds;

    private Double bd;

    private byte[] memo;

    @JsonProperty("_metadata")
    private Metadata metadata;

    private LocalDateTime ts;


    private LocalDate date;

    private OffsetDateTime tstz;

    public UsrView(Long usrId, String name, Period ym, @Nullable Duration ds, Double bd, @Nullable byte[] memo, Metadata metadata, @Nullable LocalDateTime ts,
                   @Nullable LocalDate date, @Nullable OffsetDateTime tstz) {
        this.usrId = usrId;
        this.name = name;
        this.ym = ym;
        this.ds = ds;
        this.bd = bd;
        this.memo = memo;
        this.metadata = metadata;
        this.ts = ts;
        this.date = date;
        this.tstz = tstz;
    }

    public Long getUsrId() {
        return usrId;
    }

    public void setUsrId(Long usrId) {
        this.usrId = usrId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Period getYm() {
        return ym;
    }

    public void setYm(Period ym) {
        this.ym = ym;
    }

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

    public byte[] getMemo() {
        return memo;
    }

    public void setMemo(byte[] memo) {
        this.memo = memo;
    }

    public Metadata getMetadata() {
        return metadata;
    }

    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }

    public LocalDateTime getTs() {
        return ts;
    }

    public void setTs(LocalDateTime ts) {
        this.ts = ts;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public OffsetDateTime getTstz() {
        return tstz;
    }

    public void setTstz(OffsetDateTime tstz) {
        this.tstz = tstz;
    }
}
