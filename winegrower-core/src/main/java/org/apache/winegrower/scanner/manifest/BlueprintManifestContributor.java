package org.apache.winegrower.scanner.manifest;

import static java.util.Collections.list;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;

import java.io.File;
import java.io.IOException;
import java.util.function.Supplier;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;

import org.apache.xbean.finder.AnnotationFinder;
import org.apache.xbean.finder.archive.Archive;
import org.apache.xbean.finder.archive.FileArchive;
import org.apache.xbean.finder.archive.JarArchive;
import org.apache.xbean.finder.util.Files;

public class BlueprintManifestContributor implements ManifestContributor {
    @Override
    public void contribute(final AnnotationFinder finder, final Supplier<Manifest> manifest) {
        final Archive archive = finder.getArchive();
        if (JarArchive.class.isInstance(archive)) {
            try (final JarFile jar = new JarFile(Files.toFile(JarArchive.class.cast(archive).getUrl()))) {
                addBlueprintEntries(manifest, list(jar.entries()).stream()
                        .filter(it -> it.getName().startsWith("OSGI-INF/blueprint/") && it.getName().endsWith(".xml"))
                        .map(ZipEntry::getName)
                        .collect(joining(",")));
            } catch (final IOException e) {
                // no-op
            }
        } else if (FileArchive.class.isInstance(archive)) {
            final File base = FileArchive.class.cast(archive).getDir();
            final File blueprint = new File(base, "OSGI-INF/blueprint/");
            if (blueprint.isDirectory()) {
                addBlueprintEntries(manifest, Stream.of(ofNullable(blueprint.list()).orElseGet(() -> new String[0]))
                    .filter(it -> it.endsWith(".xml"))
                    .map(it -> "OSGI-INF/blueprint/" + it)
                    .collect(joining(",")));
            }
        }
    }

    private void addBlueprintEntries(final Supplier<Manifest> manifest, final String list) {
        if (list.isEmpty()) {
            return;
        }
        manifest.get().getMainAttributes().putValue("Bundle-Blueprint", list);
    }
}
