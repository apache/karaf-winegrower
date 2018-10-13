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

import static java.util.Collections.emptyList;
import static java.util.Collections.list;
import static java.util.stream.Collectors.toList;

import java.net.URL;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;

import org.osgi.framework.Bundle;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleRequirement;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleWire;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;
import org.osgi.resource.Wire;

public class BundleWiringImpl implements BundleWiring {
    private final BundleImpl bundle;
    private final BundleRevision revision;

    BundleWiringImpl(final BundleImpl bundle) {
        this.bundle = bundle;
        this.revision = new BundleRevisionImpl(bundle, this);
    }

    @Override
    public boolean isCurrent() {
        return true;
    }

    @Override
    public boolean isInUse() {
        return true;
    }

    @Override
    public List<BundleCapability> getCapabilities(final String namespace) {
        return emptyList();
    }

    @Override
    public List<BundleRequirement> getRequirements(final String namespace) {
        return emptyList();
    }

    @Override
    public List<BundleWire> getProvidedWires(final String namespace) {
        return emptyList();
    }

    @Override
    public List<BundleWire> getRequiredWires(final String namespace) {
        return emptyList();
    }

    @Override
    public BundleRevision getRevision() {
        return revision;
    }

    @Override
    public ClassLoader getClassLoader() {
        return bundle.getLoader();
    }

    @Override
    public List<URL> findEntries(final String path, final String filePattern, final int options) {
        return list(bundle.findEntries(path, filePattern, (BundleWiring.LISTRESOURCES_RECURSE & options) == 1));
    }

    @Override
    public Collection<String> listResources(final String path, final String filePattern, final int options) {
        return list(bundle.findEntries(path, filePattern, (BundleWiring.LISTRESOURCES_RECURSE & options) == 1))
                .stream()
                .map(it -> {
                    switch (it.getProtocol()) {
                        case "file":
                            return Paths.get(bundle.getLocation()).relativize(Paths.get(it.getFile())).toString();
                        case "jar":
                        default:
                            final String externalForm = it.toExternalForm();
                            return externalForm.substring(externalForm.lastIndexOf("!/") + "!/".length());
                    }
                })
                .collect(toList());
    }

    @Override
    public List<Capability> getResourceCapabilities(final String namespace) {
        return emptyList();
    }

    @Override
    public List<Requirement> getResourceRequirements(final String namespace) {
        return emptyList();
    }

    @Override
    public List<Wire> getProvidedResourceWires(final String namespace) {
        return emptyList();
    }

    @Override
    public List<Wire> getRequiredResourceWires(final String namespace) {
        return emptyList();
    }

    @Override
    public BundleRevision getResource() {
        return revision;
    }

    @Override
    public Bundle getBundle() {
        return bundle;
    }
}
