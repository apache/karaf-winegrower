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
package org.apache.winegrower.extension.build.common;

import static java.util.Arrays.asList;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;
import static org.apache.xbean.finder.archive.ClasspathArchive.archive;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.jar.Manifest;
import java.util.stream.Stream;

import org.apache.winegrower.scanner.manifest.ManifestContributor;
import org.apache.xbean.finder.AnnotationFinder;

public class ManifestCreator implements Runnable {
    private final Configuration configuration;

    public ManifestCreator(final Configuration configuration) {
        this.configuration = configuration;
    }

    @Override
    public void run() {
        if (!configuration.moduleToScan.exists()) {
            return;
        }

        final Manifest manifest = new Manifest();
        if (configuration.manifestBase != null && configuration.manifestBase.exists()) {
            try (final InputStream stream = new FileInputStream(configuration.manifestBase)) {
                manifest.read(stream);
            } catch (final IOException e) {
                throw new IllegalArgumentException(e);
            }
        } else {
            manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
        }
        ofNullable(configuration.entries).ifPresent(kv -> kv.forEach((k, v) -> manifest.getMainAttributes().putValue(k, v)));

        final ClassLoader startingLoader = Thread.currentThread().getContextClassLoader();
        final List<ManifestContributor> creators = ofNullable(configuration.manifestCreators)
                .map(contributors -> contributors.stream().map(clazz -> {
                    try {
                        return startingLoader.loadClass(clazz).getConstructor().newInstance();
                    } catch (final InstantiationException | NoSuchMethodException | IllegalAccessException
                            | ClassNotFoundException e) {
                        throw new IllegalArgumentException(e);
                    } catch (final InvocationTargetException e) {
                        throw new IllegalArgumentException(e.getTargetException());
                    }
                }).map(ManifestContributor.class::cast).collect(toList())).orElseGet(Collections::emptyList);

        try (final URLClassLoader loader = createLoader()) {
            final AnnotationFinder finder = new AnnotationFinder(archive(loader, configuration.moduleToScan.toURI().toURL()), false);
            creators.forEach(contributor -> contributor.contribute(finder, () -> manifest));
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
        configuration.output.getParentFile().mkdirs();
        try (final OutputStream outputStream = new FileOutputStream(configuration.output)) {
            manifest.write(outputStream);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private URLClassLoader createLoader() {
        return new URLClassLoader(
            Stream.concat(
                    Stream.of(configuration.moduleToScan),
                    configuration.libToAddInClassLoader.stream())
                .filter(File::exists)
                .map(file -> {
                    try {
                        return file.toURI().toURL();
                    } catch (final MalformedURLException e) {
                        throw new IllegalStateException(e);
                    }
                })
                .toArray(URL[]::new),
            Thread.currentThread().getContextClassLoader());
    }

    public static class Configuration {
        private final Collection<File> libToAddInClassLoader;
        private final File moduleToScan;
        private final Collection<String> manifestCreators;
        private final File manifestBase;
        private final Map<String, String> entries;
        private final File output;

        public Configuration(final Collection<File> libToAddInClassLoader,
                             final File moduleToScan,
                             final Collection<String> manifestCreators,
                             final File manifestBase,
                             final Map<String, String> entries,
                             final File output) {
            this.libToAddInClassLoader = libToAddInClassLoader;
            this.moduleToScan = moduleToScan;
            this.manifestCreators = manifestCreators;
            this.manifestBase = manifestBase;
            this.entries = entries;
            this.output = output;
        }
    }
}
