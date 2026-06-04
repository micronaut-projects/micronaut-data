package io.micronaut.data.nitrite.listener;

import io.micronaut.data.event.EntityEventContext;
import io.micronaut.data.event.EntityEventListener;
import io.micronaut.data.nitrite.model.TimestampedRecord;
import jakarta.inject.Singleton;

@Singleton
public class VetoTimestampedRecordListener implements EntityEventListener<TimestampedRecord> {

    @Override
    public boolean prePersist(EntityEventContext<TimestampedRecord> context) {
        return !"veto-me".equals(context.getEntity().getName());
    }
}
