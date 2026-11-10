# Sprint 49 — O filtro do QConcursos

| | |
|---|---|
| Versão | 1.1.0 |
| Data | 2026-11-10 |
| Subordinado a | `01_DOMINIO` v1.47.0 §3.24 (D-175 a D-177); `02_JORNADAS` v1.41.0 (J-17, passo 7); `04_FRONTEND` v1.31.0 §8L |
| Origem | Pedido do usuário (2026-11-07): "o filtro de questões do QConcursos deve pegar apenas questões de nível superior; nas configurações poderia ter a opção de definir esses filtros padrão, usando a taxonomia que conhecemos do QConcursos". Decidido numa rodada; wireframe aprovado |
| Modo de trabalho | Implementação direta (`AGENTS.md`) |

## 0. Escopo

**Entra:** o filtro padrão (nível, banca, anos, formato, dificuldade, ordem, limpeza, gabarito comentado, cargo/áreas/instituição),
de fábrica com nível superior; a exceção por concurso; os filtros aprendidos pelo endereço com nome; as marcas da folha "Questões".

**Fica de fora:** palavra-chave, prova, "inéditas" e os filtros que exigem login no QConcursos para valer (meus cadernos, meus
simulados, minhas anotações); raspar o site para listar cargos e áreas (verificação anti-robô; ver D-176).

## 1. A taxonomia usada

Fonte: `integracao/taxonomia/qconcursos-filtros-2026-11-03.json` do planejamento (catálogo enviado pelo usuário).

| Filtro | Parâmetro | Valores | Conferido em endereço real |
|---|---|---|---|
| Nível | `scholarity_ids[]` | 1 fundamental, 2 médio, 3 superior | os três |
| Dificuldade | `difficulty[]` | 1 a 5 | só o 2 (Fácil); os outros vêm do catálogo — **conferir o primeiro link** |
| Ordem | `sort` | `newest`, `hardest` | `newest`; `hardest` vem do catálogo |
| Gabarito comentado | `has_professor_commentaries=true` | — | sim |
| Cargo, formação, atuação, instituição | `job_ids[]`, `knowledge_area_ids[]`, `administrative_level_ids[]`, `institute_ids[]` | números aprendidos | parâmetros conferidos; números só os do catálogo |
| Banca, anos, modalidade, limpeza, por página | (Sprint 36) | — | sim |

## 2. Banco (V36)

| Tabela | O quê |
|---|---|
| `qconcursos_filtro` | uma linha sem concurso (o padrão, completo: `ck_..._d175_padrao_completo`, um só: `uq_..._d175_um_padrao`) e no máximo uma por concurso (a exceção; nulo = segue o padrão). `niveis` e `dificuldades` são `SMALLINT[]` contidos em {1..3} e {1..5}; `banca`, `anos`, `formato`, `ordem` com `CHECK`; `aprendidos_proprios` diz se a lista de aprendidos é a do padrão (falso) ou a da exceção |
| `qconcursos_filtro_aprendido` | `(filtro_id, tipo, numero)`; apagada em cascata com a exceção |
| `qconcursos_rotulo` | `(tipo, numero) → nome`, o nome que a pessoa deu (D-176); nasce com os números com nome do catálogo |

## 3. API

| Rota | O quê |
|---|---|
| `GET/PUT /api/qconcursos/filtro` | o padrão; o PUT exige todos os campos (`FILTRO_INCOMPLETO`), valida (`FILTRO_INVALIDO`) e pede nome para número sem nome (`NOME_FALTANDO`) |
| `POST /api/qconcursos/filtro/endereco` | lê os números de cargo, áreas e instituição do endereço, com o nome guardado (ou nulo); nada é gravado; `ENDERECO_SEM_FILTRO` |
| `GET/PUT/DELETE /api/concursos/{id}/qconcursos-filtro` | `{concursoId, excecao, efetivo}`; o PUT grava só o que vem não nulo (tudo nulo = voltar ao padrão); DELETE volta ao padrão |
| `GET /api/qconcursos/atalhos` | cada atalho ganha `marcas: [{chave, rotulo, parametros}]` (D-177) |

`LinkQConcursos.montar(Filtro, Recortes, bancaNome)` monta o endereço com os parâmetros em ordem alfabética (como o QConcursos) e as
marcas; `montar(Filtro)` continua dando o endereço de antes.

## 4. Decisões

| Decisão | Alternativa recusada | O que quebraria com ela |
|---|---|---|
| Exceção com colunas nulas = segue o padrão | Copiar o padrão inteiro para o concurso | Mudar o padrão depois não chegaria aos concursos com exceção; "guarda só o que difere" viraria mentira |
| A tela envia na exceção só os campos que diferem do padrão | Enviar o filtro inteiro | Um campo igual ao padrão ficaria preso ao valor de hoje |
| "Anos" do filtro separado do `banca_anos` | Reusar o parâmetro da incidência (D-145) | Mudar o recorte do atalho mudaria o peso pela banca |
| Aprendidos pelo endereço colado, com nome dado pela pessoa | Raspar as listas do QConcursos | O site tem verificação anti-robô; uma raspagem quebraria sem aviso |
| Marcas desligadas tiram os parâmetros no aparelho (`qc.urlDaFolha`) | Pedir ao servidor um endereço novo | Uma ida ao servidor por toque, sem ganho: as marcas já trazem os parâmetros exatos |
| A folha do filtro serve ao padrão e à exceção | Controles abertos dentro de Ajustes (como no wireframe) e de novo na aba Banca | A mesma marcação em dois lugares, divergindo com o tempo |
| Banca "Qualquer" não avisa "sem filtro de banca" | Avisar sempre que não há banca | O aviso apareceria para uma escolha da pessoa |

## 5. Testes

| Teste | Prova |
|---|---|
| `LinkQConcursosTest` (+2) | cada marca traz parâmetros que estão no endereço, rótulos e ordem; os aprendidos do endereço sem repetir e ignorando o resto |
| `QConcursosApiTest` (+4) | o padrão de fábrica (nível superior) e as marcas; o padrão trocado muda o endereço, incompleto e inválido recusados; a exceção guarda só o que difere e segue o padrão depois que ele muda, voltar ao padrão, concurso inexistente 404; aprendidos pelo endereço, `NOME_FALTANDO`, o nome guardado, lista vazia na exceção tira os aprendidos; o endereço esperado de D-150 passa a ter `scholarity_ids[]=3` |
| `EstruturaSchemaTest` | as três tabelas novas |
| Chromium (`e2e-49`) | Ajustes com o padrão de fábrica; a folha: nível, dificuldade, ordem, colar endereço, pedir nome, gravar; as marcas na folha "Questões", desligar duas tira só elas do endereço, fechar volta; a exceção na aba Banca com "deste concurso", só o nível gravado, o atalho segue a exceção e o resto do padrão, Voltar ao padrão; tema escuro |
| Regressão | 36, 37, 45, 46, 48 |

## 6. Itens e ordem

| # | Item | Situação |
|---|---|---|
| 49.0 | Especificação (D-175 a D-177) e wireframe aprovados | feito |
| 49.1 | V36, `FiltroQConcursosService`, o endereço com marcas e as rotas | feito |
| 49.2 | Ajustes, a folha do filtro, o cartão na aba Banca, as marcas na folha "Questões"; `CASCA_VERSAO` → `casca-2026-11-10-1` | feito |
| 49.3 | Testes, Chromium e regressão | feito |
| 49.4 | Deploy pelo Guardian; conferir no primeiro link os números de dificuldade e `sort=hardest` | — |

## Changelog

| Versão | Data | Mudança |
|---|---|---|
| 1.1.0 | 2026-11-10 | Itens 49.1 a 49.3 feitos |
| 1.0.0 | 2026-11-10 | Criado, com o wireframe aprovado pelo usuário |
