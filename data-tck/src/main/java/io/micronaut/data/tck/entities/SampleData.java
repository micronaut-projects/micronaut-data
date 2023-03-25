package io.micronaut.data.tck.entities;

import io.micronaut.core.annotation.Introspected;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Introspected
public class SampleData {

    private LocalDateTime localDateTime;

    private UUID uuid;

    private String etag;

    private byte[] memo;

    private Duration duration;

    private String description;

    private int grade;

    private Double rating;

    public LocalDateTime getLocalDateTime() {
        return localDateTime;
    }

    public void setLocalDateTime(LocalDateTime localDateTime) {
        this.localDateTime = localDateTime;
    }

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public String getEtag() {
        return etag;
    }

    public void setEtag(String etag) {
        this.etag = etag;
    }

    public byte[] getMemo() {
        return memo;
    }

    public void setMemo(byte[] memo) {
        this.memo = memo;
    }

    public Duration getDuration() {
        return duration;
    }

    public void setDuration(Duration duration) {
        this.duration = duration;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getGrade() {
        return grade;
    }

    public void setGrade(int grade) {
        this.grade = grade;
    }

    public Double getRating() {
        return rating;
    }

    public void setRating(Double rating) {
        this.rating = rating;
    }

}
