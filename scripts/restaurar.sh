#!/usr/bin/env bash
# docs/03E_DEPLOYMENT.md §6.3. Restaura um dump NA BASE DE PRODUÇÃO — pára a
# aplicação antes (ninguém escrevendo durante o restore) e sobe de novo
# depois, deixando o Flyway validar o schema restaurado.
#
# Para testar uma restauração sem tocar produção, use testar-restauracao.sh.
set -euo pipefail

if [ $# -ne 1 ]; then
  echo "Uso: $0 <caminho-para-backup.sql.gz>" >&2
  exit 1
fi

ARQUIVO="$1"
if [ ! -f "$ARQUIVO" ]; then
  echo "ERRO: arquivo não encontrado: $ARQUIVO" >&2
  exit 1
fi

echo "Parando a aplicação..."
docker compose stop app

echo "Restaurando $ARQUIVO em estudos..."
gunzip -c "$ARQUIVO" | docker compose exec -T postgres psql -U estudos -d estudos

echo "Subindo a aplicação..."
docker compose start app
docker compose logs -f app
