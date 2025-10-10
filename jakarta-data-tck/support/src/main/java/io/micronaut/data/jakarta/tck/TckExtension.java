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
import org.jboss.arquillian.container.spi.client.container.DeployableContainer;
import org.jboss.arquillian.container.test.spi.client.deployment.ApplicationArchiveProcessor;
import org.jboss.arquillian.container.test.spi.client.protocol.Protocol;
import org.jboss.arquillian.core.spi.LoadableExtension;

/**
 * TCK loadable extension.
 */
@Internal
public class TckExtension implements LoadableExtension {

    @Override
    public void register(ExtensionBuilder builder) {
//        SLF4JBridgeHandler.removeHandlersForRootLogger();
//        SLF4JBridgeHandler.install();
//        Logger.getLogger("").setLevel(Level.FINEST);
        builder.service(ApplicationArchiveProcessor.class, TCKArchiveProcessor.class);
        builder.service(DeployableContainer.class, TckDeployableContainer.class);
        builder.service(Protocol.class, TckProtocol.class);
        builder.observer(TckObserver.class);
    }

}
