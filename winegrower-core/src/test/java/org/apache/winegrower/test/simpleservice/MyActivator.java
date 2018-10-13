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
package org.apache.winegrower.test.simpleservice;

import java.util.Hashtable;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

public class MyActivator implements BundleActivator {

    private ServiceRegistration registration;

    @Override
    public void start(BundleContext context) {
        MyService service = new MyServiceImpl();
        Hashtable<String, String> properties = new Hashtable<>();
        properties.put("foo", "bar");
        registration = context.registerService(MyService.class, service, properties);
    }

    @Override
    public void stop(final BundleContext context) {
        if (registration != null) {
            registration.unregister();
        }
    }

}
