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
package org.apache.karaf.framework.scanner.manifest;

import java.util.function.Supplier;
import java.util.jar.Manifest;

import org.apache.karaf.framework.api.ImplicitActivator;
import org.apache.xbean.finder.AnnotationFinder;

public class ActivatorManifestContributor implements ManifestContributor {

    @Override
    public void contribute(final AnnotationFinder finder, final Supplier<Manifest> manifest) {
        finder.findAnnotatedClasses(ImplicitActivator.class).stream().findFirst().map(Class::getName)
                .ifPresent(clazz -> manifest.get().getMainAttributes().putValue("Bundle-Activator", clazz));
    }
}
