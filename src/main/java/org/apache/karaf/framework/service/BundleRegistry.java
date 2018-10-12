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
package org.apache.karaf.framework.service;

import static org.apache.xbean.finder.util.Files.toFile;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.Manifest;

import org.apache.karaf.framework.ContextualFramework;
import org.apache.karaf.framework.deployer.OSGiBundleLifecycle;

public class BundleRegistry {
    private final Map<Long, OSGiBundleLifecycle> bundles = new HashMap<>();
    private final File framework;

    public BundleRegistry(final OSGiServices services, final ContextualFramework.Configuration configuration) {
        this.framework = toFile(Thread.currentThread().getContextClassLoader().getResource(getClass().getName().replace('.', '/') + ".class"))
            .getAbsoluteFile();

        // ensure we have the framework bundle simulated
        final Manifest frameworkManifest = new Manifest();
        frameworkManifest.getMainAttributes().putValue("Manifest-Version", "1.0");
        frameworkManifest.getMainAttributes().putValue("Bundle-Version", "1.0");
        frameworkManifest.getMainAttributes().putValue("Bundle-SymbolicName", "Contextual Framework");
        bundles.put(0L, new OSGiBundleLifecycle(frameworkManifest, framework, services, this, configuration));
    }

    public File getFramework() {
        return framework;
    }

    public Map<Long, OSGiBundleLifecycle> getBundles() {
        return bundles;
    }
}
