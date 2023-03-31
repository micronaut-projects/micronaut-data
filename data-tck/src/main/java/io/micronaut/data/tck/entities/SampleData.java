package io.micronaut.data.tck.entities;

import io.micronaut.core.annotation.Introspected;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SampleData that = (SampleData) o;

        if (grade != that.grade) return false;
        if (!localDateTime.equals(that.localDateTime)) return false;
        if (!uuid.equals(that.uuid)) return false;
        if (!etag.equals(that.etag)) return false;
        if (!Arrays.equals(memo, that.memo)) return false;
        if (!duration.equals(that.duration)) return false;
        if (!description.equals(that.description)) return false;
        return rating.equals(that.rating);
    }

    @Override
    public int hashCode() {
        int result = localDateTime.hashCode();
        result = 31 * result + uuid.hashCode();
        result = 31 * result + etag.hashCode();
        result = 31 * result + Arrays.hashCode(memo);
        result = 31 * result + duration.hashCode();
        result = 31 * result + description.hashCode();
        result = 31 * result + grade;
        result = 31 * result + rating.hashCode();
        return result;
    }
}
