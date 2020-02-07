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
package org.apache.winegrower.scanner.manifest;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Stream;

import org.apache.xbean.finder.AnnotationFinder;
import org.apache.xbean.finder.archive.Archive;
import org.apache.xbean.finder.archive.FileArchive;
import org.osgi.framework.Constants;

public class OSGiCDIManifestContributor implements ManifestContributor {
    @Override
    public void contribute(final AnnotationFinder finder, final Supplier<Manifest> manifest) {
        final Archive archive = finder.getArchive();
        if (!FileArchive.class.isInstance(archive) || !hasBeansXml(FileArchive.class.cast(archive))) {
            return; // already a jar, its manifest is likely already good
        }

        final Attributes attributes = manifest.get().getMainAttributes();
        final String newRequireCapability = toCapability(finder);
        final String existing = attributes.getValue(Constants.REQUIRE_CAPABILITY);
        final String ariesCdiExtensions = findCdiExtensions(finder);
        attributes.putValue(Constants.REQUIRE_CAPABILITY, Stream.of(existing, newRequireCapability, ariesCdiExtensions)
            .filter(Objects::nonNull)
            .filter(it -> !it.isEmpty())
            .collect(joining(",")));
    }

    // todo: drop and handle @Requirement transitively in core
    private String findCdiExtensions(final AnnotationFinder finder) {
        try {
            final ClassLoader loader = ofNullable(Thread.currentThread().getContextClassLoader())
                    .orElseGet(ClassLoader::getSystemClassLoader);
            final Class<? extends Annotation> singular = (Class<? extends Annotation>)
                    loader.loadClass("org.apache.aries.cdi.extra.RequireCDIExtension");
            final Class<? extends Annotation> plural = (Class<? extends Annotation>)
                    loader.loadClass("org.apache.aries.cdi.extra.RequireCDIExtensions");
            final Method value = singular.getMethod("value");
            return Stream.concat(
                    finder.findAnnotatedClasses(plural).stream(),
                    finder.findAnnotatedClasses(singular).stream())
                    .distinct()
                    .flatMap(c -> Stream.of(c.getAnnotationsByType(singular)))
                    .map(rce -> {
                        try {
                            final Object extension = value.invoke(rce);
                            return "osgi.cdi.extension;filter:=\"(osgi.cdi.extension=" + extension + ")\"";
                        } catch (final IllegalAccessException e) {
                            throw new IllegalStateException(e);
                        } catch (final InvocationTargetException e) {
                            throw new IllegalStateException(e.getTargetException());
                        }
                    })
                    .collect(joining(","));
        } catch (final Exception cnfe) {
            return null;
        }
    }

    private String toCapability(final AnnotationFinder finder) {
        return "osgi.extender;filter:=\"(osgi.extender=osgi.cdi)\";beans:List<String>=\"" +
                String.join(",", finder.getAnnotatedClassNames()) + "\"";
    }

    private boolean hasBeansXml(final FileArchive archive) {
        return Files.exists(archive.getDir().toPath().resolve("META-INF/beans.xml"));
    }
}
