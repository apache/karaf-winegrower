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
package org.apache.winegrower.servlet.service;

import static java.util.Collections.list;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Dictionary;
import java.util.EnumSet;
import java.util.EventListener;
import java.util.Hashtable;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.FilterRegistration;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.ServletRegistration;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.osgi.service.http.HttpContext;
import org.osgi.service.http.HttpService;

public class ServletHttpService implements HttpService {
    private final ServletContext context;

    ServletHttpService(final ServletContext context) {
        this.context = context;
    }

    public void registerListener(final EventListener listener, final Hashtable<String, Object> props) {
        getContext(null).addListener(listener);
    }

    public void registerFilter(final String alias, final Filter filter, final Dictionary initParams) {
        final FilterRegistration.Dynamic dynamic = getContext(null).addFilter(alias, filter);
        if (initParams != null) {
            list(initParams.keys())
                .forEach(key -> dynamic.setInitParameter(String.valueOf(key), String.valueOf(initParams.get(key))));
        }
        dynamic.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), Boolean.parseBoolean(String.valueOf(initParams.get("is-match-after"))), alias);
    }

    @Override
    public void registerServlet(final String alias, final Servlet servlet,
                                final Dictionary initParams, final HttpContext context) {
        final ServletRegistration.Dynamic dynamic = getContext(context).addServlet(alias, servlet);
        if (initParams != null) {
            list(initParams.keys())
                .forEach(key -> dynamic.setInitParameter(String.valueOf(key), String.valueOf(initParams.get(key))));
        }
        dynamic.addMapping(alias);
    }

    @Override
    public void registerResources(final String alias, final String name, final HttpContext context) {
        final ServletContext servletContext = getContext(context);
        final ServletRegistration defaultServlet = servletContext.getServletRegistration("default");
        String servletImpl;
        if (defaultServlet != null) {
            servletImpl = defaultServlet.getClassName();
        } else if (servletContext.getClass().getName().startsWith("org.apache.catalina.")) {
            servletImpl = "org.apache.catalina.servlets.DefaultServlet";
        } else /*assumed jetty*/ {
            servletImpl = "org.eclipse.jetty.servlet.DefaultServlet";
        }
        final ServletRegistration.Dynamic dynamic = servletContext.addServlet(alias, servletImpl);
        dynamic.addMapping(alias);
    }

    @Override
    public void unregister(final String alias) {
        // no-op
    }

    @Override
    public HttpContext createDefaultHttpContext() {
        return new ServletHttpContext(this.context);
    }

    private ServletContext getContext(final HttpContext context) {
        return ServletHttpContext.class.isInstance(context) ?
                ServletHttpContext.class.cast(context).delegate : this.context;
    }

    private static class ServletHttpContext implements HttpContext {
        private final ServletContext delegate;

        ServletHttpContext(final ServletContext context) {
            this.delegate = context;
        }

        @Override
        public boolean handleSecurity(final HttpServletRequest request, final HttpServletResponse response) {
            return true;
        }

        @Override
        public URL getResource(final String name) {
            try {
                return delegate.getResource(name);
            } catch (final MalformedURLException e) {
                throw new IllegalArgumentException(e);
            }
        }

        @Override
        public String getMimeType(final String name) {
            return delegate.getMimeType(name);
        }
    }
}
