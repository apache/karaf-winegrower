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

import static java.util.stream.Collectors.joining;

import java.lang.annotation.Annotation;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.jar.Manifest;

import org.apache.xbean.finder.AnnotationFinder;

public class KarafCommandManifestContributor implements ManifestContributor {
    @Override
    public void contribute(final AnnotationFinder finder, final Supplier<Manifest> manifest) {
        try {
            final Class<? extends Annotation> commandMarker = (Class<? extends Annotation>)
                    finder.getArchive().loadClass("org.apache.karaf.shell.api.action.lifecycle.Service");
            final String packages = finder.findAnnotatedClasses(commandMarker)
                                         .stream()
                                         .map(Class::getPackage)
                                         .filter(Objects::nonNull)
                                         .map(Package::getName)
                                         .distinct()
                                         .collect(joining(","));
            if (!packages.isEmpty()) {
                manifest.get().getMainAttributes().putValue("Karaf-Commands", packages);
            }
        } catch (final ClassNotFoundException e) {
            // no-op
        }
    }
}
