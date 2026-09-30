#!/usr/bin/env bash
# Downloads a project-local JDK 21 and Maven into backend/.tools (like a Python venv).
# Nothing is installed system-wide; delete backend/.tools to undo.
#
#   ./setup.sh          # download whatever is missing
#   ./setup.sh --force  # re-download everything (e.g. to update)
#
# Then: source activate && mvn spring-boot:run
set -euo pipefail

JAVA_MAJOR=21
MAVEN_LINE=3.9  # newest 3.9.x is picked automatically

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TOOLS="$HERE/.tools"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

if [[ "${1:-}" == "--force" ]]; then
  rm -rf "$TOOLS/jdk-$JAVA_MAJOR" "$TOOLS/maven"
fi
mkdir -p "$TOOLS"

case "$(uname -s)" in
  Darwin) OS=mac ;;
  Linux)  OS=linux ;;
  *) echo "Unsupported OS: $(uname -s). Install JDK $JAVA_MAJOR and Maven manually." >&2; exit 1 ;;
esac
case "$(uname -m)" in
  arm64|aarch64) ARCH=aarch64 ;;
  x86_64|amd64)  ARCH=x64 ;;
  *) echo "Unsupported CPU: $(uname -m)" >&2; exit 1 ;;
esac

sha_check() { # <algorithm bits> <expected hash> <file>
  local actual
  actual="$(shasum -a "$1" "$3" | awk '{print $1}')"
  if [[ "$actual" != "$2" ]]; then
    echo "Checksum mismatch for $3" >&2
    exit 1
  fi
}

# --- JDK (Eclipse Temurin via the Adoptium API) ---
if [[ -d "$TOOLS/jdk-$JAVA_MAJOR" ]]; then
  echo "JDK $JAVA_MAJOR already present, skipping (use --force to update)."
else
  echo "Downloading Temurin JDK $JAVA_MAJOR ($OS/$ARCH)..."
  api="https://api.adoptium.net/v3/assets/latest/$JAVA_MAJOR/hotspot?os=$OS&architecture=$ARCH&image_type=jdk&vendor=eclipse"
  # The API lists the GitHub release asset; its checksum file lives next to it.
  url="$(curl -fsSL "$api" | grep -oE '"link" *: *"[^"]*\.tar\.gz"' | head -1 | sed -E 's/.*"(https[^"]*)"/\1/')"
  [[ -n "$url" ]] || { echo "Could not find a JDK $JAVA_MAJOR download for $OS/$ARCH" >&2; exit 1; }
  curl -fL --progress-bar -o "$TMP/jdk.tar.gz" "$url"
  sha_check 256 "$(curl -fsSL "$url.sha256.txt" | awk '{print $1}')" "$TMP/jdk.tar.gz"
  mkdir "$TMP/jdk"
  tar -xzf "$TMP/jdk.tar.gz" -C "$TMP/jdk" --strip-components=1
  mv "$TMP/jdk" "$TOOLS/jdk-$JAVA_MAJOR"
fi

# --- Maven (Apache binary distribution from Maven Central) ---
if [[ -d "$TOOLS/maven" ]]; then
  echo "Maven already present, skipping (use --force to update)."
else
  base="https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven"
  version="$(curl -fsSL "$base/maven-metadata.xml" \
    | grep -oE "<version>${MAVEN_LINE//./\\.}\.[0-9]+</version>" \
    | sed -E 's/<\/?version>//g' | sort -t. -k3 -n | tail -1)"
  echo "Downloading Maven $version..."
  url="$base/$version/apache-maven-$version-bin.tar.gz"
  curl -fL --progress-bar -o "$TMP/maven.tar.gz" "$url"
  sha_check 512 "$(curl -fsSL "$url.sha512" | awk '{print $1}')" "$TMP/maven.tar.gz"
  mkdir "$TMP/maven"
  tar -xzf "$TMP/maven.tar.gz" -C "$TMP/maven" --strip-components=1
  mv "$TMP/maven" "$TOOLS/maven"
fi

# mvn wrapper that keeps downloaded dependencies in .tools/m2 instead of ~/.m2.
# (Passed as a quoted argument, because MAVEN_OPTS breaks on paths with spaces.)
mkdir -p "$TOOLS/bin"
cat > "$TOOLS/bin/mvn" <<'EOF'
#!/usr/bin/env bash
TOOLS="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
exec "$TOOLS/maven/bin/mvn" "-Dmaven.repo.local=$TOOLS/m2" "$@"
EOF
chmod +x "$TOOLS/bin/mvn"

echo
echo "Done. Activate the tools in this terminal with:"
echo "  source activate"
