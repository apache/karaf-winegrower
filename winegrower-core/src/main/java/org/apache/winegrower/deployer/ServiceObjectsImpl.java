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
package org.apache.winegrower.deployer;

import org.apache.winegrower.service.ServiceReferenceImpl;
import org.osgi.framework.ServiceFactory;
import org.osgi.framework.ServiceObjects;
import org.osgi.framework.ServiceReference;

public class ServiceObjectsImpl<S> implements ServiceObjects<S> {
    private final ServiceReferenceImpl<S> reference;

    ServiceObjectsImpl(final ServiceReferenceImpl<S> reference) {
        this.reference = reference;
    }

    @Override
    public S getService() {
        return (S) reference.getReference();
    }

    @Override
    public void ungetService(final S service) {
        if (reference.hasFactory()) {
            ServiceFactory.class.cast(reference.getFactory()).ungetService(reference.getBundle(), reference.getRegistration(), service);
        } else if (reference.getReference() == service) {
            reference.unget();
        }
    }

    @Override
    public ServiceReference<S> getServiceReference() {
        return reference;
    }
}
