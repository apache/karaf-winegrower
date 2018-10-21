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

import static java.util.Arrays.asList;
import static java.util.Collections.list;
import static java.util.stream.Collectors.toList;
import static org.apache.xbean.finder.util.Files.toFile;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;

class FatJarTest {
    @Test
    void fatJar() throws IOException {
        final String junitMarker = Test.class.getName().replace('.', '/') + ".class";
        final File junitApi = toFile(Thread.currentThread().getContextClassLoader().getResource(junitMarker));
        final String osgiCoreMarker = Bundle.class.getName().replace('.', '/') + ".class";
        final File osgiCore = toFile(Thread.currentThread().getContextClassLoader().getResource(osgiCoreMarker));

        final File output = new File("target/farjartest/fatJar.jar");
        if (output.exists()) {
            output.delete();
        }
        new FatJar(new FatJar.Configuration(
                asList(junitApi, osgiCore),
                output
        )).run();
        assertTrue(output.exists());
        final List<String> entries;
        try (final JarFile files = new JarFile(output)) {
            entries = list(files.entries()).stream().map(JarEntry::getName).collect(toList());
        }
        assertTrue(entries.size() > 500); // 503 when writing this test
        // ensure junit and osgi-core are here by testing a few known classes
        assertTrue(entries.contains("org/junit/jupiter/api/AfterAll.class"));
        assertTrue(entries.contains("org/osgi/framework/FrameworkUtil.class"));
        // ensure fatjar meta are here
        assertTrue(entries.contains("WINEGROWER-INF/index.properties"));
        assertTrue(entries.contains("WINEGROWER-INF/manifests.properties"));
    }
}
