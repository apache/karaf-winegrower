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
package org.apache.winegrower.extension.build.common;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.jar.Manifest;

public class MetadataBuilder {
    private final boolean skipIfNoActivator;

    private final Properties manifests = new Properties();
    private final Properties index = new Properties();

    private String currentJar;
    private List<String> files;

    public MetadataBuilder(final boolean skipIfNoActivator) {
        this.skipIfNoActivator = skipIfNoActivator;
    }

    public Map<String, Properties> getMetadata() {
        final HashMap<String, Properties> meta = new HashMap<>();
        meta.put("index", index);
        meta.put("manifests", manifests);
        return meta;
    }

    public void onJar(final String jarName, final Manifest manifest) {
        if (skipIfNoActivator && (manifest == null || manifest.getMainAttributes().getValue("Bundle-Activator") == null)) {
            return;
        }
        if (manifest != null) {
            try (final ByteArrayOutputStream manifestStream = new ByteArrayOutputStream()) {
                manifest.write(manifestStream);
                manifestStream.flush();
                manifests.put(jarName, new String(manifestStream.toByteArray(), StandardCharsets.UTF_8));
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
        }
        this.currentJar = jarName;
        this.files = new ArrayList<>();
    }

    public void onFile(final String name) {
        if (files != null) {
            files.add(name);
        }
    }

    public void afterJar() {
        if (files == null) {
            return;
        }
        index.put(currentJar, String.join(",", files));
        currentJar = null;
        files = null;
    }

    public void visitFolder(final String projectArtifactName, final Path root, final FileVisitor<Path> visitor) {
        final Path manifest = root.resolve("META-INF/MANIFEST.MF");
        if (Files.exists(manifest)) {
            try (final InputStream stream = Files.newInputStream(manifest)) {
                onJar(projectArtifactName, new Manifest(stream));
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
        } else {
            onJar(projectArtifactName, null);
        }
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                    if ("META-INF/MANIFEST.MF".equals(root.relativize(file).toString())) {
                        return FileVisitResult.CONTINUE;
                    }
                    onVisit(file);
                    return visitor.visitFile(file, attrs);
                }

                @Override
                public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) throws IOException {
                    onVisit(dir);
                    return visitor.postVisitDirectory(dir, exc);
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    return visitor.preVisitDirectory(dir, attrs);
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    return visitor.visitFileFailed(file, exc);
                }

                private void onVisit(final Path path) {
                    onFile(root.relativize(path).toString());
                }
            });
            afterJar();
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
