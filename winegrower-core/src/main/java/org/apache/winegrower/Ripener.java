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

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.winegrower.deployer.OSGiBundleLifecycle;
import org.apache.winegrower.scanner.StandaloneScanner;
import org.apache.winegrower.scanner.manifest.ActivatorManifestContributor;
import org.apache.winegrower.scanner.manifest.KarafCommandManifestContributor;
import org.apache.winegrower.scanner.manifest.ManifestContributor;
import org.apache.winegrower.scanner.manifest.OSGIInfContributor;
import org.apache.winegrower.service.BundleRegistry;
import org.apache.winegrower.service.DefaultConfigurationAdmin;
import org.apache.winegrower.service.OSGiServices;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface Ripener extends AutoCloseable {
    Configuration getConfiguration();

    long getStartTime();

    Ripener start();

    void stop();

    OSGiServices getServices();

    BundleRegistry getRegistry();

    ConfigurationAdmin getConfigurationAdmin();

    @Override
    void close();

    class Configuration {
        private static final Collection<String> DEFAULT_EXCLUSIONS = asList( // todo: make it configurable
                "slf4j-",
                "xbean-",
                "org.osgi.",
                "opentest4j-"
        );

        private File workDir = new File(System.getProperty("java.io.tmpdir"), "karaf-boot_" + UUID.randomUUID().toString());
        private Predicate<String> jarFilter = it -> DEFAULT_EXCLUSIONS.stream().anyMatch(it::startsWith);
        private Collection<String> scanningIncludes;
        private Collection<String> scanningExcludes;
        private Collection<String> ignoredBundles = emptyList();
        private Collection<ManifestContributor> manifestContributors = Stream.concat(
                // built-in
                Stream.of(new KarafCommandManifestContributor(), new ActivatorManifestContributor(), new OSGIInfContributor()),
                // extensions
                StreamSupport.stream(ServiceLoader.load(ManifestContributor.class).spliterator(), false)
        ).collect(toList());
        // known bundles
        private List<String> prioritizedBundles = asList(
                "org.apache.aries.blueprint.core",
                "org.apache.aries.blueprint.cm",
                "pax-web-extender-whiteboard",
                "org.apache.aries.jax.rs.whiteboard",
                "pax-web-runtime");

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
    }


    class Impl implements Ripener {
        private static final Logger LOGGER = LoggerFactory.getLogger(Ripener.class);

        private final DefaultConfigurationAdmin configurationAdmin = new DefaultConfigurationAdmin();
        private final OSGiServices services = new OSGiServices(this);
        private final BundleRegistry registry;

        private final Configuration configuration;

        private long startTime = -1;

        public Impl(final Configuration configuration) {
            this.configuration = configuration;
            this.registry = new BundleRegistry(services, configuration);

            this.services.registerService(
                    new String[]{ConfigurationAdmin.class.getName()}, this.configurationAdmin, new Hashtable<>(),
                    this.registry.getBundles().get(0L).getBundle());
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
            startTime = System.currentTimeMillis();
            LOGGER.info("Starting Apache Winegrower application on {}",
                    LocalDateTime.ofInstant(Instant.ofEpochMilli(startTime), ZoneId.systemDefault()));
            final StandaloneScanner scanner = new StandaloneScanner(configuration, registry.getFramework());
            final AtomicLong bundleIdGenerator = new AtomicLong(1);
            Stream.concat(scanner.findOSGiBundles().stream(), scanner.findPotentialOSGiBundles().stream())
                    .sorted(this::compareBundles)
                    .map(it -> new OSGiBundleLifecycle(
                            it.getManifest(), it.getJar(),
                            services, registry, configuration,
                            bundleIdGenerator.getAndIncrement(),
                            it.getFiles()))
                    .peek(OSGiBundleLifecycle::start)
                    .peek(it -> registry.getBundles().put(it.getBundle().getBundleId(), it))
                    .forEach(bundle -> LOGGER.debug("Bundle {}", bundle));
            return this;
        }

        @Override
        public synchronized void stop() {
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

        @Override // for try with resource syntax
        public void close() {
            stop();
        }

        private int compareBundles(final StandaloneScanner.BundleDefinition bundle1, final StandaloneScanner.BundleDefinition bundle2) {
            final int index1 = matchPriorities(bundle1.getJar().getName());
            final int index2 = matchPriorities(bundle2.getJar().getName());
            if (index1 == index2) {
                return bundle1.getJar().getName().compareTo(bundle2.getJar().getName());
            }
            if (index1 == -1) {
                return 1;
            }
            if (index2 == -1) {
                return -1;
            }
            return index1 - index2;
        }

        private int matchPriorities(final String name) {
            return configuration.getPrioritizedBundles().stream()
                    .filter(name::startsWith)
                    .findFirst()
                    .map(it -> configuration.getPrioritizedBundles().indexOf(it))
                    .orElse(-1);
        }
    }

    static void main(final String[] args) {
        final CountDownLatch latch = new CountDownLatch(1);
        final Configuration configuration = new Configuration();
        ofNullable(System.getProperty("winegrower.ripener.configuration.workdir"))
                .map(String::valueOf)
                .map(File::new)
                .ifPresent(configuration::setWorkDir);
        ofNullable(System.getProperty("winegrower.ripener.configuration.prioritizedBundles"))
                .map(String::valueOf)
                .filter(it -> !it.isEmpty())
                .map(it -> asList(it.split(",")))
                .ifPresent(configuration::setPrioritizedBundles);
        ofNullable(System.getProperty("winegrower.ripener.configuration.ignoredBundles"))
                .map(String::valueOf)
                .filter(it -> !it.isEmpty())
                .map(it -> asList(it.split(",")))
                .ifPresent(configuration::setIgnoredBundles);
        ofNullable(System.getProperty("winegrower.ripener.configuration.scanningIncludes"))
                .map(String::valueOf)
                .filter(it -> !it.isEmpty())
                .map(it -> asList(it.split(",")))
                .ifPresent(configuration::setScanningIncludes);
        ofNullable(System.getProperty("winegrower.ripener.configuration.scanningExcludes"))
                .map(String::valueOf)
                .filter(it -> !it.isEmpty())
                .map(it -> asList(it.split(",")))
                .ifPresent(configuration::setScanningExcludes);
        ofNullable(System.getProperty("winegrower.ripener.configuration.manifestContributors"))
                .map(String::valueOf)
                .filter(it -> !it.isEmpty())
                .map(it -> asList(it.split(",")))
                .ifPresent(contributors -> {
                    configuration.setManifestContributors(contributors.stream().map(clazz -> {
                        try {
                            return Thread.currentThread().getContextClassLoader().loadClass(clazz).getConstructor().newInstance();
                        } catch (final InstantiationException | NoSuchMethodException | IllegalAccessException
                                | ClassNotFoundException e) {
                            throw new IllegalArgumentException(e);
                        } catch (final InvocationTargetException e) {
                            throw new IllegalArgumentException(e.getTargetException());
                        }
                    }).map(ManifestContributor.class::cast).collect(toList()));
                });
        ofNullable(System.getProperty("winegrower.ripener.configuration.jarFilter"))
                .map(String::valueOf)
                .filter(it -> !it.isEmpty())
                .ifPresent(filter -> {
                    try {
                        configuration.setJarFilter((Predicate<String>) Thread.currentThread().getContextClassLoader().loadClass(filter)
                                .getConstructor().newInstance());
                    } catch (final InstantiationException | NoSuchMethodException | IllegalAccessException | ClassNotFoundException e) {
                        throw new IllegalArgumentException(e);
                    } catch (final InvocationTargetException e) {
                        throw new IllegalArgumentException(e.getTargetException());
                    }
                });
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
