#!/usr/bin/env bash
# docs/03E_DEPLOYMENT.md §6.4 — teste de restauração, TRIMESTRAL, OBRIGATÓRIO.
# "Backup nunca restaurado não é backup: é esperança." Restaura numa base
# descartável (estudos_teste), sem tocar a de produção.
#
# Depois de rodar, registre a data e o resultado em algum lugar visível —
# um teste que ninguém anotou é um teste que ninguém fez.
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

echo "Criando base descartável estudos_teste..."
docker compose exec postgres createdb -U estudos estudos_teste

echo "Restaurando $ARQUIVO em estudos_teste..."
gunzip -c "$ARQUIVO" | docker compose exec -T postgres psql -U estudos -d estudos_teste

echo "Contagens (confira que batem com o que você esperava do backup):"
docker compose exec postgres psql -U estudos -d estudos_teste \
  -c "select 'sessao' as tabela, count(*) from sessao
      union all select 'revisao', count(*) from revisao
      union all select 'assunto', count(*) from assunto
      union all select 'erro', count(*) from erro
      union all select 'simulado', count(*) from simulado;"

echo "Removendo base de teste..."
docker compose exec postgres dropdb -U estudos estudos_teste

echo
echo "Teste concluído em $(date '+%Y-%m-%d %H:%M:%S'). Registre este resultado (03E_DEPLOYMENT.md §6.4)."
