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
package org.apache.winegrower.extension.build.maven;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;
import static org.apache.maven.plugins.annotations.ResolutionScope.RUNTIME_PLUS_SYSTEM;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.xbean.finder.util.Files;

@Mojo(name = "pour", requiresDependencyResolution = RUNTIME_PLUS_SYSTEM)
public class PourMojo extends AbstractMojo {

    @Parameter(property = "winegrower.workDir", defaultValue = "${project.build.directory}/winegrower/workdir")
    private File workDir;

    @Parameter(property = "winegrower.jarFilter")
    private String jarFilter;

    @Parameter(property = "winegrower.scanningIncludes")
    private Collection<String> scanningIncludes;

    @Parameter(property = "winegrower.scanningExcludes")
    private Collection<String> scanningExcludes;

    @Parameter(property = "winegrower.ignoredBundles")
    private Collection<String> ignoredBundles;

    @Parameter(property = "winegrower.manifestContributors")
    private Collection<String> manifestContributors;

    @Parameter(property = "winegrower.dependencyScopes", defaultValue = "provided,compile,system,runtime")
    private Collection<String> dependencyScopes;

    @Parameter(property = "winegrower.prioritizedBundles")
    private List<String> prioritizedBundles;

    @Parameter(property = "winegrower.systemVariables")
    private Map<String, String> systemVariables;

    @Parameter(property = "winegrower.waitOnSystemIn", defaultValue = "true")
    private boolean waitOnSystemIn; // if you use the shell this breaks it if set to true

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Override
    public void execute() throws MojoExecutionException {
        final Thread thread = Thread.currentThread();
        final ClassLoader loader = thread.getContextClassLoader();
        final ClassLoader appLoader = createClassLoader(loader);
        thread.setContextClassLoader(appLoader);
        try {
            final Consumer<Runnable> waitOnExit = defaultImpl -> {
                if (waitOnSystemIn) {
                    defaultImpl.run();
                } else { // just block
                    try {
                        new CountDownLatch(1).await();
                    } catch (final InterruptedException e) {
                        Thread.currentThread()
                              .interrupt();
                    }
                }
            };
            final Class<?> runClass = appLoader.loadClass("org.apache.winegrower.extension.build.common.Run");
            final Class<?> confClass = appLoader.loadClass("org.apache.winegrower.Ripener$Configuration");
            Runnable.class.cast(runClass.getConstructor(confClass, Map.class, Consumer.class)
                    .newInstance(createConfiguration(confClass), systemVariables, waitOnExit)).run();
        } catch (final Exception e) {
            throw new MojoExecutionException(e.getMessage(), e);
        } finally {
            if (appLoader != loader) {
                try {
                    URLClassLoader.class.cast(appLoader).close();
                } catch (final IOException e) {
                    getLog().warn(e.getMessage(), e);
                }
            }
            thread.setContextClassLoader(loader);
        }
    }

    private ClassLoader createClassLoader(final ClassLoader parent) {
        final List<File> jars = Stream.concat(project.getArtifacts().stream()
            .filter(a -> !((dependencyScopes == null && !(Artifact.SCOPE_COMPILE.equals(
                a.getScope()) || Artifact.SCOPE_RUNTIME.equals(
                a.getScope()))) || (dependencyScopes != null && !dependencyScopes.contains(
                a.getScope()))))
            .map(Artifact::getFile), Stream.of(project.getBuild().getOutputDirectory()).map(File::new).filter(File::exists))
            .collect(toList());
        final List<URL> urls = jars.stream().map(file -> {
            try {
                return file.toURI().toURL();
            } catch (final MalformedURLException e) {
                throw new IllegalArgumentException(e);
            }
        }).collect(toList());
        final boolean excludeParentClasses = jars.stream()
             .anyMatch(it -> it.getName().startsWith("org.osgi.") || it.getName().startsWith("osgi."));
        if (excludeParentClasses) {
            // add build-common
            final File buildCommon = Files.toFile(parent.getResource("org/apache/winegrower/extension/build/common/Run.class"));
            try {
                urls.add(buildCommon.toURI().toURL());
            } catch (final MalformedURLException e) {
                throw new IllegalArgumentException(e);
            }
        }
        return urls.isEmpty() ? parent : new URLClassLoader(
                urls.toArray(new URL[0]),
                !excludeParentClasses ? parent : new ClassLoader(parent) {
                    @Override
                    protected Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
                        if (name.startsWith("org.")) {
                            final String sub = name.substring("org.".length());
                            if (sub.startsWith("osgi.")) {
                                throw new ClassNotFoundException(name);
                            }
                            if (sub.startsWith("apache.")) {
                                final String apache = sub.substring("apache.".length());
                                if (apache.startsWith("winegrower.")) {
                                    throw new ClassNotFoundException(name);
                                }
                                if (apache.startsWith("xbean.")) {
                                    throw new ClassNotFoundException(name);
                                }
                            }
                        }
                        return super.loadClass(name, resolve);
                    }
                }) {

                    @Override
                    public boolean equals(final Object obj) {
                        return super.equals(obj) || parent.equals(obj);
                    }
                };
    }

    private Object createConfiguration(final Class<?> configClass)
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        final Object configuration = configClass.getConstructor().newInstance();
        ofNullable(workDir)
                .ifPresent(value -> doCall(configuration, "setWorkDir", new Class<?>[]{File.class}, new Object[]{value}));
        ofNullable(prioritizedBundles)
                .ifPresent(value -> doCall(configuration, "setPrioritizedBundles", new Class<?>[]{List.class}, new Object[]{value}));
        ofNullable(ignoredBundles)
                .ifPresent(value -> doCall(configuration, "setIgnoredBundles", new Class<?>[]{Collection.class}, new Object[]{value}));
        ofNullable(scanningIncludes)
                .ifPresent(value -> doCall(configuration, "setScanningIncludes", new Class<?>[]{Collection.class}, new Object[]{value}));
        ofNullable(scanningExcludes)
                .ifPresent(value -> doCall(configuration, "setScanningExcludes", new Class<?>[]{Collection.class}, new Object[]{value}));
        ofNullable(manifestContributors)
                .ifPresent(contributors -> {
                    try {
                        final Class<?> type = Thread.currentThread().getContextClassLoader().loadClass(
                                "org.apache.winegrower.scanner.manifest.ManifestContributor");
                        final Collection<?> value = contributors.stream()
                                                                .map(clazz -> {
                                                                    try {
                                                                        return Thread.currentThread()
                                                                                     .getContextClassLoader()
                                                                                     .loadClass(clazz)
                                                                                     .getConstructor()
                                                                                     .newInstance();
                                                                    } catch (final InstantiationException | NoSuchMethodException | IllegalAccessException | ClassNotFoundException e) {
                                                                        throw new IllegalArgumentException(e);
                                                                    } catch (final InvocationTargetException e) {
                                                                        throw new IllegalArgumentException(
                                                                                e.getTargetException());
                                                                    }
                                                                })
                                                                .map(type::cast)
                                                                .collect(toList());
                        doCall(configuration, "setManifestContributors", new Class<?>[]{Collection.class}, new Object[]{value});
                    } catch (final ClassNotFoundException e) {
                        throw new IllegalArgumentException(e);
                    }
                });
        ofNullable(jarFilter)
                .map(String::valueOf)
                .filter(it -> !it.isEmpty())
                .ifPresent(filter -> {
                    try {
                        final Predicate<String> predicate = (Predicate<String>) Thread.currentThread()
                                                                                      .getContextClassLoader().loadClass(filter).getConstructor().newInstance();
                        doCall(configuration, "setJarFilter", new Class<?>[]{Predicate.class}, new Object[]{predicate});
                    } catch (final InstantiationException | NoSuchMethodException | IllegalAccessException | ClassNotFoundException e) {
                        throw new IllegalArgumentException(e);
                    } catch (final InvocationTargetException e) {
                        throw new IllegalArgumentException(e.getTargetException());
                    }
                });
        return configuration;
    }

    private static void doCall(final Object instance, final String mtd, final Class<?>[] paramTypes, final Object[] args) {
        try {
            instance.getClass().getMethod(mtd, paramTypes).invoke(instance, args);
        } catch (final InvocationTargetException ite) {
            final Throwable targetException = ite.getTargetException();
            if (RuntimeException.class.isInstance(targetException)) {
                throw RuntimeException.class.cast(targetException);
            }
            throw new IllegalStateException(targetException);
        } catch (final Exception ex) {
            if (RuntimeException.class.isInstance(ex)) {
                throw RuntimeException.class.cast(ex);
            }
            throw new IllegalStateException(ex);
        }
    }
}
