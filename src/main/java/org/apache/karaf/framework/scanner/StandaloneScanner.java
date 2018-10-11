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
package org.apache.karaf.framework.scanner;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Objects;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.apache.xbean.finder.ClassLoaders;
import org.apache.xbean.finder.UrlSet;
import org.apache.xbean.finder.util.Files;

public class StandaloneScanner {

    private static final Attributes.Name OSGI_MANIFEST_MARKER = new Attributes.Name("Bundle-Version");
    private static final Collection<String> KNOWN_EXCLUSIONS = asList( // todo: make it configurable
            "slf4j-",
            "xbean-",
            "org.osgi.",
            "opentest4j-"
    );

    public Collection<BundleDefinition> findOSGiBundles() {
        try {
            return new UrlSet(ClassLoaders.findUrls(Thread.currentThread().getContextClassLoader()))
                    .excludeJvm()
                    .getUrls()
                    .stream()
                    .map(Files::toFile)
                    .filter(this::isNotExcluded)
                    .map(this::toDefinition)
                    .filter(Objects::nonNull)
                    .collect(toList());
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private boolean isNotExcluded(final File file) {
        final String name = file.getName();
        return KNOWN_EXCLUSIONS.stream().noneMatch(name::startsWith);
    }

    private BundleDefinition toDefinition(final File file) {
        try (final JarFile jar = new JarFile(file)) {
            final Manifest manifest = jar.getManifest();
            if (manifest == null) {
                return null;
            }
            if (manifest.getMainAttributes().containsKey(OSGI_MANIFEST_MARKER)) {
                return new BundleDefinition(manifest, file);
            }
            return null;
        } catch (final Exception e) {
            return null;
        }
    }

    public static class BundleDefinition {
        private final Manifest manifest;
        private final File jar;

        private BundleDefinition(final Manifest manifest, final File jar) {
            this.manifest = manifest;
            this.jar = jar;
        }

        public Manifest getManifest() {
            return manifest;
        }

        public File getJar() {
            return jar;
        }
    }
}
