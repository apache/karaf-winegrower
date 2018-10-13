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
package org.apache.winegrower.test;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

import org.apache.winegrower.Ripener;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

@Target(METHOD)
@Retention(RUNTIME)
@ExtendWith(WithRipener.Extension.class)
public @interface WithRipener {

    @Target(PARAMETER)
    @Retention(RUNTIME)
    @interface Service {
    }

    @Retention(RUNTIME)
    @interface Entry {

        String jarName() default "";

        String path();

        String prefix() default "";
    }

    String workDir() default "";

    String[] includes() default {};

    String[] dependencies() default "target/${test}/*.jar";

    Entry[] includeResources() default {};

    class Extension implements BeforeEachCallback, AfterEachCallback, ParameterResolver {

        private static final String CLASSES_BASE = System.getProperty(Extension.class.getName() + ".classesBase",
                "target/test-classes/");

        private static final String WORK_DIR = System.getProperty(Extension.class.getName() + ".workdir", "target/winegrower");

        private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(Extension.class.getName());

        @Override
        public void beforeEach(final ExtensionContext extensionContext) {
            final WithRipener config = getConfig(extensionContext).orElseThrow(IllegalArgumentException::new);

            final Thread thread = Thread.currentThread();
            final URL[] urls = createUrls(config, extensionContext);
            final URLClassLoader loader = new URLClassLoader(urls, thread.getContextClassLoader());
            final ExtensionContext.Store store = extensionContext.getStore(NAMESPACE);
            store.put(Context.class, new Context(thread, thread.getContextClassLoader(), loader));
            thread.setContextClassLoader(loader);

            final Ripener.Configuration configuration = new Ripener.Configuration();
            configuration.setScanningExcludes(singletonList("test-classes"));
            setConfiguration(configuration, config);

            final Ripener ripener = new Ripener.Impl(configuration).start();
            store.put(Ripener.class, ripener);

            ripener.getServices().inject(extensionContext.getRequiredTestInstance());
        }

        @Override
        public void afterEach(final ExtensionContext extensionContext) {
            final ExtensionContext.Store store = extensionContext.getStore(NAMESPACE);
            if (store == null) {
                return;
            }
            ofNullable(store.get(Context.class, Context.class)).ifPresent(Context::close);
            ofNullable(store.get(FilesToDelete.class, FilesToDelete.class)).ifPresent(it -> it.files.forEach(f -> {
                if (f.exists() && !f.delete()) {
                    f.deleteOnExit();
                }
            }));
            ofNullable(store.get(Ripener.class, Ripener.class)).ifPresent(Ripener::stop);
        }

        private void setConfiguration(final Ripener.Configuration configuration, final WithRipener config) {
            final Collection<String> includes = asList(config.includes());
            if (!includes.isEmpty()) {
                configuration.setJarFilter(it -> includes.stream().anyMatch(e -> e.startsWith(it)));
            }

            final String workDir = config.workDir();
            if (!workDir.isEmpty()) {
                configuration.setWorkDir(new File(workDir));
            }
        }

        private URL[] createUrls(final WithRipener config, final ExtensionContext context) {
            return Stream.concat(Stream.of(config.dependencies())
                    .flatMap(it -> Stream.of(variabilize(it, context.getTestClass().map(Class::getName).orElse("default")),
                            variabilize(it, "default")))
                    .flatMap(this::listFiles).filter(File::exists).map(f -> {
                        try {
                            return f.toURI().toURL();
                        } catch (final MalformedURLException e) {
                            throw new IllegalArgumentException(e);
                        }
                    }), Stream.of(config.includeResources()).map(resources -> {
                        try {
                            final File jar = createJar(resources);
                            context.getStore(NAMESPACE).getOrComputeIfAbsent(FilesToDelete.class, ignored -> new FilesToDelete(),
                                    FilesToDelete.class).files.add(jar);
                            return jar.toURI().toURL();
                        } catch (final MalformedURLException e) {
                            throw new IllegalArgumentException(e);
                        }
                    })).toArray(URL[]::new);
        }

        private Optional<WithRipener> getConfig(ExtensionContext context) {
            return context
                    .getElement().map(e -> ofNullable(e.getAnnotation(WithRipener.class))
                            .orElseGet(()-> context.getParent()
                                                   .flatMap(ExtensionContext::getElement)
                                                   .map(it -> it.getAnnotation(WithRipener.class))
                                                   .orElse(null)));
        }

        private Stream<File> listFiles(final String it) {
            return it.endsWith("*.jar")
                    ? Stream.of(ofNullable(new File(it.substring(0, it.length() - "*.jar".length()))
                            .listFiles((dir, name) -> name.endsWith(".jar"))).orElseGet(() -> new File[0]))
                    : Stream.of(new File(it));
        }

        private File createJar(final Entry entry) {
            final File out = new File(
                    WORK_DIR,
                    of(entry.jarName()).filter(it -> !it.isEmpty()).orElseGet(() -> UUID.randomUUID().toString()) + ".jar");
            out.getParentFile().mkdirs();
            try (final JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream(out))) {
                final Set<String> createdFolders = new HashSet<>();
                try {
                    final File classesRoot = new File(CLASSES_BASE);
                    final Path classesPath = classesRoot.toPath();
                    final Path root = new File(classesRoot, entry.path().replace(".", "/")).toPath();
                    Files.walkFileTree(root, new SimpleFileVisitor<Path>() {

                        @Override
                        public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                            String relative = classesPath.relativize(file).toString().substring(entry.prefix().length());
                            if (relative.endsWith("META-INF/MANIFEST.MF")) { // simpler config
                                relative = "META-INF/MANIFEST.MF";
                            }
                            final String[] segments = relative.split("/");
                            final StringBuilder builder = new StringBuilder(relative.length());
                            for (int i = 0; i < segments.length - 1; i++) {
                                builder.append(segments[i]).append('/');
                                final String folder = builder.toString();
                                if (createdFolders.add(folder)) {
                                    jarOutputStream.putNextEntry(new JarEntry(folder));
                                    jarOutputStream.closeEntry();
                                }
                            }
                            jarOutputStream.putNextEntry(new JarEntry(relative));
                            Files.copy(file, jarOutputStream);
                            jarOutputStream.closeEntry();
                            return super.visitFile(file, attrs);
                        }
                    });
                } catch (final IOException e) {
                    throw new IllegalStateException(e);
                }
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
            return out;
        }

        private String variabilize(final String name, final String testName) {
            return name.replace("${test}", testName);
        }

        @Override
        public boolean supportsParameter(final ParameterContext parameterContext, final ExtensionContext extensionContext)
                throws ParameterResolutionException {
            return supports(parameterContext.getParameter().getType());
        }

        @Override
        public Object resolveParameter(final ParameterContext parameterContext, final ExtensionContext extensionContext)
                throws ParameterResolutionException {
            return findInjection(extensionContext, parameterContext.getParameter().getType());
        }

        private boolean supports(final Class<?> type) {
            return type == Ripener.class;
        }

        private <T> T findInjection(final ExtensionContext extensionContext, final Class<T> type) {
            return extensionContext.getStore(NAMESPACE).get(type, type);
        }

        private static class Context implements AutoCloseable {

            private final Thread thread;

            private final ClassLoader previousLoader;

            private final URLClassLoader currentLoader;

            private Context(final Thread thread, final ClassLoader previousLoader, final URLClassLoader currentLoader) {
                this.thread = thread;
                this.previousLoader = previousLoader;
                this.currentLoader = currentLoader;
            }

            @Override
            public void close() {
                thread.setContextClassLoader(previousLoader);
                try {
                    currentLoader.close();
                } catch (final IOException e) {
                    throw new IllegalStateException(e);
                }
            }
        }

        private static class FilesToDelete {
            private final Collection<File> files = new ArrayList<>();
        }
    }
}
