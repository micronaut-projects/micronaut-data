package io.micronaut.data.jdbc.graal;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.context.event.BeanEvent;
import io.micronaut.core.annotation.Internal;
import io.micronaut.jdbc.spring.DataSourceTransactionManagerFactory;

import javax.sql.DataSource;

/**
 * Disables transaction aware data source proxy for Graal.
 *
 * @author graemerocher
 * @since 1.0.0
 */
@Internal
@TargetClass(DataSourceTransactionManagerFactory.class)
final class DataSourceTransactionManagerFactorySubstitutions {
    /**
     * Substitution method.
     * @return A listener that doesn't wrap the data source
     */
    @Substitute
    BeanCreatedEventListener<DataSource> transactionAwareDataSourceListener() {
        return BeanEvent::getBean;
    }
}
