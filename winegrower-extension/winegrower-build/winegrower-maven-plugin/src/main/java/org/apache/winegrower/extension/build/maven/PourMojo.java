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
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.winegrower.Ripener;
import org.apache.winegrower.extension.build.common.Run;
import org.apache.winegrower.scanner.manifest.ManifestContributor;

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
    public void execute() {
        final Thread thread = Thread.currentThread();
        final ClassLoader loader = thread.getContextClassLoader();
        final ClassLoader appLoader = createClassLoader(loader);
        thread.setContextClassLoader(appLoader);
        try {
            new Run(createConfiguration(), systemVariables) {

                @Override
                protected void waitForExit() {
                    if (waitOnSystemIn) {
                        super.waitForExit();
                    } else { // just block
                        try {
                            new CountDownLatch(1).await();
                        } catch (final InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            }.run();
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
        final List<URL> urls = Stream.concat(
                project.getArtifacts().stream()
                        .filter(a -> !((dependencyScopes == null
                                && !(Artifact.SCOPE_COMPILE.equals(a.getScope()) || Artifact.SCOPE_RUNTIME.equals(a.getScope())))
                                || (dependencyScopes != null && !dependencyScopes.contains(a.getScope()))))
                        .map(Artifact::getFile),
                Stream.of(project.getBuild().getOutputDirectory()).map(File::new).filter(File::exists)).map(file -> {
                    try {
                        return file.toURI().toURL();
                    } catch (final MalformedURLException e) {
                        throw new IllegalArgumentException(e);
                    }
                }).collect(toList());
        return urls.isEmpty() ? parent : new URLClassLoader(urls.toArray(new URL[0]), parent) {

            @Override
            public boolean equals(final Object obj) {
                return super.equals(obj) || parent.equals(obj);
            }
        };
    }

    private Ripener.Configuration createConfiguration() {
        final Ripener.Configuration configuration = new Ripener.Configuration();
        ofNullable(workDir).ifPresent(configuration::setWorkDir);
        ofNullable(prioritizedBundles).filter(it -> !it.isEmpty()).ifPresent(configuration::setPrioritizedBundles);
        ofNullable(ignoredBundles).filter(it -> !it.isEmpty()).ifPresent(configuration::setIgnoredBundles);
        ofNullable(scanningIncludes).filter(it -> !it.isEmpty()).ifPresent(configuration::setScanningIncludes);
        ofNullable(scanningExcludes).filter(it -> !it.isEmpty()).ifPresent(configuration::setScanningIncludes);
        ofNullable(manifestContributors).filter(it -> !it.isEmpty()).ifPresent(contributors -> {
            configuration.setManifestContributors(contributors.stream().map(clazz -> {
                try {
                    return Thread.currentThread().getContextClassLoader().loadClass(clazz).getConstructor().newInstance();
                } catch (final InstantiationException | NoSuchMethodException | IllegalAccessException
                        | ClassNotFoundException e) {
                    throw new IllegalArgumentException(e);
                } catch (final InvocationTargetException e) {
                    throw new IllegalArgumentException(e.getTargetException());
                }
            }).map(ManifestContributor.class::cast).collect(toList()));
        });
        ofNullable(jarFilter).ifPresent(filter -> {
            try {
                configuration.setJarFilter((Predicate<String>) Thread.currentThread().getContextClassLoader().loadClass(filter)
                        .getConstructor().newInstance());
            } catch (final InstantiationException | NoSuchMethodException | IllegalAccessException | ClassNotFoundException e) {
                throw new IllegalArgumentException(e);
            } catch (final InvocationTargetException e) {
                throw new IllegalArgumentException(e.getTargetException());
            }
        });
        return configuration;
    }
}
