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
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;

import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Hashtable;

public class Config implements BundleActivator {

  private ServiceRegistration<ManagedService> registration = null;

  @Override
  public void start(BundleContext bundleContext) throws Exception {
    // first old style static approach, ServiceTracker is also possible
    ServiceReference ref = bundleContext.getServiceReference(ConfigurationAdmin.class);
    ConfigurationAdmin configurationAdmin = (ConfigurationAdmin) bundleContext.getService(ref);

    System.out.println("Printing my.example.config configuration properties");
    Configuration myExampleConfig = configurationAdmin.getConfiguration("my.example.config");
    Dictionary<String, Object> myExampleConfigProperties = myExampleConfig.getProperties();
    Enumeration<String> keys = myExampleConfigProperties.keys();
    while (keys.hasMoreElements()) {
      String key = keys.nextElement();
      String value = (String) myExampleConfigProperties.get(key);
      System.out.println("\t" + key + "=" + value);
    }
    System.out.println("");

    bundleContext.ungetService(ref);

    // other approach using a ManagedService
    ConfigPrinter configPrinter = new ConfigPrinter();
    Hashtable<String, String> serviceProperties = new Hashtable<>();
    serviceProperties.put(Constants.SERVICE_PID, "external.config");
    registration = bundleContext.registerService(ManagedService.class, configPrinter, serviceProperties);
  }

  @Override
  public void stop(BundleContext bundleContext) throws Exception {
    if (registration != null) {
      registration.unregister();
    }
  }

  private class ConfigPrinter implements ManagedService {

    @Override
    public void updated(Dictionary<String, ?> properties) throws ConfigurationException {
      System.out.println("Printing external.config configuration properties");
      Enumeration<String> keys = properties.keys();
      while (keys.hasMoreElements()) {
        String key = keys.nextElement();
        String value = properties.get(key).toString();
        System.out.println("\t" + key + "=" + value);
      }
      System.out.println("");
    }

  }

}
