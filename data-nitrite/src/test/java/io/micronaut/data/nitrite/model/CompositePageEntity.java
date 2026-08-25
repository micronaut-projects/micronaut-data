package io.micronaut.data.nitrite.model;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

/**
 * Composite-identity entity used to page over rows that share one non-unique sort value. The
 * payload is unique per row so a skipped or repeated row is visible in the assertions.
 */
@MappedEntity
public class CompositePageEntity {

    @Id
    private String shard;

    @Id
    private String seq;

    private String sortKey;

    private String payload;

    public CompositePageEntity() {
    }

    public CompositePageEntity(String shard, String seq, String sortKey, String payload) {
        this.shard = shard;
        this.seq = seq;
        this.sortKey = sortKey;
        this.payload = payload;
    }

    public String getShard() {
        return shard;
    }

    public void setShard(String shard) {
        this.shard = shard;
    }

    public String getSeq() {
        return seq;
    }

    public void setSeq(String seq) {
        this.seq = seq;
    }

    public String getSortKey() {
        return sortKey;
    }

    public void setSortKey(String sortKey) {
        this.sortKey = sortKey;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }
}
