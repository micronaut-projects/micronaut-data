package example

import io.micronaut.data.event.EntityEventContext
import io.micronaut.data.event.EntityEventListener
import jakarta.inject.Singleton

// tag::pre-persist-listener[]
@Singleton
class VetoTimestampedRecordListener implements EntityEventListener<TimestampedRecord> {
    @Override
    boolean prePersist(EntityEventContext<TimestampedRecord> context) {
        return context.entity.name != "veto-me"
    }
}
// end::pre-persist-listener[]
