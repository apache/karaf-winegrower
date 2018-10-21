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

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

public class FatJar implements Runnable {
    private final Configuration configuration;

    public FatJar(final Configuration configuration) {
        this.configuration = configuration;
    }

    @Override
    public void run() {
        requireNonNull(configuration.jars, "Jars are not set");
        requireNonNull(configuration.output, "Output is not set").getParentFile().mkdirs();

        try (final JarOutputStream outputStream = new JarOutputStream(
                new BufferedOutputStream(new FileOutputStream(configuration.output)))) {
            final Properties manifests = new Properties();
            final Properties index = new Properties();
            byte[] buffer = new byte[8192];
            final Set<String> alreadyAdded = new HashSet<>();
            configuration.jars.forEach(shadedJar -> {
                final Collection<String> files = new ArrayList<>();
                try (final JarInputStream inputStream = new JarInputStream(new BufferedInputStream(new FileInputStream(shadedJar)))) {
                    final Manifest manifest = inputStream.getManifest();
                    if (manifest != null) {
                        try (final ByteArrayOutputStream manifestStream = new ByteArrayOutputStream()) {
                            manifest.write(manifestStream);
                            manifestStream.flush();
                            manifests.put(shadedJar.getName(), new String(manifestStream.toByteArray(), StandardCharsets.UTF_8));
                        }
                    }

                    ZipEntry nextEntry;
                    while ((nextEntry = inputStream.getNextEntry()) != null) {
                        final String name = nextEntry.getName();
                        if (!alreadyAdded.add(name)) {
                            continue;
                        }
                        files.add(name);
                        outputStream.putNextEntry(nextEntry);
                        int count;
                        while ((count = inputStream.read(buffer, 0, buffer.length)) >= 0) {
                            if (count > 0) {
                                outputStream.write(buffer, 0, count);
                            }
                        }
                        outputStream.closeEntry();
                    }
                } catch (final IOException e) {
                    throw new IllegalStateException(e);
                }
                index.put(shadedJar.getName(), files.stream().collect(joining(",")));
            });

            outputStream.putNextEntry(new JarEntry("WINEGROWER-INF/"));
            outputStream.closeEntry();

            outputStream.putNextEntry(new JarEntry("WINEGROWER-INF/index.properties"));
            index.store(outputStream, "index");
            outputStream.closeEntry();

            outputStream.putNextEntry(new JarEntry("WINEGROWER-INF/manifests.properties"));
            manifests.store(outputStream, "manifests");
            outputStream.closeEntry();
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static class Configuration {
        private final Collection<File> jars;
        private final File output;

        public Configuration(final Collection<File> jars, final File output) {
            this.jars = jars;
            this.output = output;
        }
    }
}
