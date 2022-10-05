package io.micronaut.data.cosmos.config;

public class CosmosContainerSettings {

    private String partitionKeyPath;

    private ThroughputSettings throughputSettings;

    public String getPartitionKeyPath() {
        return partitionKeyPath;
    }

    public void setPartitionKeyPath(String partitionKeyPath) {
        this.partitionKeyPath = partitionKeyPath;
    }

    public ThroughputSettings getThroughputSettings() {
        return throughputSettings;
    }

    public void setThroughputSettings(ThroughputSettings throughputSettings) {
        this.throughputSettings = throughputSettings;
    }
}
