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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.DefaultDependencyResolutionRequest;
import org.apache.maven.project.DependencyResolutionException;
import org.apache.maven.project.DependencyResolutionRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import org.apache.winegrower.extension.build.common.Build;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;
import org.eclipse.aether.impl.ArtifactResolver;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

@Mojo(name = "distribution", requiresDependencyResolution = RUNTIME_PLUS_SYSTEM)
public class DistributionMojo extends AbstractMojo {
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "provided,compile,runtime", property = "winegrower.includeScopes")
    private Collection<String> includeScopes;

    @Parameter(defaultValue = "${project.build.directory}/${project.artifactId}-distribution", property = "winegrower.workDir")
    private File workDir;

    @Parameter(defaultValue = "zip", property = "winegrower.formats")
    private Collection<String> formats;

    @Parameter(defaultValue = "org.apache.winegrower.Ripener", property = "winegrower.main")
    private String main;

    @Parameter(property = "winegrower.bin", defaultValue = "src/main/winegrower/bin")
    private String bin;

    @Parameter(property = "winegrower.conf", defaultValue = "src/main/winegrower/conf")
    private String conf;

    @Parameter(property = "winegrower.libs")
    private Collection<String> libs;

    @Parameter(property = "winegrower.no-root", defaultValue = "false")
    private boolean skipArchiveRootFolder;

    @Parameter(property = "winegrower.keep-exploded-folder", defaultValue = "false")
    private boolean keepExplodedFolder;

    @Parameter(defaultValue = "true", property = "winegrower.attach")
    private boolean attach;

    @Parameter(defaultValue = "fatjar-%s", property = "winegrower.classifier")
    private String classifier;

    @Parameter(defaultValue = "${project.build.directory}/${project.build.finalName}.${project.packaging}", property = "winegrower.buildArtifact")
    private File buildArtifact;

    @Component
    private MavenProjectHelper helper;

    @Component
    private ArtifactResolver resolver;

    @Component
    private RepositorySystem repositorySystem;

    @Component
    private ProjectDependenciesResolver dependenciesResolver;

    @Component
    private DependencyGraphBuilder graphBuilder;

    @Parameter(defaultValue = "${repositorySystemSession}")
    private RepositorySystemSession session;

    @Parameter(defaultValue = "${project.remoteProjectRepositories}")
    private List<RemoteRepository> remoteRepositories;

    @Override
    public void execute() {
        new Build(new Build.Configuration(
                workDir, project.getBasedir(), project.getArtifactId(),
                collectJars(), formats,
                main, bin, conf, skipArchiveRootFolder, keepExplodedFolder
        )).run();
        if (attach) {
            formats.forEach(ext -> helper.attachArtifact(
                    project,
                    new File(workDir, project.getArtifactId() + "-winegrower-distribution." + ext),
                    String.format(classifier, ext)));
        }
    }

    private Stream<File> collectTransitiveDependencies(final Dependency dependency) {
        final DependencyResolutionRequest request = new DefaultDependencyResolutionRequest();
        request.setMavenProject(new MavenProject() {{
            getDependencies().add(dependency);
        }});
        request.setRepositorySession(session);
        try {
            final Collection<File> files = new ArrayList<>();
            dependenciesResolver.resolve(request).getDependencyGraph().accept(new DependencyVisitor() {
                @Override
                public boolean visitEnter(final DependencyNode node) {
                    return true;
                }

                @Override
                public boolean visitLeave(final DependencyNode node) {
                    final org.eclipse.aether.artifact.Artifact artifact = node.getArtifact();
                    files.add(artifact.getFile());
                    return true;
                }
            });
            return files.stream();
        } catch (final DependencyResolutionException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private File resolve(final String group, final String artifact, final String version, final String classifier) {
        final DefaultArtifact art = new DefaultArtifact(group, artifact, classifier, "jar", version);
        final ArtifactRequest artifactRequest = new ArtifactRequest().setArtifact(art).setRepositories(remoteRepositories);

        final LocalRepositoryManager lrm = session.getLocalRepositoryManager();
        art.setFile(new File(lrm.getRepository().getBasedir(), lrm.getPathForLocalArtifact(artifactRequest.getArtifact())));

        try {
            final ArtifactResult result = repositorySystem.resolveArtifact(session, artifactRequest);
            if (result.isMissing()) {
                throw new IllegalStateException("Can't find commons-cli, please add it to the pom.");
            }
            return result.getArtifact().getFile();
        } catch (final ArtifactResolutionException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private Stream<File> collectLibs() {
        return ofNullable(libs)
                .map(value -> value.stream().flatMap(l -> {
                    final boolean transitive = l.endsWith("?transitive");
                    final String coords = transitive ? l.substring(0, l.length() - "?transitive".length()) : l;
                    final String[] c = coords.split(":");
                    if (c.length < 3 || c.length > 5) {
                        throw new IllegalArgumentException("libs syntax is groupId:artifactId:version[:classifier][:type[?transitive]]");
                    }
                    if (!transitive) {
                        return Stream.of(resolve(c[0], c[1], c[2], c.length == 4 ? c[3] : ""));
                    } else {
                        return collectTransitiveDependencies(new Dependency() {{
                            setGroupId(c[0]);
                            setArtifactId(c[1]);
                            setVersion(c[2]);
                            if (c.length == 4 && !"-".equals(c[3])) {
                                setClassifier(c[3]);
                            }
                            if (c.length == 5) {
                                setType(c[4]);
                            }
                        }});
                    }
                }).filter(it -> !it.getName().endsWith(".pom")))
                .orElseGet(Stream::empty);
    }
    private Collection<File> collectJars() {
        return Stream.concat(Stream.concat(
                    collectDependencies(),
                    Stream.of(buildArtifact)),
                    collectLibs())
                .filter(Objects::nonNull)
                .filter(File::exists)
                .collect(toList());
    }

    private Stream<File> collectDependencies() {
        return project.getArtifacts().stream()
            .filter(it -> includeScopes.contains(it.getScope()))
            .map(Artifact::getFile);
    }
}
