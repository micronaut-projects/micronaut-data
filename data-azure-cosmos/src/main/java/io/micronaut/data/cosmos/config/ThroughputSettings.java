package io.micronaut.data.cosmos.config;

/**
 * Throughput settings for database and container.
 *
 * @author radovanradic
 * @since 4.0.0
 */
public final class ThroughputSettings {

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
