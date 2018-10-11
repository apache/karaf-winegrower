/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.karaf.framework.service;

import java.util.ArrayList;
import java.util.Collection;

import org.osgi.framework.ServiceListener;

// holder of all services
public class OSGiServices {
    private final Collection<ServiceListenerDefinition> serviceListeners = new ArrayList<>();

    public synchronized void addListener(final ServiceListener listener, final String filter) {
        serviceListeners.add(new ServiceListenerDefinition(listener, filter));
    }

    public synchronized void removeListener(final ServiceListener listener) {
        serviceListeners.removeIf(d -> d.listener == listener);
    }

    private static class ServiceListenerDefinition {
        private final ServiceListener listener;
        private final String filter;

        private ServiceListenerDefinition(final ServiceListener listener, final String filter) {
            this.listener = listener;
            this.filter = filter;
        }
    }
}
