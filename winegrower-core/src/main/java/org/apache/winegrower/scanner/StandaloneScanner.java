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
package org.apache.winegrower.scanner;

import static java.util.Arrays.asList;
import static java.util.Collections.list;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.apache.xbean.finder.archive.ClasspathArchive.archive;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Stream;

import org.apache.winegrower.Ripener;
import org.apache.winegrower.scanner.manifest.ManifestCreator;
import org.apache.xbean.finder.AnnotationFinder;
import org.apache.xbean.finder.ClassLoaders;
import org.apache.xbean.finder.UrlSet;
import org.apache.xbean.finder.archive.Archive;
import org.apache.xbean.finder.util.Files;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StandaloneScanner {
    private final static Logger LOGGER = LoggerFactory.getLogger(StandaloneScanner.class);
    private static final Attributes.Name OSGI_MANIFEST_MARKER = new Attributes.Name("Bundle-Version");

    private final List<URL> urls;
    private final Ripener.Configuration configuration;
    private final ClassLoader loader;
    private final File frameworkJar;
    private final Map<String, Manifest> providedManifests;
    private final Map<String, List<String>> providedIndex;

    public StandaloneScanner(final Ripener.Configuration configuration, final File frameworkJar) {
        this.configuration = configuration;
        this.frameworkJar = frameworkJar;
        this.loader = Thread.currentThread().getContextClassLoader();
        try {
            this.urls = new UrlSet(ClassLoaders.findUrls(loader))
                    .excludeJvm()
                    .getUrls();
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }

        try { // fatjar plugin
            providedManifests = list(this.loader.getResources("WINEGROWER-INF/manifests.properties")).stream()
                .flatMap(url -> {
                    final Properties properties = new Properties();
                    try (final InputStream stream = url.openStream()) {
                        properties.load(stream);
                    } catch (final IOException e) {
                        throw new IllegalArgumentException(e);
                    }
                    return properties.stringPropertyNames().stream()
                            .collect(toMap(identity(), key -> {
                                final String property = properties.getProperty(key);
                                final Manifest manifest = new Manifest();
                                try (final ByteArrayInputStream mfStream = new ByteArrayInputStream(property.getBytes(StandardCharsets.UTF_8))) {
                                    manifest.read(mfStream);
                                } catch (final IOException e) {
                                    throw new IllegalArgumentException(e);
                                }
                                return manifest;
                            })).entrySet().stream();
                })
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
            providedIndex = list(this.loader.getResources("WINEGROWER-INF/index.properties")).stream()
                    .flatMap(url -> {
                        final Properties properties = new Properties();
                        try (final InputStream stream = url.openStream()) {
                            properties.load(stream);
                        } catch (final IOException e) {
                            throw new IllegalArgumentException(e);
                        }
                        return properties.stringPropertyNames().stream()
                                .collect(toMap(identity(), key -> {
                                    final String property = properties.getProperty(key);
                                    return asList(property.split(","));
                                })).entrySet().stream();
                    })
                    .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
        } catch (final IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public Collection<BundleDefinition> findPotentialOSGiBundles() {
        final KnownJarsFilter filter = new KnownJarsFilter(configuration);
        return urls.stream()
              .map(it -> new FileAndUrl(Files.toFile(it), it))
              .filter(it -> !it.file.getAbsoluteFile().equals(frameworkJar))
              .filter(it -> filter.test(it.file.getName()))
              .filter(it -> toDefinition(it.file) == null)
              .map(it -> {
                  final Archive jarArchive = archive(loader, it.url);
                  // we scan per archive to be able to create bundle after
                  try {
                      final AnnotationFinder archiveFinder = new AnnotationFinder(jarArchive);
                      final ManifestCreator manifestCreator = new ManifestCreator(it.file.getName());
                      configuration.getManifestContributors()
                                   .forEach(c -> c.contribute(archiveFinder, manifestCreator));
                      final Manifest manifest = manifestCreator.getManifest();
                      if (manifest == null) {
                          LOGGER.debug("{} was scanned for nothing, maybe adjust scanning exclusions", it.file);
                          return null;
                      }
                      LOGGER.debug("{} was scanned and is converted to a bundle", it.file);
                      return new BundleDefinition(manifest, it.file, null);
                  } catch (final LinkageError e) {
                      LOGGER.debug("{} is not scannable, maybe exclude it in framework configuration", it.file);
                      return null;
                  }
              })
              .filter(Objects::nonNull)
              .collect(toList());
    }

    public Collection<BundleDefinition> findOSGiBundles() {
        return Stream.concat(
                    urls.stream()
                        .map(Files::toFile)
                        .filter(this::isIncluded)
                        .filter(it -> this.configuration.getIgnoredBundles().stream().noneMatch(ex -> it.getName().startsWith(ex)))
                        .map(this::toDefinition)
                        .filter(Objects::nonNull),
                    providedManifests.entrySet().stream()
                        .map(it -> new BundleDefinition(it.getValue(), null, providedIndex.get(it.getKey()))))
                .collect(toList());
    }

    private boolean isIncluded(final File file) {
        return !configuration.getJarFilter().test(file.getName());
    }

    private BundleDefinition toDefinition(final File file) {
        if (file.isDirectory()) {
            final File manifest = new File(file, "META-INF/MANIFEST.MF");
            if (manifest.exists()) {
                try (final InputStream stream = new FileInputStream(manifest)) {
                    final Manifest mf = new Manifest(stream);
                    if (isOSGi(mf)) {
                        return new BundleDefinition(mf, file, null);
                    }
                    return null;
                } catch (final IOException e) {
                    throw new IllegalStateException(e);
                }
            }
            return null;
        }
        try (final JarFile jar = new JarFile(file)) {
            final Manifest manifest = jar.getManifest();
            if (manifest == null) {
                return null;
            }
            if (isOSGi(manifest)) {
                return new BundleDefinition(manifest, file, null);
            }
            return null;
        } catch (final Exception e) {
            return null;
        }
    }

    private boolean isOSGi(final Manifest mf) {
        return mf.getMainAttributes().containsKey(OSGI_MANIFEST_MARKER);
    }

    public static class BundleDefinition {
        private final Manifest manifest;
        private final File jar;
        private final Collection<String> files;

        private BundleDefinition(final Manifest manifest, final File jar, final Collection<String> files) {
            this.manifest = manifest;
            this.jar = jar;
            this.files = files;
        }

        public Collection<String> getFiles() {
            return files;
        }

        public Manifest getManifest() {
            return manifest;
        }

        public File getJar() {
            return jar;
        }
    }

    private static class FileAndUrl {
        private final File file;
        private final URL url;

        private FileAndUrl(final File file, final URL url) {
            this.file = file;
            this.url = url;
        }
    }
}
