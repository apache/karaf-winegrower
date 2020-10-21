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
package org.apache.winegrower.framework;

import org.apache.winegrower.Ripener;
import org.apache.winegrower.deployer.BundleContextImpl;
import org.apache.winegrower.deployer.BundleImpl;
import org.apache.winegrower.deployer.OSGiBundleLifecycle;
import org.apache.winegrower.scanner.StandaloneScanner;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.Version;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collector;
import java.util.stream.Stream;

import static java.util.Optional.ofNullable;

public class WinegrowerFramework implements Framework {
    private volatile int state = INSTALLED;

    private final AtomicLong bundleIdGenerator = new AtomicLong(1);

    private Ripener ripener;
    private Ripener.Configuration configuration = new Ripener.Configuration();
    private FrameworkListener[] listeners;
    private BundleImpl frameworkBundle;

    public WinegrowerFramework() {
        configuration.setLazyInstall(!Boolean.getBoolean("winegrower.framework.lazyInstall.skip"));
    }

    public void setConfiguration(final Ripener.Configuration configuration) {
        this.configuration = configuration;
        this.configuration.fromProperties(System.getProperties());
    }

    public void setConfigurationProperties(final Properties configuration) {
        this.configuration.fromProperties(configuration);
    }

    @Override
    public void init() {
        init(new FrameworkListener[0]);
    }

    @Override
    public void init(final FrameworkListener... listeners) {
        ripener = Ripener.create(configuration);
        frameworkBundle = ripener.getRegistry().getBundles().get(0L).getBundle();
        BundleContextImpl.class.cast(getBundleContext()).setInstaller(this::installBundle);
        this.listeners = listeners;
        state = INSTALLED;
        fireFrameworkEvent(null);
    }

    @Override
    public FrameworkEvent waitForStop(final long timeout) throws InterruptedException {
        final Clock clock = Clock.systemUTC();
        final Instant end = clock.instant().plusMillis(timeout);
        while (clock.instant().isBefore(end)) {
            switch (state) {
                case ACTIVE:
                case RESOLVED:
                case INSTALLED:
                    Thread.sleep(250);
                default:
                    break;
            }
        }
        return new FrameworkEvent(state, getFrameworkBundle(), null);
    }

    @Override
    public void start() throws BundleException {
        start(ACTIVE);
    }

    @Override
    public int getState() {
        return state;
    }

    @Override
    public void start(final int options) throws BundleException {
        state = STARTING;
        fireFrameworkEvent(null);
        try {
            ripener.start();
            state = ACTIVE;
            fireFrameworkEvent(null);
        } catch (final RuntimeException re) {
            state = UNINSTALLED;
            fireFrameworkEvent(re);
            throw re;
        } catch (final Exception e) {
            state = UNINSTALLED;
            fireFrameworkEvent(e);
            throw new BundleException(e.getMessage(), e);
        }
    }

    @Override
    public void stop() throws BundleException {
        stop(STOP_TRANSIENT);
    }

    @Override
    public void stop(final int options) throws BundleException {
        state = STOPPING;
        fireFrameworkEvent(null);
        try {
            ripener.stop();
            state = STOP_TRANSIENT;
            fireFrameworkEvent(null);
        } catch (final RuntimeException re) {
            state = UNINSTALLED;
            fireFrameworkEvent(re);
            throw re;
        } catch (final Exception e) {
            state = UNINSTALLED;
            fireFrameworkEvent(e);
            throw new BundleException(e.getMessage(), e);
        }
    }

    @Override
    public void uninstall() {
        state = Framework.UNINSTALLED;
        final Map<Long, OSGiBundleLifecycle> bundles = ripener.getRegistry().getBundles();
        bundles.entrySet().stream()
                .filter(it -> it.getKey() > 0)
                .forEach(e -> e.getValue().stop());
        final OSGiBundleLifecycle fwk = bundles.remove(0L);
        bundles.clear();
        bundles.put(0L, fwk);
        fireFrameworkEvent(null);
    }

    @Override
    public Dictionary<String, String> getHeaders() {
        return getHeaders(null);
    }

    @Override
    public void update() {
        // no-op
    }

    @Override
    public void update(final InputStream in) {
        // no-op
    }

    @Override
    public long getBundleId() {
        return 0;
    }

    @Override
    public String getLocation() {
        return "system";
    }

    @Override
    public ServiceReference<?>[] getRegisteredServices() {
        return ripener.getServices().getServices().stream()
                .map(ServiceRegistration::getReference)
                .toArray(ServiceReference[]::new);
    }

    @Override
    public ServiceReference<?>[] getServicesInUse() {
        return getRegisteredServices();
    }

    @Override
    public boolean hasPermission(final Object permission) {
        return true;
    }

    @Override
    public URL getResource(final String name) {
        return getFrameworkBundle().getResource(name);
    }

    @Override
    public Dictionary<String, String> getHeaders(final String locale) {
        return getFrameworkBundle().getHeaders(locale);
    }

    @Override
    public String getSymbolicName() {
        return getFrameworkBundle().getSymbolicName();
    }

    @Override
    public Class<?> loadClass(final String name) throws ClassNotFoundException {
        return getFrameworkBundle().loadClass(name);
    }

    @Override
    public Enumeration<URL> getResources(final String name) throws IOException {
        return getFrameworkBundle().getResources(name);
    }

    @Override
    public Enumeration<String> getEntryPaths(final String path) {
        return getFrameworkBundle().getEntryPaths(path);
    }

    @Override
    public URL getEntry(final String path) {
        return getFrameworkBundle().getEntry(path);
    }

    @Override
    public long getLastModified() {
        return getFrameworkBundle().getLastModified();
    }

    @Override
    public Enumeration<URL> findEntries(final String path, final String filePattern, final boolean recurse) {
        return getFrameworkBundle().findEntries(path, filePattern, recurse);
    }

    @Override
    public BundleContext getBundleContext() {
        return getFrameworkBundle().getBundleContext();
    }

    @Override
    public Map<X509Certificate, List<X509Certificate>> getSignerCertificates(final int signersType) {
        return getFrameworkBundle().getSignerCertificates(signersType);
    }

    @Override
    public Version getVersion() {
        return getFrameworkBundle().getVersion();
    }

    @Override
    public <A> A adapt(final Class<A> type) {
        return getFrameworkBundle().adapt(type);
    }

    @Override
    public File getDataFile(final String filename) {
        return getFrameworkBundle().getDataFile(filename);
    }

    @Override
    public int compareTo(final Bundle o) {
        return getFrameworkBundle().compareTo(o);
    }

    private Bundle installBundle(final String location) {
        final StandaloneScanner scanner = Ripener.Impl.class.cast(ripener).getScanner();
        final StandaloneScanner.BundleDefinition bundleDefinition = Stream.concat(
                scanner.findOSGiBundles().stream(),
                scanner.findPotentialOSGiBundles().stream())
                .filter(bundle -> doesLocationMatches(bundle, location))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No bundle found for " + location + ", available:\n\n" +
                        scanner.findOSGiBundles() + "\n" +
                        scanner.findPotentialOSGiBundles()));
        final OSGiBundleLifecycle lifecycle = new OSGiBundleLifecycle(
                bundleDefinition.getManifest(), bundleDefinition.getJar(),
                ripener.getServices(), ripener.getRegistry(), configuration,
                bundleIdGenerator.getAndIncrement(),
                bundleDefinition.getFiles());
        lifecycle.start();
        ripener.getRegistry().getBundles().put(lifecycle.getBundle().getBundleId(), lifecycle);
        return lifecycle.getBundle();
    }

    // todo: enhance with mvn:, file:// support
    private boolean doesLocationMatches(final StandaloneScanner.BundleDefinition bundle, final String location) {
        if (bundle.getJar() != null) {
            final boolean direct = location.contains(bundle.getJar().getAbsolutePath());
            if (direct) {
                return true;
            }

            final String normalizedName = location.replace(File.separatorChar, '/');
            if (!normalizedName.contains("/")) {
                return bundle.getJar().getName().equals(normalizedName);
            }

            try {
                return bundle.getJar().toURI().toURL().toExternalForm().equals(location);
            } catch (final MalformedURLException e) {
                return false;
            }
        }
        return false;
    }

    private BundleImpl getFrameworkBundle() {
        return frameworkBundle;
    }

    private void fireFrameworkEvent(final Throwable error) { // todo: shouldn't really be state
        final FrameworkEvent event = new FrameworkEvent(error != null ? FrameworkEvent.ERROR : state, getFrameworkBundle(), error);
        ofNullable(listeners).map(Stream::of).orElseGet(Stream::empty).forEach(l -> l.frameworkEvent(event));
    }

    public static class Factory implements FrameworkFactory {
        @Override
        public Framework newFramework(final Map<String, String> configuration) {
            final WinegrowerFramework framework = new WinegrowerFramework();
            ofNullable(configuration)
                    .map(c -> c.entrySet().stream().collect(Collector.of(
                            Properties::new,
                            (p, i) -> p.setProperty(i.getKey(), i.getValue()),
                            (p1, p2) -> {
                                p1.putAll(p2);
                                return p1;
                            })))
                    .ifPresent(framework.configuration::fromProperties);
            return framework;
        }
    }
}
