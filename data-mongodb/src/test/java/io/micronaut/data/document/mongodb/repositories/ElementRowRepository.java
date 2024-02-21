package io.micronaut.data.document.mongodb.repositories;

import io.micronaut.data.document.mongodb.entities.ElementRow;
import io.micronaut.data.mongodb.annotation.MongoAggregateQuery;
import io.micronaut.data.mongodb.annotation.MongoRepository;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.serde.annotation.Serdeable;
import org.bson.types.ObjectId;

import java.util.List;
import java.util.Map;

@MongoRepository
public interface ElementRowRepository extends CrudRepository<ElementRow, ObjectId> {

    @MongoAggregateQuery("[{ $match: {$and: [{ eventId: :eventId}, {rowState: :rowState}] } },"
        + "{ $group: { _id: '$subType', count: { $sum: 1 }, total: { $sum: 1 } } },"
        + "{ $group: { _id: null, segregatedCount: { $push: { k: '$_id', v: '$count'} }, totalCount: { $sum: '$total'}} },"
        + "{ $project: { _id: 0, segregatedCount: { $arrayToObject: '$segregatedCount'}, totalCount: 1} } ]")
    ElementCountResponse customAggregateCount(long eventId, String rowState);

    @MongoAggregateQuery("[{ $match: {subType: :subType} },"
        + "{$group: {_id: '', eventIds: {$push: '$eventId'}}}, {$project: {eventIds:  1, _id:  0}}]")
    ElementEventIdsResponse customAggregateEventIds(String subType);

    @Serdeable
    class ElementCountResponse {
        private long totalCount;

        private Map<String, Long> segregatedCount;

        public long getTotalCount() {
            return totalCount;
        }

        public void setTotalCount(long totalCount) {
            this.totalCount = totalCount;
        }

        public Map<String, Long> getSegregatedCount() {
            return segregatedCount;
        }

        public void setSegregatedCount(Map<String, Long> segregatedCount) {
            this.segregatedCount = segregatedCount;
        }
    }

    @Serdeable
    class ElementEventIdsResponse {
        private List<Long> eventIds;

        public List<Long> getEventIds() {
            return eventIds;
        }

        public void setEventIds(List<Long> eventIds) {
            this.eventIds = eventIds;
        }
    }
}
