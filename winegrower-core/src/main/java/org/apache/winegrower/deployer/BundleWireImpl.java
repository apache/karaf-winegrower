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

import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleRequirement;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleWire;
import org.osgi.framework.wiring.BundleWiring;

public class BundleWireImpl implements BundleWire {
    private final BundleRevision requirer;
    private final BundleRevision provider;
    private final BundleWiring requirerWiring;
    private final BundleWiring providerWiring;
    private final BundleRequirement requirement;
    private final BundleCapability capability;

    public BundleWireImpl(final BundleRevision requirer, final BundleRevision provider,
                          final BundleWiring requirerWiring, final BundleWiring providerWiring,
                          final BundleRequirement requirement, final BundleCapability capability) {
        this.requirer = requirer;
        this.provider = provider;
        this.requirerWiring = requirerWiring;
        this.providerWiring = providerWiring;
        this.requirement = requirement;
        this.capability = capability;
    }

    @Override
    public BundleCapability getCapability() {
        return capability;
    }

    @Override
    public BundleRequirement getRequirement() {
        return requirement;
    }

    @Override
    public BundleWiring getProviderWiring() {
        return providerWiring;
    }

    @Override
    public BundleWiring getRequirerWiring() {
        return requirerWiring;
    }

    @Override
    public BundleRevision getProvider() {
        return provider;
    }

    @Override
    public BundleRevision getRequirer() {
        return requirer;
    }
}
