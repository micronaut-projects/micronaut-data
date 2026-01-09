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

import io.micronaut.core.annotation.Internal;
import org.jboss.arquillian.container.spi.client.protocol.ProtocolDescription;
import org.jboss.arquillian.container.spi.client.protocol.metadata.ProtocolMetaData;
import org.jboss.arquillian.container.test.impl.client.protocol.local.LocalDeploymentPackager;
import org.jboss.arquillian.container.test.impl.execution.event.LocalExecutionEvent;
import org.jboss.arquillian.container.test.spi.ContainerMethodExecutor;
import org.jboss.arquillian.container.test.spi.client.deployment.DeploymentPackager;
import org.jboss.arquillian.container.test.spi.client.protocol.Protocol;
import org.jboss.arquillian.container.test.spi.client.protocol.ProtocolConfiguration;
import org.jboss.arquillian.container.test.spi.command.CommandCallback;
import org.jboss.arquillian.core.api.Event;
import org.jboss.arquillian.core.api.Injector;
import org.jboss.arquillian.core.api.Instance;
import org.jboss.arquillian.core.api.annotation.Inject;
import org.jboss.arquillian.test.spi.TestMethodExecutor;
import org.jboss.arquillian.test.spi.TestResult;
import org.jspecify.annotations.NullUnmarked;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

@NullUnmarked
@Internal
final class TckProtocol implements Protocol<TckProtocol.TckProtocolConfiguration> {

    @Inject
    Instance<Injector> injector;

    @Override
    public Class<TckProtocolConfiguration> getProtocolConfigurationClass() {
        return TckProtocolConfiguration.class;
    }

    @Override
    public ProtocolDescription getDescription() {
        return new ProtocolDescription("Micronaut");
    }

    @Override
    public DeploymentPackager getPackager() {
        return new LocalDeploymentPackager();
    }

    @Override
    public ContainerMethodExecutor getExecutor(TckProtocolConfiguration protocolConfiguration,
                                               ProtocolMetaData metaData,
                                               CommandCallback callback) {
        return injector.get().inject(new TckMethodExecutor());
    }

    public static class TckProtocolConfiguration implements ProtocolConfiguration {
    }

    static class TckMethodExecutor implements ContainerMethodExecutor {

        @Inject
        Event<LocalExecutionEvent> event;

        @Inject
        Instance<TestResult> testResult;

        @Inject
        Instance<ClassLoader> classLoaderInstance;

        @Override
        public TestResult invoke(TestMethodExecutor testMethodExecutor) {

            event.fire(new LocalExecutionEvent(new TestMethodExecutor() {

                @Override
                public void invoke(Object... parameters) throws Throwable {
                    ClassLoader loader = Thread.currentThread().getContextClassLoader();
                    try {
                        Thread.currentThread().setContextClassLoader(classLoaderInstance.get());

                        Object actualTestInstance = TckDeployableContainer.testInstance;
                        Method actualMethod = null;
                        try {
                            actualMethod = actualTestInstance.getClass().getMethod(getMethod().getName(),
                                convertToTCCL(getMethod().getParameterTypes()));
                        } catch (NoSuchMethodException e) {
                            // the method should still be present, just not public, let's try declared methods
                            actualMethod = actualTestInstance.getClass().getDeclaredMethod(getMethod().getName(),
                                convertToTCCL(getMethod().getParameterTypes()));
                            actualMethod.setAccessible(true);
                        }
                        try {
                            actualMethod.invoke(actualTestInstance, parameters);
                        } catch (InvocationTargetException e) {
                            Throwable cause = e.getCause();
                            if (cause != null) {
                                throw cause;
                            } else {
                                throw e;
                            }
                        }
                    } finally {
                        Thread.currentThread().setContextClassLoader(loader);
                    }
                }

                @Override
                public Method getMethod() {
                    return testMethodExecutor.getMethod();
                }

                @Override
                public Object getInstance() {
                    return TckDeployableContainer.testInstance;
                }

                @Override
                public String getMethodName() {
                    return testMethodExecutor.getMethod().getName();
                }
            }));
            return testResult.get();
        }

    }

    static Class<?>[] convertToTCCL(Class<?>[] classes) throws ClassNotFoundException {
        return convertToCL(classes, Thread.currentThread().getContextClassLoader());
    }

    static Class<?>[] convertToCL(Class<?>[] classes, ClassLoader classLoader) throws ClassNotFoundException {
        Class<?>[] result = new Class<?>[classes.length];
        for (int i = 0; i < classes.length; i++) {
            if (classes[i].getClassLoader() != classLoader) {
                result[i] = classLoader.loadClass(classes[i].getName());
            } else {
                result[i] = classes[i];
            }
        }
        return result;
    }

}
