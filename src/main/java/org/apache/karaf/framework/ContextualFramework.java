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
package org.apache.karaf.framework;

import static java.util.Arrays.asList;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.karaf.framework.deployer.OSGiBundleLifecycle;
import org.apache.karaf.framework.scanner.StandaloneScanner;
import org.apache.karaf.framework.scanner.manifest.KarafCommandManifestContributor;
import org.apache.karaf.framework.scanner.manifest.ManifestContributor;
import org.apache.karaf.framework.service.BundleRegistry;
import org.apache.karaf.framework.service.OSGiServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface ContextualFramework extends AutoCloseable {
    long getStartTime();

    ContextualFramework start();

    void stop();

    OSGiServices getServices();

    BundleRegistry getRegistry();

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
        private Collection<ManifestContributor> manifestContributors = Stream.concat(
                Stream.of(new KarafCommandManifestContributor()), // built-in
                StreamSupport.stream(ServiceLoader.load(ManifestContributor.class).spliterator(), false) // extensions
        ).collect(toList());

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


    class Impl implements ContextualFramework {
        private final static Logger LOGGER = LoggerFactory.getLogger(ContextualFramework.class);

        private final OSGiServices services = new OSGiServices();
        private final BundleRegistry registry;

        private final Configuration configuration;

        private long startTime = -1;

        public Impl(final Configuration configuration) {
            this.configuration = configuration;
            this.registry = new BundleRegistry(services, configuration);
        }

        @Override
        public long getStartTime() {
            return startTime;
        }

        @Override
        public synchronized ContextualFramework start() {
            startTime = System.currentTimeMillis();
            LOGGER.info("Starting Apache Karaf Contextual Framework on {}",
                    LocalDateTime.ofInstant(Instant.ofEpochMilli(startTime), ZoneId.systemDefault()));
            final StandaloneScanner scanner = new StandaloneScanner(configuration, registry.getFramework());
            Stream.concat(scanner.findOSGiBundles().stream(), scanner.findPotentialOSGiBundles().stream())
                    .sorted(comparing(b -> b.getJar().getName()))
                    .map(it -> new OSGiBundleLifecycle(it.getManifest(), it.getJar(), services, registry, configuration))
                    .peek(OSGiBundleLifecycle::start)
                    .peek(it -> registry.getBundles().put(it.getBundle().getBundleId(), it))
                    .forEach(bundle -> LOGGER.debug("Bundle {}", bundle));
            return this;
        }

        @Override
        public synchronized void stop() {
            LOGGER.info("Stopping Apache Karaf Contextual Framework on {}", LocalDateTime.now());
            final Map<Long, OSGiBundleLifecycle> bundles = registry.getBundles();
            bundles.forEach((k, v) -> v.stop());
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

        @Override // for try with resource syntax
        public void close() {
            stop();
        }
    }

    static void main(final String[] args) {
        final CountDownLatch latch = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread() {

            {
                setName(getClass().getName() + "-shutdown-hook");
            }

            @Override
            public void run() {
                latch.countDown();
            }
        });
        try (final ContextualFramework framework = new ContextualFramework.Impl(new Configuration()).start()) {
            try {
                latch.await();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
