#!/usr/bin/env bash
# Menyalakan instance MySQL dev terisolasi port 3307 untuk proyek ini.
# Root password = nilai db.password di service.properties (default file proyek).
set -euo pipefail
cd "$(dirname "$0")/.."
MYSQL_BIN=${MYSQL_BIN:-/opt/homebrew/opt/mysql@8.4/bin}
WORK=".mysql-test"

mkdir -p "$WORK"
if [ ! -f "$WORK/my.cnf" ]; then
  ROOT="$(pwd)"
  cat > "$WORK/my.cnf" <<EOF
[mysqld]
port=3307
socket=$ROOT/$WORK/mysql.sock
skip-networking=0
mysqlx=0
datadir=$ROOT/$WORK/data
log-error=$ROOT/$WORK/mysqld.err
pid-file=$ROOT/$WORK/mysqld.pid
EOF
fi

# Inisialisasi datadir bila belum ada.
if [ ! -d "$WORK/data/mysql" ]; then
  "$MYSQL_BIN/mysqld" --initialize-insecure --datadir="$PWD/$WORK/data"
fi

# Jalankan server kecuali sudah hidup.
if "$MYSQL_BIN/mysqladmin" -h127.0.0.1 -P3307 -uroot ping >/dev/null 2>&1; then
  echo "mysql dev sudah hidup (3307)."
elif "$MYSQL_BIN/mysqladmin" -h127.0.0.1 -P3307 -uroot -p'P@ssw0rd' ping 2>/dev/null; then
  echo "mysql dev sudah hidup (3307, password terpasang)."
else
  "$MYSQL_BIN/mysqld" --defaults-file="$PWD/$WORK/my.cnf" --user=$(whoami) >/dev/null 2>&1 &
  for _ in $(seq 1 30); do
    sleep 1
    "$MYSQL_BIN/mysqladmin" -h127.0.0.1 -P3307 -uroot ping >/dev/null 2>&1 && break
  done
fi

# Database aplikasi + password root mengikuti service.properties.
PASS_DB=$(awk -F= '$1=="db.password"{print $2}' service.properties | tr -d '\r')
"$MYSQL_BIN/mysql" -h127.0.0.1 -P3307 -uroot <<SQL 2>/dev/null || true
CREATE DATABASE IF NOT EXISTS peternakan CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER USER 'root'@'localhost' IDENTIFIED BY '$PASS_DB';
FLUSH PRIVILEGES;
SQL

if "$MYSQL_BIN/mysqladmin" -h127.0.0.1 -P3307 -uroot -p"$PASS_DB" ping 2>/dev/null; then
  echo "OK: mysql dev hidup di 127.0.0.1:3307 (user root)."
else
  echo "GAGAL: cek $WORK/mysqld.err" >&2
  exit 1
fi
