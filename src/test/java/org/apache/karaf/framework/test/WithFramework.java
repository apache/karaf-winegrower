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
package org.apache.karaf.framework.test;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
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
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

import org.apache.karaf.framework.StandaloneLifecycle;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;

@Target(TYPE)
@Retention(RUNTIME)
@ExtendWith(WithFramework.Extension.class)
public @interface WithFramework {
    @Target(FIELD)
    @Retention(RUNTIME)
    @interface Service {
    }

    String[] dependencies() default "target/${test}/*.jar";
    String[] includeResources() default "";

    class Extension implements BeforeAllCallback, AfterAllCallback, TestInstancePostProcessor, ParameterResolver {

        private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(Extension.class.getName());

        @Override
        public void beforeAll(final ExtensionContext extensionContext) {
            final Thread thread = Thread.currentThread();
            final URLClassLoader loader = new URLClassLoader(createUrls(extensionContext), thread.getContextClassLoader());
            final ExtensionContext.Store store = extensionContext.getStore(NAMESPACE);
            store.put(Context.class, new Context(thread, loader.getParent(), loader));
            store.put(StandaloneLifecycle.class, new StandaloneLifecycle().start());
        }

        @Override
        public void afterAll(final ExtensionContext extensionContext) {
            final ExtensionContext.Store store = extensionContext.getStore(NAMESPACE);
            if (store == null) {
                return;
            }
            ofNullable(store.get(Context.class, Context.class)).ifPresent(Context::close);
            ofNullable(store.get(StandaloneLifecycle.class, StandaloneLifecycle.class)).ifPresent(StandaloneLifecycle::stop);
        }

        private URL[] createUrls(final ExtensionContext context) {
            return context.getElement()
                   .map(e -> e.getAnnotation(WithFramework.class))
                   .map(config -> Stream.concat(Stream.of(config.dependencies())
                          .flatMap(it -> Stream.of(
                                  variabilize(it, context.getTestClass().map(Class::getName).orElse("default")),
                                  variabilize(it, "default")))
                          .map(File::new)
                          .filter(File::exists)
                          .map(f -> {
                              try {
                                  return f.toURI().toURL();
                              } catch (final MalformedURLException e) {
                                  throw new IllegalArgumentException(e);
                              }
                          }), of(config.includeResources())
                               .filter(it -> it.length > 0)
                               .map(resources -> {
                                   try {
                                       return Stream.of(createJar(resources).toURI().toURL());
                                   } catch (final MalformedURLException e) {
                                       throw new IllegalArgumentException(e);
                                   }
                               })
                               .orElseGet(Stream::empty))
                          .toArray(URL[]::new))
                   .orElseGet(() -> new URL[0]);
        }

        private File createJar(final String[] resources) {
            final File out = new File("target/test");
            try (final JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream(out))) {
                
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
            return out;
        }

        private String variabilize(final String name, final String testName) {
            return name.replace("${test}", testName);
        }

        @Override
        public  boolean supportsParameter(final ParameterContext parameterContext, final ExtensionContext extensionContext)
                throws ParameterResolutionException {
            return supports(parameterContext.getParameter().getType());
        }

        @Override
        public Object resolveParameter(final ParameterContext parameterContext, final ExtensionContext extensionContext)
                throws ParameterResolutionException {
            return findInjection(extensionContext, parameterContext.getParameter().getType());
        }

        @Override
        public void postProcessTestInstance(final Object testInstance, final ExtensionContext context) {
            Class<?> testClass = context.getRequiredTestClass();
            while (testClass != Object.class) {
                Stream.of(testClass.getDeclaredFields()).filter(c -> c.isAnnotationPresent(Service.class)).forEach(
                        f -> {
                            if (!supports(f.getType())) {
                                throw new IllegalArgumentException("@Service not supported on " + f);
                            }
                            if (!f.isAccessible()) {
                                f.setAccessible(true);
                            }
                            try {
                                f.set(testInstance, findInjection(context, f.getType()));
                            } catch (final IllegalAccessException e) {
                                throw new IllegalStateException(e);
                            }
                        });
                testClass = testClass.getSuperclass();
            }
        }

        private boolean supports(final Class<?> type) {
            return type == StandaloneLifecycle.class;
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
    }
}
