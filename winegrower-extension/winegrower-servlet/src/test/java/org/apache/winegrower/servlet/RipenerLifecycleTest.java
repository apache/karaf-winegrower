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
package org.apache.winegrower.servlet;

import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URL;
import java.util.Hashtable;

import javax.inject.Inject;
import javax.servlet.ServletContext;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.spi.Provider;
import org.apache.meecrowave.Meecrowave;
import org.apache.meecrowave.junit5.MeecrowaveConfig;
import org.apache.meecrowave.testing.ConfigurationInject;
import org.apache.winegrower.Ripener;
import org.apache.winegrower.api.ImplicitActivator;
import org.apache.winegrower.api.InjectedService;
import org.junit.jupiter.api.Test;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

@MeecrowaveConfig
class RipenerLifecycleTest {

    @Inject
    private ServletContext context;

    @InjectedService
    private Provider log4j;

    @ConfigurationInject
    private Meecrowave.Builder serverConfig;

    @Test
    void ensureIsStarted() throws IOException {
        final Object attribute = context.getAttribute(Ripener.class.getName());
        assertNotNull(attribute);
        final Ripener ripener = Ripener.class.cast(attribute);
        assertTrue(ripener.getRegistry().getBundles().size() > 3 /* cxf, log4j, .... */);
        assertTrue(ripener.getStartTime() > 0);
        assertNull(log4j);
        ripener.getServices().inject(this);
        assertNotNull(log4j);
        final String output;
        try (final BufferedReader reader = new BufferedReader(
                new InputStreamReader(new URL(String.format("http://localhost:%d/example", serverConfig.getHttpPort())).openStream()))) {
            output = reader.lines().collect(joining("\n"));
        }
        assertEquals("Example", output.trim());
    }

    @WebServlet(name = "ExampleServlet", urlPatterns = "/example")
    public static class ExampleServlet extends HttpServlet {

        @Override
        public void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
            try (final PrintWriter writer = response.getWriter()) {
                writer.println("Example");
            }
        }

        @ImplicitActivator
        public static class Registrar implements BundleActivator {

            @Override
            public void start(final BundleContext context) {
                context.registerService(HttpServlet.class, new ExampleServlet(), new Hashtable<>());
            }

            @Override
            public void stop(final BundleContext context) {
                // no-op
            }
        }
    }
}
