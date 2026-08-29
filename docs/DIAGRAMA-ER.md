# DIAGRAMA ER

Visualização do schema de banco em Mermaid. **Não define nada** — é espelho
do que já existe nas migrações; a fonte de verdade de coluna, restrição e
índice continua sendo `docs/SPRINT-1-BANCO.md` e os arquivos em
`src/main/resources/db/migration/`. Se este diagrama e o schema real
divergirem, o schema real vence, e este arquivo está desatualizado — não o
contrário.

| Campo | Valor |
|---|---|
| Versão | 1.0.0 |
| Data | 2026-08-28 |
| Status | Vigente |
| Subordinado a | `docs/SPRINT-1-BANCO.md`, `src/main/resources/db/migration/V1__tabelas.sql`, `V3__restricoes.sql` |

---

## Diagrama

```mermaid
erDiagram
    disciplina ||--o{ assunto : "fk_assunto_disciplina"
    assunto ||--o{ sessao : "fk_sessao_d01_assunto"
    assunto ||--o{ revisao : "fk_revisao_assunto"
    assunto ||--o{ erro : "fk_erro_assunto"
    sessao |o--o{ revisao : "fk_revisao_sessao_origem (opcional)"
    sessao |o--o{ revisao : "fk_revisao_sessao_cumpriu (D-06, opcional)"
    sessao |o--o{ erro : "fk_erro_sessao (opcional)"

    disciplina {
        bigint id PK
        text nome
        text peso "CHECK ALTO|MEDIO|BAIXO"
        boolean ativo
        timestamptz criado_em
        timestamptz atualizado_em
    }

    assunto {
        bigint id PK
        bigint disciplina_id FK
        text nome "único por disciplina, sem acento (J-1)"
        text peso "CHECK ALTO|MEDIO|BAIXO"
        smallint dificuldade_percebida "CHECK 1..5"
        integer ordem "obrigatório via CHECK, D-41"
        boolean ativo
        timestamptz criado_em
        timestamptz atualizado_em
    }

    sessao {
        bigint id PK
        bigint assunto_id FK
        text tipo "ESTUDO|QUESTOES|FLASHCARDS|RECUPERACAO"
        date data
        integer tempo_minutos "CHECK maior que 0"
        text resultado "SUCESSO|PARCIAL|FALHA, nullable"
        integer questoes_corretas
        integer questoes_total
        text formato "MULTIPLA_ESCOLHA|CERTO_ERRADO"
        smallint previsao_percentual
        boolean previsao_reconstrucao
        uuid tentativa_id "único, D-45"
        date proxima_sessao_data
        text proxima_sessao_descricao
        boolean ativo
        timestamptz criado_em
        timestamptz atualizado_em
    }

    revisao {
        bigint id PK
        bigint assunto_id FK
        integer nivel "CHECK maior ou igual a 1"
        date data_prevista
        bigint sessao_origem_id FK "nullable"
        bigint sessao_cumpriu_id FK "nullable, exigido se CUMPRIDA (D-06)"
        text situacao "PENDENTE|CUMPRIDA|CANCELADA"
        timestamptz criado_em
        timestamptz atualizado_em
    }

    erro {
        bigint id PK
        bigint assunto_id FK
        bigint sessao_id FK "nullable"
        text descricao
        text causa "7 valores fechados"
        text confianca "BAIXA|MEDIA|ALTA"
        boolean resolvido
        timestamptz criado_em
        timestamptz atualizado_em
    }

    parametro {
        text chave PK
        text valor
        text descricao
        timestamptz atualizado_em
    }
```

## Notas de leitura

- `parametro` não aparece com nenhuma linha de relacionamento — é
  configuração pura (`01_DOMINIO` §3), sem FK para nenhuma outra tabela.
- `revisao` tem duas FKs distintas para `sessao` (`sessao_origem_id` e
  `sessao_cumpriu_id`): a sessão que gerou a revisão e a sessão que a
  cumpriu não são a mesma pergunta. D-06 amarra a segunda: só pode ficar
  nula se `situacao != 'CUMPRIDA'`.
- Nenhuma tabela tem coluna de fase (D-16). A fase do assunto é derivada em
  memória a partir de `revisao`/`sessao` — não existe aqui porque não deve
  existir.
- Todas as 5 tabelas de domínio já existem no schema desde a Sprint 1
  (`PROGRESSO.md` §1). Ganhar entidade JPA é progresso de sprint separado:
  `disciplina`/`assunto` na Sprint 2; `sessao` na Sprint 3; `revisao` na
  Sprint 4; `erro` na Sprint 7 (`docs/SPRINT-2-CADASTRO.md` §0).

---

## Changelog

| Versão | Data | Mudança |
|---|---|---|
| 1.0.0 | 2026-08-28 | Criado, a partir de `V1__tabelas.sql` e `V3__restricoes.sql` (schema fechado desde a Sprint 1) |
