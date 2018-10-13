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
package org.apache.winegrower.examples;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

import java.util.Hashtable;

public class Bundle implements BundleActivator {

  private ServiceRegistration<EchoService> registration;

  @Override
  public void start(BundleContext bundleContext) {
    System.out.println("Instantiate the echo service");
    EchoServiceImpl echoService = new EchoServiceImpl();
    System.out.println("Creating echo service properties");
    Hashtable<String, String> properties = new Hashtable<>();
    properties.put("foo", "bar");
    System.out.println("Registering echo service");
    registration = bundleContext.registerService(EchoService.class, echoService, properties);
  }

  @Override
  public void stop(BundleContext bundleContext) {
    if (registration != null) {
      System.out.println("Unregistering echo service");
      registration.unregister();
    }
  }

}
