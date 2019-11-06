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

import static org.apache.maven.plugins.annotations.ResolutionScope.RUNTIME_PLUS_SYSTEM;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarInputStream;
import java.util.zip.ZipEntry;

import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.winegrower.extension.build.common.MetadataBuilder;

@Mojo(name = "metadata", requiresDependencyResolution = RUNTIME_PLUS_SYSTEM)
public class MetadataBuilderMojo extends BaseClasspathMojo {
    @Parameter(defaultValue = "${project.build.outputDirectory}", property = "winegrower.metadata.output")
    private File output;

    @Parameter(defaultValue = "WINEGROWER-INF/%s.properties", property = "winegrower.metadata.namingPattern")
    private String namingPattern;

    @Override
    public void execute() {
        final MetadataBuilder metadataBuilder = new MetadataBuilder(skipIfNoActivator);
        final Set<String> alreadyAdded = new HashSet<>();
        collectJars().forEach(jar -> {
            if (jar.isDirectory()) {
                metadataBuilder.visitFolder(getProjectArtifactName(), jar.toPath(), new SimpleFileVisitor<Path>() {});
            } else {
                try (final JarInputStream inputStream = new JarInputStream(new BufferedInputStream(new FileInputStream(jar)))) {
                    metadataBuilder.onJar(jar.getName(), inputStream.getManifest());

                    ZipEntry nextEntry;
                    while ((nextEntry = inputStream.getNextEntry()) != null) {
                        final String name = nextEntry.getName();
                        if (!alreadyAdded.add(name)) {
                            continue;
                        }
                        metadataBuilder.onFile(name);
                    }
                } catch (final IOException e) {
                    throw new IllegalStateException(e);
                }
                metadataBuilder.afterJar();
            }
        });

        metadataBuilder.getMetadata().forEach((key, value) -> {
            final Path target = output.toPath().resolve(String.format(namingPattern, key));
            try {
                if (!Files.exists(target.getParent())) {
                    Files.createDirectories(target.getParent());
                }
                try (final OutputStream out = Files.newOutputStream(target)) {
                    value.store(out, "index");
                }
                getLog().info("Generated '" + target + "'");
            } catch (final IOException ioe) {
                throw new IllegalStateException(ioe);
            }
        });
    }
}
