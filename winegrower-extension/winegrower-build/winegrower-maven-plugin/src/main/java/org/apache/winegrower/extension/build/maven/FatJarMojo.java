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
import static org.apache.maven.plugins.annotations.ResolutionScope.RUNTIME_PLUS_SYSTEM;

import java.io.File;
import java.util.Collection;
import java.util.Objects;
import java.util.stream.Stream;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.winegrower.extension.build.common.FatJar;

@Mojo(name = "fatjar", requiresDependencyResolution = RUNTIME_PLUS_SYSTEM)
public class FatJarMojo extends AbstractMojo {
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "provided,compile,runtime", property = "winegrower.includeScopes")
    private Collection<String> includeScopes;

    @Parameter(defaultValue = "${project.build.directory}/${project.artifactId}-fatjar.jar", property = "winegrower.output")
    private File output;

    @Parameter(defaultValue = "true", property = "winegrower.attach")
    private boolean attach;

    @Parameter(defaultValue = "fatjar", property = "winegrower.classifier")
    private String classifier;

    @Parameter(defaultValue = "${project.build.directory}/${project.build.finalName}.${project.packaging}", property = "winegrower.buildArtifact")
    private File buildArtifact;

    @Component
    private MavenProjectHelper helper;

    @Override
    public void execute() {
        new FatJar(new FatJar.Configuration(collectJars(), output)).run();
        if (attach) {
            helper.attachArtifact(project, output, classifier);
        }
    }

    private Collection<File> collectJars() {
        return Stream.concat(
                    project.getArtifacts().stream()
                        .filter(it -> includeScopes.contains(it.getScope()))
                        .map(Artifact::getFile),
                    Stream.of(buildArtifact))
                .filter(Objects::nonNull)
                .filter(File::exists)
                .collect(toList());
    }
}
