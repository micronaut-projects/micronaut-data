package example

import io.micronaut.data.event.EntityEventContext
import io.micronaut.data.event.EntityEventListener
import jakarta.inject.Singleton

// tag::pre-persist-listener[]
@Singleton
class VetoTimestampedRecordListener : EntityEventListener<TimestampedRecord> {
    override fun prePersist(context: EntityEventContext<TimestampedRecord>): Boolean {
        return context.entity.name != "veto-me"
    }
}
// end::pre-persist-listener[]
