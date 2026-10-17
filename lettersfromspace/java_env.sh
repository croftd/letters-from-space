# Source this file to put the local JDK on the PATH:   source java_env.sh
if [ -z "${JAVA_HOME:-}" ] && ! command -v java >/dev/null 2>&1; then
  JDK=$(ls -d "$HOME"/.local/jdk/jdk-* 2>/dev/null | head -1)
  if [ -n "$JDK" ]; then export JAVA_HOME="$JDK"; export PATH="$JDK/bin:$PATH"; fi
fi
