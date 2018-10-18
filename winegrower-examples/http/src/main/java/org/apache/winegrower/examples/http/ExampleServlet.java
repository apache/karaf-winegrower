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
package org.apache.winegrower.examples.http;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Hashtable;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.winegrower.api.ImplicitActivator;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

@WebServlet(name = "ExampleServlet", urlPatterns = "/")
public class ExampleServlet extends HttpServlet {

  @Override
  public void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
    try (PrintWriter writer = response.getWriter()) {
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
