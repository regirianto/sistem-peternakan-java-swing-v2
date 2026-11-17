#!/usr/bin/env bash
# E2E API test untuk Sistem Peternakan Kambing.
# Menjalankan server + DB sekali pakai, lalu menguji alur bisnis lewat REST.
#
# Pemakaian:
#   scripts/e2e/api-e2e.sh
#   SKIP_BUILD=1 KEEP_DB=1 scripts/e2e/api-e2e.sh
#
# Hasil: docs/e2e/E2E_API_REPORT.md (ringkasan) dan docs/e2e/e2e-api.log (log).

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

MYSQL_BIN="${MYSQL_BIN:-/opt/homebrew/opt/mysql@8.4/bin/mysql}"
MYSQLDUMP_BIN="${MYSQLDUMP_BIN:-$(dirname "$MYSQL_BIN")/mysqldump}"
PORT="${E2E_PORT:-8780}"
DB="${E2E_DB:-peternakan_e2e}"
DB_USER="${E2E_DB_USER:-root}"
DB_HOST="${E2E_DB_HOST:-127.0.0.1}"
DB_PORT="${E2E_DB_PORT:-3307}"
OWNER_USER="owner"
OWNER_PASS="Owner#2024"
JAR="$ROOT/dist/SistemPerternakanV2.jar"
ANT_BIN="${ANT_BIN:-/Applications/Apache NetBeans.app/Contents/Resources/netbeans/extide/ant/bin/ant}"
APP_CP="$JAR:$ROOT/dist/lib/*"
BASE="http://127.0.0.1:$PORT"
TODAY="$(date +%F)"
MONTH="$(date +%Y-%m)"
FROM="$(date +%Y-%m-01)"
OUT_DIR="$ROOT/docs/e2e"
LOG="$OUT_DIR/e2e-api.log"
BODY_FILE="$(mktemp -t e2e-body.XXXXXX)"
PDF_FILE="$(mktemp -t e2e-pdf.XXXXXX)"
SERVER_PID=""
TOTAL=0
PASS=0
FAIL=0
FAILURES=()

mkdir -p "$OUT_DIR"
: > "$LOG"

DB_PASS="$(awk -F= '$1=="db.password"{print substr($0,index($0,"=")+1)}' service.properties | tr -d '\r')"
export MYSQL_PWD="$DB_PASS"

log() { printf '%s %s\n' "$(date '+%H:%M:%S')" "$*" | tee -a "$LOG"; }
section() { log ""; log "=== $* ==="; }

check() {
  TOTAL=$((TOTAL + 1))
  if [ "$1" = "ok" ]; then
    PASS=$((PASS + 1))
    log "PASS  $2"
  else
    FAIL=$((FAIL + 1))
    FAILURES+=("$2")
    log "FAIL  $2 :: ${3:-}"
  fi
}

assert_code() {
  local label="$1" expected="$2"
  if [ "$CODE" = "$expected" ]; then check ok "$label"; else check fail "$label" "HTTP $CODE (harap $expected) body=$BODY"; fi
}

assert_ok() {
  local label="$1"
  if [ "$CODE" = "200" ] && ! echo "$BODY" | jq -e '.error' >/dev/null 2>&1; then
    check ok "$label"
  else
    check fail "$label" "HTTP $CODE body=$BODY"
  fi
}

assert_error() {
  local label="$1" needle="$2"
  local err
  err="$(echo "$BODY" | jq -r '.error // empty' 2>/dev/null)"
  if [ "$CODE" -ge 400 ] && [[ "$err" == *"$needle"* ]]; then
    check ok "$label"
  else
    check fail "$label" "HTTP $CODE error=$err (harap memuat: $needle)"
  fi
}

assert_jq() {
  local label="$1" expr="$2"
  if echo "$BODY" | jq -e "$expr" >/dev/null 2>&1; then
    check ok "$label"
  else
    check fail "$label" "jq gagal: $expr body=$BODY"
  fi
}

assert_eq() {
  local label="$1" expected="$2" actual="$3"
  if [ "$expected" = "$actual" ]; then check ok "$label"; else check fail "$label" "harap [$expected] dapat [$actual]"; fi
}

jqget() { echo "$BODY" | jq -r "$1"; }

api() {
  local method="$1" path="$2" data="${3-}" token="${4-$TOK}"
  local rid="${REQ_ID:-$(uuidgen)}"
  local args=(-sS -o "$BODY_FILE" -w '%{http_code}' -X "$method" "$BASE$path"
    -H 'Accept: application/json' -H 'Content-Type: application/json' -H "X-Request-Id: $rid")
  [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
  [ -n "$data" ] && args+=(-d "$data")
  CODE="$(curl "${args[@]}" 2>>"$LOG")" || CODE="000"
  BODY="$(cat "$BODY_FILE" 2>/dev/null || true)"
  REQ_ID=""
}

download() {
  local path="$1" out="$2" token="${3-$TOK}"
  CODE="$(curl -sS -o "$out" -w '%{http_code}' "$BASE$path" -H "Authorization: Bearer $token")"
}

sqlq() { "$MYSQL_BIN" -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -N -e "$1" "$DB" 2>>"$LOG"; }

cleanup() {
  if [ -n "$SERVER_PID" ] && kill -0 "$SERVER_PID" 2>/dev/null; then
    kill "$SERVER_PID" 2>/dev/null || true
    wait "$SERVER_PID" 2>/dev/null || true
  fi
  if [ "${KEEP_DB:-0}" != "1" ]; then
    "$MYSQL_BIN" -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" \
      -e "DROP DATABASE IF EXISTS \`$DB\`" >/dev/null 2>&1 || true
  fi
  rm -f "$BODY_FILE" "$PDF_FILE"
}
trap cleanup EXIT

wait_health() {
  for _ in $(seq 1 40); do
    if curl -sS -m 2 "$BASE/api/health" >/dev/null 2>&1; then return 0; fi
    sleep 0.5
  done
  return 1
}

write_report() {
  local report="$OUT_DIR/E2E_API_REPORT.md"
  {
    echo "# E2E Test Report"
    echo
    echo "- Tanggal: $(date '+%Y-%m-%d %H:%M:%S')"
    echo "- Database: \`$DB\` (MySQL $DB_HOST:$DB_PORT)"
    echo "- Server: $BASE"
    echo "- Total skenario: **$TOTAL**"
    echo "- Lulus: **$PASS**"
    echo "- Gagal: **$FAIL**"
    echo
    if [ "$FAIL" -eq 0 ]; then
      echo "Status: **SEMUA LULUS**"
    else
      echo "Status: **ADA KEGAGALAN**"
      echo
      echo "## Kegagalan"
      for f in "${FAILURES[@]}"; do echo "- $f"; done
    fi
    echo
    echo "Log lengkap: \`docs/e2e/e2e-api.log\`"
  } > "$report"
  log "Laporan ditulis: $report"
}

# ---------------------------------------------------------------- setup
section "BUILD & DATABASE"
if [ "${SKIP_BUILD:-0}" != "1" ]; then
  log "ant clean jar"
  ( cd "$ROOT" && "$ANT_BIN" -q clean jar ) >>"$LOG" 2>&1 || { log "Build gagal"; write_report; exit 1; }
fi
[ -f "$JAR" ] || { log "JAR tidak ditemukan: $JAR"; write_report; exit 1; }

"$MYSQL_BIN" -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" \
  -e "DROP DATABASE IF EXISTS \`$DB\`; CREATE DATABASE \`$DB\` CHARACTER SET utf8mb4;" >>"$LOG" 2>&1
DB_URL="jdbc:mysql://$DB_HOST:$DB_PORT/$DB?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Jakarta&characterEncoding=utf8"
export DB_URL
java -cp "$APP_CP" id.peternakan.service.ServiceMain migrate >>"$LOG" 2>&1 || { log "Migrasi gagal"; write_report; exit 1; }
java -cp "$APP_CP" id.peternakan.service.ServiceMain bootstrap-owner --username "$OWNER_USER" --password "$OWNER_PASS" >>"$LOG" 2>&1 \
  || { log "Bootstrap gagal"; write_report; exit 1; }

SERVER_PORT="$PORT" java -cp "$APP_CP" id.peternakan.service.ServiceMain serve >>"$LOG" 2>&1 &
SERVER_PID=$!
wait_health || { log "Server tidak siap"; write_report; exit 1; }
assert_eq "Migrasi mencapai V10" "10" "$(sqlq 'SELECT MAX(version) FROM schema_migrations')"
check ok "Server uji siap di port $PORT"

# ---------------------------------------------------------------- auth
section "AUTH, SESI, DAN PROFIL"
api GET "/api/public/info" "" ""
assert_ok "Profil usaha dapat dibaca publik"
assert_eq "Nama usaha" "Berkah Aamin Farm" "$(jqget '.companyName')"

api POST "/api/login" "{\"username\":\"$OWNER_USER\",\"password\":\"salah\"}" ""
assert_error "Login password salah ditolak" "salah"

api POST "/api/login" "{\"username\":\"$OWNER_USER\",\"password\":\"$OWNER_PASS\"}" ""
assert_ok "Login owner"
TOK="$(jqget '.token')"
[ -n "$TOK" ] && [ "$TOK" != "null" ] && check ok "Token owner diterbitkan" || check fail "Token owner diterbitkan" "$BODY"
api GET "/api/me" "" "$TOK"
assert_jq "Profil owner memiliki permission" '.permissions | index("finance.view") != null'

# ---------------------------------------------------------------- branches
section "CABANG DAN PENUGASAN"
api GET "/api/branches"
assert_ok "Daftar cabang"
assert_eq "Cabang awal Pusat" "PUSAT" "$(jqget '.items[0].code')"
assert_eq "Cabang awal Cikarang" "CIKARANG" "$(jqget '.items[1].code')"
PUSAT_ID="$(jqget '.items[] | select(.code=="PUSAT") | .id')"
CIKARANG_ID="$(jqget '.items[] | select(.code=="CIKARANG") | .id')"

api POST "/api/branches" '{"code":"RPH-X","name":"RPH X","type":"RPH"}'
assert_error "Tipe RPH ditolak" "PUSAT atau CABANG"

api POST "/api/branches" '{"code":"BANDUNG","name":"Bandung","type":"CABANG","address":"Bandung","phone":"0221"}'
assert_ok "Tambah cabang Bandung"
BANDUNG_ID="$(jqget '.id')"
api GET "/api/branches/all"
assert_jq "Owner melihat semua cabang" '.items | length >= 3'

api POST "/api/branches" '{"code":"BANDUNG","name":"Duplikat","type":"CABANG"}'
assert_error "Kode cabang duplikat ditolak" "Gagal menyimpan"

api PUT "/api/branches/$BANDUNG_ID" '{"name":"Bandung Raya","type":"CABANG","address":"Bandung","phone":"0222","active":true}'
assert_ok "Ubah cabang Bandung"

# ---------------------------------------------------------------- users & permissions
section "PENGGUNA, ROLE, DAN IZIN"
api POST "/api/users" '{"username":"admin_e2e","password":"Admin#2024","fullName":"Admin E2E","roleCode":"ADMIN"}'
assert_ok "Buat admin E2E"
ADMIN_ID="$(jqget '.id')"
api PUT "/api/users/$ADMIN_ID/branches" "{\"branchIds\":[$BANDUNG_ID]}"
assert_ok "Tugaskan admin E2E ke Bandung"
api PUT "/api/users/$ADMIN_ID/branches" '{"branchIds":[]}'
assert_error "Non-owner tanpa cabang ditolak" "minimal harus memiliki satu cabang"
api PUT "/api/users/$ADMIN_ID/branches" "{\"branchIds\":[$BANDUNG_ID]}"

api POST "/api/users" '{"username":"petugas_e2e","password":"Petugas#2024","fullName":"Petugas E2E","roleCode":"PETUGAS"}'
assert_ok "Buat petugas E2E"
PETUGAS_ID="$(jqget '.id')"
api PUT "/api/users/$PETUGAS_ID/branches" "{\"branchIds\":[$PUSAT_ID]}"
assert_ok "Tugaskan petugas E2E ke Pusat"
api POST "/api/login" '{"username":"petugas_e2e","password":"Petugas#2024"}' ""
assert_ok "Login petugas E2E"
PTOK="$(jqget '.token')"
api POST "/api/change-password" '{"currentPassword":"Petugas#2024","newPassword":"Petugas#2025"}' "$PTOK"
assert_ok "Petugas E2E ganti password"
api POST "/api/login" '{"username":"petugas_e2e","password":"Petugas#2025"}' ""
assert_ok "Login petugas E2E baru"
PTOK="$(jqget '.token')"

api GET "/api/users"
assert_ok "Owner melihat daftar pengguna"
assert_jq "Daftar pengguna memuat cabang" '.items[] | select(.username=="admin_e2e") | .branch_names != null'

api POST "/api/users" '{"username":"admin_pusat","password":"Admin#2024","fullName":"Admin Pusat E2E","roleCode":"ADMIN"}'
assert_ok "Buat admin Pusat E2E"
ADMIN_PUSAT_ID="$(jqget '.id')"
api PUT "/api/users/$ADMIN_PUSAT_ID/branches" "{\"branchIds\":[$PUSAT_ID]}"
assert_ok "Tugaskan admin Pusat ke Pusat"
api POST "/api/login" '{"username":"admin_pusat","password":"Admin#2024"}' ""
assert_ok "Login admin Pusat"
APTOK="$(jqget '.token')"
api POST "/api/change-password" '{"currentPassword":"Admin#2024","newPassword":"Admin#2025"}' "$APTOK"
assert_ok "Admin Pusat ganti password"
api POST "/api/login" '{"username":"admin_pusat","password":"Admin#2025"}' ""
assert_ok "Login admin Pusat baru"
APTOK="$(jqget '.token')"

api GET "/api/users/permissions"
assert_ok "Katalog permission"
assert_jq "Permission branch.manage ada" '.permissions[] | select(.code=="branch.manage")'

# Login admin dengan password wajib diganti
api POST "/api/login" '{"username":"admin_e2e","password":"Admin#2024"}' ""
assert_ok "Login admin E2E"
ATOK="$(jqget '.token')"
assert_jq "Admin wajib ganti password" '.user.mustChangePassword == true'
api GET "/api/pens" "" "$ATOK"
assert_error "Endpoint diblokir sebelum ganti password" "mengganti password"
api POST "/api/change-password" '{"currentPassword":"Admin#2024","newPassword":"Admin#2025"}' "$ATOK"
assert_ok "Admin ganti password"
api POST "/api/login" '{"username":"admin_e2e","password":"Admin#2025"}' ""
assert_ok "Login admin dengan password baru"
ATOK="$(jqget '.token')"
api GET "/api/users" "" "$ATOK"
assert_error "Admin tidak boleh mengelola pengguna" "tidak memiliki izin"

# ---------------------------------------------------------------- parties
section "SUPPLIER DAN PELANGGAN"
api POST "/api/parties" '{"code":"SUP-E2E","name":"Supplier E2E","phone":"0811","address":"Bandung","roles":["SUPPLIER"]}'
assert_ok "Buat supplier"
SUPPLIER_ID="$(jqget '.id')"
api POST "/api/parties" '{"code":"CUS-E2E","name":"Pelanggan E2E","phone":"0812","address":"Jakarta","roles":["CUSTOMER"]}'
assert_ok "Buat pelanggan"
CUSTOMER_ID="$(jqget '.id')"
api PUT "/api/parties/$SUPPLIER_ID" '{"name":"Supplier E2E Baru","phone":"0813","address":"Bandung","active":true,"notes":"uji","roles":["SUPPLIER"]}'
assert_ok "Ubah supplier"
api GET "/api/parties?role=SUPPLIER"
assert_ok "Daftar supplier"
assert_jq "Supplier baru terdaftar" '.items[] | select(.id=='"$SUPPLIER_ID"')'

# ---------------------------------------------------------------- pens
section "KANDANG"
api POST "/api/pens" "{\"code\":\"E2E-P1\",\"name\":\"Kandang Pusat E2E\",\"capacity\":10,\"branchId\":$PUSAT_ID}"
assert_ok "Tambah kandang Pusat"
PEN_PUSAT="$(jqget '.id')"
api POST "/api/pens" "{\"code\":\"E2E-C1\",\"name\":\"Kandang Cikarang E2E\",\"capacity\":10,\"branchId\":$CIKARANG_ID}"
assert_ok "Tambah kandang Cikarang"
PEN_CIKARANG="$(jqget '.id')"
api POST "/api/pens" "{\"code\":\"E2E-B1\",\"name\":\"Kandang Bandung E2E\",\"capacity\":10,\"branchId\":$BANDUNG_ID}"
assert_ok "Tambah kandang Bandung"
PEN_BANDUNG="$(jqget '.id')"
api POST "/api/pens" "{\"code\":\"E2E-P1\",\"name\":\"Duplikat\",\"capacity\":5,\"branchId\":$PUSAT_ID}"
assert_error "Kode kandang duplikat dalam cabang ditolak" "Gagal menyimpan"
api POST "/api/pens" "{\"code\":\"E2E-P1\",\"name\":\"Kandang Cikarang Sama\",\"capacity\":5,\"branchId\":$CIKARANG_ID}"
assert_ok "Kode kandang sama boleh di cabang berbeda"
PEN_CIKARANG2="$(jqget '.id')"

api GET "/api/pens?branchId=$PUSAT_ID"
assert_ok "Daftar kandang Pusat"
assert_jq "Kandang Pusat terfilter" 'all(.items[]; .branch_id=='"$PUSAT_ID"')'
api GET "/api/pens/$PEN_PUSAT"
assert_ok "Detail kandang"
assert_jq "Detail kandang memuat daftar kambing" '.goats != null'

# Isolasi admin
api GET "/api/pens" "" "$ATOK"
assert_ok "Admin melihat kandang"
assert_jq "Admin hanya melihat Bandung" 'all(.items[]; .branch_id=='"$BANDUNG_ID"')'
api GET "/api/pens?branchId=$PUSAT_ID" "" "$ATOK"
assert_error "Admin tidak boleh filter cabang lain" "tidak memiliki akses"
api GET "/api/pens/$PEN_PUSAT" "" "$ATOK"
assert_error "Admin tidak boleh detail kandang cabang lain" "tidak memiliki akses"

# ---------------------------------------------------------------- purchase & receipt
section "PEMBELIAN DAN PENERIMAAN"
api POST "/api/purchases" "{\"supplierId\":$SUPPLIER_ID,\"purchaseDate\":\"$TODAY\",\"branchId\":$PUSAT_ID,\"notes\":\"E2E\",\"items\":[{\"description\":\"Kambing E2E\",\"qty\":3,\"unitPrice\":\"2000000\"}]}"
assert_ok "Buat pembelian 3 ekor"
PURCHASE1="$(jqget '.id')"
api POST "/api/purchases/$PURCHASE1/confirm" '{}'
assert_ok "Konfirmasi pembelian"
api GET "/api/purchases/$PURCHASE1"
assert_ok "Detail pembelian"
assert_jq "Status pembelian dikonfirmasi" '.status=="DIKONFIRMASI"'
ITEM1="$(jqget '.items[0].id')"

api POST "/api/receipts" "{\"purchaseId\":$PURCHASE1,\"receiptDate\":\"$TODAY\",\"shippingCost\":\"50000\",\"requestId\":\"$(uuidgen)\",\"items\":[{\"purchaseItemId\":$ITEM1,\"goatCode\":\"E2E-G1\",\"weight\":\"30\",\"penId\":$PEN_PUSAT,\"gender\":\"JANTAN\",\"jenis\":\"Kambing\",\"condition\":\"SEHAT\"}]}"
assert_ok "Terima sebagian 1 ekor"
RECEIPT1="$(jqget '.id')"
api GET "/api/purchases/$PURCHASE1"
assert_jq "Pembelian menjadi diterima sebagian" '.status=="DITERIMA_SEBAGIAN"'
assert_eq "Nilai perolehan termasuk kirim" "2050000.00" "$(jqget '.received_value')"
assert_eq "Sisa utang setelah kirim" "2000000.00" "$(jqget '.remaining_payable')"

api POST "/api/receipts" "{\"purchaseId\":$PURCHASE1,\"receiptDate\":\"$TODAY\",\"requestId\":\"$(uuidgen)\",\"items\":[{\"purchaseItemId\":$ITEM1,\"goatCode\":\"E2E-X\",\"weight\":\"30\",\"penId\":$PEN_PUSAT,\"gender\":\"JANTAN\",\"condition\":\"SEHAT\"},{\"purchaseItemId\":$ITEM1,\"goatCode\":\"E2E-Y\",\"weight\":\"30\",\"penId\":$PEN_PUSAT,\"gender\":\"JANTAN\",\"condition\":\"SEHAT\"},{\"purchaseItemId\":$ITEM1,\"goatCode\":\"E2E-Z\",\"weight\":\"30\",\"penId\":$PEN_PUSAT,\"gender\":\"JANTAN\",\"condition\":\"SEHAT\"}]}"
assert_error "Terima melebihi pesanan ditolak" "melebihi pesanan"

api POST "/api/receipts" "{\"purchaseId\":$PURCHASE1,\"receiptDate\":\"$TODAY\",\"requestId\":\"$(uuidgen)\",\"items\":[{\"purchaseItemId\":$ITEM1,\"goatCode\":\"E2E-GX\",\"weight\":\"30\",\"penId\":$PEN_CIKARANG,\"gender\":\"JANTAN\",\"condition\":\"SEHAT\"}]}"
assert_error "Kandang beda cabang ditolak" "harus berada di cabang pembelian"

api POST "/api/payments" "{\"sourceType\":\"ADVANCE_SUPPLIER\",\"partyId\":$SUPPLIER_ID,\"paymentDate\":\"$TODAY\",\"amount\":\"500000\",\"method\":\"TUNAI\",\"branchId\":$PUSAT_ID,\"requestId\":\"$(uuidgen)\"}"
assert_ok "Uang muka supplier"
ADV_SUP_PAY="$(jqget '.id')"

api POST "/api/receipts" "{\"purchaseId\":$PURCHASE1,\"receiptDate\":\"$TODAY\",\"requestId\":\"$(uuidgen)\",\"items\":[{\"purchaseItemId\":$ITEM1,\"goatCode\":\"E2E-BAD\",\"weight\":\"29\",\"penId\":$PEN_PUSAT,\"gender\":\"JANTAN\",\"jenis\":\"Boer\",\"condition\":\"SEHAT\"}]}"
assert_error "Jenis ternak selain Kambing/Domba ditolak" "Jenis ternak harus Kambing atau Domba"
api POST "/api/receipts" "{\"purchaseId\":$PURCHASE1,\"receiptDate\":\"$TODAY\",\"requestId\":\"$(uuidgen)\",\"items\":[{\"purchaseItemId\":$ITEM1,\"goatCode\":\"E2E-G2\",\"weight\":\"29\",\"penId\":$PEN_PUSAT,\"gender\":\"JANTAN\",\"jenis\":\"Domba\",\"condition\":\"SEHAT\"},{\"purchaseItemId\":$ITEM1,\"goatCode\":\"E2E-G3\",\"weight\":\"31\",\"penId\":$PEN_PUSAT,\"gender\":\"BETINA\",\"jenis\":\"Kambing\",\"condition\":\"SEHAT\"}]}"
assert_ok "Terima sisa 2 ekor"
assert_eq "Kambing Domba tersimpan" "Domba" "$(sqlq "SELECT jenis FROM goats WHERE code='E2E-G2'")"
G1="$(sqlq "SELECT id FROM goats WHERE code='E2E-G1'")"
G2="$(sqlq "SELECT id FROM goats WHERE code='E2E-G2'")"
G3="$(sqlq "SELECT id FROM goats WHERE code='E2E-G3'")"

api GET "/api/purchases/$PURCHASE1"
assert_jq "Pembelian selesai diterima" '.status=="SELESAI"'

api GET "/api/payments/payables?branchId=$PUSAT_ID"
assert_ok "Daftar utang supplier"
assert_jq "Utang pembelian terdaftar" '.items[] | select(.doc_no != null)'

api POST "/api/payments" "{\"sourceType\":\"PURCHASE\",\"sourceId\":$PURCHASE1,\"paymentDate\":\"$TODAY\",\"amount\":\"9999999\",\"method\":\"TUNAI\",\"requestId\":\"$(uuidgen)\"}"
assert_error "Pembayaran utang melebihi sisa ditolak" "melebihi sisa utang"
api POST "/api/payments" "{\"sourceType\":\"PURCHASE\",\"sourceId\":$PURCHASE1,\"paymentDate\":\"$TODAY\",\"amount\":\"1500000\",\"method\":\"TUNAI\",\"requestId\":\"$(uuidgen)\"}"
assert_ok "Bayar sisa utang"

# close remaining & cancel
api POST "/api/purchases" "{\"supplierId\":$SUPPLIER_ID,\"purchaseDate\":\"$TODAY\",\"branchId\":$PUSAT_ID,\"items\":[{\"description\":\"Kambing tutup\",\"qty\":2,\"unitPrice\":\"1000000\"}]}"
PURCHASE2="$(jqget '.id')"
api POST "/api/purchases/$PURCHASE2/confirm" '{}'
assert_ok "Konfirmasi pembelian tutup sisa"
api POST "/api/purchases/$PURCHASE2/close-remaining" '{"reason":"Uji tutup sisa","requestId":"'"$(uuidgen)"'"}'
assert_ok "Tutup sisa pembelian"
api GET "/api/purchases/$PURCHASE2"
assert_jq "Status tutup sisa" '.status=="SELESAI"'

api POST "/api/purchases" "{\"supplierId\":$SUPPLIER_ID,\"purchaseDate\":\"$TODAY\",\"branchId\":$PUSAT_ID,\"items\":[{\"description\":\"Kambing batal\",\"qty\":1,\"unitPrice\":\"500000\"}]}"
PURCHASE3="$(jqget '.id')"
api POST "/api/purchases/$PURCHASE3/cancel" '{"reason":"Uji batal","requestId":"'"$(uuidgen)"'"}'
assert_ok "Batalkan pembelian draft"
api GET "/api/purchases/$PURCHASE3"
assert_jq "Status pembelian dibatalkan" '.status=="DIBATALKAN"'

# Kambing mati: retur, tidak masuk persediaan/utang, menutup pesanan.
api POST "/api/purchases" "{\"supplierId\":$SUPPLIER_ID,\"purchaseDate\":\"$TODAY\",\"branchId\":$PUSAT_ID,\"items\":[{\"description\":\"Kambing mati\",\"qty\":1,\"unitPrice\":\"1500000\"}]}"
assert_ok "Buat pembelian kambing mati"
PURCHASE_DEAD="$(jqget '.id')"
api POST "/api/purchases/$PURCHASE_DEAD/confirm" '{}'
assert_ok "Konfirmasi pembelian kambing mati"
api GET "/api/purchases/$PURCHASE_DEAD"
ITEM_DEAD="$(jqget '.items[0].id')"
api POST "/api/receipts" "{\"purchaseId\":$PURCHASE_DEAD,\"receiptDate\":\"$TODAY\",\"requestId\":\"$(uuidgen)\",\"items\":[{\"purchaseItemId\":$ITEM_DEAD,\"status\":\"MATI\"}]}"
assert_ok "Terima kambing mati"
assert_jq "Kambing mati dihitung retur" '.rejectedCount == 1'
assert_jq "Kambing mati tidak masuk persediaan" '.inventoryTotal == 0'
api GET "/api/purchases/$PURCHASE_DEAD"
assert_jq "Pembelian mati selesai" '.status=="SELESAI"'
assert_eq "Nilai diterima kambing mati nol" "0.00" "$(jqget '.received_value')"
assert_eq "Utang kambing mati nol" "0.00" "$(jqget '.remaining_payable')"
assert_eq "Rejected qty tercatat" "1" "$(sqlq "SELECT rejected_qty FROM purchase_items WHERE id=$ITEM_DEAD")"
assert_eq "Tidak ada kambing dibuat untuk status mati" "1" "$(sqlq "SELECT COUNT(*) FROM receipt_items WHERE purchase_item_id=$ITEM_DEAD AND status='MATI' AND goat_id IS NULL")"

# admin default branch
api POST "/api/purchases" "{\"supplierId\":$SUPPLIER_ID,\"purchaseDate\":\"$TODAY\",\"items\":[{\"description\":\"Kambing Bandung\",\"qty\":1,\"unitPrice\":\"1500000\"}]}" "$ATOK"
assert_ok "Admin membuat pembelian tanpa branchId"
PURCHASE_BANDUNG="$(jqget '.id')"
api GET "/api/purchases/$PURCHASE_BANDUNG" "" "$ATOK"
assert_jq "Pembelian admin masuk Bandung" ".branch_id==$BANDUNG_ID"
api GET "/api/purchases/$PURCHASE1" "" "$ATOK"
assert_error "Admin tidak boleh detail pembelian Pusat" "tidak memiliki akses"

# ---------------------------------------------------------------- goats
section "KAMBING, BERAT, KESEHATAN, KANDANG"
api GET "/api/goats?branchId=$PUSAT_ID"
assert_ok "Daftar kambing Pusat"
assert_jq "Kambing Pusat terfilter" 'all(.items[]; .branch_name=="Pusat")'
api GET "/api/goats/$G1"
assert_ok "Detail kambing"
assert_jq "Detail kambing memuat riwayat" '.history != null'
api POST "/api/goats/$G1/weights" '{"measuredAt":"'"$TODAY"'","weight":"32","notes":"E2E"}'
assert_ok "Catat berat kambing"
api POST "/api/goats/$G1/health" '{"occurredAt":"'"$TODAY"'","complaint":"E2E","examination":"E2E","action":"Observasi","healthStatus":"SEHAT","responsible":"E2E"}'
assert_ok "Catat kesehatan kambing"
api POST "/api/goats/$G1/readiness" '{"readiness":"SIAP_JUAL"}'
assert_ok "Tandai siap jual"
api POST "/api/goats/$G2/readiness" '{"readiness":"SIAP_JUAL"}'
assert_ok "Tandai kambing kedua siap jual"
api POST "/api/goats/$G3/readiness" '{"readiness":"SIAP_JUAL"}'
assert_ok "Tandai kambing ketiga siap jual"

api POST "/api/goats/$G1/move" "{\"penId\":$PEN_CIKARANG,\"date\":\"$TODAY\",\"reason\":\"E2E\"}"
assert_error "Pindah kandang lintas cabang ditolak" "antar cabang"
api POST "/api/goats/$G1/move" "{\"penId\":$PEN_PUSAT,\"date\":\"$TODAY\",\"reason\":\"E2E\"}"
assert_error "Pindah ke kandang yang sama ditolak" "sudah berada"
api GET "/api/goats/$G1" "" "$ATOK"
assert_error "Admin tidak boleh detail kambing Pusat" "tidak memiliki akses"

# ---------------------------------------------------------------- sales
section "PENJUALAN KAMBING"
api GET "/api/goats/sellable?branchId=$PUSAT_ID"
assert_ok "Daftar kambing siap jual"
assert_jq "Kambing siap jual terdaftar" '.items[] | select(.code=="E2E-G1")'

api POST "/api/sales" "{\"customerId\":$CUSTOMER_ID,\"saleDate\":\"$TODAY\",\"branchId\":$PUSAT_ID,\"items\":[{\"goatId\":$G1,\"method\":\"PER_EKOR\",\"rate\":\"3500000\"}]}"
assert_ok "Draft penjualan per ekor"
SALE1="$(jqget '.id')"
api POST "/api/sales/$SALE1/confirm" '{"requestId":"'"$(uuidgen)"'"}'
assert_ok "Konfirmasi penjualan per ekor"
api GET "/api/sales/$SALE1"
assert_jq "Penjualan terkonfirmasi" '.status=="DIKONFIRMASI"'
assert_jq "HPP penjualan terbentuk" '.total_hpp > 0'

download "/api/reports/invoice_penjualan/file?format=pdf&id=$SALE1" "$PDF_FILE"
assert_eq "Invoice PDF dihasilkan" "200" "$CODE"
assert_eq "Invoice berawalan PDF" "%PDF" "$(head -c 4 "$PDF_FILE")"

api POST "/api/payments" "{\"sourceType\":\"SALE\",\"sourceId\":$SALE1,\"paymentDate\":\"$TODAY\",\"amount\":\"1000000\",\"method\":\"TUNAI\",\"requestId\":\"$(uuidgen)\"}"
assert_ok "Pembayaran sebagian penjualan"
PAY1="$(jqget '.id')"
api POST "/api/payments" "{\"sourceType\":\"SALE\",\"sourceId\":$SALE1,\"paymentDate\":\"$TODAY\",\"amount\":\"9999999\",\"method\":\"TUNAI\",\"requestId\":\"$(uuidgen)\"}"
assert_error "Pembayaran melebihi tagihan ditolak" "melebihi sisa tagihan"
api GET "/api/sales/$SALE1"
assert_jq "Pembayaran sebagian tercatat" '.payment_status=="SEBAGIAN"'
api POST "/api/payments/$PAY1/reverse" '{"reason":"Uji reverse","requestId":"'"$(uuidgen)"'"}'
assert_ok "Reverse pembayaran penjualan"
api GET "/api/sales/$SALE1"
assert_jq "Status pembayaran kembali belum bayar" '.payment_status=="BELUM_BAYAR"'

# per kg
api POST "/api/sales" "{\"customerId\":$CUSTOMER_ID,\"saleDate\":\"$TODAY\",\"branchId\":$PUSAT_ID,\"items\":[{\"goatId\":$G2,\"method\":\"PER_KG\",\"rate\":\"110000\",\"weight\":\"30\"}]}"
assert_ok "Draft penjualan per kg"
SALE2="$(jqget '.id')"
api POST "/api/sales/$SALE2/confirm" '{"requestId":"'"$(uuidgen)"'"}'
assert_ok "Konfirmasi penjualan per kg"
api POST "/api/sales/$SALE2/cancel" '{"reason":"Uji batal jual","requestId":"'"$(uuidgen)"'"}'
assert_ok "Batalkan penjualan terkonfirmasi"
api GET "/api/goats/$G2"
assert_jq "Kambing kembali aktif setelah batal" '.status=="AKTIF"'

# below HPP + approval (siapkan ulang setelah pembatalan penjualan)
api POST "/api/goats/$G2/readiness" '{"readiness":"SIAP_JUAL"}'
assert_ok "Kambing siap jual ulang setelah pembatalan"
api POST "/api/sales" "{\"customerId\":$CUSTOMER_ID,\"saleDate\":\"$TODAY\",\"branchId\":$PUSAT_ID,\"items\":[{\"goatId\":$G2,\"method\":\"PER_EKOR\",\"rate\":\"1000000\"}]}" "$APTOK"
assert_ok "Admin membuat draft penjualan di bawah HPP"
SALE3="$(jqget '.id')"
api POST "/api/sales/$SALE3/confirm" '{"requestId":"'"$(uuidgen)"'"}' "$APTOK"
assert_error "Penjualan di bawah HPP admin butuh persetujuan" "memerlukan persetujuan owner"
api POST "/api/sales/$SALE3/request-approval" '{"reason":"Uji approval"}' "$APTOK"
assert_ok "Admin mengajukan persetujuan harga"
api POST "/api/sales/$SALE3/approve" '{"reason":"Disetujui E2E"}'
assert_ok "Owner menyetujui harga"
api POST "/api/sales/$SALE3/confirm" '{"requestId":"'"$(uuidgen)"'"}' "$APTOK"
assert_ok "Konfirmasi setelah persetujuan"
assert_jq "Hasil konfirmasi menandai approval" '.approvalRequired == true'
api GET "/api/sales/$SALE3" "" "$TOK"
assert_jq "Penjualan di bawah HPP terkonfirmasi" '.status=="DIKONFIRMASI"' 

# cross branch sale
api POST "/api/sales" "{\"customerId\":$CUSTOMER_ID,\"saleDate\":\"$TODAY\",\"branchId\":$CIKARANG_ID,\"items\":[{\"goatId\":$G3,\"method\":\"PER_EKOR\",\"rate\":\"2000000\"}]}"
assert_error "Jual kambing Pusat lewat cabang Cikarang ditolak" "berada di cabang lain"

# ---------------------------------------------------------------- payments advance/refund
section "UANG MUKA DAN REFUND"
api POST "/api/payments" "{\"sourceType\":\"ADVANCE_CUSTOMER\",\"partyId\":$CUSTOMER_ID,\"paymentDate\":\"$TODAY\",\"amount\":\"300000\",\"method\":\"TUNAI\",\"branchId\":$PUSAT_ID,\"requestId\":\"$(uuidgen)\"}"
assert_ok "Uang muka pelanggan"
ADV_CUST="$(jqget '.id')"
api POST "/api/payments" "{\"sourceType\":\"REFUND\",\"partyId\":$CUSTOMER_ID,\"paymentDate\":\"$TODAY\",\"amount\":\"100000\",\"method\":\"TUNAI\",\"branchId\":$PUSAT_ID,\"refundType\":\"CUSTOMER\",\"requestId\":\"$(uuidgen)\"}"
assert_ok "Refund pelanggan"

# ---------------------------------------------------------------- feed
section "PAKAN"
api POST "/api/feed/items" "{\"code\":\"E2E-F1\",\"name\":\"Pakan E2E\",\"unit\":\"kg\",\"minStock\":\"10\",\"branchId\":$PUSAT_ID}"
assert_ok "Tambah item pakan Pusat"
FEED_PUSAT="$(jqget '.id')"
api POST "/api/feed/items" "{\"code\":\"E2E-F1\",\"name\":\"Pakan E2E Cikarang\",\"unit\":\"kg\",\"minStock\":\"10\",\"branchId\":$CIKARANG_ID}"
assert_ok "Kode pakan sama di cabang lain boleh"
FEED_CIKARANG="$(jqget '.id')"
api POST "/api/feed/items" "{\"code\":\"E2E-F1\",\"name\":\"Duplikat\",\"unit\":\"kg\",\"minStock\":\"10\",\"branchId\":$PUSAT_ID}"
assert_error "Kode pakan duplikat dalam cabang ditolak" "Gagal menyimpan"

api POST "/api/feed/receive" "{\"feedItemId\":$FEED_PUSAT,\"movementDate\":\"$TODAY\",\"qty\":\"100\",\"unitCost\":\"10000\",\"requestId\":\"$(uuidgen)\"}"
assert_ok "Penerimaan pakan"
api POST "/api/feed/usage" "{\"feedItemId\":$FEED_PUSAT,\"movementDate\":\"$TODAY\",\"qty\":\"20\",\"goatId\":$G3,\"notes\":\"E2E\",\"requestId\":\"$(uuidgen)\"}"
assert_ok "Pemakaian pakan"
api GET "/api/feed/items?branchId=$PUSAT_ID"
assert_ok "Daftar pakan Pusat"
assert_jq "Stok pakan berkurang" '.items[] | select(.id=='"$FEED_PUSAT"') | .stock_qty == 80'
api GET "/api/feed/movements?branchId=$PUSAT_ID"
assert_ok "Mutasi pakan"
api GET "/api/feed/stock-value?branchId=$PUSAT_ID"
assert_ok "Nilai stok pakan"
api POST "/api/feed/usage" "{\"feedItemId\":$FEED_CIKARANG,\"movementDate\":\"$TODAY\",\"qty\":\"5\",\"goatId\":$G3,\"requestId\":\"$(uuidgen)\"}"
assert_error "Pakan cabang lain untuk kambing Pusat ditolak" "cabang lain"
api GET "/api/feed/items" "" "$ATOK"
assert_ok "Admin melihat pakan"
assert_jq "Admin hanya melihat pakan Bandung" 'all(.items[]; .branch_id=='"$BANDUNG_ID"')'

# ---------------------------------------------------------------- expenses & allocation
section "PENGELUARAN DAN ALOKASI"
api GET "/api/expenses/categories"
assert_ok "Kategori pengeluaran"
LISTRIK_CAT="$(jqget '.items[] | select(.code=="LISTRIK") | .id')"
[ -n "$LISTRIK_CAT" ] || LISTRIK_CAT=1
api POST "/api/expenses" "{\"categoryId\":$LISTRIK_CAT,\"expenseDate\":\"$TODAY\",\"amount\":\"300000\",\"branchId\":$PUSAT_ID,\"purpose\":\"E2E operasional\"}"
assert_ok "Buat pengeluaran"
EXPENSE1="$(jqget '.id')"
api POST "/api/expenses/$EXPENSE1/post" '{"requestId":"'"$(uuidgen)"'"}'
assert_ok "Posting pengeluaran"
api POST "/api/expenses/$EXPENSE1/reverse" '{"reason":"Uji reverse biaya","requestId":"'"$(uuidgen)"'"}'
assert_ok "Reverse pengeluaran"
api POST "/api/expenses" "{\"categoryId\":$LISTRIK_CAT,\"expenseDate\":\"$TODAY\",\"amount\":\"200000\",\"branchId\":$PUSAT_ID,\"purpose\":\"E2E alokasi\",\"allocatable\":true,\"serviceStart\":\"$FROM\",\"serviceEnd\":\"$TODAY\",\"scopePenId\":$PEN_PUSAT}"
assert_ok "Buat biaya bersama dialokasikan"
EXPENSE2="$(jqget '.id')"
api POST "/api/expenses/$EXPENSE2/post" '{"requestId":"'"$(uuidgen)"'"}'
assert_ok "Posting biaya bersama"
api GET "/api/expenses/postable?branchId=$PUSAT_ID"
assert_ok "Daftar pengeluaran draft"
api GET "/api/allocations/pending?branchId=$PUSAT_ID"
assert_ok "Daftar alokasi menunggu"
COST_SOURCE="$(jqget '.items[0].id')"
if [ -n "$COST_SOURCE" ] && [ "$COST_SOURCE" != "null" ]; then
  api POST "/api/allocations/cost-sources/$COST_SOURCE/preview" '{}'
  assert_ok "Pratinjau alokasi"
  api POST "/api/allocations/cost-sources/$COST_SOURCE/finalize" '{}'
  assert_ok "Finalisasi alokasi"
else
  check fail "Sumber alokasi tersedia" "tidak ada cost source"
fi
api POST "/api/expenses" "{\"categoryId\":$LISTRIK_CAT,\"expenseDate\":\"$TODAY\",\"amount\":\"50000\",\"branchId\":$PUSAT_ID,\"purpose\":\"Salah cabang\",\"scopePenId\":$PEN_CIKARANG}"
assert_error "Pengeluaran dengan kandang cabang lain ditolak" "cabang lain"

# ---------------------------------------------------------------- slaughter & meat
section "PEMOTONGAN DAN DAGING"
# siapkan kambing untuk dipotong
api POST "/api/purchases" "{\"supplierId\":$SUPPLIER_ID,\"purchaseDate\":\"$TODAY\",\"branchId\":$PUSAT_ID,\"items\":[{\"description\":\"Kambing potong\",\"qty\":1,\"unitPrice\":\"2100000\"}]}"
PURCHASE4="$(jqget '.id')"
api POST "/api/purchases/$PURCHASE4/confirm" '{}'
api GET "/api/purchases/$PURCHASE4"
ITEM4="$(jqget '.items[0].id')"
api POST "/api/receipts" "{\"purchaseId\":$PURCHASE4,\"receiptDate\":\"$TODAY\",\"requestId\":\"$(uuidgen)\",\"items\":[{\"purchaseItemId\":$ITEM4,\"goatCode\":\"E2E-G4\",\"weight\":\"30\",\"penId\":$PEN_PUSAT,\"gender\":\"JANTAN\",\"condition\":\"SEHAT\"}]}"
assert_ok "Terima kambing untuk dipotong"
G4="$(sqlq "SELECT id FROM goats WHERE code='E2E-G4'")"
api POST "/api/goats/$G4/readiness" '{"readiness":"SIAP_JUAL"}'
assert_ok "Kambing potong siap"
api POST "/api/slaughter" "{\"goatId\":$G4,\"slaughterDate\":\"$TODAY\",\"liveWeight\":\"30\",\"carcassWeight\":\"18\",\"meatWeight\":\"10\",\"boneWeight\":\"4\",\"processingCost\":\"100000\"}"
assert_error "Berat daging+tulang tidak cocok ditolak" "harus sama"
api POST "/api/slaughter" "{\"goatId\":$G4,\"slaughterDate\":\"$TODAY\",\"liveWeight\":\"30\",\"carcassWeight\":\"18\",\"meatWeight\":\"13\",\"boneWeight\":\"5\",\"processingCost\":\"100000\"}"
assert_ok "Draft pemotongan valid"
SLAUGHTER="$(jqget '.id')"
api POST "/api/slaughter/$SLAUGHTER/post" '{"requestId":"'"$(uuidgen)"'"}'
assert_ok "Posting pemotongan"
BATCH="$(jqget '.batchId')"
api GET "/api/meat/batches?branchId=$PUSAT_ID&open=all"
assert_ok "Daftar batch daging"
assert_jq "Batch daging terbentuk" '.items[] | select(.id=='"$BATCH"')'
api PUT "/api/settings" '{"meat.price_per_kg":"125000"}'
assert_ok "Atur harga daging"
api POST "/api/sales" "{\"customerId\":$CUSTOMER_ID,\"saleDate\":\"$TODAY\",\"branchId\":$PUSAT_ID,\"items\":[{\"itemType\":\"DAGING\",\"meatBatchId\":$BATCH,\"weight\":\"4\"}]}"
assert_ok "Draft penjualan daging"
SALE_MEAT="$(jqget '.id')"
api POST "/api/sales/$SALE_MEAT/confirm" '{"requestId":"'"$(uuidgen)"'"}' 
assert_ok "Konfirmasi penjualan daging"
api POST "/api/meat/waste" "{\"batchId\":$BATCH,\"movementDate\":\"$TODAY\",\"qty\":\"1\",\"requestId\":\"$(uuidgen)\"}"
assert_ok "Catat susut daging"
api POST "/api/slaughter/$SLAUGHTER/cancel" '{"reason":"Uji batal potong","requestId":"'"$(uuidgen)"'"}'
assert_error "Batal potong setelah daging terjual ditolak" "sudah memiliki"


# ---------------------------------------------------------------- role matrix
section "MATRIKS PERAN"
# Petugas: operasional baca/timbang/kesehatan/pakan, tanpa transaksi/keuangan.
api GET "/api/dashboard" "" "$PTOK"
assert_ok "Petugas boleh dashboard"
api GET "/api/goats" "" "$PTOK"
assert_ok "Petugas boleh daftar kambing"
api GET "/api/pens" "" "$PTOK"
assert_ok "Petugas boleh daftar kandang"
api GET "/api/feed/items" "" "$PTOK"
assert_ok "Petugas boleh daftar pakan"
api GET "/api/slaughter" "" "$PTOK"
assert_ok "Petugas boleh melihat pemotongan (meat.view)"
api GET "/api/meat/batches?open=all" "" "$PTOK"
assert_ok "Petugas boleh melihat batch daging"
api GET "/api/purchases" "" "$PTOK"
assert_error "Petugas tidak boleh pembelian" "tidak memiliki izin"
api GET "/api/sales" "" "$PTOK"
assert_error "Petugas tidak boleh penjualan" "tidak memiliki izin"
api GET "/api/payments" "" "$PTOK"
assert_error "Petugas tidak boleh pembayaran" "tidak memiliki izin"
api GET "/api/payments/receivables" "" "$PTOK"
assert_error "Petugas tidak boleh piutang" "tidak memiliki izin"
api GET "/api/payments/payables" "" "$PTOK"
assert_error "Petugas tidak boleh utang" "tidak memiliki izin"
api GET "/api/expenses" "" "$PTOK"
assert_error "Petugas tidak boleh pengeluaran" "tidak memiliki izin"
api GET "/api/reports/inventory" "" "$PTOK"
assert_error "Petugas tidak boleh laporan keuangan" "hanya dapat diakses owner"
api GET "/api/goats?branchId=$CIKARANG_ID" "" "$PTOK"
assert_error "Petugas tidak boleh cabang lain" "tidak memiliki akses"
api POST "/api/goats/$G3/weights" '{"measuredAt":"'"$TODAY"'","weight":"32"}' "$PTOK"
assert_ok "Petugas boleh menimbang"
api POST "/api/goats/$G3/health" '{"occurredAt":"'"$TODAY"'","complaint":"E2E role","action":"Observasi","healthStatus":"SEHAT"}' "$PTOK"
assert_ok "Petugas boleh catatan kesehatan"
api POST "/api/goats/$G3/readiness" '{"readiness":"SIAP_JUAL"}' "$PTOK"
assert_error "Petugas tidak boleh ubah kelayakan jual" "tidak memiliki izin"
api POST "/api/feed/usage" "{\"feedItemId\":$FEED_PUSAT,\"movementDate\":\"$TODAY\",\"qty\":\"1\",\"goatId\":$G3,\"requestId\":\"$(uuidgen)\"}" "$PTOK"
assert_ok "Petugas boleh mencatat pemakaian pakan"
api POST "/api/feed/receive" "{\"feedItemId\":$FEED_PUSAT,\"movementDate\":\"$TODAY\",\"qty\":\"5\",\"unitCost\":\"10000\",\"requestId\":\"$(uuidgen)\"}" "$PTOK"
assert_error "Petugas tidak boleh penerimaan pakan" "tidak memiliki izin"
api POST "/api/slaughter" "{\"goatId\":$G3,\"slaughterDate\":\"$TODAY\",\"liveWeight\":\"30\",\"carcassWeight\":\"18\",\"meatWeight\":\"13\",\"boneWeight\":\"5\"}" "$PTOK"
assert_error "Petugas tidak boleh membuat pemotongan" "tidak memiliki izin"
download "/api/reports/rekap_operasional/file?format=csv&from=$FROM&to=$TODAY" "$PDF_FILE" "$PTOK"
assert_eq "Petugas boleh rekap operasional CSV" "200" "$CODE"

# Admin: transaksi operasional, tanpa keuangan/pengguna/pengaturan.
api GET "/api/purchases" "" "$ATOK"
assert_ok "Admin boleh pembelian"
api GET "/api/sales" "" "$ATOK"
assert_ok "Admin boleh penjualan"
api GET "/api/payments" "" "$ATOK"
assert_ok "Admin boleh pembayaran"
api GET "/api/payments/receivables" "" "$ATOK"
assert_ok "Admin boleh piutang pelanggan"
api GET "/api/payments/payables" "" "$ATOK"
assert_ok "Admin boleh utang supplier"
api GET "/api/expenses" "" "$ATOK"
assert_ok "Admin boleh pengeluaran"
api GET "/api/slaughter" "" "$ATOK"
assert_ok "Admin boleh pemotongan"
api GET "/api/reports/inventory" "" "$ATOK"
assert_error "Admin tidak boleh laporan keuangan" "hanya dapat diakses owner"
api GET "/api/users" "" "$ATOK"
assert_error "Admin tidak boleh pengguna" "tidak memiliki izin"
api GET "/api/settings" "" "$ATOK"
assert_error "Admin tidak boleh pengaturan" "tidak memiliki izin"
api GET "/api/branches/all" "" "$ATOK"
assert_error "Admin tidak boleh kelola cabang" "tidak memiliki izin"
api GET "/api/audit" "" "$ATOK"
assert_error "Admin tidak boleh audit" "tidak memiliki izin"
api GET "/api/backups" "" "$ATOK"
assert_error "Admin tidak boleh backup" "tidak memiliki izin"
api POST "/api/users" '{"username":"x","password":"X#1234567","fullName":"X","roleCode":"PETUGAS"}' "$ATOK"
assert_error "Admin tidak boleh membuat pengguna" "tidak memiliki izin"

# ---------------------------------------------------------------- journals & reports
section "AKUNTANSI, PERIODE, DAN LAPORAN"
api GET "/api/journals?branchId=$PUSAT_ID"
assert_ok "Daftar jurnal Pusat"
assert_jq "Jurnal Pusat terfilter" 'all(.items[]; .branch_name=="Pusat")'
api GET "/api/journals/reconciliation?branchId=$PUSAT_ID"
assert_ok "Rekonsiliasi jurnal"
assert_jq "Jurnal seimbang" '.balanced == true'
api GET "/api/journals?branchId=$CIKARANG_ID"
assert_ok "Daftar jurnal Cikarang"

api GET "/api/periods"
assert_ok "Daftar periode"
api POST "/api/periods/close" "{\"periodCode\":\"$MONTH\",\"reason\":\"Uji tutup E2E\"}"
assert_ok "Tutup periode"
api POST "/api/periods/reopen" "{\"periodCode\":\"$MONTH\",\"reason\":\"Uji buka E2E\"}"
assert_ok "Buka periode"

api GET "/api/dashboard?from=$FROM&to=$TODAY"
assert_ok "Dashboard semua cabang"
assert_jq "Dashboard konsolidasi berisi kambing" '.activeGoats > 0'
api GET "/api/dashboard?from=$FROM&to=$TODAY&branchId=$PUSAT_ID"
assert_ok "Dashboard Pusat"
assert_jq "Dashboard Pusat terfilter" '.activeGoats >= 1'
api GET "/api/dashboard?from=$FROM&to=$TODAY&branchId=$BANDUNG_ID"
assert_ok "Dashboard Bandung"
assert_eq "Bandung belum punya kambing aktif" "0" "$(jqget '.activeGoats')"

for key in laporan_persediaan laba_rugi_per_kambing rekap_keuntungan_kerugian laba_rugi_periode utang_supplier piutang_pelanggan rekap_operasional; do
  download "/api/reports/$key/file?format=pdf&from=$FROM&to=$TODAY&branchId=$PUSAT_ID" "$PDF_FILE"
  assert_eq "PDF $key" "200" "$CODE"
  assert_eq "Signature PDF $key" "%PDF" "$(head -c 4 "$PDF_FILE")"
  download "/api/reports/$key/file?format=csv&from=$FROM&to=$TODAY&branchId=$PUSAT_ID" "$PDF_FILE"
  assert_eq "CSV $key" "200" "$CODE"
done
download "/api/reports/invoice_penjualan/file?format=pdf&id=$SALE1" "$PDF_FILE"
assert_eq "PDF invoice penjualan" "%PDF" "$(head -c 4 "$PDF_FILE")"
download "/api/reports/kuitansi_pembayaran/file?format=pdf&id=$ADV_CUST" "$PDF_FILE"
assert_eq "PDF kuitansi pembayaran" "%PDF" "$(head -c 4 "$PDF_FILE")"

# reports permission
api GET "/api/reports/inventory" "" "$ATOK"
assert_error "Admin tidak boleh laporan keuangan" "hanya dapat diakses owner"

# ---------------------------------------------------------------- settings, audit, backup
section "PENGATURAN, AUDIT, BACKUP"
api GET "/api/settings"
assert_ok "Owner membaca pengaturan"
api PUT "/api/settings" '{"company.phone":"021000"}'
assert_ok "Owner mengubah pengaturan"
api GET "/api/settings" "" "$ATOK"
assert_error "Admin tidak boleh pengaturan" "tidak memiliki izin"
api GET "/api/audit?module=PURCHASES"
assert_ok "Log aktivitas"
api GET "/api/audit/modules"
assert_ok "Modul audit"
api GET "/api/backups"
assert_ok "Daftar backup"
api POST "/api/backups" '{}'
assert_ok "Buat backup"
api GET "/api/backups"
assert_jq "Backup tercatat" '.items | length > 0'

# ---------------------------------------------------------------- idempotency & invariants
section "IDEMPOTENSI DAN INTEGRITAS DATA"
REQ_ID="e2e-idem-1"
api POST "/api/payments" "{\"sourceType\":\"SALE\",\"sourceId\":$SALE1,\"paymentDate\":\"$TODAY\",\"amount\":\"50000\",\"method\":\"TUNAI\",\"requestId\":\"e2e-idem-1\"}"
assert_ok "Pembayaran idempoten pertama"
IDEM_PAY="$(jqget '.id')"
REQ_ID="e2e-idem-1"
api POST "/api/payments" "{\"sourceType\":\"SALE\",\"sourceId\":$SALE1,\"paymentDate\":\"$TODAY\",\"amount\":\"50000\",\"method\":\"TUNAI\",\"requestId\":\"e2e-idem-1\"}"
assert_ok "Pembayaran idempoten kedua"
assert_eq "Pembayaran tidak ganda" "$IDEM_PAY" "$(jqget '.id')"

assert_eq "Jurnal seluruhnya seimbang" "1" "$(sqlq 'SELECT SUM(debit)=SUM(credit) FROM journal_lines')"
assert_eq "Tidak ada jurnal tidak seimbang" "0" "$(sqlq 'SELECT COUNT(*) FROM (SELECT entry_id FROM journal_lines GROUP BY entry_id HAVING SUM(debit)<>SUM(credit)) x')"
assert_eq "Tidak ada stok pakan negatif" "0" "$(sqlq 'SELECT COUNT(*) FROM feed_items WHERE stock_qty < 0')"
assert_eq "Tidak ada stok daging negatif" "0" "$(sqlq 'SELECT COUNT(*) FROM meat_batches WHERE qty_remaining_kg < 0')"
assert_eq "Setiap kambing punya cabang" "0" "$(sqlq 'SELECT COUNT(*) FROM goats WHERE branch_id IS NULL')"
assert_eq "Setiap jurnal punya cabang" "0" "$(sqlq 'SELECT COUNT(*) FROM journal_entries WHERE branch_id IS NULL')"

# ---------------------------------------------------------------- result
write_report
log ""
log "TOTAL=$TOTAL PASS=$PASS FAIL=$FAIL"
if [ "$FAIL" -ne 0 ]; then
  log "Kegagalan:"
  for f in "${FAILURES[@]}"; do log " - $f"; done
  exit 1
fi
exit 0
