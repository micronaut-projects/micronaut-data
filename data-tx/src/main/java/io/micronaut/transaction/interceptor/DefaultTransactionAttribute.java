/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.transaction.interceptor;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.transaction.support.DefaultTransactionDefinition;

import java.util.Arrays;

/**
 * @author graemerocher
 * @since 1.0
 * @deprecated Class is not needed anymore
 */
@Deprecated
public class DefaultTransactionAttribute extends DefaultTransactionDefinition implements TransactionAttribute {

    private String qualifier;

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
        setDontRollbackOn(Arrays.asList(noRollbackFor));
    }

    @Nullable
    @Override
    public String getQualifier() {
        return qualifier;
    }

    @Override
    public boolean rollbackOn(Throwable ex) {
        return super.rollbackOn(ex);
    }

}
