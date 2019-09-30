package io.micronaut.data.jdbc.graal;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.TypeHint;
import io.micronaut.jdbc.spring.DataSourceTransactionManagerFactory;
import io.micronaut.jdbc.spring.HibernatePresenceCondition;

import javax.sql.DataSource;

/**
 * Disables transaction aware data source proxy for Graal.
 *
 * @author graemerocher
 * @since 1.0.0
 */
@Internal
@TargetClass(value = DataSourceTransactionManagerFactory.class, innerClass = "TransactionAwareDataSourceListener")
@TypeHint(HibernatePresenceCondition.class)
@Substitute
final class DataSourceListenerSubstitution implements BeanCreatedEventListener<DataSource> {
    /**
     * Constructor replacement.
     */
    @TargetElement(name = TargetElement.CONSTRUCTOR_NAME)
    @Substitute
    DataSourceListenerSubstitution() {
    }

    @Override
    public DataSource onCreated(BeanCreatedEvent<DataSource> event) {
        return event.getBean();
    }
}
