package io.micronaut.data.cosmos.config;

public class CosmosContainerSettings {

    private String partitionKeyPath;

    private CosmosDatabaseConfiguration.ThroughputSettings throughputSettings;

    public String getPartitionKeyPath() {
        return partitionKeyPath;
    }

    public void setPartitionKeyPath(String partitionKeyPath) {
        this.partitionKeyPath = partitionKeyPath;
    }

    public CosmosDatabaseConfiguration.ThroughputSettings getThroughputSettings() {
        return throughputSettings;
    }

    public void setThroughputSettings(CosmosDatabaseConfiguration.ThroughputSettings throughputSettings) {
        this.throughputSettings = throughputSettings;
    }
}
