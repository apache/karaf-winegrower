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

import org.apache.xbean.finder.AnnotationFinder;
import org.apache.xbean.finder.archive.Archive;

import java.util.List;
import java.util.function.Supplier;
import java.util.jar.Manifest;

import static java.util.stream.Collectors.toList;

public interface ManifestContributor {
    void contribute(final AnnotationFinder finder, final Supplier<Manifest> manifest);

    class WinegrowerAnnotationFinder extends AnnotationFinder {
        public WinegrowerAnnotationFinder(final Archive archive, final boolean checkRuntimeAnnotation) {
            super(archive, checkRuntimeAnnotation);
        }

        // todo: port over xbean
        public List<Class<?>> findAnnotatedClasses(final String annotation) {
            return this.getAnnotationInfos(annotation).stream()
                    .map(it -> {
                        try {
                            return AnnotationFinder.ClassInfo.class.cast(it).get();
                        } catch (final ClassNotFoundException var8) {
                            // skip
                            return null;
                        }
                    }).collect(toList());
        }
    }
}
