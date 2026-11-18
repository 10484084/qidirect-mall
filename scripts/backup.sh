#!/usr/bin/env bash
# 数据库 + 上传文件备份，保留最近 N 天（默认 14 天）。
# 用法：DB_PASSWORD=xxx ./scripts/backup.sh [备份目录]
# 建议 cron：0 3 * * * cd /opt/qidirect-mall && DB_PASSWORD=xxx ./scripts/backup.sh >> backups/backup.log 2>&1
set -euo pipefail

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-qidirect_mall}"
DB_USER="${DB_USER:-root}"
DB_PASSWORD="${DB_PASSWORD:?需要设置 DB_PASSWORD}"
KEEP_DAYS="${KEEP_DAYS:-14}"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKUP_DIR="${1:-$ROOT_DIR/backups}"
STAMP="$(date +%Y%m%d_%H%M%S)"

mkdir -p "$BACKUP_DIR"

echo "[$(date '+%F %T')] 开始备份 $DB_NAME"

mysqldump --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USER" --password="$DB_PASSWORD" \
  --single-transaction --quick --default-character-set=utf8mb4 \
  --databases "$DB_NAME" | gzip > "$BACKUP_DIR/db_${STAMP}.sql.gz"

if [ -d "$ROOT_DIR/uploads" ]; then
  tar -czf "$BACKUP_DIR/uploads_${STAMP}.tar.gz" -C "$ROOT_DIR" uploads
fi

find "$BACKUP_DIR" -name 'db_*.sql.gz' -mtime "+$KEEP_DAYS" -delete
find "$BACKUP_DIR" -name 'uploads_*.tar.gz' -mtime "+$KEEP_DAYS" -delete

echo "[$(date '+%F %T')] 备份完成：${BACKUP_DIR}（保留 ${KEEP_DAYS} 天）"
