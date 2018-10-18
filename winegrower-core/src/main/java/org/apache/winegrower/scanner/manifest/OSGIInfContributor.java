package org.apache.winegrower.scanner.manifest;

import static java.util.Collections.list;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.function.Supplier;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;

import org.apache.xbean.finder.AnnotationFinder;
import org.apache.xbean.finder.archive.Archive;
import org.apache.xbean.finder.archive.FileArchive;
import org.apache.xbean.finder.archive.JarArchive;
import org.apache.xbean.finder.util.Files;

public class OSGIInfContributor implements ManifestContributor {

    @Override
    public void contribute(final AnnotationFinder finder, final Supplier<Manifest> manifest) {
        final Archive archive = finder.getArchive();
        if (JarArchive.class.isInstance(archive)) {
            try (final JarFile jar = new JarFile(Files.toFile(JarArchive.class.cast(archive).getUrl()))) {
                final Collection<JarEntry> entries = list(jar.entries());
                addBlueprintEntries(manifest, filterEntries(entries, "OSGI-INF/blueprint/"));
                addServiceComponentEntries(manifest, filterEntries(entries, "OSGI-INF/"));
            } catch (final IOException e) {
                // no-op
            }
        } else if (FileArchive.class.isInstance(archive)) {
            final File base = FileArchive.class.cast(archive).getDir();
            {
                final File blueprint = new File(base, "OSGI-INF/blueprint/");
                if (blueprint.isDirectory()) {
                    addBlueprintEntries(manifest, filterChildren(blueprint, "OSGI-INF/blueprint/"));
                }
            }
            {
                final File from = new File(base, "OSGI-INF/");
                if (from.isDirectory()) {
                    addServiceComponentEntries(manifest, filterChildren(from, "OSGI-INF/"));
                }
            }
        }
    }

    private String filterChildren(final File from, final String prefix) {
        return Stream.of(ofNullable(from.list()).orElseGet(() -> new String[0]))
                     .filter(it -> it.endsWith(".xml"))
                     .map(it -> prefix + it)
                     .collect(joining(","));
    }

    private String filterEntries(final Collection<JarEntry> entries, String s) {
        return entries.stream()
                      .filter(it -> it.getName().startsWith(s) && it.getName().endsWith(".xml"))
                      .map(ZipEntry::getName)
                      .collect(joining(","));
    }

    private void addBlueprintEntries(final Supplier<Manifest> manifest, final String list) {
        if (list.isEmpty()) {
            return;
        }
        manifest.get().getMainAttributes().putValue("Bundle-Blueprint", list);
    }

    private void addServiceComponentEntries(final Supplier<Manifest> manifest, final String list) {
        if (list.isEmpty()) {
            return;
        }
        manifest.get().getMainAttributes().putValue("Service-Component", list);
    }
}
