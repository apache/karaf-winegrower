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

import java.util.List;

import org.osgi.framework.Bundle;
import org.osgi.framework.Version;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleRequirement;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;

public class BundleRevisionImpl implements BundleRevision {
    private final BundleImpl bundle;
    private final BundleWiringImpl wiring;

    BundleRevisionImpl(final BundleImpl bundle, final BundleWiringImpl bundleWiring) {
        this.bundle = bundle;
        this.wiring = bundleWiring;
    }

    @Override
    public String getSymbolicName() {
        return bundle.getSymbolicName();
    }

    @Override
    public Version getVersion() {
        return bundle.getVersion();
    }

    @Override
    public List<BundleCapability> getDeclaredCapabilities(final String namespace) {
        return emptyList();
    }

    @Override
    public List<BundleRequirement> getDeclaredRequirements(final String namespace) {
        return emptyList();
    }

    @Override
    public int getTypes() {
        return 0;
    }

    @Override
    public BundleWiring getWiring() {
        return wiring;
    }

    @Override
    public List<Capability> getCapabilities(final String namespace) {
        return emptyList();
    }

    @Override
    public List<Requirement> getRequirements(final String namespace) {
        return emptyList();
    }

    @Override
    public Bundle getBundle() {
        return bundle;
    }
}
