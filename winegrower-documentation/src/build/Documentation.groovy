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
package org.apache.winegrower.build

import org.asciidoctor.Asciidoctor
import org.asciidoctor.AttributesBuilder
import org.asciidoctor.OptionsBuilder
import org.asciidoctor.Placement

import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes

def color = '#303284'
def header = """
<header>
      <nav class="navbar navbar-expand-md navbar-dark fixed-top bg-dark" style="background-color: ${color} !important;">
        <div class="container">
          <a class="navbar-brand" href="index.html">
            <img src="https://karaf.apache.org/images/karaf-logo-new.png">
          </a>
          <button class="navbar-toggler" type="button" data-toggle="collapse" data-target="#navbarCollapse" aria-controls="navbarCollapse" aria-expanded="false" aria-label="Toggle navigation">
            <span class="navbar-toggler-icon"></span>
          </button>
          <div class="collapse navbar-collapse justify-content-end" id="navbarCollapse">
            <div>
              <ul class="navbar-nav mr-auto align-items-center text-uppercase">
                <li class="nav-item active">
                  <a class="nav-link" href="index.html">Home</span></a>
                </li>
                <!--
                <li class="nav-item">
                  <a class="nav-link" href="download.html">Download</a>
                </li>
                -->
                <li class="nav-item dropdown">
                  <a class="nav-link dropdown-toggle" href="#" id="navbarDropdown" role="button" data-toggle="dropdown" aria-haspopup="true" aria-expanded="false">
                    Documentation
                  </a>
                  <div class="dropdown-menu" aria-labelledby="navbarDropdown">
                    <a class="dropdown-item" href="@{relativePath}index.html">Home</a>
                    <div class="dropdown-divider"></div>
                    <a class="dropdown-item" href="@{relativePath}core/index.html">Core</a>
                    <div class="dropdown-divider"></div>
                    <a class="dropdown-item" href="@{relativePath}extension/index.html">Extensions</a>
                    <a class="dropdown-item" href="@{relativePath}extension/maven/index.html">Maven</a>
                    <a class="dropdown-item" href="@{relativePath}extension/junit5/index.html">Junit 5</a>
                    <a class="dropdown-item" href="@{relativePath}extension/cdi/index.html">CDI</a>
                    <a class="dropdown-item" href="@{relativePath}extension/servlet/index.html">Servlet</a>
                    <a class="dropdown-item" href="@{relativePath}extension/agent/index.html">Javaagent</a>
                  </div>
                </li>
                <li class="nav-item">
                  <a class="nav-link" href="https://karaf.apache.org/community.html">Community</a>
                </li>
                <li class="nav-item">
                  <a class="nav-link disabled" href="https://www.apache.org">
                    <img src="https://karaf.apache.org/images/apache-feather-tm-new.png">
                  </a>
                </li>
              </ul>
            <div>
          </div>
        </div>
      </div></div></nav>
    </header>
"""

def footer= """
<footer class="container-fluid bg-dark pt-5 pb-3 text-white text-center" style="background-color: ${color} !important;">
  <div class="mx-auto pb-5">
    <div>Karaf goodness without OSGi build headache!</div>
  </div>
  <p class="float-right"><a href="#">Back to top</a></p>
  <p>&copy; 2018 <a href="https://www.apache.org">Apache Software Foundation</a> - <a href="privacy.html">Privacy Policy</a><br>
  Apache Karaf, Karaf, Apache, the Apache feather logo, and the Apache Karaf project logo are trademarks of The Apache Software Foundation.</p>
</footer>
"""

def bootstrapCss = """
<link rel=\"stylesheet\" href=\"https://maxcdn.bootstrapcdn.com/bootstrap/4.0.0/css/bootstrap.min.css\" integrity=\"sha384-Gn5384xqQ1aoWXA+058RXPxPg6fy4IWvTNh0E263XmFcJlSAwiGgFAW/dAiS6JXm\" crossorigin=\"anonymous\">
"""
def bootstrapJs = """
<script src="https://code.jquery.com/jquery-3.2.1.slim.min.js" integrity="sha384-KJ3o2DKtIkvYIK3UENzmM7KCkRr/rE9/Qpg6aAZGJwFDMVNA/GpGFF93hXpG5KkN" crossorigin="anonymous"></script>
<script src="https://cdnjs.cloudflare.com/ajax/libs/popper.js/1.12.9/umd/popper.min.js" integrity="sha384-ApNbgh9B+Y1QKtv3Rn7W3mgPxhU9K/ScQsAP7hUibX39j7fakFPskvXusvfa0b4Q" crossorigin="anonymous"></script>
<script src="https://maxcdn.bootstrapcdn.com/bootstrap/4.0.0/js/bootstrap.min.js" integrity="sha384-JZR6Spejh4U02d8jOt6vLEHfe/JQGiRRSQQxSfFWpi1MquVdAyjUar5+76PVCmYl" crossorigin="anonymous"></script>
"""

def root = project.basedir.parentFile
def output = new File(project.build.directory, 'documentation')
def adoc = Asciidoctor.Factory.create()
// copy css
def cssSource = new File(project.basedir, '.asciidoctor/theme/manual/src/main/asciidoc/style/apache.css')
def copyCss = { ref ->
    def apacheCss = new File(ref.parentFile, 'css/apache.css')
    if (apacheCss.exists()) {
        return
    }
    apacheCss.parentFile.mkdirs()
    apacheCss.text = cssSource.text

    stream = Thread.currentThread().getContextClassLoader()
            .getResourceAsStream("gems/asciidoctor-1.5.7.1/data/stylesheets/coderay-asciidoctor.css")
    Files.copy(stream, new File(apacheCss.parentFile, 'coderay-asciidoctor.css').toPath(), StandardCopyOption.REPLACE_EXISTING)
    stream.close()
}


def render = {file ->
    def module = file.parentFile.parentFile.parentFile.parentFile
    def relativePath = root.toPath().relativize(file.parentFile.toPath()).toString()
    def sanitizedpath = relativePath
            .replace('winegrower-', '')
            .replace('testing-', '')
            .replace('-plugin', '')
            .replace('/build', '')
            .replace('/testing', '')
            .replace(File.separator, '/')
            .replace('/src/main/asciidoc', '') + '/'
    def htmlName = file.name.substring(0, file.name.length() - 'adoc'.length()) + 'html'
    def fileName = (module.name == 'winegrower-documentation' ? '' : sanitizedpath) + htmlName
    log.info("Rendering ${file} to ${output}/${fileName}")
    def attributes = AttributesBuilder.attributes()
            .docType('article')
            .imagesDir("${project.basedir}/.asciidoctor/theme/manual/src/main/theme/images")
            .icons('font')
            .dataUri(true)
            .setAnchors(true)
            .stylesDir('css')
            .styleSheetName('apache.css')
            .attribute('source-highlighter', 'coderay')
            .attribute('idprefix')
            .attribute('idseparator', '-')
            .attribute('source-highlighter', 'coderay')
            .get()
    def options = OptionsBuilder.options()
            .backend('html5')
            .headerFooter(true)
            .inPlace(false)
            .baseDir(new File("${project.build.directory}"))
            .attributes(attributes)

    def rendered = adoc.render(file.text, options)
    def renderedFile = new File(output, fileName)
    renderedFile.parentFile.mkdirs()
    def relativeLink = renderedFile.parentFile.toPath().relativize(output.toPath()).toString()
    if (!relativeLink.isEmpty()) {
        relativeLink = relativeLink + '/'
    }
    renderedFile.text = rendered
    // add bootstrap and move apache css at the end to ensure it is used
            .replace('<link rel="stylesheet" href="css/apache.css">', '')
            .replace('</head>', "${bootstrapCss}<link rel=\"stylesheet\" href=\"css/apache.css\"></head>")
            .replace('</body>', "</div>${bootstrapJs}</body>")
    // wrap in main tag
            .replace('<div id="header">', "${header.replace('@{relativePath}', relativeLink)}<main role=\"main\"><div id=\"header\">")
            .replace('</body>', "</main>${footer}</body>")
            .replace('<body', "<body style=\"padding-top: 3rem;\"")
    // home link
            .replace('"index.html"', "\"${relativeLink}index.html\"")
    // drop adoc footer
            .replaceAll('<div id="footer-text">[^<]+</div>', '')
            .replaceAll('<div id="footer">[^<]+</div>', '')

    copyCss(renderedFile)
}

// only support flat .adoc in src/main/asciidoc for now
Files.walkFileTree(root.toPath(), new SimpleFileVisitor<Path>() {
    @Override
    FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        def asFile = dir.toFile()
        if (new File(asFile, 'pom.xml').exists()
                || new File(asFile, 'main').exists()
                || new File(asFile, 'asciidoc').exists()
                || 'asciidoc' == asFile.name) {
            return FileVisitResult.CONTINUE
        }
        return FileVisitResult.SKIP_SUBTREE
    }

    @Override
    FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        def asFile = file.toFile()
        if (asFile.name.endsWith(".adoc") && 'README.adoc' != asFile.name) {
            render(asFile)
        }
        return FileVisitResult.CONTINUE
    }
})
