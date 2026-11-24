package example;

import io.micronaut.context.ApplicationContext;
import io.micronaut.data.jdbc.runtime.JdbcOperations;
import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SameNameDataSourceSpec {

	@Test
	void testJdbcAndR2dbcWithSameName() {
		try (ApplicationContext context = ApplicationContext.run(Map.of(
				"datasources.default.url", "jdbc:h2:mem:sameNameJdbcDb;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE",
				"datasources.default.driverClassName", "org.h2.Driver",
				"datasources.default.username", "sa",
				"datasources.default.password", "",
				"datasources.default.dialect", "H2",
				"r2dbc.datasources.default.url", "r2dbc:h2:mem:///sameNameR2dbcDb;DB_CLOSE_DELAY=10",
				"r2dbc.datasources.default.dialect", "H2"
		))) {
			JdbcOperations jdbcOperations = context.getBean(JdbcOperations.class, Qualifiers.byName("default"));
			R2dbcOperations r2dbcOperations = context.getBean(R2dbcOperations.class, Qualifiers.byName("default"));

			assertEquals(
					"H2",
					jdbcOperations.execute(connection -> connection.getMetaData().getDatabaseProductName())
			);
			assertEquals(
					"H2",
					Mono.from(r2dbcOperations.withConnection(connection -> Mono.just(connection.getMetadata().getDatabaseProductName()))).block()
			);
		}
	}

}
