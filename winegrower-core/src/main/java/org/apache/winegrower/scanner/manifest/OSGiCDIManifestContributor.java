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
import org.apache.xbean.finder.archive.Archive;
import org.apache.xbean.finder.archive.FileArchive;
import org.apache.xbean.finder.archive.JarArchive;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Stream;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;
import static org.apache.xbean.asm9.ClassReader.SKIP_CODE;
import static org.apache.xbean.asm9.ClassReader.SKIP_DEBUG;
import static org.apache.xbean.asm9.ClassReader.SKIP_FRAMES;
import static org.apache.xbean.asm9.Opcodes.ASM9;
import static org.osgi.framework.Constants.REQUIRE_CAPABILITY;

// simplified flavor of the scanner requiring to have a META-INF/beans.xml (as in CDI 1.1)
public class OSGiCDIManifestContributor implements ManifestContributor {
    @Override
    public void contribute(final AnnotationFinder finder, final Supplier<Manifest> manifest) {
        final Manifest mf = manifest.get();
        if (hasCdiExtender(mf)) {
            return;
        }

        final Archive archive = finder.getArchive();
        final WinegrowerAnnotationFinder waf = WinegrowerAnnotationFinder.class.cast(finder);
        if (JarArchive.class.isInstance(archive)) {
            try (final JarFile jar = new JarFile(org.apache.xbean.finder.util.Files.toFile(JarArchive.class.cast(archive).getUrl()))) {
                if (jar.getEntry("META-INF/beans.xml") == null) {
                    return;
                }
                appendOsgiCDIExtender(mf, waf);
            } catch (final IOException e) {
                // no-op
            }
        } else if (FileArchive.class.isInstance(archive)) {
            final Path base = FileArchive.class.cast(archive).getDir().toPath();
            if (!Files.exists(base.resolve("META-INF/beans.xml"))) {
                return;
            }
            appendOsgiCDIExtender(mf, waf);
        }
    }

    private void appendOsgiCDIExtender(final Manifest mf, final WinegrowerAnnotationFinder af) {
        if (af.getAnnotatedClassNames().isEmpty()) { // aries-cdi will skip bundles with no bean classes anyway
            return;
        }
        final Attributes attributes = mf.getMainAttributes();
        attributes.putValue(
                REQUIRE_CAPABILITY,
                Stream.of(attributes.getValue(REQUIRE_CAPABILITY), toCapability(af), findCdiExtensions(af))
                        .filter(Objects::nonNull)
                        .filter(it -> !it.isEmpty())
                        .collect(joining(",")));
    }

    private boolean hasCdiExtender(final Manifest manifest) {
        return ofNullable(manifest.getMainAttributes().getValue(REQUIRE_CAPABILITY))
                .map(a -> a.contains("(osgi.extender=osgi.cdi)"))
                .orElse(false);
    }

    // todo: drop and handle @Requirement transitively in core
    private String findCdiExtensions(final WinegrowerAnnotationFinder finder) {
        try {
            return Stream.concat(
                    finder.findAnnotatedClasses("org.apache.aries.cdi.extra.RequireCDIExtensions").stream(),
                    finder.findAnnotatedClasses("org.apache.aries.cdi.extra.RequireCDIExtension").stream())
                    .distinct()
                    .flatMap(c -> extractAriesCdiExtensions(c, finder))
                    .filter(it -> !it.isEmpty())
                    .map(it -> "osgi.cdi.extension;filter:=\"(osgi.cdi.extension=" + it + ")\"")
                    .distinct()
                    .sorted()
                    .collect(joining(","));
        } catch (final Exception cnfe) {
            return null;
        }
    }

    private Stream<String> extractAriesCdiExtensions(final Class<?> aClass, final WinegrowerAnnotationFinder finder) {
        try (final InputStream bytecode = finder.getArchive().getBytecode(aClass.getName())) {
            final Collection<String> extensions = new ArrayList<>();
            final ClassReader reader = new ClassReader(bytecode);
            reader.accept(new EmptyVisitor() {
                @Override
                public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                    switch (desc) {
                        case "Lorg/apache/aries/cdi/extra/RequireCDIExtension;":
                            return new AnnotationVisitor(ASM9) {
                                @Override
                                public void visit(final String name, final Object value) {
                                    if ("value".equals(name)) {
                                        extensions.add(String.valueOf(value));
                                    }
                                }
                            };
                        case "Lorg/apache/aries/cdi/extra/RequireCDIExtensions;":
                            return new AnnotationVisitor(ASM9) {
                                @Override
                                public AnnotationVisitor visitAnnotation(final String name, final String descriptor) {
                                    if ("Lorg/apache/aries/cdi/extra/RequireCDIExtension;".equals(descriptor)) {
                                        return new AnnotationVisitor(ASM9) {
                                            @Override
                                            public void visit(final String name, final Object value) {
                                                if ("value".equals(name)) {
                                                    extensions.add(String.valueOf(value));
                                                }
                                            }
                                        };
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
            return extensions.stream();
        } catch (final IOException | ClassNotFoundException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private String toCapability(final WinegrowerAnnotationFinder finder) {
        return "osgi.extender;filter:=\"(osgi.extender=osgi.cdi)\";beans:List<String>=\"" +
                finder.getAnnotatedClassNames().stream().sorted().collect(joining(",")) + "\"";
    }
}
