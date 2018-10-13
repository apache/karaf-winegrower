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
package org.apache.winegrower.test.simpleconsumer;

import org.apache.winegrower.test.simpleservice.MyService;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;

public class MyActivator implements BundleActivator {

    private ServiceTracker<MyService, MyService> tracker;
    public volatile boolean registered = false;

    @Override
    public void start(BundleContext context) {
        tracker = new ServiceTracker<>(context, MyService.class.getName(), new ServiceTrackerCustomizer<MyService, MyService>() {
            @Override
            public MyService addingService(ServiceReference serviceReference) {
                registered = true;
                return null;
            }

            @Override
            public void modifiedService(ServiceReference serviceReference, MyService o) {
                // nothing to do
            }

            @Override
            public void removedService(ServiceReference serviceReference, MyService o) {
                registered = false;
            }
        });
        tracker.open();
    }

    @Override
    public void stop(final BundleContext context) {
        tracker.close();
    }
}
