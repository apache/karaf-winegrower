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
package org.apache.winegrower.deployer;

import static java.util.Collections.enumeration;
import static java.util.Collections.list;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collector;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;

import org.apache.winegrower.Ripener;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleListener;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.Version;
import org.osgi.framework.wiring.BundleWiring;

public class BundleImpl implements Bundle {
    private final File file;
    private final ClassLoader loader;
    private final long id;
    private final BundleContextImpl context;
    private final Version version;
    private final String symbolicName;
    private final Dictionary<String, String> headers;
    private final File dataFileBase;
    private final Collection<String> includedResources;
    private int state = Bundle.UNINSTALLED;

    BundleImpl(final Manifest manifest, final File file, final BundleContextImpl context,
               final Ripener.Configuration configuration, final long id,
               final Collection<String> includedResources) {
        this.file = file;
        this.dataFileBase = new File(configuration.getWorkDir(),
                file == null ? Long.toString(System.identityHashCode(manifest)) : file.getName());
        this.context = context;
        this.id = id;
        this.loader = Thread.currentThread().getContextClassLoader();
        this.includedResources = includedResources;
        this.version = ofNullable(manifest.getMainAttributes().getValue(Constants.BUNDLE_VERSION))
            .map(Version::new)
            .orElse(Version.emptyVersion);
        this.symbolicName = manifest.getMainAttributes().getValue(Constants.BUNDLE_SYMBOLICNAME);
        this.headers = manifest.getMainAttributes().entrySet().stream()
            .collect(Collector.of(
                    Hashtable::new,
                    (t, e) -> t.put(Attributes.Name.class.cast(e.getKey()).toString(), e.getValue().toString()),
                    (t1, t2) -> {
                        t1.putAll(t2);
                        return t1;
                    }));
    }

    ClassLoader getLoader() {
        return loader;
    }

    private Stream<BundleListener> allBundleListeners() {
        return context.getRegistry().getBundles().values().stream()
                      .flatMap(it -> BundleContextImpl.class.cast(it.getBundle().getBundleContext()).getBundleListeners().stream());
    }

    void onStart() {
        start();
        final BundleEvent event = new BundleEvent(BundleEvent.STARTED, this);
        allBundleListeners()
               .forEach(listener -> listener.bundleChanged(event));
    }

    void onStop() {
        stop();
        final BundleEvent event = new BundleEvent(BundleEvent.STOPPED, this);
        allBundleListeners().forEach(listener -> listener.bundleChanged(event));
    }

    @Override
    public int getState() {
        return state;
    }

    @Override
    public void start(final int options) {
        state = options;
    }

    @Override
    public void start() {
        start(Bundle.ACTIVE);
    }

    @Override
    public void stop(final int options) {
        state = options;
    }

    @Override
    public void stop() {
        stop(Bundle.UNINSTALLED);
    }

    @Override
    public void update(final InputStream input) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void update() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void uninstall() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Dictionary<String, String> getHeaders() {
        return headers;
    }

    @Override
    public long getBundleId() {
        return id;
    }

    @Override
    public String getLocation() {
        return includedResources != null ? null : file.getAbsolutePath();
    }

    @Override
    public ServiceReference<?>[] getRegisteredServices() {
        return context.getServices().getServices().stream()
                .filter(it -> it.getReference().getBundle() == this)
                .map(ServiceRegistration::getReference)
                .toArray(ServiceReference[]::new);
    }

    @Override
    public ServiceReference<?>[] getServicesInUse() {
        return new ServiceReference[0];
    }

    @Override
    public boolean hasPermission(final Object permission) {
        return true;
    }

    @Override
    public URL getResource(final String name) {
        return loader.getResource(name);
    }

    @Override
    public Dictionary<String, String> getHeaders(final String locale) {
        return headers; // ignore the locale for now
    }

    @Override
    public String getSymbolicName() {
        return symbolicName;
    }

    @Override
    public Class<?> loadClass(final String name) throws ClassNotFoundException {
        return loader.loadClass(name);
    }

    @Override
    public Enumeration<URL> getResources(final String name) throws IOException {
        return loader.getResources(name);
    }

    @Override
    public Enumeration<String> getEntryPaths(final String path) {
        if (includedResources != null) {
            return enumeration(includedResources.stream()
                    .filter(it -> it.startsWith(path))
                    .collect(toList()));
        }
        if (file.isDirectory()) {
            final Path base = file.toPath().toAbsolutePath();
            final Path subPath = new File(file, path == null ? "" : (path.startsWith("/") ? path.substring(1) : path)).toPath();
            final Collection<String> paths = new ArrayList<>();
            try {
                Files.walkFileTree(subPath, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                        if (file.toAbsolutePath().toString().startsWith(base.toString())) {
                            paths.add(base.relativize(file).toString());
                        }
                        return super.visitFile(file, attrs);
                    }
                });
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
            return enumeration(paths);
        }
        try (final JarFile jar = new JarFile(file)) {
            return enumeration(list(jar.entries()).stream()
                    .filter(it -> it.getName().startsWith(path))
                    .map(ZipEntry::getName)
                    .collect(toList()));
        } catch (final IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public URL getEntry(final String path) {
        return loader.getResource(path);
    }

    @Override
    public long getLastModified() {
        return file == null ? -1 : file.lastModified();
    }

    @Override
    public Enumeration<URL> findEntries(final String path, final String filePattern, final boolean recurse) {
        final Filter filter = filePattern == null ?
                null : context.createFilter("(filename=" + filePattern + ")");
        final String prefix = path == null ? "" : (path.startsWith("/") ? path.substring(1) : path);

        if (includedResources != null) {
            if (!recurse) {
                return enumeration(includedResources.stream()
                    .filter(it -> doFilterEntry(filter, prefix, it))
                    .map(loader::getResource)
                    .collect(toList()));
            }
        }

        final File baseFile = new File(file, prefix);
        final Path base = baseFile.toPath();
        final Path filePath = this.file.toPath();
        if (file.isDirectory()) {
            if (!recurse) {
                return enumeration(ofNullable(baseFile.listFiles())
                        .map(Stream::of)
                        .orElseGet(Stream::empty)
                        .filter(file -> doFilterEntry(filter, prefix, filePath.relativize(file.toPath()).toString()))
                        .map(f -> {
                            try {
                                return f.getAbsoluteFile().toURI().toURL();
                            } catch (final MalformedURLException e) {
                                throw new IllegalStateException(e);
                            }
                        })
                        .collect(toList()));
            } else {
                final Collection<URL> files = new ArrayList<>();
                try {
                    Files.walkFileTree(base, new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                            if (doFilterEntry(filter, prefix, filePath.relativize(file).toString())) {
                                files.add(file.toAbsolutePath().toUri().toURL());
                            }
                            return super.visitFile(file, attrs);
                        }
                    });
                } catch (final IOException e) {
                    throw new IllegalStateException(e);
                }
                return enumeration(files);
            }
        } else {
            try (final JarFile jar = new JarFile(file)) {
                return enumeration(list(jar.entries()).stream().filter(it -> it.getName().startsWith(prefix))
                                                      .map(ZipEntry::getName).filter(name -> !name.endsWith("/")) // folders
                                                      .filter(name -> doFilterEntry(filter, prefix, name)).map(name -> {
                            try {
                                return new URL("jar", null, file.toURI().toURL().toExternalForm() + "!/" + name);
                            } catch (final MalformedURLException e) {
                                throw new IllegalArgumentException(e);
                            }
                        }).collect(toList()));
            } catch (final IOException e) {
                throw new IllegalArgumentException(e);
            }
        }
    }

    private boolean doFilterEntry(final Filter filter, final String prefix, final String name) {
        final String path = name.replace(File.separatorChar, '/');
        if (prefix != null && !path.startsWith(prefix)) {
            return false;
        }
        if (filter == null) {
            return true;
        }
        final Hashtable<String, Object> props = new Hashtable<>();
        props.put("filename", path.substring(path.lastIndexOf('/') + 1));
        return filter.matches(props);
    }

    @Override
    public BundleContext getBundleContext() {
        return context;
    }

    @Override
    public Map<X509Certificate, List<X509Certificate>> getSignerCertificates(final int signersType) {
        return null;
    }

    @Override
    public Version getVersion() {
        return version;
    }

    @Override
    public <A> A adapt(final Class<A> type) {
        if (BundleWiring.class == type) {
            return type.cast(new BundleWiringImpl(this));
        }
        return null;
    }

    @Override
    public File getDataFile(final String filename) {
        final File file = new File(dataFileBase, filename);
        file.getParentFile().mkdirs();
        return file;
    }

    @Override
    public int compareTo(final Bundle o) {
        return (int) (id - o.getBundleId());
    }

    @Override
    public String toString() {
        return "BundleImpl{file=" + file + ", id=" + id + '}';
    }
}
