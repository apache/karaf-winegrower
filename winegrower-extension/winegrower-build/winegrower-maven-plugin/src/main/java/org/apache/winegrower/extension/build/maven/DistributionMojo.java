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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.winegrower.extension.build.common.Build;

import java.io.File;
import java.util.Collection;
import java.util.Objects;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.apache.maven.plugins.annotations.ResolutionScope.RUNTIME_PLUS_SYSTEM;

@Mojo(name = "distribution", requiresDependencyResolution = RUNTIME_PLUS_SYSTEM)
public class DistributionMojo extends LibsMojo {
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
