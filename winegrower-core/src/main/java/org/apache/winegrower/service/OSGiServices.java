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
package org.apache.winegrower.service;

import static java.util.Arrays.asList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import org.apache.winegrower.ContextualFramework;
import org.apache.winegrower.api.InjectedService;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.PrototypeServiceFactory;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceFactory;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceRegistration;

// holder of all services
public class OSGiServices {
    private final AtomicLong idGenerator = new AtomicLong(1);

    private final Collection<ServiceListenerDefinition> serviceListeners = new ArrayList<>();
    private final Collection<ServiceRegistrationImpl<?>> services = new ArrayList<>();
    private final ContextualFramework framework;

    public OSGiServices(final ContextualFramework framework) {
        this.framework = framework;
    }

    public <T> T inject(final T instance) {
        doInject(instance.getClass(), instance);
        return instance;
    }

    private <T> void doInject(final Class<?> typeScope, final T instance) {
        if (typeScope == null || typeScope == Object.class) {
            return;
        }
        Stream.of(typeScope.getDeclaredFields())
              .filter(field -> field.isAnnotationPresent(InjectedService.class))
              .peek(field -> {
                  if (!field.isAccessible()) {
                      field.setAccessible(true);
                  }
              })
              .forEach(field -> {
                  if (ContextualFramework.class == field.getType()) {
                      try {
                          field.set(instance, framework);
                      } catch (final IllegalAccessException e) {
                          throw new IllegalStateException(e);
                      }
                  } else if (OSGiServices.class == field.getType()) {
                      try {
                          field.set(instance, framework.getServices());
                      } catch (final IllegalAccessException e) {
                          throw new IllegalStateException(e);
                      }
                  } else {
                      services.stream()
                              .filter(it -> asList(it.getClasses()).contains(field.getType().getName()))
                              .findFirst()
                              .ifPresent(reg -> {
                                  try {
                                      field.set(instance, ServiceReferenceImpl.class.cast(reg.getReference()).getReference());
                                  } catch (final IllegalAccessException e) {
                                      throw new IllegalStateException(e);
                                  }
                              });
                  }
              });
        doInject(typeScope.getSuperclass(), instance);
    }

    public synchronized void addListener(final ServiceListener listener, final Filter filter) {
        serviceListeners.add(new ServiceListenerDefinition(listener, filter));
    }

    public synchronized void removeListener(final ServiceListener listener) {
        serviceListeners.removeIf(d -> d.listener == listener);
    }

    public synchronized ServiceRegistration<?> registerService(final String[] classes, final Object service,
                                                               final Dictionary<String, ?> properties,
                                                               final Bundle from) {
        final Hashtable<String, Object> serviceProperties = new Hashtable<>();
        if (properties != null) {
            serviceProperties.putAll(Map.class.cast(properties));
        }
        serviceProperties.put(Constants.OBJECTCLASS, classes);
        serviceProperties.put(Constants.SERVICE_ID, idGenerator.getAndIncrement());
        serviceProperties.put(Constants.SERVICE_BUNDLEID, from.getBundleId());
        if (ServiceFactory.class.isInstance(service)) {
            serviceProperties.put(Constants.SERVICE_SCOPE, PrototypeServiceFactory.class.isInstance(service) ?
                    Constants.SCOPE_PROTOTYPE : Constants.SCOPE_BUNDLE);
        } else {
            serviceProperties.put(Constants.SERVICE_SCOPE, Constants.SCOPE_SINGLETON);
        }

        final ServiceRegistrationImpl<Object> registration = new ServiceRegistrationImpl<>(classes,
                properties, new ServiceReferenceImpl<>(serviceProperties, from, service), reg -> {
            final ServiceEvent event = new ServiceEvent(ServiceEvent.UNREGISTERING, reg.getReference());
            getListeners(reg).forEach(listener -> listener.listener.serviceChanged(event));
            synchronized (OSGiServices.this) {
                services.remove(reg);
            }
        });
        services.add(registration);
        final ServiceEvent event = new ServiceEvent(ServiceEvent.REGISTERED, registration.getReference());
        getListeners(registration).forEach(listener -> listener.listener.serviceChanged(event));
        return registration;
    }

    private Stream<ServiceListenerDefinition> getListeners(final ServiceRegistration<?> reg) {
        return serviceListeners.stream()
                .filter(it -> it.filter == null || it.filter.match(reg.getReference()));
    }

    public synchronized Collection<ServiceRegistration<?>> getServices() {
        return new ArrayList<>(services);
    }

    private static class ServiceListenerDefinition {
        private final ServiceListener listener;
        private final Filter filter;

        private ServiceListenerDefinition(final ServiceListener listener, final Filter filter) {
            this.listener = listener;
            this.filter = filter;
        }

        @Override
        public String toString() {
            return "ServiceListenerDefinition{listener=" + listener + ", filter=" + filter + '}';
        }
    }
}
