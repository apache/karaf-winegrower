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

    @Parameter(property = "winegrower.includeArtifacts")
    private Collection<String> includeArtifacts;

    @Parameter(property = "winegrower.excludeArtifacts")
    private Collection<String> excludeArtifacts;

    @Parameter(defaultValue = "${project.build.outputDirectory}")
    private File classes;

    @Parameter(defaultValue = "${project.build.finalName}.${project.packaging}")
    private String projectArtifactName;

    @Parameter(defaultValue = "${project.build.directory}/${project.build.finalName}.${project.packaging}", property = "winegrower.buildArtifact")
    private File buildArtifact;

    @Parameter(defaultValue = "false", property = "winegrower.autoFiltering")
    protected boolean autoFiltering;

    public String getProjectArtifactName() {
        if (projectArtifactName.endsWith(".bundle")) {
            return projectArtifactName.substring(0, projectArtifactName.length() - "bundle".length()) + "jar";
        }
        return projectArtifactName;
    }

    protected Collection<File> collectJars() {
        return Stream.concat(
                    project.getArtifacts().stream()
                        .filter(it -> includeScopes.contains(it.getScope()))
                        .filter(it -> isIncluded(it) || !isExcluded(it))
                        .map(Artifact::getFile),
                    Stream.of(buildArtifact.exists() ? buildArtifact : classes)
                        .map(this::fixBundleExtension))
                .filter(Objects::nonNull)
                .filter(File::exists)
                .collect(toList());
    }

    private boolean isIncluded(final Artifact it) {
        return includeArtifacts == null ||
                includeArtifacts.contains(it.getArtifactId()) ||
                includeArtifacts.contains(it.getGroupId() + ':' + it.getArtifactId());
    }

    private boolean isExcluded(final Artifact it) {
        return excludeArtifacts != null &&
                (excludeArtifacts.contains(it.getArtifactId()) || excludeArtifacts.contains(it.getGroupId() + ':' + it.getArtifactId()));
    }

    private File fixBundleExtension(final File file) {
        if (!file.exists() && file.getName().endsWith(".bundle")) {
            return new File(file.getParent(), file.getName().substring(0, file.getName().length() - "bundle".length()) + "jar");
        }
        return file;
    }
}
