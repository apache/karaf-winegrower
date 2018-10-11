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
import java.util.Dictionary;

import org.osgi.framework.Bundle;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;

// holder of all services
public class OSGiServices {
    private final Collection<ServiceListenerDefinition> serviceListeners = new ArrayList<>();
    private final Collection<ServiceRegistration<?>> services = new ArrayList<>();

    public synchronized void addListener(final ServiceListener listener, final String filter) {
        serviceListeners.add(new ServiceListenerDefinition(listener, filter));
    }

    public synchronized void removeListener(final ServiceListener listener) {
        serviceListeners.removeIf(d -> d.listener == listener);
    }

    public synchronized ServiceRegistration<?> registerService(final String[] classes, final Object service,
                                                               final Dictionary<String, ?> properties) {
        // TODO: add to services
        return new ServiceRegistration<Object>() { // todo
            private volatile Dictionary<String, ?> props = properties;

            @Override
            public ServiceReference<Object> getReference() {
                return new ServiceReference<Object>() {
                    @Override
                    public Object getProperty(String key) {
                        return null;
                    }

                    @Override
                    public String[] getPropertyKeys() {
                        return new String[0];
                    }

                    @Override
                    public Bundle getBundle() {
                        return null;
                    }

                    @Override
                    public Bundle[] getUsingBundles() {
                        return new Bundle[0];
                    }

                    @Override
                    public boolean isAssignableTo(Bundle bundle, String className) {
                        return false;
                    }

                    @Override
                    public int compareTo(Object reference) {
                        return 0;
                    }
                };
            }

            @Override
            public void setProperties(final Dictionary<String, ?> properties) {
                this.props = properties;
            }

            @Override
            public void unregister() {
                synchronized (OSGiServices.this) {
                    services.remove(this);
                }
            }
        };
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
