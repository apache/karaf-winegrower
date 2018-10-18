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

import static java.util.Collections.list;

import java.util.Dictionary;

import org.osgi.framework.Bundle;
import org.osgi.framework.ServiceFactory;
import org.osgi.framework.ServiceReference;

public class ServiceReferenceImpl<T> implements ServiceReference<T> {
    private final Dictionary<String, ?> properties;
    private final Bundle bundle;
    private final Bundle[] usingBundles = new Bundle[0];
    private final Object reference;
    private final boolean usesFactory;
    private ServiceRegistrationImpl registration;

    ServiceReferenceImpl(final Dictionary<String, ?> properties, final Bundle bundle, final Object reference) {
        this.properties = properties;
        this.bundle = bundle;
        this.usesFactory = ServiceFactory.class.isInstance(reference);
        this.reference = reference;
    }

    public boolean hasFactory() {
        return usesFactory;
    }

    public ServiceRegistrationImpl getRegistration() {
        return registration;
    }

    void setRegistration(final ServiceRegistrationImpl registration) {
        this.registration = registration;
    }

    public Object getReference() {
        if (usesFactory) {
            return ServiceFactory.class.cast(reference).getService(bundle, registration);
        }
        return reference;
    }

    @Override
    public Object getProperty(final String key) {
        return properties.get(key);
    }

    @Override
    public String[] getPropertyKeys() {
        return list(properties.keys()).toArray(new String[0]);
    }

    @Override
    public Bundle getBundle() {
        return bundle;
    }

    @Override
    public Bundle[] getUsingBundles() {
        return usingBundles;
    }

    @Override
    public boolean isAssignableTo(final Bundle bundle, final String className) {
        return true;
    }

    @Override
    public int compareTo(final Object reference) {
        if (this.reference.equals(reference)) {
            return 0;
        }
        return System.identityHashCode(this.reference) - System.identityHashCode(reference);
    }

    public boolean unget() {
        if (usesFactory) {
            throw new UnsupportedOperationException();
        }
        return true;
    }

    public ServiceFactory<?> getFactory() {
        if (!usesFactory) {
            throw new UnsupportedOperationException();
        }
        return ServiceFactory.class.cast(reference);
    }

    @Override
    public String toString() {
        return "ServiceReferenceImpl{reference=" + reference + '}';
    }
}
