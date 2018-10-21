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
import static java.util.Locale.ENGLISH;
import static java.util.stream.Collectors.joining;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.HashMap;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.text.StringSubstitutor;

public class Build implements Runnable {
    private final Configuration configuration;

    public Build(final Configuration configuration) {
        this.configuration = configuration;
    }

    @Override
    public void run() {
        final File distroFolder = new File(configuration.workDir, configuration.artifactId + "-winegrower-distribution");
        if (distroFolder.exists()) {
            delete(distroFolder);
        }

        Stream.of("bin", "conf", "logs", "lib").forEach(i -> new File(distroFolder, i).mkdirs());

        for (final String ext : asList("sh", "bat")) {
            try (final BufferedReader reader = new BufferedReader(new InputStreamReader(
                    Thread.currentThread().getContextClassLoader().getResourceAsStream("bin/winegrower." + ext)))) {
                write(new File(distroFolder, "bin/winegrower." + ext), StringSubstitutor.replace(reader.lines().collect(joining("\n")),
                        new HashMap<String, String>() {{
                            put("main", configuration.main);
                        }}));
            } catch (final IOException e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
        }

        copyProvidedFiles(configuration.basedir, distroFolder);

        Stream.of("conf", "logs").forEach(folder ->
                write(new File(distroFolder, folder + "/you_can_safely_delete.txt"), "Just there to not loose the folder cause it is empty, you can safely delete."));

        configuration.jars.forEach(it -> addLib(distroFolder, it));

        final Path prefix = configuration.skipArchiveRootFolder ? distroFolder.toPath() : distroFolder.getParentFile().toPath();
        for (final String format : configuration.formats) {
            final File output = new File(configuration.workDir, configuration.artifactId + "-winegrower-distribution." + format);

            switch (format.toLowerCase(ENGLISH)) {
                case "tar.gz":
                    try (final TarArchiveOutputStream tarGz =
                                 new TarArchiveOutputStream(new GZIPOutputStream(new FileOutputStream(output)))) {
                        tarGz.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
                        for (final String entry : distroFolder.list()) {
                            tarGz(tarGz, new File(distroFolder, entry), prefix);
                        }
                    } catch (final IOException e) {
                        throw new IllegalStateException(e.getMessage(), e);
                    }
                    break;
                case "zip":
                    try (final ZipArchiveOutputStream zos =
                                 new ZipArchiveOutputStream(new FileOutputStream(output))) {
                        for (final String entry : distroFolder.list()) {
                            zip(zos, new File(distroFolder, entry), prefix);
                        }
                    } catch (final IOException e) {
                        throw new IllegalStateException(e.getMessage(), e);
                    }
                    break;
                default:
                    throw new IllegalArgumentException(format + " is not supported");
            }
        }

        if (!configuration.keepExplodedFolder) {
            delete(distroFolder);
        }
    }

    private void copyProvidedFiles(final File basedir, final File base) {
        final File srcConf = new File(basedir, configuration.conf);
        if (srcConf.exists() && srcConf.isDirectory()) {
            final File targetConf = new File(base, "conf");
            targetConf.mkdirs();

            for (final File file : srcConf.listFiles()) {
                final String fileName = file.getName();
                if (fileName.startsWith(".")) {
                    // hidden file -> ignore
                    continue;
                }

                try {
                    Files.copy(file.toPath(), new File(targetConf, fileName).toPath());
                } catch (final IOException e) {
                    throw new IllegalStateException("Could not copy file " + file.getAbsolutePath(), e);
                }
            }
        }

        final File srcBin = new File(basedir, configuration.bin);
        if (srcBin.exists() && srcBin.isDirectory()) {
            final File targetRoot = new File(base, "bin");
            targetRoot.mkdirs();
            Stream.of(srcBin.listFiles())
                    .filter(f -> !f.isDirectory()) // not nested for now
                    .forEach(f -> {
                        try {
                            final File target = new File(targetRoot, f.getName());
                            Files.copy(f.toPath(), target.toPath());
                            if (target.getName().endsWith(".sh")) {
                                target.setExecutable(true);
                            }
                        } catch (final IOException e) {
                            throw new IllegalArgumentException("Could not copy file " + f.getAbsolutePath(), e);
                        }
                    });
        }
    }

    private void write(final File file, final String content) {
        try {
            Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8), StandardOpenOption.WRITE, StandardOpenOption.CREATE);
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private void delete(final File file) { // not critical
        final Path rootPath = file.toPath();
        try {
            Files.walkFileTree(rootPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }

            });
        } catch (final IOException e) {
            // no-op
        }
    }

    private void addLib(final File base, final File cc) {
        try {
            Files.copy(cc.toPath(), new File(base, "lib/" + cc.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (final IOException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private void zip(final ZipArchiveOutputStream zip, final File f, final Path prefix) throws IOException {
        final String path = prefix.relativize(f.toPath()).toString().replace(File.separator, "/");
        final ZipArchiveEntry archiveEntry = new ZipArchiveEntry(f, path);
        if (isSh(path)) {
            archiveEntry.setUnixMode(0755);
        }
        zip.putArchiveEntry(archiveEntry);
        if (f.isDirectory()) {
            zip.closeArchiveEntry();
            final File[] files = f.listFiles();
            if (files != null) {
                for (final File child : files) {
                    zip(zip, child, prefix);
                }
            }
        } else {
            Files.copy(f.toPath(), zip);
            zip.closeArchiveEntry();
        }
    }

    private void tarGz(final TarArchiveOutputStream tarGz, final File f, final Path prefix) throws IOException {
        final String path = prefix.relativize(f.toPath()).toString().replace(File.separator, "/");
        final TarArchiveEntry archiveEntry = new TarArchiveEntry(f, path);
        if (isSh(path)) {
            archiveEntry.setMode(0755);
        }
        tarGz.putArchiveEntry(archiveEntry);
        if (f.isDirectory()) {
            tarGz.closeArchiveEntry();
            final File[] files = f.listFiles();
            if (files != null) {
                for (final File child : files) {
                    tarGz(tarGz, child, prefix);
                }
            }
        } else {
            Files.copy(f.toPath(), tarGz);
            tarGz.closeArchiveEntry();
        }
    }

    private boolean isSh(final String path) {
        return path.endsWith(".sh");
    }

    public static class Configuration {
        private final File workDir;
        private final File basedir;
        private final String artifactId;
        private final Collection<File> jars;
        private final Collection<String> formats;
        private final String main;
        private final String bin;
        private final String conf;
        private final boolean skipArchiveRootFolder;
        private final boolean keepExplodedFolder;

        public Configuration(final File workDir, final File basedir,
                             final String artifactId, final Collection<File> jars,
                             final Collection<String> formats,
                             final String main, final String bin, final String conf,
                             final boolean skipArchiveRootFolder, final boolean keepExplodedFolder) {
            this.workDir = workDir;
            this.basedir = basedir;
            this.artifactId = artifactId;
            this.jars = jars;
            this.formats = formats;
            this.main = main;
            this.bin = bin;
            this.conf = conf;
            this.skipArchiveRootFolder = skipArchiveRootFolder;
            this.keepExplodedFolder = keepExplodedFolder;
        }
    }
}
