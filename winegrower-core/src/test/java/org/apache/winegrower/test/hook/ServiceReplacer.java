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
package org.apache.winegrower.test.hook;

import java.util.Collection;
import java.util.Hashtable;
import java.util.Map;

import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.hooks.service.EventListenerHook;
import org.osgi.framework.hooks.service.FindHook;
import org.osgi.framework.hooks.service.ListenerHook;

public class ServiceReplacer implements FindHook, EventListenerHook, ServiceListener {
    private final long bundleId;

    public ServiceReplacer(final long bundleId) {
        this.bundleId = bundleId;
    }

    @Override // replaced services are not forward to listeners except the bundle owning the replacer and #0 (optional for the test)
    public void event(final ServiceEvent event, final Map<BundleContext, Collection<ListenerHook.ListenerInfo>> listeners) {
        if (event.getServiceReference().getProperty("replaced") != null) {
            listeners.keySet().removeIf(b -> b.getBundle().getBundleId() != 0);
        }
    }

    @Override // remove replaced services to keep only replacements
    public void find(final BundleContext context, final String name, final String filter,
                     final boolean allServices, final Collection<ServiceReference<?>> references) {
        final long consumingBundleId = context.getBundle().getBundleId();
        if (consumingBundleId != 0) {
            references.removeIf(r -> r.getProperty("replaced") != null);
        }
    }

    @Override // actual replacement
    public void serviceChanged(final ServiceEvent serviceEvent) {
        if (serviceEvent.getServiceReference().getProperty("replaced") != null) {
            final BundleContext context = serviceEvent.getServiceReference().getBundle().getBundleContext();
            final String clazz = String.valueOf(serviceEvent.getServiceReference().getProperty(Constants.OBJECTCLASS));
            context.registerService(clazz, new SimpleService() {
                @Override
                public String get() {
                    return "I am the replacement";
                }
            }, new Hashtable<>());
        }
    }
}
