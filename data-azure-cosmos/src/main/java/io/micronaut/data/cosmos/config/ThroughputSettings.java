package io.micronaut.data.cosmos.config;


/**
 * Throughput settings for database and container.
 */
public class ThroughputSettings {

    private Integer requestUnits;

    private boolean autoScale;

    public Integer getRequestUnits() {
        return requestUnits;
    }

    public void setRequestUnits(Integer requestUnits) {
        this.requestUnits = requestUnits;
    }

    public boolean isAutoScale() {
        return autoScale;
    }

    public void setAutoScale(boolean autoScale) {
        this.autoScale = autoScale;
    }
}
