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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.osgi.service.cm.Configuration;

import java.io.File;

public class DefaultConfigurationAdminTest {

  @Test
  @DisplayName("Should return value from system property")
  public void systemPropertiesTest() throws Exception {
    System.setProperty("winegrower.service.test.foo", "bar");
    DefaultConfigurationAdmin configurationAdmin = new DefaultConfigurationAdmin();
    Configuration configuration = configurationAdmin.getConfiguration("test");
    Assertions.assertEquals("bar", configuration.getProperties().get("foo"));
  }

  @Test
  @DisplayName("Should return value from cfg file in classpath")
  public void externalConfigClasspathTest() throws Exception {
    DefaultConfigurationAdmin configurationAdmin = new DefaultConfigurationAdmin();
    Configuration configuration = configurationAdmin.getConfiguration("external.test");
    Assertions.assertEquals("bar", configuration.getProperties().get("foo"));
  }

  @Test
  @DisplayName("Should return value from cfg file in winegrower.config.path location")
  public void externalConfigPathTest() throws Exception {
    File file = new File("src/test/resources");
    System.out.println(file.getAbsolutePath());
    System.setProperty("winegrower.config.path", "src/test/resources");
    DefaultConfigurationAdmin configurationAdmin = new DefaultConfigurationAdmin();
    Configuration configuration = configurationAdmin.getConfiguration("external.test");
    Assertions.assertEquals("bar", configuration.getProperties().get("foo"));
  }

}
