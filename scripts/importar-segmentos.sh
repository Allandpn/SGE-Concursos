#!/usr/bin/env bash
set -euo pipefail

# Disparo pós-rclone da Sprint 10 (docs/SPRINT-10-SEGMENTO.md §7).
# Sincroniza os CSVs do Google Drive e chama validar/confirmar em cada um —
# fora do processo da JVM, mesmo princípio de scripts/backup.sh.

BASE=/opt/estudos/importacao-planejamento
APP=http://localhost:8080
LOG=/var/log/estudos/importacao-planejamento.log

log() { echo "$(date -Iseconds) $*" | tee -a "$LOG"; }

rclone sync remoto:SGE-Importacao "$BASE" --create-empty-src-dirs

importar() {
  local endpoint="$1" arquivo="$2"
  local resumo
  resumo=$(curl -sf -F "arquivo=@${arquivo}" "$APP/api/${endpoint}/importacoes/validar")
  if echo "$resumo" | grep -q '"recusadas":\[\]'; then
    curl -sf -F "arquivo=@${arquivo}" "$APP/api/${endpoint}/importacoes/confirmar" > /dev/null
    log "OK: $arquivo confirmado"
  else
    log "RECUSADO: $arquivo — $resumo"
  fi
}

# assuntos.csv sempre antes de segmentos.csv no mesmo concurso: um segmento
# referenciando uma chaveExternaAssunto que só existe no arquivo de assuntos
# da mesma rodada precisa que o assunto já tenha sido confirmado.
for pasta in "$BASE"/*/; do
  [ -f "${pasta}assuntos.csv" ] && importar assuntos "${pasta}assuntos.csv"
  [ -f "${pasta}segmentos.csv" ] && importar segmentos "${pasta}segmentos.csv"
done
