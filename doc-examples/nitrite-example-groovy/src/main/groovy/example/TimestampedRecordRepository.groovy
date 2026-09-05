package example

import io.micronaut.data.nitrite.annotation.NitriteRepository
import io.micronaut.data.repository.CrudRepository

@NitriteRepository
interface TimestampedRecordRepository extends CrudRepository<TimestampedRecord, String> {
}
