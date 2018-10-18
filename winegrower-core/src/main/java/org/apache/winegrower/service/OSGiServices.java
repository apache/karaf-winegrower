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
import static java.util.Collections.list;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import org.apache.winegrower.Ripener;
import org.apache.winegrower.api.InjectedService;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.PrototypeServiceFactory;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceFactory;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.cm.ConfigurationEvent;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ConfigurationListener;
import org.osgi.service.cm.ManagedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// holder of all services
public class OSGiServices {
    private static final Logger LOGGER = LoggerFactory.getLogger(OSGiServices.class);

    private final AtomicLong idGenerator = new AtomicLong(1);

    private final Collection<ServiceListenerDefinition> serviceListeners = new ArrayList<>();
    private final Collection<ServiceRegistrationImpl<?>> services = new ArrayList<>();
    private final Ripener framework;

    public OSGiServices(final Ripener framework) {
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
                  if (Ripener.class == field.getType()) {
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
                      findService(field.getType())
                              .ifPresent(value -> {
                                  try {
                                      field.set(instance, value);
                                  } catch (final IllegalAccessException e) {
                                      throw new IllegalStateException(e);
                                  }
                              });
                  }
              });
        doInject(typeScope.getSuperclass(), instance);
    }

    public <T> Optional<T> findService(final Class<T> type) {
        return services.stream()
                .filter(it -> asList(it.getClasses()).contains(type.getName()))
                .findFirst()
                .map(reg -> (T) ServiceReferenceImpl.class.cast(reg.getReference()).getReference());
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
        final Hashtable<String, Object> serviceProperties = new Hashtable<String, Object>() {
            @Override
            public Object get(final Object key) {
                final String property = System.getProperty(String.valueOf(key));
                return property != null ? property : super.get(key);
            }
        };
        if (properties != null) {
            list(properties.keys()).forEach(key -> serviceProperties.put(key, properties.get(key)));
        }
        serviceProperties.put(Constants.OBJECTCLASS, classes.length == 1 ? classes[0] : classes);
        serviceProperties.put(Constants.SERVICE_ID, idGenerator.getAndIncrement());
        serviceProperties.put(Constants.SERVICE_BUNDLEID, from.getBundleId());
        if (ServiceFactory.class.isInstance(service)) {
            serviceProperties.put(Constants.SERVICE_SCOPE, PrototypeServiceFactory.class.isInstance(service) ?
                    Constants.SCOPE_PROTOTYPE : Constants.SCOPE_BUNDLE);
        } else {
            serviceProperties.put(Constants.SERVICE_SCOPE, Constants.SCOPE_SINGLETON);
        }

        final Object pid = serviceProperties.get("service.pid");
        if (pid != null) {
            final ConfigurationAdmin configurationAdmin = framework.getConfigurationAdmin();
            final String pidStr = String.valueOf(pid);
            try {
                final Configuration configuration = configurationAdmin.getConfiguration(pidStr);
                ofNullable(configuration.getProperties())
                        .ifPresent(prop -> list(prop.keys())
                                .forEach(key -> serviceProperties.put(key, configuration.getProperties().get(key))));
            } catch (final IOException e) {
                LOGGER.warn(e.getMessage());
            }

            if (Stream.of(classes).anyMatch(it -> it.equals(ConfigurationListener.class.getName()))) {
                final ConfigurationEvent event = new ConfigurationEvent(
                        (ServiceReference<ConfigurationAdmin>) services.iterator().next().getReference(),
                        ConfigurationEvent.CM_UPDATED,null, pidStr);
                ConfigurationListener.class.cast(service).configurationEvent(event);
            }
        }

        final ServiceRegistrationImpl<Object> registration = new ServiceRegistrationImpl<>(classes,
                serviceProperties, new ServiceReferenceImpl<>(serviceProperties, from, service), reg -> {
            final ServiceEvent event = new ServiceEvent(ServiceEvent.UNREGISTERING, reg.getReference());
            getListeners(reg).forEach(listener -> listener.listener.serviceChanged(event));
            synchronized (OSGiServices.this) {
                services.remove(reg);
            }
        });
        services.add(registration);
        final ServiceEvent event = new ServiceEvent(ServiceEvent.REGISTERED, registration.getReference());
        if (ManagedService.class.isInstance(service)) {
            try {
                ManagedService.class.cast(service).updated(serviceProperties);
            } catch (final ConfigurationException e) {
                throw new IllegalStateException(e);
            }
        }
        getListeners(registration).forEach(listener -> listener.listener.serviceChanged(event));
        return registration;
    }

    private Collection<ServiceListenerDefinition> getListeners(final ServiceRegistration<?> reg) {
        return serviceListeners.stream()
                .filter(it -> it.filter == null || it.filter.match(reg.getReference()))
                .collect(toList());
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
