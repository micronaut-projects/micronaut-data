package io.micronaut.data.jdbc.h2.one2one;

import io.micronaut.data.annotation.Join;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.jpa.JpaSpecificationExecutor;

import java.util.Optional;

@JdbcRepository(dialect = Dialect.H2)
public interface RecordAssetRepository extends CrudRepository<RecordAsset, RecordAssetId>, JpaSpecificationExecutor<RecordAsset> {

    @Join("metadata")
    Optional<RecordAsset> findById(RecordAssetId id);
}
