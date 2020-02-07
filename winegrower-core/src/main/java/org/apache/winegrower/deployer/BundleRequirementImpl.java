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

import java.util.Map;

import org.osgi.framework.Filter;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleRequirement;
import org.osgi.framework.wiring.BundleRevision;

public class BundleRequirementImpl implements BundleRequirement {
    private final BundleRevision revision;
    private final String path;
    private final Map<String, String> directives;
    private final Map<String, Object> attributes;
    private final Filter filter;

    public BundleRequirementImpl(final BundleRevision revision, final String path,
                                 final Map<String, String> directives, final Map<String, Object> attributes,
                                 final Filter filter) {
        this.revision = revision;
        this.path = path;
        this.directives = directives;
        this.attributes = attributes;
        this.filter = filter;
    }

    @Override
    public boolean matches(final BundleCapability capability) {
        return filter == null || filter.matches(capability.getAttributes());
    }

    @Override
    public String getNamespace() {
        return path;
    }

    @Override
    public Map<String, String> getDirectives() {
        return directives;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public BundleRevision getRevision() {
        return revision;
    }

    @Override
    public BundleRevision getResource() {
        return revision;
    }

    Filter getFilter() {
        return filter;
    }
}
