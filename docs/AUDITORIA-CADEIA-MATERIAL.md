# Auditoria da cadeia acervo → material (Fase 1: diagnóstico)

**Versão** 1.0.0 · **Data** 2026-10-18 · **Natureza:** relatório de medição, não muda código nem especificação.

## 1. Por que existe

O ensaio da Sprint 24 (item 24.8) com a prova da FCC (TJ-CE, Analista de Infraestrutura de TI, tipo 4) foi
rodado contra o concurso-alvo, que **é o mesmo edital da prova**. Resultado do julgamento (prova id 2):

| Das 39 questões avaliáveis | Quantas |
|---|---|
| Respondíveis (o material sustenta a alternativa do gabarito) | 6 |
| Parciais | 5 |
| Não respondíveis | 28 |

Como o edital é o mesmo, a lacuna não é diferença de programa: algo na cadeia perdeu o conteúdo. Esta auditoria
mede **onde**, usando o índice completo (`indice.db`: 2.639 PDFs, 264.013 páginas, 160 cursos) como verdade.

## 2. Método

Para cada uma das 33 questões `NAO_RESPONDIVEL` ou `PARCIAL`, o campo `falta` do veredito diz o conceito que as
páginas oferecidas não ensinavam. Cada conceito foi buscado no índice inteiro (FTS5, sem filtro de curso). Causas:

- **A** — o acervo não tem o conceito;
- **B** — tem, mas a página está vazia (PDF de imagem) ou o arquivo não foi indexado;
- **C** — o acervo e o índice têm, mas a busca do assunto não o trouxe ao julgamento;
- **D** — foi trazido, mas a IA ou o fatiamento o descartou;
- **E** — a questão foi atribuída ao assunto errado;
- **F** — a questão depende de algo que não é material (texto-base ou gráfico da própria prova).

"Tem" significa que o termo aparece em páginas do índice. **Não** foi lido o texto dessas páginas: que elas
*ensinem* o conceito é indício forte, não prova. Isso é o que a conferência manual (24.8) fecha.

## 3. Resultado

| Causa | Questões | N |
|---|---|---|
| **A** acervo não tem | 22 (SQL Server RESTORE/NORECOVERY), 23 (PgBouncer), 39 (NTP/Kiss-o'-Death), 34 (DAI; ARP spoofing existe) | 4 |
| **F** depende da prova | 2 (texto de Sêneca), 12 (gráfico de barras) | 2 |
| **assunto sem nenhum segmento**, e o acervo tem (flexão verbal 47 págs., função sintática 256) | 4, 7, 8 | 3 |
| **C/D** o acervo tem, a cadeia não trouxe | 13, 14, 28, 29, 30, 33, 35, 36, 37, 38, 40, 41, 42, 46, 47, 49, 54, 56, 59 | 19 |
| **a verificar** (conceito não buscado) | 6, 21, 24, 27, 45 | 5 |

Páginas no índice, por conceito (amostra do que a prova cobrou e o material não entregou):
RPO+RTO 63 · RSTP 116 · spanning tree 147 · BGP 110 · Zabbix 54 · DiffServ 53 · DSCP 31 · DMARC 22 · DKIM 37 ·
Dockerfile 165 · Kubernetes 725 · LDAP 513 · Active Directory 364 · hiperconvergente 14 · IPv6 fc00 12 ·
checksum 162 · função hash 385 · supply chain 81 · "razão e proporção" 291 · "variação percentual" 324.
Cursos de TI que o acervo traz: `Redes e Segurança` (SEFAZ-CE 2026, TCE-SC, TSE, TRT-SC) e `Especialidade TI`.

**Conclusão:** no máximo 4 das 33 (12%) são ausência real do acervo. Pelo menos 22 (3 + 19) são falha da cadeia, e
nenhuma delas é OCR (só 4.864 páginas, 1,8%, estão vazias; B é irrelevante hoje).

## 4. O mecanismo (reproduzido offline, `integracao/acervo.py` sobre o índice real)

Assunto: *"Infraestrutura e redes: servidores, virtualização, contêineres, modelos OSI e TCP/IP, protocolos, VLAN,
NAT, VPN"* (15 questões da prova, 14 não respondíveis).

1. A consulta vira 12 termos em OU: `infraestrutura, redes, servidores, virtualizacao, conteineres, modelos, osi,
   tcp, protocolos, vlan, nat, vpn`. Palavras comuns (*infraestrutura, redes, modelos, protocolos*) fazem a consulta
   casar **63.013 páginas, 24% do acervo**. O ranking BM25 não distingue o tema.
2. Fica o topo de 150 páginas, depois **6 arquivos**, depois **12 páginas por arquivo**: no máximo 72 páginas para um
   assunto que, pela prova, abrange mais de 20 subtemas (DR, ARP, RSTP, BGP, PoE, NTP, QoS, IPv6, Zabbix, HCI,
   AD/LDAP, IKEv2...).
3. A busca com filtro de disciplina usa só 6 cursos "afins" (por radicais de nome). Os cursos que realmente ensinam
   redes para este cargo (`SEFAZ-CE 2026\28 - Redes e Segurança`, `TCE-SC\06 - Redes e Segurança`,
   `TSE\06 - Redes e Segurança`) **não entram no filtro**: "Ciências da Computação" não compartilha radical com
   "Redes e Segurança". Entram duas aulas de "Redes de Computadores" e três do curso da Área Fiscal.
4. A IA, corretamente, só pode escolher dentro do que recebeu. Por isso o segmento ensina OSI/VLAN/IP e o resto some.
   O julgador a seguir reporta a falta, também corretamente.

Os três de português (Q4, Q7, Q8) nem chegaram a ter segmento: o assunto foi tratado como sem material (lacuna da
Sprint 19) mesmo com o acervo cobrindo o tema.

## 5. O que isso diz sobre o desenho

- **O defeito é de granularidade.** Um assunto largo do edital vira uma consulta e um orçamento de ~72 páginas.
  Nenhuma etapa lista os subitens do que o assunto cobra nem confere se cada um chegou ao material.
- **Não existe métrica de cobertura por subitem.** "PRONTO" quer dizer "existe segmento" (`MaterialService`). A
  prova de 60 questões só enxerga os subitens que sorteia.
- **O filtro por disciplina é frágil** (afinidade por radicais de nome de pasta).
- **Ausência real do acervo é pequena** e hoje não tem relatório (nada diz ao dono "isto você precisa comprar").

## 6. Limites desta auditoria

- 33 questões de uma prova de 60; os conceitos foram buscados por termo, não lidos.
- `falta` vem do julgador (IA) e pode estar rigoroso demais; a conferência manual de ~20 vereditos (24.8) continua
  pendente e é o que valida a causa C/D.
- O índice usado é a cópia em `Downloads\indice.db` (2.639 PDFs, igual ao total do log de indexação).
- Q21 e Q42 foram reconstruídas pela recuperação de número perdido; o rodapé de página entrou no texto delas. A Q42
  está em C (hiperconvergente: 14 páginas no índice).

## 7. Próximo passo (Fase 2, depende de decisão do dono)

Corrigir o elo dominante (C/D/E): buscar por **subitem** e não pelo nome inteiro do assunto; cobrir os cursos certos
sem depender de radical de nome; trocar o orçamento fixo (6 × 12) por um proporcional ao número de subitens;
medir cobertura por subitem para **todo** o edital; reportar o que o acervo de fato não tem. Mudança de domínio
(assunto decomposto em subitens) começa pelo documento (`01_DOMINIO.md`, ADR) e exige exceção registrada para
escrever no repositório do planejamento.

## Histórico

| Versão | Data | Mudança |
|---|---|---|
| 1.0.0 | 2026-10-18 | Primeira auditoria: ensaio da FCC (prova 2) contra o índice completo |
