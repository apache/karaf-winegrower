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
import org.apache.maven.settings.crypto.DefaultSettingsDecryptionRequest
import org.apache.maven.settings.crypto.SettingsDecrypter
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider

import static java.util.Collections.singleton

def source = new File(project.build.directory, 'documentation')
if (!source.exists() || !new File(source, 'index.html').exists()) {
    log.warn('Not ready to deploy, skipping')
    return
}

def branch = 'refs/heads/gh-pages'
def workDir = new File(project.build.directory, UUID.randomUUID().toString() + '_' + System.currentTimeMillis())

def url = project.parent.scm.url
def serverId = project.properties['github.serverId']
log.info("Using server ${serverId}")

def server = session.settings.servers.findAll { it.id == serverId }.iterator().next()
def decryptedServer = session.container.lookup(SettingsDecrypter).decrypt(new DefaultSettingsDecryptionRequest(server))
server = decryptedServer.server != null ? decryptedServer.server : server

log.info("Using url=${url}")
log.info("Using user=${server.username}")
log.info("Using branch=${branch}")

def credentialsProvider = new UsernamePasswordCredentialsProvider(server.username, server.password)
def git = Git.cloneRepository()
        .setCredentialsProvider(credentialsProvider)
        .setURI(url)
        .setDirectory(workDir)
        .setBranchesToClone(singleton(branch))
        .setBranch(branch)
        .call()

new AntBuilder().copy(todir: workDir.absolutePath, overwrite: true) {
    fileset(dir: source.absolutePath)
}
// we don't drop old files, stay conservative for now

def message = "Updating the documentation for version ${project.version} // " + new Date().toString()
git.add().addFilepattern(".").call()
git.commit().setAll(true).setMessage(message).call()
git.status().call()
git.push().setCredentialsProvider(credentialsProvider).add(branch).call()
log.info("Updated the documentation on ${new Date()}")
