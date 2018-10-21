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
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.winegrower.extension.build.common.ManifestCreator;

@Mojo(name = "manifest", requiresDependencyResolution = RUNTIME_PLUS_SYSTEM)
public class ManifestMojo extends AbstractMojo {
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${project.build.outputDirectory}", property = "winegrower.classes")
    private File classes;

    @Parameter(defaultValue = "${project.build.outputDirectory}/META-INF/MANIFEST.MF", property = "winegrower.output")
    private File output;

    @Parameter(property = "winegrower.manifestBase")
    private File manifestBase;

    @Parameter(property = "winegrower.customEntries")
    private Map<String, String> customEntries;

    @Parameter(defaultValue = "org.apache.winegrower.scanner.manifest.ActivatorManifestContributor," +
            "org.apache.winegrower.scanner.manifest.KarafCommandManifestContributor," +
            "org.apache.winegrower.scanner.manifest.OSGIInfContributor", property = "winegrower.manifestContributors")
    private Collection<String> manifestContributors;

    @Override
    public void execute() {
        new ManifestCreator(new ManifestCreator.Configuration(
                collectClassLoaderFiles(),
                classes,
                manifestContributors,
                manifestBase,
                customEntries,
                output
        )).run();
    }

    private Collection<File> collectClassLoaderFiles() {
        return Stream.concat(
                    project.getArtifacts().stream().map(Artifact::getFile),
                    Stream.of(classes))
                .filter(Objects::nonNull)
                .filter(File::exists)
                .collect(toList());
    }
}
