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

import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;

import org.osgi.framework.ServiceReference;
import org.osgi.framework.hooks.bundle.FindHook;
import org.osgi.framework.hooks.service.EventListenerHook;

public class Hooks {
    private final Collection<ServiceReference<EventListenerHook>> eventListenerHooks = new CopyOnWriteArrayList<>();
    private final Collection<ServiceReference<FindHook>> bundleFindHooks = new CopyOnWriteArrayList<>();
    private final Collection<ServiceReference<org.osgi.framework.hooks.service.FindHook>> serviceFindHooks = new CopyOnWriteArrayList<>();

    public Collection<ServiceReference<EventListenerHook>> getEventListenerHooks() {
        return eventListenerHooks;
    }

    public Collection<ServiceReference<FindHook>> getBundleFindHooks() {
        return bundleFindHooks;
    }

    public Collection<ServiceReference<org.osgi.framework.hooks.service.FindHook>> getServiceFindHooks() {
        return serviceFindHooks;
    }
}
