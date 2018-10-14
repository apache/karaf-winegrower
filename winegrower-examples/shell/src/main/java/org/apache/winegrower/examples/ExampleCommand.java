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
package org.apache.winegrower.examples;

import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Service;

@Service
@Command(scope = "winegrower", name = "example", description = "Winegrower shell command example")
public class ExampleCommand implements Action {

  @Argument(index = 0, name = "argument", description = "Single argument", required = true, multiValued = false)
  private String argument;

  @Override
  public Object execute() throws Exception {
    System.out.println("You entered " + argument);
    return null;
  }

}
