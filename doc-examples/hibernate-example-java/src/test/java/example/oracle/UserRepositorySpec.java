package example.oracle;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.hibernate.Session;
import org.hibernate.query.MutationQuery;

@MicronautTest
@Requires(env="oracle")
@Property(name = "jpa.default.properties.hibernate.hbm2ddl.auto", value = "create-drop")
class UserRepositorySpec extends example.UserRepositorySpec {

    @Override
    protected void seedUsers(long count) {
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
