package example;

import example.OperationLog;
import example.OperationLogRepository;
import io.micronaut.runtime.EmbeddedApplication;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

import jakarta.inject.Inject;

import java.util.Optional;
import java.util.UUID;

@MicronautTest
class MnHibUuidTest {

    @Inject
    EmbeddedApplication<?> application;

    @Inject
    OperationLogRepository repository;

    @Test
    void testItWorks() {
        OperationLog operationLog = new OperationLog();
        operationLog.setId(UUID.randomUUID());
        operationLog.setTenant("a1");
        repository.save(operationLog);
        Optional<OperationLog> opt = repository.findOneByIdAndTenant(operationLog.getId(), operationLog.getTenant());
        Assertions.assertTrue(opt.isPresent());
    }

}
