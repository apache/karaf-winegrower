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
package org.apache.karaf.framework;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.karaf.framework.deployer.OSGiBundleLifecycle;
import org.apache.karaf.framework.test.WithFramework;
import org.junit.jupiter.api.Test;
import org.osgi.framework.ServiceRegistration;

@WithFramework
class CoreTest {

  @WithFramework.Service
  private ContextualFramework framework;

  @Test
  void testBundles() {
    assertEquals(5, framework.getRegistry().getBundles().keySet().size());

    final OSGiBundleLifecycle bundle =  framework.getRegistry().getBundles().get(1L);
    assertEquals(1, bundle.getBundle().getBundleId());
    assertEquals("org.opentest4j", bundle.getBundle().getSymbolicName());
    assertTrue(bundle.getBundle().getLocation().contains("opentest4j-1.1.1.jar"));
    assertNotNull(bundle.getBundle().getBundleContext());
  }

  @Test
  void testServices() {
    assertEquals(8, framework.getServices().getServices().size());

    ServiceRegistration registration = framework.getServices().getServices().iterator().next();
    assertNotNull(registration);
    assertNotNull(registration.getReference());
  }

}
