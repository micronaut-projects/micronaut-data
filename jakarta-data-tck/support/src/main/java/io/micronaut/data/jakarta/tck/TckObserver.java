/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.data.jakarta.tck;

import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import org.jboss.arquillian.container.spi.context.annotation.DeploymentScoped;
import org.jboss.arquillian.core.api.InstanceProducer;
import org.jboss.arquillian.core.api.annotation.Inject;
import org.jboss.arquillian.core.api.annotation.Observes;
import org.jboss.arquillian.test.spi.event.suite.After;
import org.jboss.arquillian.test.spi.event.suite.Before;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/**
 * Observe the test events and invoke before/after.
 *
 * @author Denis Stepanov
 */
@NullUnmarked
public class TckObserver {

    @Inject
    @DeploymentScoped
    private InstanceProducer<ApplicationContext> runningApplicationContext;

    public void execute(@Observes Before event) {
        Object testInstance = TckDeployableContainer.testInstance;
        BeanDefinition<?> beanDefinition = runningApplicationContext.get().getBeanDefinition(testInstance.getClass());
        beanDefinition.getExecutableMethods().stream()
            .filter(method -> method.hasAnnotation(BeforeEach.class))
            .forEach(executableMethod -> {
                ((ExecutableMethod) executableMethod).invoke(testInstance);
            });
    }

    public void execute(@Observes After event) {
        Object testInstance = TckDeployableContainer.testInstance;
        BeanDefinition<?> beanDefinition = runningApplicationContext.get().getBeanDefinition(testInstance.getClass());
        beanDefinition.getExecutableMethods().stream()
            .filter(method -> method.hasAnnotation(AfterEach.class))
            .forEach(executableMethod -> {
                ((ExecutableMethod) executableMethod).invoke(testInstance);
            });
    }

}
