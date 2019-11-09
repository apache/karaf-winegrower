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

import static org.osgi.framework.Constants.BUNDLE_ACTIVATOR;

import java.util.Hashtable;

import org.osgi.annotation.bundle.Header;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.hooks.service.EventListenerHook;
import org.osgi.framework.hooks.service.FindHook;

@Header(name = BUNDLE_ACTIVATOR, value = "${@class}")
public class RegisterHooks implements BundleActivator {
    @Override
    public void start(final BundleContext context) {
        // in practise we would get all services to replace existing one too,
        // here we control what we want to replace (replaced=true) so let's keep it simple for tests
        final ServiceReplacer replacer = new ServiceReplacer(context.getBundle().getBundleId());
        context.addServiceListener(replacer);
        context.registerService(
                new String[] { FindHook.class.getName(), EventListenerHook.class.getName() },
                replacer, new Hashtable<>());

        context.registerService(SimpleService.class, new SimpleService(), new Hashtable<String, Object>() {{
            put("replaced", "true");
        }});
    }

    @Override
    public void stop(final BundleContext context) {
        // no-op
    }
}
