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
package org.apache.winegrower;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.apache.winegrower.api.InjectedService;
import org.apache.winegrower.service.ServiceReferenceImpl;
import org.apache.winegrower.test.WithFramework;
import org.apache.winegrower.test.WithFramework.Entry;
import org.apache.winegrower.test.WithFramework.Service;
import org.apache.winegrower.test.simpleservice.MyService;
import org.junit.jupiter.api.Test;

class InjectionTest {

    @Test
    @WithFramework(includeResources = @Entry(path = "org.apache.winegrower.test.simpleservice"))
    void inject(@Service final ContextualFramework framework) {
        final Injected injected = framework.getServices().inject(new Injected());
        assertNotNull(injected.service);
        assertEquals(
                ServiceReferenceImpl.class.cast(framework.getServices().getServices().iterator().next().getReference()).getReference(),
                injected.service);
    }

    public static class Injected {
        @InjectedService
        private MyService service;
    }

}
