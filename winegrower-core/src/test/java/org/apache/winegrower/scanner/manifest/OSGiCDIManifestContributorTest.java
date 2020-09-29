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
package org.apache.winegrower.scanner.manifest;

import org.apache.winegrower.scanner.manifest.cdi.AriesPluralRequirement;
import org.apache.winegrower.scanner.manifest.cdi.AriesSingularRequirement;
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

class OSGiCDIManifestContributorTest {
    @Test
    void scanSingularAriesRequireExtension(@TempDir final Path temp) throws IOException {
        prepare(AriesSingularRequirement.class, temp);
        assertEquals("osgi.extender;filter:=\"(osgi.extender=osgi.cdi)\";" +
                "beans:List<String>=\"org.apache.winegrower.scanner.manifest.cdi.AriesSingularRequirement\"," +
                "osgi.cdi.extension;filter:=\"(osgi.cdi.extension=the-extension)\"", execute(temp));
    }

    @Test
    void scanPluralAriesRequireExtension(@TempDir final Path temp) throws IOException {
        prepare(AriesPluralRequirement.class, temp);
        assertEquals("osgi.extender;filter:=\"(osgi.extender=osgi.cdi)\";" +
                "beans:List<String>=\"org.apache.winegrower.scanner.manifest.cdi.AriesPluralRequirement\"," +
                "osgi.cdi.extension;filter:=\"(osgi.cdi.extension=the-extension-1)\"," +
                "osgi.cdi.extension;filter:=\"(osgi.cdi.extension=the-extension-2)\"", execute(temp));
    }

    @Test
    void scanDefaultRequirement(@TempDir final Path temp) throws IOException {
        prepare(StandardRequirement.class, temp);
        // here we don't scan an aries-cdi extension - but std requirement work, see requirement manifest contributor test
        assertEquals("osgi.extender;filter:=\"(osgi.extender=osgi.cdi)\";beans:List<String>=\"org.apache.winegrower.scanner.manifest.cdi.StandardRequirement\"", execute(temp));
    }

    private String execute(final Path temp) {
        final Manifest manifest = new Manifest();
        final ManifestContributor.WinegrowerAnnotationFinder finder = new ManifestContributor.WinegrowerAnnotationFinder(
                new FileArchive(Thread.currentThread().getContextClassLoader(), temp.toFile()), false);
        new OSGiCDIManifestContributor().contribute(finder, () -> manifest);
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
