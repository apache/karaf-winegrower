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
package org.apache.karaf.framework.deployer;

import static java.util.Optional.ofNullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.jar.Manifest;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;

public class BundleImpl implements Bundle {
    private static final AtomicLong ID_GENERATOR = new AtomicLong(1);

    private final File file;
    private final ClassLoader loader;
    private final long id;
    private final BundleContext context;
    private final Version version;
    private final String symbolicName;

    BundleImpl(final Manifest manifest, final File file, final BundleContext context) {
        this.file = file;
        this.context = context;
        this.id = ID_GENERATOR.getAndIncrement();
        this.loader = Thread.currentThread().getContextClassLoader();
        this.version = ofNullable(manifest.getMainAttributes().getValue(Constants.BUNDLE_VERSION))
            .map(Version::new)
            .orElse(Version.emptyVersion);
        this.symbolicName = manifest.getMainAttributes().getValue(Constants.BUNDLE_SYMBOLICNAME);
    }

    @Override
    public int getState() {
        return ACTIVE;
    }

    @Override
    public void start(final int options) throws BundleException {
        // no-op
    }

    @Override
    public void start() throws BundleException {
        start(Bundle.ACTIVE);
    }

    @Override
    public void stop(final int options) throws BundleException {
        // no-op
    }

    @Override
    public void stop() throws BundleException {
        stop(Bundle.UNINSTALLED);
    }

    @Override
    public void update(final InputStream input) throws BundleException {
        // no-op
    }

    @Override
    public void update() throws BundleException {
        // no-op
    }

    @Override
    public void uninstall() throws BundleException {
        // no-op
    }

    @Override
    public Dictionary<String, String> getHeaders() {
        return null;
    }

    @Override
    public long getBundleId() {
        return id;
    }

    @Override
    public String getLocation() {
        return file.getAbsolutePath();
    }

    @Override
    public ServiceReference<?>[] getRegisteredServices() {
        return new ServiceReference[0];
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
        return null;
    }

    @Override
    public String getSymbolicName() {
        return symbolicName;
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        return loader.loadClass(name);
    }

    @Override
    public Enumeration<URL> getResources(final String name) throws IOException {
        return loader.getResources(name);
    }

    @Override
    public Enumeration<String> getEntryPaths(final String path) {
        return null;
    }

    @Override
    public URL getEntry(final String path) {
        return null;
    }

    @Override
    public long getLastModified() {
        return file.lastModified();
    }

    @Override
    public Enumeration<URL> findEntries(final String path, final String filePattern, final boolean recurse) {
        return null;
    }

    @Override
    public BundleContext getBundleContext() {
        return context;
    }

    @Override
    public Map<X509Certificate, List<X509Certificate>> getSignerCertificates(int signersType) {
        return null;
    }

    @Override
    public Version getVersion() {
        return version;
    }

    @Override
    public <A> A adapt(final Class<A> type) {
        return null;
    }

    @Override
    public File getDataFile(final String filename) {
        return null;
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
