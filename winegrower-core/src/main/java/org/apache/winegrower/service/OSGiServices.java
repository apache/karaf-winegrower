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

import static java.util.Collections.list;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import org.apache.winegrower.Ripener;
import org.apache.winegrower.api.InjectedService;
import org.apache.winegrower.deployer.BundleContextImpl;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.PrototypeServiceFactory;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceFactory;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.hooks.service.EventListenerHook;
import org.osgi.framework.hooks.service.FindHook;
import org.osgi.framework.hooks.service.ListenerHook;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ConfigurationListener;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// holder of all services
public class OSGiServices {
    private static final Logger LOGGER = LoggerFactory.getLogger(OSGiServices.class);

    private final AtomicLong idGenerator = new AtomicLong(1);

    private final Collection<ServiceListenerDefinition> serviceListeners = new ArrayList<>();
    private final Collection<ServiceRegistrationImpl<?>> services = new ArrayList<>();
    private final Hooks hooks = new Hooks();
    private final Collection<ConfigurationListener> configurationListeners;
    private final Collection<DefaultEventAdmin.EventHandlerInstance> eventListeners;
    private final Ripener framework;

    public OSGiServices(final Ripener framework,
                        final Collection<ConfigurationListener> configurationListeners,
                        final Collection<DefaultEventAdmin.EventHandlerInstance> eventListeners) {
        this.framework = framework;
        this.configurationListeners = configurationListeners;
        this.eventListeners = eventListeners;
    }

    public Hooks getHooks() {
        return hooks;
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
        final Bundle bundle = framework.getRegistry().getBundles().get(0L).getBundle();
        final ServiceTracker<T, T> tracker = new ServiceTracker<>(bundle.getBundleContext(), type, null);
        tracker.open();
        try {
            return ofNullable(tracker.waitForService(0L));
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    public void addListener(final ServiceListener listener, final Filter filter,
                                         final BundleContext context) {
        synchronized (serviceListeners) {
            serviceListeners.add(new ServiceListenerDefinition(listener, filter, context));
        }
    }

    public void removeListener(final ServiceListener listener) {
        synchronized (serviceListeners) {
            serviceListeners.removeIf(d -> d.listener == listener);
        }
    }

    public ServiceRegistration<?> registerService(final String[] classes, final Object service,
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

        final boolean isConfigListener = Stream.of(classes).anyMatch(it -> it.equals(ConfigurationListener.class.getName()));
        if (isConfigListener) {
            synchronized (configurationListeners) {
                configurationListeners.add(ConfigurationListener.class.cast(service));
            }
        }

        boolean isEventHandler = Stream.of(classes).anyMatch(it -> it.equals(EventHandler.class.getName()));
        final boolean removeEventHandler = isEventHandler;
        final boolean serviceFindHook = Stream.of(classes).anyMatch(it -> it.equals(FindHook.class.getName()));
        final boolean bundleFindHook = Stream.of(classes).anyMatch(it -> it.equals(org.osgi.framework.hooks.bundle.FindHook.class.getName()));
        final boolean eventListenerHook = Stream.of(classes).anyMatch(it -> it.equals(EventListenerHook.class.getName()));
        final ServiceReferenceImpl<Object> ref = new ServiceReferenceImpl<>(serviceProperties, from, service);
        final ServiceRegistrationImpl<Object> registration = new ServiceRegistrationImpl<>(classes,
                serviceProperties, ref, reg -> {
            final ServiceEvent event = new ServiceEvent(ServiceEvent.UNREGISTERING, reg.getReference());
            fireEvent(reg, event);
            synchronized (OSGiServices.this) {
                services.remove(reg);
            }

            if (isConfigListener) {
                synchronized (configurationListeners) {
                    configurationListeners.remove(ConfigurationListener.class.cast(service));
                }
            }
            if (removeEventHandler) {
                synchronized (eventListeners) {
                    eventListeners.removeIf(it -> it.getHandler() == service);
                }
            }
            if (serviceFindHook) {
                hooks.getServiceFindHooks().remove(ref);
            }
            if (bundleFindHook) {
                hooks.getBundleFindHooks().remove(ref);
            }
            if (eventListenerHook) {
                hooks.getEventListenerHooks().remove(ref);
            }
        });

        if (isEventHandler) {
            final Object topics = properties.get(EventConstants.EVENT_TOPIC);
            final String[] topicsArray = String[].class.isInstance(topics) ?
                    String[].class.cast(topics) :
                    (String.class.isInstance(topics) ? new String[]{ String.class.cast(topics)} :
                    Collection.class.isInstance(properties) ?
                            ((Collection<Object>) topics).stream().map(String::valueOf).toArray(String[]::new) :
                            null);
            if (topics == null) {
                LOGGER.warn("No topic for {}", service);
            } else {
                synchronized (eventListeners) {
                    eventListeners.add(new DefaultEventAdmin.EventHandlerInstance(
                            from,
                            EventHandler.class.isInstance(service) ?
                                    EventHandler.class.cast(service) :
                                    new DefaultEventAdmin.EventHandlerFactory(
                                            from, ServiceRegistration.class.cast(registration),
                                            ServiceFactory.class.cast(service)),
                            Stream.of(topicsArray).anyMatch("*"::equals) ? null : topicsArray,
                            ofNullable(properties.get(EventConstants.EVENT_FILTER)).map(String::valueOf).orElse(null)));
                }
            }
        }

        final Object pid = serviceProperties.get(Constants.SERVICE_PID);
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
        }

        final Object factoryPid = serviceProperties.get("service.factoryPid");
        if (factoryPid != null) {
            final ConfigurationAdmin configurationAdmin = framework.getConfigurationAdmin();
            final String pidStr = String.valueOf(factoryPid);
            try {
                final Configuration configuration = configurationAdmin.createFactoryConfiguration(pidStr);
                ofNullable(configuration.getProperties())
                        .ifPresent(prop -> list(prop.keys())
                                .forEach(key -> serviceProperties.put(key, configuration.getProperties().get(key))));
            } catch (final IOException e) {
                LOGGER.warn(e.getMessage());
            }
        }

        services.add(registration);

        final ServiceEvent event = new ServiceEvent(ServiceEvent.REGISTERED, ref);
        if (ManagedService.class.isInstance(service)) {
            try {
                ManagedService.class.cast(service).updated(serviceProperties);
            } catch (final ConfigurationException e) {
                throw new IllegalStateException(e);
            }
        }
        fireEvent(registration, event);

        if (serviceFindHook) {
            hooks.getServiceFindHooks().add(ServiceReference.class.cast(ref));
        }
        if (bundleFindHook) {
            hooks.getBundleFindHooks().add(ServiceReference.class.cast(ref));
        }
        if (eventListenerHook) {
            hooks.getEventListenerHooks().add(ServiceReference.class.cast(ref));
        }

        return registration;
    }

    private void fireEvent(final ServiceRegistration<?> reg, final ServiceEvent event) {
        final List<ServiceListenerDefinition> listeners = getListeners(reg);
        final Collection<ServiceReference<EventListenerHook>> eventListenerHooks = hooks.getEventListenerHooks();
        if (!eventListenerHooks.isEmpty() && !listeners.isEmpty()) {
            eventListenerHooks.forEach(hook -> {
                final BundleContext bundleContext = hook.getBundle().getBundleContext();
                final EventListenerHook instance = bundleContext.getService(hook);
                if (instance != null) {
                    try {
                        final Map<BundleContext, ? extends Collection<ListenerHook.ListenerInfo>> listenerInfo = listeners.stream()
                                .collect(groupingBy(ServiceListenerDefinition::getBundleContext, toList()));
                        instance.event(event, (Map<BundleContext, Collection<ListenerHook.ListenerInfo>>) listenerInfo);
                    } catch (final Throwable th) {
                        LoggerFactory.getLogger(BundleContextImpl.class).warn("Can't call '{}'", hook, th);
                    } finally {
                        bundleContext.ungetService(hook);
                    }
                }
            });
        }
        listeners.forEach(listener -> listener.listener.serviceChanged(event));
    }

    private List<ServiceListenerDefinition> getListeners(final ServiceRegistration<?> reg) {
        final List<ServiceListenerDefinition> copy;
        synchronized (serviceListeners) {
            copy = new ArrayList<>(serviceListeners);
        }
        return copy.stream()
                .filter(it -> it.filter == null || it.filter.match(reg.getReference()))
                .collect(toList());
    }

    public synchronized Collection<ServiceRegistration<?>> getServices() {
        return new ArrayList<>(services);
    }

    private static class ServiceListenerDefinition implements ListenerHook.ListenerInfo {
        private final ServiceListener listener;
        private final Filter filter;
        private final BundleContext context;

        private ServiceListenerDefinition(final ServiceListener listener, final Filter filter,
                                          final BundleContext context) {
            this.listener = listener;
            this.filter = filter;
            this.context = context;
        }

        @Override
        public String toString() {
            return "ServiceListenerDefinition{listener=" + listener + ", filter=" + filter + '}';
        }

        @Override
        public BundleContext getBundleContext() {
            return context;
        }

        @Override
        public String getFilter() {
            return filter.toString();
        }

        @Override
        public boolean isRemoved() {
            return false;
        }
    }
}
