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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.winegrower.Ripener;
import org.apache.winegrower.deployer.BundleImpl;
import org.apache.winegrower.deployer.OSGiBundleLifecycle;
import org.apache.winegrower.test.WithRipener;
import org.apache.winegrower.test.WithRipener.Entry;
import org.apache.winegrower.test.WithRipener.Service;
import org.apache.winegrower.test.hook.SimpleService;
import org.junit.jupiter.api.Test;
import org.osgi.framework.BundleContext;
import org.osgi.util.tracker.ServiceTracker;

class HookTest {
    @Test
    @WithRipener(includeResources = @Entry(path = "org.apache.winegrower.test.hook"))
    void replaceServiceInstance(@Service final Ripener ripener) throws InterruptedException {
        final BundleContext bundleContext = ripener.getRegistry().getBundles().values().stream()
                .filter(it -> it.getBundle().getBundleId() > 0)
                .findFirst()
                .map(OSGiBundleLifecycle::getBundle)
                .map(BundleImpl::getBundleContext)
                .orElseThrow(IllegalStateException::new);
        final ServiceTracker<SimpleService, SimpleService> tracker = new ServiceTracker<>(
                bundleContext, SimpleService.class, null);
        tracker.open();
        tracker.waitForService(5000L);
        assertEquals("I am the replacement", tracker.getService().get());
    }
}
