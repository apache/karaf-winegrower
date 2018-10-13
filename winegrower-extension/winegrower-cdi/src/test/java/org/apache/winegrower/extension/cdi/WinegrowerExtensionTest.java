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
package org.apache.winegrower.extension.cdi;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.se.SeContainerInitializer;

import org.apache.winegrower.Ripener;
import org.apache.winegrower.service.OSGiServices;
import org.junit.jupiter.api.Test;

class WinegrowerExtensionTest {

    @Test
    void ensureBeansAreAdded() {
        try (final Ripener ripener = new Ripener.Impl(new Ripener.Configuration());
                final SeContainer container = WinegrowerExtension.RipenerLocator.wrapCdiBoot(ripener,
                        () -> SeContainerInitializer.newInstance().initialize())) {
            assertNotNull(container.select(Ripener.class).get());
            assertNotNull(container.select(Ripener.Configuration.class).get());
            assertNotNull(container.select(OSGiServices.class).get());
        }
    }
}
