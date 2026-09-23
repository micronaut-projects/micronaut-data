/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.data.jdbc.notification.oracle;

import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;

import java.util.Properties;

/**
 * Oracle-specific runtime definition for one {@code @ChangeListener} method.
 *
 * <p>The definition contains the listener method, the mapped table identity, the generated
 * Oracle registration query and properties, the entity loader used to reload non-deleted rows
 * by {@code ROWID}, and the registration renewal policy.</p>
 *
 * @param beanDefinition         The bean definition that owns the listener method.
 * @param method                 The executable listener method.
 * @param tableIdentifier        The persistent Oracle table identifier used for notification matching.
 * @param registrationQuery      The query associated with the Oracle notification registration.
 * @param entityLoader           The loader that resolves current entity state from an Oracle {@code ROWID}.
 * @param registrationProperties The properties passed to the Oracle notification registration.
 * @param renewalPolicy          The timeout and replacement policy for the registration.
 */
record OracleChangeListenerDefinition(BeanDefinition<?> beanDefinition,
                                      ExecutableMethod<?, ?> method,
                                      OracleTableIdentifier tableIdentifier,
                                      String registrationQuery,
                                      OracleChangeListenerEntityLoader<?> entityLoader,
                                      Properties registrationProperties,
                                      OracleChangeNotificationRenewalPolicy renewalPolicy) {
}
