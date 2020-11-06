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
package org.apache.winegrower;

import org.apache.winegrower.api.LifecycleCallbacks;
import org.apache.winegrower.deployer.OSGiBundleLifecycle;
import org.apache.winegrower.scanner.StandaloneScanner;
import org.apache.winegrower.scanner.manifest.HeaderManifestContributor;
import org.apache.winegrower.scanner.manifest.KarafCommandManifestContributor;
import org.apache.winegrower.scanner.manifest.ManifestContributor;
import org.apache.winegrower.scanner.manifest.OSGIInfContributor;
import org.apache.winegrower.scanner.manifest.OSGiCDIManifestContributor;
import org.apache.winegrower.scanner.manifest.RequirementManifestContributor;
import org.apache.winegrower.service.BundleRegistry;
import org.apache.winegrower.service.DefaultConfigurationAdmin;
import org.apache.winegrower.service.DefaultEventAdmin;
import org.apache.winegrower.service.OSGiServices;
import org.apache.winegrower.service.Slf4jOSGiLoggerFactory;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.cm.ConfigurationListener;
import org.osgi.service.event.EventAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Comparator.comparing;
import static java.util.Locale.ROOT;
import static java.util.Optional.ofNullable;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

public interface Ripener extends AutoCloseable {
    Configuration getConfiguration();

    long getStartTime();

    Ripener start();

    void stop();

    OSGiServices getServices();

    BundleRegistry getRegistry();

    ConfigurationAdmin getConfigurationAdmin();

    EventAdmin getEventAdmin();

    @Override
    void close();

    class Configuration {
        static {
            // not the most elegant way but logs are clearly misleading today
            if ("ERROR".equals(System.setProperty("org.slf4j.simpleLogger.log.org.apache.aries.spifly",
                    System.getProperty("org.slf4j.simpleLogger.log.org.apache.aries.spifly", "ERROR")))) {
                java.util.logging.Logger.getLogger("org.apache.aries.spifly").setLevel(Level.SEVERE);
            }
        }

        private static final Collection<String> DEFAULT_EXCLUSIONS = asList(
                "slf4j-",
                "xbean-",
                "org.osgi.",
                "opentest4j-",
                "junit-platform-",
                "junit-jupiter-",
                "debugger-agent",
                "asm-"
        );

        private File workDir = new File(System.getProperty("java.io.tmpdir"), "karaf-boot_" + UUID.randomUUID().toString());
        private Predicate<String> jarFilter = it -> DEFAULT_EXCLUSIONS.stream().anyMatch(it::startsWith);
        private boolean lazyInstall;
        private Collection<String> scanningIncludes;
        private Collection<String> scanningExcludes;
        private Collection<String> ignoredBundles = emptyList();
        private Collection<ManifestContributor> manifestContributors = Stream.concat(
                // built-in
                Stream.of(
                        new HeaderManifestContributor(), new RequirementManifestContributor(),
                        new OSGIInfContributor(),
                        new KarafCommandManifestContributor(),
                        new OSGiCDIManifestContributor()),
                // extensions
                StreamSupport.stream(ServiceLoader.load(ManifestContributor.class).spliterator(), false)
        ).collect(toList());
        // known bundles
        private List<String> prioritizedBundles = asList(
                "org.apache.aries.blueprint.core",
                "org.apache.aries.blueprint.cm",
                "pax-web-extender-whiteboard",
                "org.apache.felix.http.jetty",
                "org.apache.aries.jax.rs.whiteboard",
                "johnzon-osgi",
                "pax-web-runtime",
                "org.apache.aries.cdi");
        private List<String> defaultConfigurationAdminPids;

        private List<LifecycleCallbacks> lifecycleCallbacks;

        /**
         * Only for SPI ones, the epxlicit ones in {@link #lifecycleCallbacks} are always used.
         */
        private boolean useLifecycleCallbacks = true;

        public boolean isUseLifecycleCallbacks() {
            return useLifecycleCallbacks;
        }

        public void setUseLifecycleCallbacks(final boolean useLifecycleCallbacks) {
            this.useLifecycleCallbacks = useLifecycleCallbacks;
        }

        public List<LifecycleCallbacks> getLifecycleCallbacks() {
            return lifecycleCallbacks;
        }

        public void setLifecycleCallbacks(final List<LifecycleCallbacks> lifecycleCallbacks) {
            this.lifecycleCallbacks = lifecycleCallbacks;
        }

        public List<String> getDefaultConfigurationAdminPids() {
            return defaultConfigurationAdminPids;
        }

        public void setDefaultConfigurationAdminPids(final List<String> defaultConfigurationAdminPids) {
            this.defaultConfigurationAdminPids = defaultConfigurationAdminPids;
        }

        public boolean isLazyInstall() {
            return lazyInstall;
        }

        public void setLazyInstall(boolean lazyInstall) {
            this.lazyInstall = lazyInstall;
        }

        public Collection<String> getIgnoredBundles() {
            return ignoredBundles;
        }

        public void setIgnoredBundles(final Collection<String> ignoredBundles) {
            this.ignoredBundles = ignoredBundles;
        }

        public List<String> getPrioritizedBundles() {
            return prioritizedBundles;
        }

        public void setPrioritizedBundles(final List<String> prioritizedBundles) {
            this.prioritizedBundles = prioritizedBundles;
        }

        public Collection<ManifestContributor> getManifestContributors() {
            return manifestContributors;
        }

        public void setManifestContributors(final Collection<ManifestContributor> manifestContributors) {
            this.manifestContributors = manifestContributors;
        }

        public Collection<String> getScanningIncludes() {
            return scanningIncludes;
        }

        public void setScanningIncludes(final Collection<String> scanningIncludes) {
            this.scanningIncludes = scanningIncludes;
        }

        public Collection<String> getScanningExcludes() {
            return scanningExcludes;
        }

        public void setScanningExcludes(final Collection<String> scanningExcludes) {
            this.scanningExcludes = scanningExcludes;
        }

        public File getWorkDir() {
            return workDir;
        }

        public void setWorkDir(final File workDir) {
            this.workDir = workDir;
        }

        public void setJarFilter(final Predicate<String> jarFilter) {
            this.jarFilter = jarFilter;
        }

        public Predicate<String> getJarFilter() {
            return jarFilter;
        }

        public void fromProperties(final Properties properties) {
            ofNullable(properties.getProperty("winegrower.ripener.configuration.workdir"))
                    .map(String::valueOf)
                    .map(File::new)
                    .ifPresent(this::setWorkDir);
            ofNullable(properties.getProperty("winegrower.ripener.configuration.lazyInstall"))
                    .map(String::valueOf)
                    .map(Boolean::parseBoolean)
                    .ifPresent(this::setLazyInstall);
            ofNullable(properties.getProperty("winegrower.ripener.configuration.prioritizedBundles"))
                    .map(String::valueOf)
                    .filter(it -> !it.isEmpty())
                    .map(it -> asList(it.split(",")))
                    .ifPresent(this::setPrioritizedBundles);
            ofNullable(properties.getProperty("winegrower.ripener.configuration.ignoredBundles"))
                    .map(String::valueOf)
                    .filter(it -> !it.isEmpty())
                    .map(it -> asList(it.split(",")))
                    .ifPresent(this::setIgnoredBundles);
            ofNullable(properties.getProperty("winegrower.ripener.configuration.scanningIncludes"))
                    .map(String::valueOf)
                    .filter(it -> !it.isEmpty())
                    .map(it -> asList(it.split(",")))
                    .ifPresent(this::setScanningIncludes);
            ofNullable(properties.getProperty("winegrower.ripener.configuration.scanningExcludes"))
                    .map(String::valueOf)
                    .filter(it -> !it.isEmpty())
                    .map(it -> asList(it.split(",")))
                    .ifPresent(this::setScanningExcludes);
            ofNullable(properties.getProperty("winegrower.ripener.configuration.manifestContributors"))
                    .map(String::valueOf)
                    .filter(it -> !it.isEmpty())
                    .map(it -> asList(it.split(",")))
                    .ifPresent(contributors -> setManifestContributors(contributors.stream().map(clazz -> {
                        try {
                            return Thread.currentThread().getContextClassLoader().loadClass(clazz).getConstructor().newInstance();
                        } catch (final InstantiationException | NoSuchMethodException | IllegalAccessException
                                | ClassNotFoundException e) {
                            throw new IllegalArgumentException(e);
                        } catch (final InvocationTargetException e) {
                            throw new IllegalArgumentException(e.getTargetException());
                        }
                    }).map(ManifestContributor.class::cast).collect(toList())));
            ofNullable(properties.getProperty("winegrower.ripener.configuration.jarFilter"))
                    .map(String::valueOf)
                    .filter(it -> !it.isEmpty())
                    .ifPresent(filter -> {
                        try {
                            setJarFilter((Predicate<String>) Thread.currentThread().getContextClassLoader().loadClass(filter)
                                    .getConstructor().newInstance());
                        } catch (final InstantiationException | NoSuchMethodException | IllegalAccessException | ClassNotFoundException e) {
                            throw new IllegalArgumentException(e);
                        } catch (final InvocationTargetException e) {
                            throw new IllegalArgumentException(e.getTargetException());
                        }
                    });
            ofNullable(properties.getProperty("winegrower.ripener.configuration.defaultConfigurationAdminPids"))
                    .ifPresent(it -> setDefaultConfigurationAdminPids(Stream.of(it.split(","))
                            .map(String::trim)
                            .filter(v -> !v.isEmpty())
                            .collect(toList())));
        }
    }

    class Impl implements Ripener {
        private static final Logger LOGGER = LoggerFactory.getLogger(Ripener.class);

        private final ConfigurationAdmin configurationAdmin;
        private final EventAdmin eventAdmin;
        private final OSGiServices services;
        private final BundleRegistry registry;

        private final Configuration configuration;

        private long startTime = -1;
        private StandaloneScanner scanner;

        public Impl(final Configuration configuration) {
            this.configuration = configuration;

            final ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
            if (configuration.isUseLifecycleCallbacks()) {
                final List<LifecycleCallbacks> callbacks = StreamSupport.stream(
                        ServiceLoader.load(LifecycleCallbacks.class, contextClassLoader).spliterator(), false)
                        .sorted(comparing(LifecycleCallbacks::order))
                        .collect(toList());
                if (configuration.getLifecycleCallbacks() == null) {
                    configuration.setLifecycleCallbacks(callbacks);
                } else {
                    configuration.setLifecycleCallbacks(Stream.concat(
                            configuration.getLifecycleCallbacks().stream(),
                            callbacks.stream()).sorted(comparing(LifecycleCallbacks::order))
                            .collect(toList()));
                }
            } else if (configuration.getLifecycleCallbacks() == null) {
                configuration.setLifecycleCallbacks(emptyList());
            }
            runCallbacks(LifecycleCallbacks::processConfiguration, configuration);

            final Collection<ConfigurationListener> configurationListeners = new ArrayList<>();
            final Collection<DefaultEventAdmin.EventHandlerInstance> eventListeners = new ArrayList<>();
            this.services = new OSGiServices(this, configurationListeners, eventListeners);
            this.registry = new BundleRegistry(services, configuration);

            try (final InputStream stream = contextClassLoader.getResourceAsStream("winegrower.properties")) {
                loadConfiguration(stream);
            } catch (final IOException e) {
                LOGGER.warn(e.getMessage());
            }

            this.configurationAdmin = loadConfigurationAdmin(configurationListeners);
            this.eventAdmin = loadEventAdmin(eventListeners);
            registerBuiltInService(ConfigurationAdmin.class, this.configurationAdmin, new Hashtable<>());
            registerBuiltInService(EventAdmin.class, this.eventAdmin, new Hashtable<>());
            registerBuiltInService(org.osgi.service.log.LoggerFactory.class, loadLoggerFactory(), new Hashtable<>());
            if (DefaultConfigurationAdmin.class.isInstance(configurationAdmin)) {
                DefaultConfigurationAdmin.class.cast(configurationAdmin).preload(configuration.getDefaultConfigurationAdminPids());
            }
        }

        public <T> void registerBuiltInService(final Class<T> type, final T impl, final Dictionary<String, Object> props) {
            if (Boolean.getBoolean("winegrower.builtin.services." + type.getName() + ".skip")) {
                return;
            }
            this.services.registerService(new String[]{type.getName()}, impl, props, this.registry.getBundles().get(0L).getBundle());
        }

        private <A> void runCallbacks(final BiConsumer<LifecycleCallbacks, A> action, final A arg) {
            configuration.getLifecycleCallbacks().forEach(c -> action.accept(c, arg));
        }

        private ConfigurationAdmin loadConfigurationAdmin(final Collection<ConfigurationListener> configurationListeners) {
            final Iterator<ConfigurationAdmin> configurationAdminIterator = ServiceLoader.load(ConfigurationAdmin.class).iterator();
            if (configurationAdminIterator.hasNext()) {
                return configurationAdminIterator.next();
            }
            return new DefaultConfigurationAdmin(new HashMap<>(), configurationListeners) {
                @Override
                protected ServiceReference<ConfigurationAdmin> getSelfReference() {
                    return (ServiceReference<ConfigurationAdmin>) services.getServices().iterator().next().getReference();
                }
            };
        }

        private org.osgi.service.log.LoggerFactory loadLoggerFactory() {
            final Iterator<org.osgi.service.log.LoggerFactory> eventAdminIterator = ServiceLoader.load(org.osgi.service.log.LoggerFactory.class).iterator();
            if (eventAdminIterator.hasNext()) {
                return eventAdminIterator.next();
            }
            return new Slf4jOSGiLoggerFactory();
        }

        private EventAdmin loadEventAdmin(final Collection<DefaultEventAdmin.EventHandlerInstance> listeners) {
            final Iterator<EventAdmin> eventAdminIterator = ServiceLoader.load(EventAdmin.class).iterator();
            if (eventAdminIterator.hasNext()) {
                return eventAdminIterator.next();
            }
            return new DefaultEventAdmin(
                    listeners,
                    Integer.getInteger("winegrower.builtin.services." + EventAdmin.class.getName() + ".pool.core", Math.max(Runtime.getRuntime().availableProcessors(), 2)));
        }

        public void loadConfiguration(final InputStream stream) throws IOException {
            final Properties embedConfig = new Properties();
            if (stream != null) {
                embedConfig.load(stream);
                if (!embedConfig.isEmpty()) {
                    loadConfiguration(embedConfig);
                }
            }
        }

        // case insensitive
        public void loadConfiguration(final Properties embedConfig) {
            final Map<Object, Method> setters = Stream.of(this.configuration.getClass().getMethods())
                    .filter(it -> it.getName().startsWith("set") && it.getParameterCount() == 1)
                    .collect(toMap(it ->
                                    (Character.toLowerCase(it.getName().charAt(3)) + it.getName().substring(4)).toLowerCase(ROOT),
                            identity()));
            final Collection<String> matched = new ArrayList<>();
            embedConfig.stringPropertyNames().stream().filter(it -> setters.containsKey(it.toLowerCase(ROOT))).forEach(key -> {
                final String value = embedConfig.getProperty(key);
                if (value == null) {
                    return;
                }
                final String keyLowerCase = key.toLowerCase(ROOT);
                final Method setter = setters.get(keyLowerCase);
                matched.add(keyLowerCase);
                final Class<?> type = setter.getParameters()[0].getType();
                try {
                    if (type == String.class) {
                        setter.invoke(this.configuration, value);
                        return;
                    } else if (type == File.class) {
                        setter.invoke(this.configuration, new File(value));
                        return;
                    }

                    // from here all parameters are lists
                    final Collection<String> asList = Stream.of(value.split(","))
                            .map(String::trim)
                            .filter(it -> !it.isEmpty())
                            .collect(toList());
                    if (type == Predicate.class) { // Predicate<String> + startsWith logic
                        final Predicate<String> predicate =
                                val -> val != null && asList.stream().anyMatch(val::startsWith);
                        setter.invoke(this.configuration, predicate);
                    } else if (type == List.class) {
                        setter.invoke(this.configuration, asList);
                    } else if (type == Collection.class
                            && ManifestContributor.class == ParameterizedType.class.cast(
                            setter.getParameters()[0].getParameterizedType()).getActualTypeArguments()[0]) {
                        final ClassLoader loader = Thread.currentThread().getContextClassLoader();
                        setter.invoke(this.configuration, asList.stream()
                                .map(it -> {
                                    try {
                                        return loader.loadClass(it);
                                    } catch (final ClassNotFoundException e) {
                                        throw new IllegalArgumentException(e);
                                    }
                                })
                                .collect(toList()));
                    } else if (type == Collection.class) { // Collection<String>
                        setter.invoke(this.configuration, asList);
                    } else {
                        throw new IllegalArgumentException("Unsupported: " + setter);
                    }
                } catch (final IllegalAccessException e) {
                    throw new IllegalArgumentException(e);
                } catch (final InvocationTargetException e) {
                    throw new IllegalArgumentException(e.getTargetException());
                }
            });

            if (DefaultConfigurationAdmin.class.isInstance(configurationAdmin)) {
                final DefaultConfigurationAdmin dca = DefaultConfigurationAdmin.class.cast(configurationAdmin);
                embedConfig.stringPropertyNames()
                        .stream()
                        .filter(it -> it.startsWith("winegrower.service."))
                        .peek(matched::add)
                        .forEach(key -> dca.getProvidedConfiguration().put(key, embedConfig.getProperty(key)));
            }

            embedConfig.stringPropertyNames().stream().filter(it -> !matched.contains(it.toLowerCase(ROOT)))
                    .forEach(it -> LOGGER.warn("Didn't match configuration {}, did you mispell it?", it));
        }

        @Override
        public Configuration getConfiguration() {
            return configuration;
        }

        @Override
        public long getStartTime() {
            return startTime;
        }

        @Override
        public synchronized Ripener start() {
            runCallbacks(LifecycleCallbacks::beforeStart, this);
            try {
                startTime = System.currentTimeMillis();
                LOGGER.info("Starting Apache Winegrower application on {}",
                        LocalDateTime.ofInstant(Instant.ofEpochMilli(startTime), ZoneId.systemDefault()));
                if (configuration.isLazyInstall()) {
                    return this;
                }
                final StandaloneScanner scanner = getScanner();
                final AtomicLong bundleIdGenerator = new AtomicLong(1);
                Stream.concat(Stream.concat(
                        scanner.findOSGiBundles().stream(),
                        scanner.findPotentialOSGiBundles().stream()),
                        scanner.findEmbeddedClasses().stream())
                        .sorted(this::compareBundles)
                        .map(it -> new OSGiBundleLifecycle(
                                it.getManifest(), it.getJar(),
                                services, registry, configuration,
                                bundleIdGenerator.getAndIncrement(),
                                it.getFiles()))
                        .peek(OSGiBundleLifecycle::start)
                        .peek(it -> registry.getBundles().put(it.getBundle().getBundleId(), it))
                        .forEach(bundle -> LOGGER.debug("Bundle {}", bundle));
                this.scanner = null; // we don't need it anymore since we don't support runtime install so make it gc friendly
            } finally {
                runCallbacks(LifecycleCallbacks::afterStart, this);
            }
            return this;
        }

        public synchronized StandaloneScanner getScanner() {
            return scanner == null ? scanner = new StandaloneScanner(configuration, registry.getFramework()) : scanner;
        }

        @Override
        public synchronized void stop() {
            runCallbacks(LifecycleCallbacks::beforeStop, this);
            try {
                LOGGER.info("Stopping Apache Winegrower application on {}", LocalDateTime.now());
                final Map<Long, OSGiBundleLifecycle> bundles = registry.getBundles();
                bundles.values().stream()
                        .sorted((o1, o2) -> (int) (o2.getBundle().getBundleId() - o1.getBundle().getBundleId()))
                        .forEach(OSGiBundleLifecycle::stop);
                bundles.clear();
                if (configuration.getWorkDir().exists()) {
                    try {
                        Files.walkFileTree(configuration.getWorkDir().toPath(), new SimpleFileVisitor<Path>() {
                            @Override
                            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                                Files.delete(file);
                                return super.visitFile(file, attrs);
                            }

                            @Override
                            public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) throws IOException {
                                Files.delete(dir);
                                return super.postVisitDirectory(dir, exc);
                            }
                        });
                    } catch (final IOException e) {
                        LOGGER.warn("Can't delete work directory", e);
                    }
                }
                if (DefaultEventAdmin.class.isInstance(eventAdmin)) {
                    DefaultEventAdmin.class.cast(eventAdmin).close();
                }
            } finally {
                runCallbacks(LifecycleCallbacks::afterStop, this);
            }
        }

        @Override
        public OSGiServices getServices() {
            return services;
        }

        @Override
        public BundleRegistry getRegistry() {
            return registry;
        }

        @Override
        public ConfigurationAdmin getConfigurationAdmin() {
            return configurationAdmin;
        }

        @Override
        public EventAdmin getEventAdmin() {
            return eventAdmin;
        }

        @Override // for try with resource syntax
        public void close() {
            stop();
        }

        private int compareBundles(final StandaloneScanner.BundleDefinition bundle1, final StandaloneScanner.BundleDefinition bundle2) {
            final String id1 = getBundleId(bundle1);
            final String id2 = getBundleId(bundle2);
            final int index1 = matchPriorities(id1);
            final int index2 = matchPriorities(id2);
            if (index1 == index2) {
                return id1.compareTo(id2);
            }
            if (index1 == -1) {
                return 1;
            }
            if (index2 == -1) {
                return -1;
            }
            return index1 - index2;
        }

        private String getBundleId(final StandaloneScanner.BundleDefinition bundle) {
            return ofNullable(bundle.getJar()).map(File::getName)
                    .orElseGet(() -> Stream.of("Bundle-SymbolicName", "Bundle-Name")
                            .map(k -> bundle.getManifest().getMainAttributes().getValue(k))
                            .findFirst()
                            .orElseGet(() -> Long.toString(System.identityHashCode(bundle))));
        }

        private int matchPriorities(final String name) {
            return configuration.getPrioritizedBundles().stream()
                    .filter(name::startsWith)
                    .findFirst()
                    .map(it -> configuration.getPrioritizedBundles().indexOf(it))
                    .orElse(-1);
        }

    }

    static Ripener create(final Configuration configuration) {
        // we can plug a SPI later on here if needed
        return new Impl(configuration);
    }

    static void main(final String[] args) {
        final CountDownLatch latch = new CountDownLatch(1);
        final Configuration configuration = new Configuration();
        configuration.fromProperties(System.getProperties());
        final Ripener main = new Impl(configuration).start();
        Runtime.getRuntime().addShutdownHook(new Thread() {

            {
                setName(getClass().getName() + "-shutdown-hook");
            }

            @Override
            public void run() {
                main.stop();
                latch.countDown();
            }
        });
        try {
            latch.await();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
