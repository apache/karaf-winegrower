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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.util.Hashtable;

import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.PrototypeServiceFactory;
import org.osgi.framework.ServiceRegistration;

class ServiceReferenceImplTest {
    @Test
    void singleton() {
        final ServiceReferenceImpl<Object> ref = new ServiceReferenceImpl<>(new Hashtable<>(), null,
                new SomeService());
        assertEquals(ref.getReference(), ref.getReference());
    }

    @Test
    void prototype() {
        final ServiceReferenceImpl<Object> ref = new ServiceReferenceImpl<>(new Hashtable<>(), null,
                new PrototypeServiceFactory<SomeService>() {
                    @Override
                    public SomeService getService(final Bundle bundle, final ServiceRegistration<SomeService> registration) {
                        return new SomeService();
                    }

                    @Override
                    public void ungetService(final Bundle bundle, final ServiceRegistration<SomeService> registration, final SomeService service) {
                        // no-op
                    }
                });
        assertNotSame(ref.getReference(), ref.getReference());
    }

    static class SomeService {}
}
