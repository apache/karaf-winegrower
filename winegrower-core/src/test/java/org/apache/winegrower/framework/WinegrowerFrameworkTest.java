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
package org.apache.winegrower.framework;

import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.launch.Framework;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class WinegrowerFrameworkTest {
    @Test
    public void run() throws BundleException {
        final Framework framework = new WinegrowerFramework();
        { // let this specific API not be exposed in the other parts of the test
            WinegrowerFramework.class.cast(framework).setConfigurationProperties(new Properties() {{
                setProperty("winegrower.ripener.configuration.scanningExcludes", "test-classes");
            }});
        }
        assertEquals(Framework.INSTALLED, framework.getState());
        framework.init();
        assertEquals(Framework.INSTALLED, framework.getState());
        framework.start();
        assertEquals(Framework.ACTIVE, framework.getState());
        final Bundle[] bundles = framework.getBundleContext().getBundles();
        assertEquals(1, bundles.length);
        framework.getBundleContext().installBundle("org.apache.aries.cdi.extra-1.1.0.jar");
        assertEquals(2, framework.getBundleContext().getBundles().length);
        framework.stop();
        assertEquals(Framework.STOP_TRANSIENT, framework.getState());
    }
}
