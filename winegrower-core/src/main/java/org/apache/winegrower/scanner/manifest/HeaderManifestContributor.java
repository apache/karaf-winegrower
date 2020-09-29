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

import org.apache.xbean.asm9.AnnotationVisitor;
import org.apache.xbean.asm9.ClassReader;
import org.apache.xbean.asm9.ClassVisitor;
import org.apache.xbean.finder.AnnotationFinder;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.jar.Manifest;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toMap;
import static org.apache.xbean.asm9.ClassReader.SKIP_CODE;
import static org.apache.xbean.asm9.ClassReader.SKIP_DEBUG;
import static org.apache.xbean.asm9.ClassReader.SKIP_FRAMES;
import static org.apache.xbean.asm9.Opcodes.ASM9;

public class HeaderManifestContributor implements ManifestContributor {

    @Override
    public void contribute(final AnnotationFinder finder, final Supplier<Manifest> manifest) {
        final ClassLoader loader = Thread.currentThread().getContextClassLoader();
        final List<Class<?>> headerClasses;
        final List<Class<?>> headersClasses;
        try {
            final WinegrowerAnnotationFinder waf = WinegrowerAnnotationFinder.class.cast(finder); // temp, see impl
            headerClasses = waf.findAnnotatedClasses("org.osgi.annotation.bundle.Header");
            headersClasses = waf.findAnnotatedClasses("org.osgi.annotation.bundle.Headers");
            if (headerClasses.isEmpty() && headersClasses.isEmpty()) { // reuse the finder to ensure it exists
                return;
            }
        } catch (final Exception | Error e) {
            return;
        }

        // read it in the bytecode since reflection can't help here
        final Map<String, String> headers = Stream.concat(headersClasses.stream(), headerClasses.stream())
                .flatMap(clazz -> read(loader, clazz))
                .collect(toMap(header -> header.name, header -> header.value, (a, b) -> {
                    throw new UnsupportedOperationException("not called normally");
                }, () -> new HashMap<String, String>() {
                    @Override // override to access the key which is important here
                    public String merge(final String key, final String value,
                                        final BiFunction<? super String, ? super String, ? extends String> ignored) {
                        final String oldValue = get(key);
                        final String newValue = oldValue == null ? value : HeaderManifestContributor.this.mergeManifestValues(key, oldValue, value);
                        put(key, newValue);
                        return newValue;
                    }
                }));
        headers.forEach((k, v) -> manifest.get().getMainAttributes().putValue(k, v));
    }

    private Stream<KeyValue> read(final ClassLoader loader, final Class<?> clazz) {
        try (final InputStream stream = loader.getResourceAsStream(clazz.getName().replace('.', '/') + ".class")) {
            final ClassReader reader = new ClassReader(stream);
            final Collection<KeyValue> headers = new ArrayList<>();
            final Supplier<AnnotationVisitor> newHeaderVisitor = () -> new AnnotationVisitor(ASM9) {
                private final KeyValue header = new KeyValue();

                @Override
                public void visit(final String name, final Object value) {
                    switch (name) {
                        case "name":
                            header.name = String.valueOf(value);
                            break;
                        case "value":
                            header.value = String.valueOf(value)
                                    .replace("${@class}", clazz.getName());
                            break;
                        default:
                    }
                }

                @Override
                public void visitEnd() {
                    headers.add(header);
                }
            };

            reader.accept(new ClassVisitor(ASM9) {
                @Override
                public AnnotationVisitor visitAnnotation(final String descriptor, final boolean visible) {
                    switch (descriptor) {
                        case "Lorg/osgi/annotation/bundle/Headers;":
                            return new PluralAnnotationVisitor("Lorg/osgi/annotation/bundle/Header;", newHeaderVisitor);
                        case "Lorg/osgi/annotation/bundle/Header;":
                            return newHeaderVisitor.get();
                        default:
                            return super.visitAnnotation(descriptor, visible);
                    }
                }
            }, SKIP_CODE + SKIP_DEBUG + SKIP_FRAMES);

            return headers.stream();
        } catch (final Exception e) {
            return Stream.empty();
        }
    }

    private String mergeManifestValues(final String key, final String value1, final String value2) {
        if ("Bundle-Activator".equals(key)) { // can't take 2 values
            throw new IllegalArgumentException("Conflicting activators: " + value1 + ", " + value2);
        }
        return value1 + "," + value2;
    }

    private static class KeyValue {
        private String name;
        private String value;
    }

    private static class PluralAnnotationVisitor extends AnnotationVisitor {
        private final String singular;
        private final Supplier<AnnotationVisitor> visitor;

        private PluralAnnotationVisitor(final String singular, final Supplier<AnnotationVisitor> nestedVisitor) {
            super(ASM9);
            this.visitor = nestedVisitor;
            this.singular = singular;
        }

        @Override
        public AnnotationVisitor visitArray(final String name) {
            switch (name) {
                case "value":
                    return new AnnotationVisitor(ASM9) {
                        @Override
                        public AnnotationVisitor visitAnnotation(final String name, final String descriptor) {
                            if (singular.equals(descriptor)) {
                                return visitor.get();
                            }
                            return super.visitAnnotation(name, descriptor);
                        }
                    };
                default:
                    return super.visitArray(name);
            }
        }
    }
}
