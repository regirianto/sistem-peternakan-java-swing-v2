#!/usr/bin/env bash
# UI smoke E2E: menjalankan aplikasi Swing terhadap server + DB sekali pakai.
# Memerlukan sesi grafis (bukan headless).

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

MYSQL_BIN="${MYSQL_BIN:-/opt/homebrew/opt/mysql@8.4/bin/mysql}"
PORT="${E2E_UI_PORT:-8781}"
DB="${E2E_UI_DB:-peternakan_ui_e2e}"
DB_USER="${E2E_UI_DB_USER:-root}"
DB_HOST="${E2E_UI_DB_HOST:-127.0.0.1}"
DB_PORT="${E2E_UI_DB_PORT:-3307}"
JAR="$ROOT/dist/SistemPerternakanV2.jar"
ANT_BIN="${ANT_BIN:-/Applications/Apache NetBeans.app/Contents/Resources/netbeans/extide/ant/bin/ant}"
APP_CP="$JAR:$ROOT/dist/lib/*"
JUNIT="$ROOT/src/sistemperternakanv2/lib/junit-platform-console-standalone-1.10.2.jar"
BASE="http://127.0.0.1:$PORT"
OUT_DIR="$ROOT/docs/e2e"
LOG="$OUT_DIR/e2e-ui.log"
SERVER_PID=""
RESULT="FAIL"

mkdir -p "$OUT_DIR"
: > "$LOG"
log() { printf '%s %s\n' "$(date '+%H:%M:%S')" "$*" | tee -a "$LOG"; }

DB_PASS="$(awk -F= '$1=="db.password"{print substr($0,index($0,"=")+1)}' service.properties | tr -d '\r')"
export MYSQL_PWD="$DB_PASS"

cleanup() {
  if [ -n "$SERVER_PID" ] && kill -0 "$SERVER_PID" 2>/dev/null; then
    kill "$SERVER_PID" 2>/dev/null || true
    wait "$SERVER_PID" 2>/dev/null || true
  fi
  if [ "${KEEP_DB:-0}" != "1" ]; then
    "$MYSQL_BIN" -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" \
      -e "DROP DATABASE IF EXISTS \`$DB\`" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

log "Build Ant"
( cd "$ROOT" && "$ANT_BIN" -q clean jar ) >>"$LOG" 2>&1 || exit 1

log "Menyiapkan database $DB"
"$MYSQL_BIN" -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" \
  -e "DROP DATABASE IF EXISTS \`$DB\`; CREATE DATABASE \`$DB\` CHARACTER SET utf8mb4;" >>"$LOG" 2>&1 || exit 1
DB_URL="jdbc:mysql://$DB_HOST:$DB_PORT/$DB?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Jakarta&characterEncoding=utf8"
export DB_URL
java -cp "$APP_CP" id.peternakan.service.ServiceMain migrate >>"$LOG" 2>&1 || exit 1
java -cp "$APP_CP" id.peternakan.service.ServiceMain bootstrap-owner --username owner --password 'Owner#2024' >>"$LOG" 2>&1 || exit 1
java -cp "$APP_CP" id.peternakan.service.ServiceMain demo >>"$LOG" 2>&1 || exit 1

log "Menjalankan server di $BASE"
SERVER_PORT="$PORT" java -cp "$APP_CP" id.peternakan.service.ServiceMain serve >>"$LOG" 2>&1 &
SERVER_PID=$!
for _ in $(seq 1 40); do
  curl -sS -m 2 "$BASE/api/health" >/dev/null 2>&1 && break
  sleep 0.5
done
if ! curl -sS -m 2 "$BASE/api/health" >/dev/null 2>&1; then
  log "Server tidak siap"
  exit 1
fi

log "Kompilasi dan jalankan UiE2ESmokeTest"
rm -rf "$ROOT/build/e2e-test"
mkdir -p "$ROOT/build/e2e-test"
javac -cp "$APP_CP" -d "$ROOT/build/e2e-test" \
  "$ROOT/test/id/peternakan/desktop/ui/UiE2ESmokeTest.java" \
  "$ROOT/test/id/peternakan/desktop/ui/UiBusinessFlowE2ETest.java" \
  "$ROOT/test/id/peternakan/desktop/ui/UiOperationalFlowsE2ETest.java" \
  "$ROOT/test/id/peternakan/desktop/ui/UiRoleAccessE2ETest.java" >>"$LOG" 2>&1 || exit 1
if E2E_UI=1 E2E_BASE_URL="$BASE" java -Djava.awt.headless=false -jar "$JUNIT" \
  -cp "$ROOT/build/e2e-test:$APP_CP" \
  --select-class id.peternakan.desktop.ui.UiE2ESmokeTest \
  --select-class id.peternakan.desktop.ui.UiBusinessFlowE2ETest \
  --select-class id.peternakan.desktop.ui.UiOperationalFlowsE2ETest \
  --select-class id.peternakan.desktop.ui.UiRoleAccessE2ETest >>"$LOG" 2>&1; then
  RESULT="PASS"
  log "UI smoke PASS"
else
  RESULT="FAIL"
  log "UI smoke FAIL"
fi

{
  echo "# UI E2E Smoke Report"
  echo
  echo "- Tanggal: $(date '+%Y-%m-%d %H:%M:%S')"
  echo "- Server: $BASE"
  echo "- Database: \`$DB\`"
  echo "- Hasil: **$RESULT**"
  echo
  echo "Cakupan: login form, menu/data/selector, beli → terima → jual → bayar, jual per kg + batal, pakan, pengeluaran + alokasi, pemotongan + batal, penjualan daging, susut, uang muka + refund + reverse, kandang, mitra, cabang, pengguna, operasi kambing (timbang/kesehatan/pindah/kematian), bayar utang, batal beli, tutup sisa, periode, edit pengaturan, backup, edit master data, ekspor CSV laporan, pratinjau PDF, penerimaan status Mati/retur, dan menu per role (owner/admin/petugas/cikarang) tanpa dialog error."
  echo
  echo "Log lengkap: \`docs/e2e/e2e-ui.log\`"
} > "$OUT_DIR/E2E_UI_REPORT.md"

[ "$RESULT" = "PASS" ]
