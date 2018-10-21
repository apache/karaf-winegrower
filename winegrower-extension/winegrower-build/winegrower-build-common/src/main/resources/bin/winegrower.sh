#!/bin/sh

# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

#
# This script is forked from Apache Tomcat
#

# OS specific support.  $var _must_ be set to either true or false.
cygwin=false
darwin=false
os400=false
hpux=false
case "`uname`" in
CYGWIN*) cygwin=true;;
Darwin*) darwin=true;;
OS400*) os400=true;;
HP-UX*) hpux=true;;
esac

# resolve links - $0 may be a softlink
PRG="$0"

while [ -h "$PRG" ]; do
  ls=`ls -ld "$PRG"`
  link=`expr "$ls" : '.*-> \(.*\)$'`
  if expr "$link" : '/.*' > /dev/null; then
    PRG="$link"
  else
    PRG=`dirname "$PRG"`/"$link"
  fi
done

# Get standard environment variables
PRGDIR=`dirname "$PRG"`

# Only set WINEGROWER_HOME if not already set
[ -z "$WINEGROWER_HOME" ] && WINEGROWER_HOME=`cd "$PRGDIR/.." >/dev/null; pwd`

# Copy WINEGROWER_BASE from WINEGROWER_HOME if not already set
[ -z "$WINEGROWER_BASE" ] && WINEGROWER_BASE="$WINEGROWER_HOME"

# Ensure that any user defined CLASSPATH variables are not used on startup,
# but allow them to be specified in setenv.sh, in rare case when it is needed.
CLASSPATH=

if [ -r "$WINEGROWER_BASE/bin/setenv.sh" ]; then
  . "$WINEGROWER_BASE/bin/setenv.sh"
elif [ -r "$WINEGROWER_HOME/bin/setenv.sh" ]; then
  . "$WINEGROWER_HOME/bin/setenv.sh"
fi

# For Cygwin, ensure paths are in UNIX format before anything is touched
if $cygwin; then
  [ -n "$JAVA_HOME" ] && JAVA_HOME=`cygpath --unix "$JAVA_HOME"`
  [ -n "$JRE_HOME" ] && JRE_HOME=`cygpath --unix "$JRE_HOME"`
  [ -n "$WINEGROWER_HOME" ] && WINEGROWER_HOME=`cygpath --unix "$WINEGROWER_HOME"`
  [ -n "$WINEGROWER_BASE" ] && WINEGROWER_BASE=`cygpath --unix "$WINEGROWER_BASE"`
  [ -n "$CLASSPATH" ] && CLASSPATH=`cygpath --path --unix "$CLASSPATH"`
fi

# Ensure that neither WINEGROWER_HOME nor WINEGROWER_BASE contains a colon
# as this is used as the separator in the classpath and Java provides no
# mechanism for escaping if the same character appears in the path.
case $WINEGROWER_HOME in
  *:*) echo "Using WINEGROWER_HOME:   $WINEGROWER_HOME";
       echo "Unable to start as WINEGROWER_HOME contains a colon (:) character";
       exit 1;
esac
case $WINEGROWER_BASE in
  *:*) echo "Using WINEGROWER_BASE:   $WINEGROWER_BASE";
       echo "Unable to start as WINEGROWER_BASE contains a colon (:) character";
       exit 1;
esac

# For OS400
if $os400; then
  # Set job priority to standard for interactive (interactive - 6) by using
  # the interactive priority - 6, the helper threads that respond to requests
  # will be running at the same priority as interactive jobs.
  COMMAND='chgjob job('$JOBNAME') runpty(6)'
  system $COMMAND

  # Enable multi threading
  export QIBM_MULTI_THREADED=Y
fi

# Get standard Java environment variables
# Make sure prerequisite environment variables are set
if [ -z "$JAVA_HOME" -a -z "$JRE_HOME" ]; then
  if $darwin; then
    # Bugzilla 54390
    if [ -x '/usr/libexec/java_home' ] ; then
      export JAVA_HOME=`/usr/libexec/java_home`
    # Bugzilla 37284 (reviewed).
    elif [ -d "/System/Library/Frameworks/JavaVM.framework/Versions/CurrentJDK/Home" ]; then
      export JAVA_HOME="/System/Library/Frameworks/JavaVM.framework/Versions/CurrentJDK/Home"
    fi
  else
    JAVA_PATH=`which java 2>/dev/null`
    if [ "x$JAVA_PATH" != "x" ]; then
      JAVA_PATH=`dirname $JAVA_PATH 2>/dev/null`
      JRE_HOME=`dirname $JAVA_PATH 2>/dev/null`
    fi
    if [ "x$JRE_HOME" = "x" ]; then
      # XXX: Should we try other locations?
      if [ -x /usr/bin/java ]; then
        JRE_HOME=/usr
      fi
    fi
  fi
  if [ -z "$JAVA_HOME" -a -z "$JRE_HOME" ]; then
    echo "Neither the JAVA_HOME nor the JRE_HOME environment variable is defined"
    echo "At least one of these environment variable is needed to run this program"
    exit 1
  fi
fi
if [ -z "$JAVA_HOME" -a "$1" = "debug" ]; then
  echo "JAVA_HOME should point to a JDK in order to run in debug mode."
  exit 1
fi
if [ -z "$JRE_HOME" ]; then
  JRE_HOME="$JAVA_HOME"
fi

# If we're running under jdb, we need a full jdk.
if [ "$1" = "debug" ] ; then
  if [ "$os400" = "true" ]; then
    if [ ! -x "$JAVA_HOME"/bin/java -o ! -x "$JAVA_HOME"/bin/javac ]; then
      echo "The JAVA_HOME environment variable is not defined correctly"
      echo "This environment variable is needed to run this program"
      echo "NB: JAVA_HOME should point to a JDK not a JRE"
      exit 1
    fi
  else
    if [ ! -x "$JAVA_HOME"/bin/java -o ! -x "$JAVA_HOME"/bin/jdb -o ! -x "$JAVA_HOME"/bin/javac ]; then
      echo "The JAVA_HOME environment variable is not defined correctly"
      echo "This environment variable is needed to run this program"
      echo "NB: JAVA_HOME should point to a JDK not a JRE"
      exit 1
    fi
  fi
fi

# Set standard commands for invoking Java, if not already set.
if [ -z "$_RUNJAVA" ]; then
  _RUNJAVA="$JRE_HOME"/bin/java
fi
if [ "$os400" != "true" ]; then
  if [ -z "$_RUNJDB" ]; then
    _RUNJDB="$JAVA_HOME"/bin/jdb
  fi
fi

# Add on extra jar files to CLASSPATH
if [ ! -z "$CLASSPATH" ] ; then
  CLASSPATH="$CLASSPATH":
fi
if [ "$WINEGROWER_HOME" != "$WINEGROWER_BASE" ]; then
  for i in "$WINEGROWER_BASE/lib/"*.jar ; do
    if [ -z "$CLASSPATH" ] ; then
      CLASSPATH="$i"
    else
      CLASSPATH="$i:$CLASSPATH"
    fi
  done
fi
for i in "$WINEGROWER_HOME/lib/"*.jar ; do
  if [ -z "$CLASSPATH" ] ; then
    CLASSPATH="$i"
  else
    CLASSPATH="$i:$CLASSPATH"
  fi
done

if [ -z "$WINEGROWER_OUT" ] ; then
  WINEGROWER_OUT="$WINEGROWER_BASE"/logs/winegrower.out
fi

if [ -z "$WINEGROWER_TMPDIR" ] ; then
  # Define the java.io.tmpdir to use for WINEGROWER
  WINEGROWER_TMPDIR="$WINEGROWER_BASE"/temp
fi

# Bugzilla 37848: When no TTY is available, don't output to console
have_tty=0
if [ "`tty`" != "not a tty" ]; then
    have_tty=1
fi

# For Cygwin, switch paths to Windows format before running java
if $cygwin; then
  JAVA_HOME=`cygpath --absolute --windows "$JAVA_HOME"`
  JRE_HOME=`cygpath --absolute --windows "$JRE_HOME"`
  WINEGROWER_HOME=`cygpath --absolute --windows "$WINEGROWER_HOME"`
  WINEGROWER_BASE=`cygpath --absolute --windows "$WINEGROWER_BASE"`
  WINEGROWER_TMPDIR=`cygpath --absolute --windows "$WINEGROWER_TMPDIR"`
  CLASSPATH=`cygpath --path --windows "$CLASSPATH"`
fi

if [ -z "$JSSE_OPTS" ] ; then
  JSSE_OPTS="-Djdk.tls.ephemeralDHKeySize=2048"
fi
JAVA_OPTS="$JAVA_OPTS $JSSE_OPTS"

# Set juli LogManager config file if it is present and an override has not been issued
if [ -z "$LOGGING_CONFIG" ]; then
  if [ -r "$WINEGROWER_BASE"/conf/logging.properties ]; then
    LOGGING_CONFIG="-Djava.util.logging.config.file=$WINEGROWER_BASE/conf/logging.properties"
  else
    # Bugzilla 45585
    LOGGING_CONFIG="-Dwinegrower.script.nologgingconfig"
  fi
fi

if [ -z "$LOGGING_MANAGER" ]; then
  LOGGING_MANAGER="-Djava.util.logging.manager=org.apache.logging.log4j.jul.LogManager"
fi

# Set UMASK unless it has been overridden
if [ -z "$UMASK" ]; then
    UMASK="0027"
fi
umask $UMASK

# Uncomment the following line to make the umask available when using the
# org.apache.tomcat.security.SecurityListener
#JAVA_OPTS="$JAVA_OPTS -Dorg.apache.tomcat.security.SecurityListener.UMASK=`umask`"

if [ -z "$USE_NOHUP" ]; then
    if $hpux; then
        USE_NOHUP="true"
    else
        USE_NOHUP="false"
    fi
fi
unset _NOHUP
if [ "$USE_NOHUP" = "true" ]; then
    _NOHUP=nohup
fi

if [ -z "$WINEGROWER_PID" ]; then
  WINEGROWER_PID="$WINEGROWER_BASE"/conf/winegrower.pid
fi
# ----- Execute The Requested Command -----------------------------------------

# Bugzilla 37848: only output this if we have a TTY
if [ $have_tty -eq 1 ]; then
  echo "Using WINEGROWER_BASE:   $WINEGROWER_BASE"
  echo "Using WINEGROWER_HOME:   $WINEGROWER_HOME"
  echo "Using WINEGROWER_TMPDIR: $WINEGROWER_TMPDIR"
  echo "Using JRE_HOME:        $JRE_HOME"
  echo "Using CLASSPATH:       $CLASSPATH"
  if [ ! -z "$WINEGROWER_PID" ]; then
    echo "Using WINEGROWER_PID:    $WINEGROWER_PID"
  fi
fi

if [ "$1" = "jpda" ] ; then
  if [ -z "$JPDA_TRANSPORT" ]; then
    JPDA_TRANSPORT="dt_socket"
  fi
  if [ -z "$JPDA_ADDRESS" ]; then
    JPDA_ADDRESS="localhost:8000"
  fi
  if [ -z "$JPDA_SUSPEND" ]; then
    JPDA_SUSPEND="n"
  fi
  if [ -z "$JPDA_OPTS" ]; then
    JPDA_OPTS="-agentlib:jdwp=transport=$JPDA_TRANSPORT,server=y,address=$JPDA_ADDRESS,suspend=$JPDA_SUSPEND"
  fi
  WINEGROWER_OPTS="$JPDA_OPTS $WINEGROWER_OPTS"
  shift
fi

WINEGROWER_LOG4J2_PATH="$WINEGROWER_BASE"/conf/log4j2.xml
if [ -f "$WINEGROWER_LOG4J2_PATH" ]; then
  if $cygwin; then
    WINEGROWER_LOG4J2_PATH=`cygpath --absolute --windows "$WINEGROWER_LOG4J2_PATH"`
  fi
  WINEGROWER_OPTS="$WINEGROWER_OPTS "-Dlog4j.configurationFile=\"$WINEGROWER_LOG4J2_PATH\"""
fi

if [ "$1" = "run" ]; then

  shift
  eval exec "\"$_RUNJAVA\"" "\"$LOGGING_CONFIG\"" $LOGGING_MANAGER $JAVA_OPTS $WINEGROWER_OPTS \
    -classpath "\"$CLASSPATH\"" \
    -Dwinegrower.base="\"$WINEGROWER_BASE\"" \
    -Dwinegrower.home="\"$WINEGROWER_HOME\"" \
    -Dkaraf.home="\"$WINEGROWER_HOME\"" \
    -Dkaraf.base="\"$WINEGROWER_BASE\"" \
    -Djava.io.tmpdir="\"$WINEGROWER_TMPDIR\"" \
    ${main} "$WINEGROWER_ARGS" "$@"

elif [ "$1" = "start" ] ; then

  if [ ! -z "$WINEGROWER_PID" ]; then
    if [ -f "$WINEGROWER_PID" ]; then
      if [ -s "$WINEGROWER_PID" ]; then
        echo "Existing PID file found during start."
        if [ -r "$WINEGROWER_PID" ]; then
          PID=`cat "$WINEGROWER_PID"`
          ps -p $PID >/dev/null 2>&1
          if [ $? -eq 0 ] ; then
            echo "Winegrower appears to still be running with PID $PID. Start aborted."
            echo "If the following process is not a Winegrower process, remove the PID file and try again:"
            ps -f -p $PID
            exit 1
          else
            echo "Removing/clearing stale PID file."
            rm -f "$WINEGROWER_PID" >/dev/null 2>&1
            if [ $? != 0 ]; then
              if [ -w "$WINEGROWER_PID" ]; then
                cat /dev/null > "$WINEGROWER_PID"
              else
                echo "Unable to remove or clear stale PID file. Start aborted."
                exit 1
              fi
            fi
          fi
        else
          echo "Unable to read PID file. Start aborted."
          exit 1
        fi
      else
        rm -f "$WINEGROWER_PID" >/dev/null 2>&1
        if [ $? != 0 ]; then
          if [ ! -w "$WINEGROWER_PID" ]; then
            echo "Unable to remove or write to empty PID file. Start aborted."
            exit 1
          fi
        fi
      fi
    fi
  fi

  shift
  touch "$WINEGROWER_OUT"
  eval $_NOHUP "\"$_RUNJAVA\"" "\"$LOGGING_CONFIG\"" $LOGGING_MANAGER $JAVA_OPTS $WINEGROWER_OPTS \
    -classpath "\"$CLASSPATH\"" \
    -Dwinegrower.base="\"$WINEGROWER_BASE\"" \
    -Dwinegrower.home="\"$WINEGROWER_HOME\"" \
    -Djava.io.tmpdir="\"$WINEGROWER_TMPDIR\"" \
    ${main} "$WINEGROWER_ARGS" "$@" \
    >> "$WINEGROWER_OUT" 2>&1 "&"

  if [ ! -z "$WINEGROWER_PID" ]; then
    echo $! > "$WINEGROWER_PID"
  fi

  echo "Winegrower started."

elif [ "$1" = "stop" ] ; then

  shift

  SLEEP=15
  if [ ! -z "$1" ]; then
    echo $1 | grep "[^0-9]" >/dev/null 2>&1
    if [ $? -gt 0 ]; then
      SLEEP=$1
      shift
    fi
  fi

  FORCE=0
  if [ "$1" = "-force" ]; then
    shift
    FORCE=1
  fi

  if [ ! -z "$WINEGROWER_PID" ]; then
    if [ -f "$WINEGROWER_PID" ]; then
      if [ -s "$WINEGROWER_PID" ]; then
        kill -15 `cat "$WINEGROWER_PID"` >/dev/null 2>&1
        if [ $? -gt 0 ]; then
          echo "PID file found but no matching process was found. Stop aborted."
          exit 1
        fi
      else
        echo "PID file is empty and has been ignored."
      fi
    else
      echo "\$WINEGROWER_PID was set but the specified file does not exist. Is Winegrower running? Stop aborted."
      exit 1
    fi
  fi

  if [ ! -z "$WINEGROWER_PID" ]; then
    if [ -f "$WINEGROWER_PID" ]; then
      while [ $SLEEP -ge 0 ]; do
        kill -15 `cat "$WINEGROWER_PID"` >/dev/null 2>&1
        if [ $? -gt 0 ]; then
          rm -f "$WINEGROWER_PID" >/dev/null 2>&1
          if [ $? != 0 ]; then
            if [ -w "$WINEGROWER_PID" ]; then
              cat /dev/null > "$WINEGROWER_PID"
              # If Winegrower has stopped don't try and force a stop with an empty PID file
              FORCE=0
            else
              echo "The PID file could not be removed or cleared."
            fi
          fi
          echo "Winegrower stopped."
          break
        fi
        if [ $SLEEP -gt 0 ]; then
          sleep 1
        fi
        if [ $SLEEP -eq 0 ]; then
          echo "Winegrower did not stop in time."
          if [ $FORCE -eq 0 ]; then
            echo "PID file was not removed."
          fi
          echo "To aid diagnostics a thread dump has been written to standard out."
          kill -3 `cat "$WINEGROWER_PID"`
        fi
        SLEEP=`expr $SLEEP - 1 `
      done
    fi
  fi

  KILL_SLEEP_INTERVAL=15
  if [ $FORCE -eq 1 ]; then
    if [ -z "$WINEGROWER_PID" ]; then
      echo "Kill failed: \$WINEGROWER_PID not set"
    else
      if [ -f "$WINEGROWER_PID" ]; then
        PID=`cat "$WINEGROWER_PID"`
        echo "Killing Winegrower with the PID: $PID"
        kill -9 $PID
        while [ $KILL_SLEEP_INTERVAL -ge 0 ]; do
            kill -0 `cat "$WINEGROWER_PID"` >/dev/null 2>&1
            if [ $? -gt 0 ]; then
                rm -f "$WINEGROWER_PID" >/dev/null 2>&1
                if [ $? != 0 ]; then
                    if [ -w "$WINEGROWER_PID" ]; then
                        cat /dev/null > "$WINEGROWER_PID"
                    else
                        echo "The PID file could not be removed."
                    fi
                fi
                echo "The Winegrower process has been killed."
                break
            fi
            if [ $KILL_SLEEP_INTERVAL -gt 0 ]; then
                sleep 1
            fi
            KILL_SLEEP_INTERVAL=`expr $KILL_SLEEP_INTERVAL - 1 `
        done
        if [ $KILL_SLEEP_INTERVAL -lt 0 ]; then
            echo "Winegrower has not been killed completely yet. The process might be waiting on some system call or might be UNINTERRUPTIBLE."
        fi
      fi
    fi
  fi

else

  echo "Usage: WINEGROWER.sh ( commands ... )"
  echo "commands:"
  echo "  jpda start        Start WINEGROWER under JPDA debugger"
  echo "  run               Start WINEGROWER in the current window"
  echo "  start             Start WINEGROWER in a separate window"
  echo "  stop              Stop WINEGROWER, waiting up t/o 15 seconds for the process to end"
  echo "  stop n            Stop WINEGROWER, waiting up to n seconds for the process to end"
  echo "  stop -force       Stop WINEGROWER, wait up to 15 seconds and then use kill -KILL if still running"
  echo "  stop n -force     Stop WINEGROWER, wait up to n seconds and then use kill -KILL if still running"
  echo "Note: Waiting for the process to end and use of the -force option require that \$WINEGROWER_PID is defined"
  exit 1

fi