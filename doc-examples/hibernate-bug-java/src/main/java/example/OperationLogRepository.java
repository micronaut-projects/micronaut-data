package example;

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.GenericRepository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OperationLogRepository extends GenericRepository<OperationLog, UUIDTenantId> {
    Optional<OperationLog> findOneByIdAndTenant(UUID id, String tenant);

    OperationLog save(OperationLog operationLog);
}
