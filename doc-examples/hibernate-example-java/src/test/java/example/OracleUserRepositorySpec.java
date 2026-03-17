package example;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.transaction.TransactionOperations;
import jakarta.inject.Inject;
import org.hibernate.Session;
import org.hibernate.query.MutationQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@MicronautTest
@Requires(env="oracle")
@DisabledIfEnvironmentVariable(named = "MICRONAUT_ENVIRONMENTS", matches = "h2")
@Property(name = "jpa.default.properties.hibernate.hbm2ddl.auto", value = "create-drop")
class OracleUserRepositorySpec extends UserRepositorySpec {

    @Override
    void seedUsers(long count) {
        String sql = """
            INSERT INTO users(id, name, enabled)
                   SELECT level, 'Name ' || level, true
                   FROM DUAL CONNECT BY LEVEL <= 1 + ?
        """.stripIndent();
        // Execute via Hibernate Session to avoid requiring @Connectable on DataSource
        transactionOperations.executeWrite(status -> {
            Session session = status.getConnection();
            MutationQuery query = session.createNativeMutationQuery(sql);
            query.setParameter(1, count - 1);
            query.executeUpdate();
            return null;
        });
    }
}
