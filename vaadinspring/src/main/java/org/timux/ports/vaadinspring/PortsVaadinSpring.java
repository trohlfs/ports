/*
 * Copyright 2018-2020 Tim Rohlfs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.timux.ports.vaadinspring;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.timux.ports.AsyncPolicy;
import org.timux.ports.Ports;

import javax.annotation.PostConstruct;

/**
 * A utility class for functionality specific for the Vaadin/Spring tandem.
 *
 * @since 0.4.0
 */
@Component
public final class PortsVaadinSpring {

    private final ApplicationContext applicationContext;

    private static PortsVaadinSpring self;

    public PortsVaadinSpring(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @PostConstruct
    private void init() {
        self = this;

        // NO_CONTEXT_SWITCHES is the default value, but just to be sure, let's set it here.
        Ports.setAsyncPolicy(AsyncPolicy.COMPONENT_SYNC_SAME_THREAD);
    }

    /**
     * Checks whether all {@link org.timux.ports.Request} ports of all instantiated components are connected.
     *
     * @throws org.timux.ports.PortNotConnectedException If there is a Request port that is not connected.
     */
    public static void verify() {
        PortConnector portConnector = self.applicationContext.getBean(PortConnector.class);
        portConnector.verify();
    }
}
