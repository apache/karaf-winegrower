package org.apache.winegrower.scanner.manifest;

import org.apache.winegrower.scanner.manifest.cdi.StandardRequirement;
import org.apache.xbean.finder.archive.FileArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.osgi.framework.Constants.REQUIRE_CAPABILITY;

class RequirementManifestContributorTest {
    @Test
    void scanDefaultRequirement(@TempDir final Path temp) throws IOException {
        prepare(StandardRequirement.class, temp);
        assertEquals("osgi.cdi.extension;filter:=\"(osgi.cdi.extension=the-direct-extension)\"", execute(temp));
    }

    private String execute(final Path temp) {
        final Manifest manifest = new Manifest();
        final ManifestContributor.WinegrowerAnnotationFinder finder = new ManifestContributor.WinegrowerAnnotationFinder(
                new FileArchive(Thread.currentThread().getContextClassLoader(), temp.toFile()), false);
        new RequirementManifestContributor().contribute(finder, () -> manifest);
        return manifest.getMainAttributes().getValue(REQUIRE_CAPABILITY);
    }

    private void prepare(final Class<?> toScan, final Path temp) throws IOException {
        final String path = toScan.getName().replace('.', '/') + ".class";
        final Path to = temp.resolve(path);
        Files.createDirectories(to.getParent());
        final Path base = Paths.get("target/test-classes");
        Files.copy(base.resolve(path), to);
        final Path metaInf = temp.resolve("META-INF");
        Files.createDirectories(metaInf);
        Files.write(metaInf.resolve("beans.xml"), "<beans/>".getBytes(StandardCharsets.UTF_8));
    }
}
