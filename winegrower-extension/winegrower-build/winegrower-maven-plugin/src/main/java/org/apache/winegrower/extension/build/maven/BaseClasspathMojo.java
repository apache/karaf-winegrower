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

import static java.util.stream.Collectors.toList;

import java.io.File;
import java.util.Collection;
import java.util.Objects;
import java.util.stream.Stream;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

public abstract class BaseClasspathMojo extends AbstractMojo {
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject project;

    @Parameter(defaultValue = "provided,compile,runtime", property = "winegrower.includeScopes")
    private Collection<String> includeScopes;

    @Parameter(property = "winegrower.excludeArtifacts")
    private Collection<String> excludeArtifacts;

    @Parameter(defaultValue = "${project.build.directory}/${project.build.finalName}.${project.packaging}", property = "winegrower.buildArtifact")
    private File buildArtifact;

    @Parameter(defaultValue = "false", property = "winegrower.skipIfNoActivator")
    protected boolean skipIfNoActivator;

    protected Collection<File> collectJars() {
        return Stream.concat(
                    project.getArtifacts().stream()
                        .filter(it -> includeScopes.contains(it.getScope()))
                        .filter(it -> excludeArtifacts == null ||
                                (!excludeArtifacts.contains(it.getArtifactId()) && !excludeArtifacts.contains(it.getGroupId() + ':' + it.getArtifactId())))
                        .map(Artifact::getFile),
                    Stream.of(buildArtifact))
                .filter(Objects::nonNull)
                .filter(File::exists)
                .collect(toList());
    }
}
