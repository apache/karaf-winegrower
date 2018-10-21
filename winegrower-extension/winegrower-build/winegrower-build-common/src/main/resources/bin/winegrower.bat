@echo off
rem Licensed to the Apache Software Foundation (ASF) under one or more
rem contributor license agreements.  See the NOTICE file distributed with
rem this work for additional information regarding copyright ownership.
rem The ASF licenses this file to You under the Apache License, Version 2.0
rem (the "License"); you may not use this file except in compliance with
rem the License.  You may obtain a copy of the License at
rem
rem     http://www.apache.org/licenses/LICENSE-2.0
rem
rem Unless required by applicable law or agreed to in writing, software
rem distributed under the License is distributed on an "AS IS" BASIS,
rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
rem See the License for the specific language governing permissions and
rem limitations under the License.

rem ---------------------------------------------------------------------------
rem Start/Stop Script for the WINEGROWER Server
rem
rem Environment Variable Prerequisites
rem
rem   Do not set the variables in this script. Instead put them into a script
rem   setenv.bat in WINEGROWER_BASE/bin to keep your customizations separate.
rem
rem   WHEN RUNNING WINEGROWER AS A WINDOWS SERVICE:
rem   Note that the environment variables that affect the behavior of this
rem   script will have no effect at all on Windows Services. As such, any
rem   local customizations made in a WINEGROWER_BASE/bin/setenv.bat script
rem   will also have no effect on Winegrower when launched as a Windows Service.
rem   The configuration that controls Windows Services is stored in the Windows
rem   Registry, and is most conveniently maintained using the "tomcatXw.exe"
rem   maintenance utility, where "X" is the major version of Tomcat embedded
rem   in Winegrower you are running.
rem
rem   WINEGROWER_HOME   May point at your WINEGROWER "build" directory.
rem
rem   WINEGROWER_BASE   (Optional) Base directory for resolving dynamic portions
rem                   of a WINEGROWER installation.  If not present, resolves to
rem                   the same directory that WINEGROWER_HOME points to.
rem
rem   WINEGROWER_OPTS   (Optional) Java runtime options used when the "start",
rem                   "run" or "debug" command is executed.
rem                   Include here and not in JAVA_OPTS all options, that should
rem                   only be used by Winegrower itself, not by the stop process,
rem                   the version command etc.
rem                   Examples are heap size, GC logging, JMX ports etc.
rem
rem   WINEGROWER_TMPDIR (Optional) Directory path location of temporary directory
rem                   the JVM should use (java.io.tmpdir).  Defaults to
rem                   %WINEGROWER_BASE%\temp.
rem
rem   JAVA_HOME       Must point at your Java Development Kit installation.
rem                   Required to run the with the "debug" argument.
rem
rem   JRE_HOME        Must point at your Java Runtime installation.
rem                   Defaults to JAVA_HOME if empty. If JRE_HOME and JAVA_HOME
rem                   are both set, JRE_HOME is used.
rem
rem   JAVA_OPTS       (Optional) Java runtime options used when any command
rem                   is executed.
rem                   Include here and not in WINEGROWER_OPTS all options, that
rem                   should be used by Winegrower and also by the stop process,
rem                   the version command etc.
rem                   Most options should go into WINEGROWER_OPTS.
rem
rem   JAVA_ENDORSED_DIRS (Optional) Lists of of semi-colon separated directories
rem                   containing some jars in order to allow replacement of APIs
rem                   created outside of the JCP (i.e. DOM and SAX from W3C).
rem                   It can also be used to update the XML parser implementation.
rem                   This is only supported for Java <= 8.
rem                   Defaults to $WINEGROWER_HOME/endorsed.
rem
rem   JPDA_TRANSPORT  (Optional) JPDA transport used when the "jpda start"
rem                   command is executed. The default is "dt_socket".
rem
rem   JPDA_ADDRESS    (Optional) Java runtime options used when the "jpda start"
rem                   command is executed. The default is localhost:8000.
rem
rem   JPDA_SUSPEND    (Optional) Java runtime options used when the "jpda start"
rem                   command is executed. Specifies whether JVM should suspend
rem                   execution immediately after startup. Default is "n".
rem
rem   JPDA_OPTS       (Optional) Java runtime options used when the "jpda start"
rem                   command is executed. If used, JPDA_TRANSPORT, JPDA_ADDRESS,
rem                   and JPDA_SUSPEND are ignored. Thus, all required jpda
rem                   options MUST be specified. The default is:
rem
rem                   -agentlib:jdwp=transport=%JPDA_TRANSPORT%,
rem                       address=%JPDA_ADDRESS%,server=y,suspend=%JPDA_SUSPEND%
rem
rem   JSSE_OPTS       (Optional) Java runtime options used to control the TLS
rem                   implementation when JSSE is used. Default is:
rem                   "-Djdk.tls.ephemeralDHKeySize=2048"
rem
rem   LOGGING_CONFIG  (Optional) Override Winegrower's logging config file
rem                   Example (all one line)
rem                   set LOGGING_CONFIG="-Djava.util.logging.config.file=%WINEGROWER_BASE%\conf\logging.properties"
rem
rem   LOGGING_MANAGER (Optional) Override Winegrower's logging manager
rem                   Example (all one line)
rem                   set LOGGING_MANAGER="-Djava.util.logging.manager=org.apache.juli.ClassLoaderLogManager"
rem
rem   TITLE           (Optional) Specify the title of Winegrower window. The default
rem                   TITLE is Winegrower if it's not specified.
rem                   Example (all one line)
rem                   set TITLE=Winegrower.Cluster#1.Server#1 [%DATE% %TIME%]
rem ---------------------------------------------------------------------------

setlocal

rem Suppress Terminate batch job on CTRL+C
if not ""%1"" == ""run"" goto mainEntry
if "%TEMP%" == "" goto mainEntry
if exist "%TEMP%\%~nx0.run" goto mainEntry
echo Y>"%TEMP%\%~nx0.run"
if not exist "%TEMP%\%~nx0.run" goto mainEntry
echo Y>"%TEMP%\%~nx0.Y"
call "%~f0" %* <"%TEMP%\%~nx0.Y"
rem Use provided errorlevel
set RETVAL=%ERRORLEVEL%
del /Q "%TEMP%\%~nx0.Y" >NUL 2>&1
exit /B %RETVAL%
:mainEntry
del /Q "%TEMP%\%~nx0.run" >NUL 2>&1

rem Guess WINEGROWER_HOME if not defined
set "CURRENT_DIR=%cd%"
if not "%WINEGROWER_HOME%" == "" goto gotHome
set "WINEGROWER_HOME=%CURRENT_DIR%"
if exist "%WINEGROWER_HOME%\bin\WINEGROWER.bat" goto okHome
cd ..
set "WINEGROWER_HOME=%cd%"
cd "%CURRENT_DIR%"
:gotHome

if exist "%WINEGROWER_HOME%\bin\WINEGROWER.bat" goto okHome
echo The WINEGROWER_HOME environment variable is not defined correctly
echo This environment variable is needed to run this program
goto end
:okHome

rem Copy WINEGROWER_BASE from WINEGROWER_HOME if not defined
if not "%WINEGROWER_BASE%" == "" goto gotBase
set "WINEGROWER_BASE=%WINEGROWER_HOME%"
:gotBase

rem Ensure that neither WINEGROWER_HOME nor WINEGROWER_BASE contains a semi-colon
rem as this is used as the separator in the classpath and Java provides no
rem mechanism for escaping if the same character appears in the path. Check this
rem by replacing all occurrences of ';' with '' and checking that neither
rem WINEGROWER_HOME nor WINEGROWER_BASE have changed
if "%WINEGROWER_HOME%" == "%WINEGROWER_HOME:;=%" goto homeNoSemicolon
echo Using WINEGROWER_HOME:   "%WINEGROWER_HOME%"
echo Unable to start as WINEGROWER_HOME contains a semicolon (;) character
goto end
:homeNoSemicolon

if "%WINEGROWER_BASE%" == "%WINEGROWER_BASE:;=%" goto baseNoSemicolon
echo Using WINEGROWER_BASE:   "%WINEGROWER_BASE%"
echo Unable to start as WINEGROWER_BASE contains a semicolon (;) character
goto end
:baseNoSemicolon

rem Ensure that any user defined CLASSPATH variables are not used on startup,
rem but allow them to be specified in setenv.bat, in rare case when it is needed.
set CLASSPATH=

rem Get standard environment variables
if not exist "%WINEGROWER_BASE%\bin\setenv.bat" goto checkSetenvHome
call "%WINEGROWER_BASE%\bin\setenv.bat"
goto setenvDone
:checkSetenvHome
if exist "%WINEGROWER_HOME%\bin\setenv.bat" call "%WINEGROWER_HOME%\bin\setenv.bat"
:setenvDone

rem Get standard Java environment variables
rem In debug mode we need a real JDK (JAVA_HOME)
if ""%1"" == ""debug"" goto needJavaHome

rem Otherwise either JRE or JDK are fine
if not "%JRE_HOME%" == "" goto gotJreHome
if not "%JAVA_HOME%" == "" goto gotJavaHome
echo Neither the JAVA_HOME nor the JRE_HOME environment variable is defined
echo At least one of these environment variable is needed to run this program
goto exit

:needJavaHome
rem Check if we have a usable JDK
if "%JAVA_HOME%" == "" goto noJavaHome
if not exist "%JAVA_HOME%\bin\java.exe" goto noJavaHome
if not exist "%JAVA_HOME%\bin\javaw.exe" goto noJavaHome
if not exist "%JAVA_HOME%\bin\jdb.exe" goto noJavaHome
if not exist "%JAVA_HOME%\bin\javac.exe" goto noJavaHome
set "JRE_HOME=%JAVA_HOME%"
goto okJava

:noJavaHome
echo The JAVA_HOME environment variable is not defined correctly.
echo It is needed to run this program in debug mode.
echo NB: JAVA_HOME should point to a JDK not a JRE.
goto exit

:gotJavaHome
rem No JRE given, use JAVA_HOME as JRE_HOME
set "JRE_HOME=%JAVA_HOME%"

:gotJreHome
rem Check if we have a usable JRE
if not exist "%JRE_HOME%\bin\java.exe" goto noJreHome
if not exist "%JRE_HOME%\bin\javaw.exe" goto noJreHome
goto okJava

:noJreHome
rem Needed at least a JRE
echo The JRE_HOME environment variable is not defined correctly
echo This environment variable is needed to run this program
goto exit

:okJava
rem Don't override the endorsed dir if the user has set it previously
if not "%JAVA_ENDORSED_DIRS%" == "" goto gotEndorseddir
rem Java 9 no longer supports the java.endorsed.dirs
rem system property. Only try to use it if
rem CATALINA_HOME/endorsed exists.
if not exist "%CATALINA_HOME%\endorsed" goto gotEndorseddir
set "JAVA_ENDORSED_DIRS=%CATALINA_HOME%\endorsed"
:gotEndorseddir

rem Don't override _RUNJAVA if the user has set it previously
if not "%_RUNJAVA%" == "" goto gotRunJava
rem Set standard command for invoking Java.
rem Also note the quoting as JRE_HOME may contain spaces.
set _RUNJAVA="%JRE_HOME%\bin\java.exe"
:gotRunJava

rem Don't override _RUNJDB if the user has set it previously
rem Also note the quoting as JAVA_HOME may contain spaces.
if not "%_RUNJDB%" == "" goto gotRunJdb
set _RUNJDB="%JAVA_HOME%\bin\jdb.exe"
:gotRunJdb
if errorlevel 1 goto end

rem Add on extra jar file to CLASSPATH
rem Note that there are no quotes as we do not want to introduce random
rem quotes into the CLASSPATH
if "%CLASSPATH%" == "" goto emptyClasspath
set "CLASSPATH=%CLASSPATH%;"
:emptyClasspath
set "CLASSPATH=%CLASSPATH%%WINEGROWER_HOME%\lib\*"

if not "%WINEGROWER_TMPDIR%" == "" goto gotTmpdir
set "WINEGROWER_TMPDIR=%WINEGROWER_BASE%\temp"
:gotTmpdir

if not "%JSSE_OPTS%" == "" goto gotJsseOpts
set JSSE_OPTS="-Djdk.tls.ephemeralDHKeySize=2048"
:gotJsseOpts
set "JAVA_OPTS=%JAVA_OPTS% %JSSE_OPTS%"

if not "%LOGGING_CONFIG%" == "" goto noJuliConfig
set LOGGING_CONFIG="-Dwinegrower.script.nologgingconfig"
if not exist "%WINEGROWER_BASE%\conf\logging.properties" goto noJuliConfig
set LOGGING_CONFIG=-Djava.util.logging.config.file="%WINEGROWER_BASE%\conf\logging.properties"
:noJuliConfig

if not "%LOGGING_MANAGER%" == "" goto noJuliManager
set LOGGING_MANAGER=-Djava.util.logging.manager=org.apache.juli.ClassLoaderLogManager
:noJuliManager

rem Configure JAVA 9 specific start-up parameters
rem TODO: set "JDK_JAVA_OPTIONS=%JDK_JAVA_OPTIONS% --add-opens=java.base/java.lang=ALL-UNNAMED"
rem TODO: set "JDK_JAVA_OPTIONS=%JDK_JAVA_OPTIONS% --add-opens=java.rmi/sun.rmi.transport=ALL-UNNAMED"

rem ----- Execute The Requested Command ---------------------------------------

echo Using WINEGROWER_BASE:   "%WINEGROWER_BASE%"
echo Using WINEGROWER_HOME:   "%WINEGROWER_HOME%"
echo Using WINEGROWER_TMPDIR: "%WINEGROWER_TMPDIR%"
if ""%1"" == ""debug"" goto use_jdk
echo Using JRE_HOME:        "%JRE_HOME%"
goto java_dir_displayed
:use_jdk
echo Using JAVA_HOME:       "%JAVA_HOME%"
:java_dir_displayed
echo Using CLASSPATH:       "%CLASSPATH%"

set _EXECJAVA=%_RUNJAVA%
set MAINCLASS=${main}
set ACTION=start
set SECURITY_POLICY_FILE=
set DEBUG_OPTS=
set JPDA=

if not ""%1"" == ""jpda"" goto noJpda
set JPDA=jpda
if not "%JPDA_TRANSPORT%" == "" goto gotJpdaTransport
set JPDA_TRANSPORT=dt_socket
:gotJpdaTransport
if not "%JPDA_ADDRESS%" == "" goto gotJpdaAddress
set JPDA_ADDRESS=localhost:8000
:gotJpdaAddress
if not "%JPDA_SUSPEND%" == "" goto gotJpdaSuspend
set JPDA_SUSPEND=n
:gotJpdaSuspend
if not "%JPDA_OPTS%" == "" goto gotJpdaOpts
set JPDA_OPTS=-agentlib:jdwp=transport=%JPDA_TRANSPORT%,address=%JPDA_ADDRESS%,server=y,suspend=%JPDA_SUSPEND%
:gotJpdaOpts
shift
:noJpda

if ""%1"" == ""debug"" goto doDebug
if ""%1"" == ""run"" goto doRun
if ""%1"" == ""start"" goto doStart
if ""%1"" == ""stop"" goto doStop

echo Usage:  WINEGROWER ( commands ... )
echo commands:
echo   run               Start WINEGROWER in the current window
echo   start             Start WINEGROWER in a separate window
echo   stop              Stop WINEGROWER
goto end

:doDebug
shift
set _EXECJAVA=%_RUNJDB%
set DEBUG_OPTS=-sourcepath "%WINEGROWER_HOME%\..\..\java"
if not ""%1"" == ""-security"" goto execCmd
shift
echo Using Security Manager
set "SECURITY_POLICY_FILE=%WINEGROWER_BASE%\conf\WINEGROWER.policy"
goto execCmd

:doRun
goto execCmd

:doStart
shift
if "%TITLE%" == "" set TITLE=Winegrower
set _EXECJAVA=start "%TITLE%" %_RUNJAVA%
goto execCmd

:doStop
shift
set ACTION=stop
set WINEGROWER_OPTS=
goto execCmd

:execCmd
rem Get remaining unshifted command line arguments and save them in the
set CMD_LINE_ARGS=
:setArgs
if ""%1""=="""" goto doneSetArgs
set CMD_LINE_ARGS=%CMD_LINE_ARGS% %1
shift
goto setArgs
:doneSetArgs

rem Execute Java with the applicable properties
if not "%JPDA%" == "" goto doJpda
if not "%SECURITY_POLICY_FILE%" == "" goto doSecurity
%_EXECJAVA% %LOGGING_CONFIG% %LOGGING_MANAGER% %JAVA_OPTS% %WINEGROWER_OPTS% %DEBUG_OPTS% -D%ENDORSED_PROP%="%JAVA_ENDORSED_DIRS%" -classpath "%CLASSPATH%" -DWINEGROWER.base="%WINEGROWER_BASE%" -DWINEGROWER.home="%WINEGROWER_HOME%" -Djava.io.tmpdir="%WINEGROWER_TMPDIR%" %MAINCLASS% %CMD_LINE_ARGS% %ACTION%
goto end
:doSecurity
%_EXECJAVA% %LOGGING_CONFIG% %LOGGING_MANAGER% %JAVA_OPTS% %WINEGROWER_OPTS% %DEBUG_OPTS% -D%ENDORSED_PROP%="%JAVA_ENDORSED_DIRS%" -classpath "%CLASSPATH%" -Djava.security.manager -Djava.security.policy=="%SECURITY_POLICY_FILE%" -DWINEGROWER.base="%WINEGROWER_BASE%" -DWINEGROWER.home="%WINEGROWER_HOME%" -Djava.io.tmpdir="%WINEGROWER_TMPDIR%" %MAINCLASS% %CMD_LINE_ARGS% %ACTION%
goto end
:doJpda
if not "%SECURITY_POLICY_FILE%" == "" goto doSecurityJpda
%_EXECJAVA% %LOGGING_CONFIG% %LOGGING_MANAGER% %JAVA_OPTS% %JPDA_OPTS% %WINEGROWER_OPTS% %DEBUG_OPTS% -D%ENDORSED_PROP%="%JAVA_ENDORSED_DIRS%" -classpath "%CLASSPATH%" -DWINEGROWER.base="%WINEGROWER_BASE%" -DWINEGROWER.home="%WINEGROWER_HOME%" -Djava.io.tmpdir="%WINEGROWER_TMPDIR%" %MAINCLASS% %CMD_LINE_ARGS% %ACTION%
goto end
:doSecurityJpda
%_EXECJAVA% %LOGGING_CONFIG% %LOGGING_MANAGER% %JAVA_OPTS% %JPDA_OPTS% %WINEGROWER_OPTS% %DEBUG_OPTS% -D%ENDORSED_PROP%="%JAVA_ENDORSED_DIRS%" -classpath "%CLASSPATH%" -Djava.security.manager -Djava.security.policy=="%SECURITY_POLICY_FILE%" -DWINEGROWER.base="%WINEGROWER_BASE%" -DWINEGROWER.home="%WINEGROWER_HOME%" -Djava.io.tmpdir="%WINEGROWER_TMPDIR%" %MAINCLASS% %CMD_LINE_ARGS% %ACTION%
goto end

:end