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

import org.apache.karaf.framework.service.BundleActivatorHandler;
import org.apache.karaf.framework.test.WithFramework;
import org.apache.karaf.framework.test.simpleactivator.MyActivator;
import org.junit.jupiter.api.Test;
import org.osgi.framework.BundleActivator;

@WithFramework(includeResources = {
    @WithFramework.Entry(path = "org.apache.karaf.framework.test.simpleactivator"),
    @WithFramework.Entry(path = "org.apache.karaf.framework.test.simpleservice") })
class ContextualFrameworkTest {
  @WithFramework.Service
  private ContextualFramework framework;

  @Test
  void simpleActivator() throws Exception {
    assertEquals(2, framework.getRegistry().getBundles().size());

    final BundleActivatorHandler activatorHandler =  framework.getRegistry().getBundles().values().iterator().next().getActivator();
    assertNotNull(activatorHandler);
    final BundleActivator activator = activatorHandler.getActivator();
    assertNotNull(activator);
    assertTrue(MyActivator.class.isInstance(activator));
    final MyActivator myActivator = MyActivator.class.cast(activator);
    assertNotNull(myActivator.getContext());
    assertEquals(1, myActivator.getStarted());
    assertEquals(0, myActivator.getStopped());
    framework.stop();
    assertEquals(1, myActivator.getStarted());
    assertEquals(1, myActivator.getStopped());
  }

  @Test
  void simpleService() throws Exception {
    assertEquals(1, framework.getServices().getServices().size());
  }

}
