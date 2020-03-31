package io.micronaut.transaction.interceptor;

import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.transaction.support.DefaultTransactionDefinition;

import java.util.Set;

/**
 * @author graemerocher
 * @since 1.0
 */
public class DefaultTransactionAttribute extends DefaultTransactionDefinition implements TransactionAttribute {

    private String qualifier;
    private Set<Class<? extends Throwable>> noRollbackFor;

    /**
     * Sets the qualifier to use for this attribute.
     * @param qualifier The qualifier.
     */
    public void setQualifier(String qualifier) {
        this.qualifier = qualifier;
    }

    /**
     * Sets the exceptions that will not cause a rollback.
     * @param noRollbackFor The exceptions
     */
    public void setNoRollbackFor(Class<? extends Throwable>... noRollbackFor) {
        if (ArrayUtils.isNotEmpty(noRollbackFor)) {
            this.noRollbackFor = CollectionUtils.setOf(noRollbackFor);
        }
    }

    @Nullable
    @Override
    public String getQualifier() {
        return qualifier;
    }

    @Override
    public boolean rollbackOn(Throwable ex) {
        if (noRollbackFor == null) {
            // rollback on all exceptions
            return true;
        } else {
            return noRollbackFor.stream().noneMatch(t -> t.isInstance(ex));
        }
    }
}
