package io.micronaut.data.age.features;

import com.zaxxer.hikari.HikariDataSource;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

/**
 * A data source defined by the application, with a camel case name that configuration keys do not keep.
 */
@Factory
@Requires(property = "spec.name", value = "AgeDataSourceGraphsSpec")
public class ReportingDataSourceFactory {

    @Singleton
    @Named("reportingDb")
    @Bean(preDestroy = "close")
    HikariDataSource reportingDb(@Property(name = "reporting.url") String url,
                                 @Property(name = "reporting.username") String username,
                                 @Property(name = "reporting.password") String password) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        return dataSource;
    }
}
