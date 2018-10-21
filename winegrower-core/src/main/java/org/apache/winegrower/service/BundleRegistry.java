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
package org.apache.winegrower.service;

import static org.apache.xbean.finder.util.Files.toFile;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.Manifest;

import org.apache.winegrower.Ripener;
import org.apache.winegrower.deployer.OSGiBundleLifecycle;

public class BundleRegistry {
    private final Map<Long, OSGiBundleLifecycle> bundles = new HashMap<>();
    private final File framework;

    public BundleRegistry(final OSGiServices services, final Ripener.Configuration configuration) {
        final String resource = getClass().getName().replace('.', '/') + ".class";
        final File file = toFile(Thread.currentThread().getContextClassLoader().getResource(resource));
        this.framework = file.getName().endsWith(".class") ?
                new File(file.getAbsolutePath().replace(File.separatorChar, '/').substring(0, file.getAbsolutePath().length() - resource.length())):
                file.getAbsoluteFile();

        // ensure we have the framework bundle simulated
        final Manifest frameworkManifest = new Manifest();
        frameworkManifest.getMainAttributes().putValue("Manifest-Version", "1.0");
        frameworkManifest.getMainAttributes().putValue("Bundle-Version", "1.0");
        frameworkManifest.getMainAttributes().putValue("Bundle-SymbolicName", "Ripener");
        final OSGiBundleLifecycle frameworkBundle = new OSGiBundleLifecycle(
                frameworkManifest, framework, services, this, configuration, 0L, null);
        frameworkBundle.start();
        bundles.put(0L, frameworkBundle);
    }

    public File getFramework() {
        return framework;
    }

    public Map<Long, OSGiBundleLifecycle> getBundles() {
        return bundles;
    }
}
