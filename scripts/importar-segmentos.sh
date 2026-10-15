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
  local endpoint="$1" arquivo="$2" extra=()
  # ADR-042: o concurso.json viaja na confirmação dos assuntos e registra o
  # concurso-alvo com a cobertura do arquivo (docs/SPRINT-18-CONCURSOS.md §5).
  if [ "$endpoint" = assuntos ] && [ -f "$(dirname "$arquivo")/concurso.json" ]; then
    extra=(-F "concurso=@$(dirname "$arquivo")/concurso.json;type=application/json")
  fi
  local resumo
  resumo=$(curl -sf -F "arquivo=@${arquivo}" "$APP/api/${endpoint}/importacoes/validar")
  if echo "$resumo" | grep -q '"recusadas":\[\]'; then
    curl -sf -F "arquivo=@${arquivo}" "${extra[@]}" "$APP/api/${endpoint}/importacoes/confirmar" > /dev/null
    log "OK: $arquivo confirmado"
  else
    log "RECUSADO: $arquivo — $resumo"
  fi
}

# ADR-039: só o concurso-alvo ativo é importado. Importar todas as pastas
# deixava o peso de um assunto comum a dois editais depender da ordem do laço.
CONCURSO_ATIVO="${CONCURSO_ATIVO:-}"
if [ -z "$CONCURSO_ATIVO" ]; then
  log "NADA IMPORTADO: defina CONCURSO_ATIVO com o slug da pasta em SGE-Importacao/ (ADR-039)"
  exit 0
fi
pasta="$BASE/$CONCURSO_ATIVO/"
if [ ! -d "$pasta" ]; then
  log "NADA IMPORTADO: pasta do concurso ativo não existe: $pasta"
  exit 0
fi

# assuntos.csv sempre antes de segmentos.csv: um segmento referenciando uma
# chaveExternaAssunto que só existe no arquivo de assuntos da mesma rodada
# precisa que o assunto já tenha sido confirmado.
[ -f "${pasta}assuntos.csv" ] && importar assuntos "${pasta}assuntos.csv"
[ -f "${pasta}segmentos.csv" ] && importar segmentos "${pasta}segmentos.csv"
