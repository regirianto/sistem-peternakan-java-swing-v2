#!/usr/bin/env bash
# Kompilasi dan jalankan unit test tanpa Maven.
set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
ANT_BIN="${ANT_BIN:-/Applications/Apache NetBeans.app/Contents/Resources/netbeans/extide/ant/bin/ant}"
JUNIT="$ROOT/src/sistemperternakanv2/lib/junit-platform-console-standalone-1.10.2.jar"
APP_CP="$ROOT/dist/SistemPerternakanV2.jar:$ROOT/dist/lib/*"

"$ANT_BIN" -q clean jar || exit 1
rm -rf build/test-classes
mkdir -p build/test-classes
find test -name '*.java' > build/test-sources.txt
javac -cp "$APP_CP" -d build/test-classes @build/test-sources.txt || exit 1
java -jar "$JUNIT" -cp "build/test-classes:$APP_CP" --scan-classpath --details=summary
