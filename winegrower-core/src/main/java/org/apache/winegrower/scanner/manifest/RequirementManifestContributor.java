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
import org.apache.xbean.asm9.shade.commons.EmptyVisitor;
import org.apache.xbean.finder.AnnotationFinder;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.apache.xbean.asm9.ClassReader.SKIP_CODE;
import static org.apache.xbean.asm9.ClassReader.SKIP_DEBUG;
import static org.apache.xbean.asm9.ClassReader.SKIP_FRAMES;
import static org.apache.xbean.asm9.Opcodes.ASM9;
import static org.osgi.framework.Constants.REQUIRE_CAPABILITY;

public class RequirementManifestContributor implements ManifestContributor {
    @Override
    public void contribute(final AnnotationFinder finder, final Supplier<Manifest> manifest) {
        final Attributes attributes = manifest.get().getMainAttributes();
        final String existing = attributes.getValue(REQUIRE_CAPABILITY);
        final Collection<String> requirements = findRequirements(WinegrowerAnnotationFinder.class.cast(finder));
        if (requirements == null || requirements.isEmpty()) {
            return;
        }
        attributes.putValue(
                REQUIRE_CAPABILITY,
                Stream.concat(
                        Stream.of(existing),
                        requirements.stream()
                                .filter(it -> existing == null || !existing.contains(it)))
                        .filter(Objects::nonNull)
                        .filter(it -> !it.isEmpty())
                        .collect(joining(",")));
    }

    private Collection<String> findRequirements(final WinegrowerAnnotationFinder finder) {
        try {
            return Stream.concat(
                    finder.findAnnotatedClasses("org.osgi.annotation.bundle.Requirement").stream(),
                    finder.findAnnotatedClasses("org.osgi.annotation.bundle.Requirements").stream())
                    .distinct()
                    .flatMap(c -> findRequirements(c, finder))
                    .filter(it -> !it.isEmpty())
                    .distinct()
                    .sorted()
                    .collect(toList());
        } catch (final Exception cnfe) {
            return emptyList();
        }
    }

    private Stream<String> findRequirements(final Class<?> aClass, final WinegrowerAnnotationFinder finder) {
        try (final InputStream bytecode = finder.getArchive().getBytecode(aClass.getName())) {
            final Collection<String> requirements = new ArrayList<>();
            final ClassReader reader = new ClassReader(bytecode);
            reader.accept(new EmptyVisitor() {
                @Override
                public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                    switch (desc) {
                        case "Lorg/osgi/annotation/bundle/Requirement;":
                            return new RequirementAnnotationVisitor(requirements);
                        case "Lorg/osgi/annotation/bundle/Requirements;":
                            return new AnnotationVisitor(ASM9) {
                                @Override
                                public AnnotationVisitor visitAnnotation(final String name, final String descriptor) {
                                    if ("Lorg/osgi/annotation/bundle/Requirement;".equals(descriptor)) {
                                        return new RequirementAnnotationVisitor(requirements);
                                    }
                                    return super.visitAnnotation(desc, descriptor);
                                }

                                @Override
                                public AnnotationVisitor visitArray(final String name) {
                                    if ("value".equals(name)) {
                                        return this;
                                    }
                                    return super.visitArray(name);
                                }
                            };
                        default:
                            return super.visitAnnotation(desc, visible);
                    }
                }
            }, SKIP_FRAMES | SKIP_CODE | SKIP_DEBUG);
            return requirements.stream();
        } catch (final IOException | ClassNotFoundException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static class RequirementAnnotationVisitor extends AnnotationVisitor {
        private final Collection<String> requirements;

        private String namespace;
        private String name;

        private RequirementAnnotationVisitor(final Collection<String> requirements) {
            super(ASM9);
            this.requirements = requirements;
        }

        @Override
        public void visit(final String name, final Object value) {
            switch (name) {
                case "name":
                    this.name = String.valueOf(value);
                    break;
                case "namespace":
                    this.namespace = String.valueOf(value);
                    break;
                default:
            }
        }

        @Override
        public void visitEnd() {
            super.visitEnd();
            if (name != null && namespace != null) {
                requirements.add(namespace + ";filter:=\"(" + namespace + '=' + name + ")\"");
            }
        }
    }
}
