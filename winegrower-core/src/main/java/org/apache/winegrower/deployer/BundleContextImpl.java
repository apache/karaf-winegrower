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
package org.apache.winegrower.deployer;

import static java.util.Arrays.asList;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.jar.Manifest;
import java.util.stream.Stream;

import org.apache.winegrower.service.BundleRegistry;
import org.apache.winegrower.service.OSGiServices;
import org.apache.winegrower.service.ServiceReferenceImpl;
import org.apache.winegrower.service.ServiceRegistrationImpl;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.BundleListener;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceFactory;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceObjects;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;

public class BundleContextImpl implements BundleContext {
    private final Manifest manifest;
    private final OSGiServices services;
    private final Supplier<Bundle> bundleSupplier;
    private final BundleRegistry registry;
    private final Collection<BundleListener> bundleListeners = new ArrayList<>();
    private final Collection<FrameworkListener> frameworkListeners = new ArrayList<>();
    private final Map<ServiceReference<?>, Object> serviceInstances = new HashMap<>();

    BundleContextImpl(final Manifest manifest, final OSGiServices services, final Supplier<Bundle> bundleSupplier,
                      final BundleRegistry registry) {
        this.manifest = manifest;
        this.services = services;
        this.bundleSupplier = bundleSupplier;
        this.registry = registry;
    }

    public BundleRegistry getRegistry() {
        return registry;
    }

    public Collection<BundleListener> getBundleListeners() {
        return bundleListeners;
    }

    public Collection<FrameworkListener> getFrameworkListeners() {
        return frameworkListeners;
    }

    OSGiServices getServices() {
        return services;
    }

    Manifest getManifest() {
        return manifest;
    }

    @Override
    public String getProperty(final String key) {
        return System.getProperty(key);
    }

    @Override
    public Bundle getBundle() {
        return bundleSupplier.get();
    }

    @Override
    public Bundle installBundle(final String location, final InputStream input) throws BundleException {
        throw new BundleException("Unsupported operation");
    }

    @Override
    public Bundle installBundle(final String location) throws BundleException {
        throw new BundleException("Unsupported operation");
    }

    @Override
    public Bundle getBundle(final long id) {
        return ofNullable(registry.getBundles().get(id)).map(OSGiBundleLifecycle::getBundle).orElse(null);
    }

    @Override
    public Bundle[] getBundles() {
        return registry.getBundles().values().stream().map(OSGiBundleLifecycle::getBundle).toArray(Bundle[]::new);
    }

    @Override
    public void addServiceListener(final ServiceListener listener, final String filter) {
        services.addListener(listener, filter == null ? null : createFilter(filter));
    }

    @Override
    public void addServiceListener(final ServiceListener listener) {
        addServiceListener(listener, null);
    }

    @Override
    public void removeServiceListener(final ServiceListener listener) {
        services.removeListener(listener);
    }

    @Override
    public void addBundleListener(final BundleListener listener) {
        bundleListeners.add(listener);
    }

    @Override
    public void removeBundleListener(final BundleListener listener) {
        bundleListeners.remove(listener);
    }

    @Override
    public void addFrameworkListener(final FrameworkListener listener) {
        frameworkListeners.add(listener);
    }

    @Override
    public void removeFrameworkListener(final FrameworkListener listener) {
        frameworkListeners.remove(listener);
    }

    @Override
    public ServiceRegistration<?> registerService(final String[] classes, final Object service, final Dictionary<String, ?> properties) {
        return services.registerService(classes, service, properties, bundleSupplier.get());
    }

    @Override
    public ServiceRegistration<?> registerService(final String clazz, final Object service, final Dictionary<String, ?> properties) {
        return registerService(new String[]{clazz}, service, properties);
    }

    @Override
    public <S> ServiceRegistration<S> registerService(final Class<S> clazz, final S service, final Dictionary<String, ?> properties) {
        return (ServiceRegistration<S>) registerService(clazz.getName(), service, properties);
    }

    @Override
    public <S> ServiceRegistration<S> registerService(final Class<S> clazz, final ServiceFactory<S> factory, final Dictionary<String, ?> properties) {
        return (ServiceRegistration<S>) registerService(clazz.getName(), factory, properties);
    }

    @Override
    public ServiceReference<?>[] getServiceReferences(final String clazz, final String filter) {
        final Filter predicate = filter == null ? null : createFilter(filter);
        final Bundle bundle = getBundle();
        final Class<?> expected;
        try {
            expected = clazz == null ? Object.class : bundle.loadClass(clazz);
        } catch (final ClassNotFoundException e) {
            return new ServiceReference<?>[0];
        }
        return services.getServices().stream()
                .filter(it -> Stream.of(ServiceRegistrationImpl.class.cast(it).getClasses())
                        .map(name -> {
                            try {
                                return bundle.loadClass(name);
                            } catch (final NoClassDefFoundError | ClassNotFoundException e) {
                                return null;
                            }
                        })
                        .filter(Objects::nonNull)
                        .anyMatch(expected::isAssignableFrom))
                .filter(it -> predicate == null || predicate.match(it.getReference()))
                .map(it -> ServiceRegistrationImpl.class.cast(it).getReference())
                .toArray(ServiceReference[]::new);
    }

    @Override
    public ServiceReference<?>[] getAllServiceReferences(final String clazz, final String filter) {
        final Filter predicate = filter == null ? null : createFilter(filter);
        return services.getServices().stream()
                .map(ServiceRegistrationImpl.class::cast)
                .filter(it -> it.getClasses() != null && asList(it.getClasses()).contains(clazz))
                .filter(it -> predicate == null || predicate.match(it.getReference()))
                .map(ServiceRegistration::getReference)
                .toArray(ServiceReference[]::new);
    }

    @Override
    public ServiceReference<?> getServiceReference(final String clazz) {
        return Arrays.stream(getAllServiceReferences(clazz, null))
                     .findFirst().orElse(null);
    }

    @Override
    public <S> ServiceReference<S> getServiceReference(final Class<S> clazz) {
        return (ServiceReference<S>) getServiceReference(clazz.getName());
    }

    @Override
    public <S> Collection<ServiceReference<S>> getServiceReferences(final Class<S> clazz, final String filter) {
        final Filter predicate = filter == null ? null : createFilter(filter);
        return Arrays.stream(getAllServiceReferences(clazz.getName(), filter))
                .map(it ->(ServiceReference<S>) it)
                .filter(it -> predicate == null || predicate.match(it))
                .collect(toList());
    }

    @Override
    public <S> S getService(final ServiceReference<S> reference) {
        final ServiceReferenceImpl ref = ServiceReferenceImpl.class.cast(reference);
        if (Constants.SCOPE_BUNDLE.equals(ref.getProperty(Constants.SERVICE_SCOPE))) {
            return (S) serviceInstances.computeIfAbsent(ref, r -> ref.getReference());
        }
        return (S) ref.getReference();
    }

    @Override
    public boolean ungetService(final ServiceReference<?> reference) {
        final ServiceReferenceImpl serviceReference = ServiceReferenceImpl.class.cast(reference);
        if (Constants.SCOPE_BUNDLE.equals(serviceReference.getProperty(Constants.SERVICE_SCOPE))) {
            return serviceInstances.remove(serviceReference) != null;
        }
        return serviceReference.unget();
    }

    @Override
    public <S> ServiceObjects<S> getServiceObjects(final ServiceReference<S> reference) {
        return new ServiceObjectsImpl<>(ServiceReferenceImpl.class.cast(reference));
    }

    @Override
    public File getDataFile(final String filename) {
        return null;
    }

    @Override
    public Filter createFilter(final String filter) {
        try {
            return FrameworkUtil.createFilter(filter);
        } catch (final InvalidSyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public Bundle getBundle(final String location) {
        return bundleSupplier.get();
    }
}
