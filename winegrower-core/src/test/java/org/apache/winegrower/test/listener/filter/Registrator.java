/**
 * Copyright (C) 2006-2019 Talend Inc. - www.talend.com
 * <p>
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
package org.apache.winegrower.test.listener.filter;

import static org.osgi.framework.Constants.BUNDLE_ACTIVATOR;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Hashtable;

import org.osgi.annotation.bundle.Header;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;

@Header(name = BUNDLE_ACTIVATOR, value = "${@class}")
public class Registrator implements ServiceListener, BundleActivator {
    private final Collection<ServiceEvent> events = new ArrayList<>();

    public Collection<ServiceEvent> getEvents() {
        return events;
    }

    @Override
    public void serviceChanged(final ServiceEvent event) {
        synchronized (events) {
            events.add(event);
        }
    }

    @Override
    public void start(final BundleContext context) throws Exception {
        context.addServiceListener(this, "(org.apache.winegrower.test.listener.filter.Registrator=true)");

        final Hashtable<String, Object> properties = new Hashtable<>();
        properties.put("org.apache.winegrower.test.listener.filter.Registrator", "true");
        context.registerService(Registrator.class, this, properties);
    }

    @Override
    public void stop(final BundleContext context) {
        context.removeServiceListener(this);
    }

    public static class SimpleService {}
}
