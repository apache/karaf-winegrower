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
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.apache.xbean.finder.util.Files.toFile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.winegrower.Ripener;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;

class BuildTest {
    @Test
    void build() throws IOException {
        final String junitMarker = Test.class.getName().replace('.', '/') + ".class";
        final File junitApi = toFile(Thread.currentThread().getContextClassLoader().getResource(junitMarker));
        final String osgiCoreMarker = Bundle.class.getName().replace('.', '/') + ".class";
        final File osgiCore = toFile(Thread.currentThread().getContextClassLoader().getResource(osgiCoreMarker));

        final File output = new File("target/buildtest/build.jar");
        if (output.exists()) {
            output.delete();
        }
        final File workDir = new File(output.getParentFile(), "work");
        new Build(new Build.Configuration(
                workDir,
                new File("src/test/resources/build"),
                "test-art",
                asList(junitApi, osgiCore),
                singletonList("zip"),
                Ripener.class.getName(),
                "bin", "conf", false, false
        )).run();
        final File distro = new File(workDir, "test-art-winegrower-distribution.zip");
        assertTrue(distro.exists());
        final List<String> entries;
        try (final JarFile files = new JarFile(distro)) {
            entries = list(files.entries()).stream().map(JarEntry::getName).collect(toList());
        }
        assertEquals(11, entries.size());
        assertTrue(entries.contains("test-art-winegrower-distribution/bin/winegrower.sh"));
        assertTrue(entries.contains("test-art-winegrower-distribution/bin/setenv.sh"));
        assertTrue(entries.contains("test-art-winegrower-distribution/lib/org.osgi.core-6.0.0.jar"));
        assertTrue(entries.contains("test-art-winegrower-distribution/lib/junit-jupiter-api-5.3.1.jar"));
    }
}
