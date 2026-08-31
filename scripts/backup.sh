#!/usr/bin/env bash
# docs/03E_DEPLOYMENT.md §6.1 (ADR-018). Roda no Raspberry Pi, via cron
# (§6.1 do doc) ou à mão. Dump comprimido + verificação de sanidade +
# retenção local + cópia para fora do Pi.
set -euo pipefail

DESTINO=/mnt/ssd/backups
CARIMBO=$(date +%Y%m%d-%H%M%S)
ARQUIVO="$DESTINO/estudos-$CARIMBO.sql.gz"

docker compose exec -T postgres pg_dump -U estudos -d estudos \
  | gzip > "$ARQUIVO"

# Sanidade: um dump íntegro deste sistema não tem menos de 10 KB comprimido
# (03E_DEPLOYMENT.md §3 — SSD desmontado sobe uma base vazia em silêncio).
TAMANHO=$(stat -c%s "$ARQUIVO")
if [ "$TAMANHO" -lt 10240 ]; then
  echo "ERRO: backup suspeito ($TAMANHO bytes). Base vazia? SSD desmontado?" >&2
  exit 1
fi

# Retenção local: 30 diários (§6.2).
find "$DESTINO" -name 'estudos-*.sql.gz' -mtime +30 -delete

# Cópia FORA do Pi — backup no mesmo disco do banco não é backup (§6.5).
rclone copy "$ARQUIVO" remoto:estudos-backups/

echo "OK: $ARQUIVO ($((TAMANHO/1024)) KB)"
