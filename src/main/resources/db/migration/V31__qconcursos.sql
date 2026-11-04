-- Sprint 36 (docs/SPRINT-36-QCONCURSOS.md §1; 01_DOMINIO §3.24; ADR-058).

-- D-151: o catálogo de bancas do QConcursos, pelo nome normalizado (sem acento, sem caixa, só letras e números;
-- CESPE e Cebraspe são "cebraspe"). Nasce com as conferidas por endereço real e aprende pelo endereço colado.
CREATE TABLE qconcursos_banca (
    nome_norm      VARCHAR(120) PRIMARY KEY,
    nome           VARCHAR(200) NOT NULL,
    numero         INTEGER NOT NULL,
    origem         VARCHAR(10) NOT NULL,
    atualizado_em  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_qconcursos_banca_numero CHECK (numero > 0),
    CONSTRAINT ck_qconcursos_banca_origem CHECK (origem IN ('CATALOGO', 'ENDERECO'))
);

INSERT INTO qconcursos_banca (nome_norm, nome, numero, origem) VALUES
    ('cebraspe', 'CESPE / CEBRASPE', 2, 'CATALOGO'),
    ('fcc', 'FCC', 1, 'CATALOGO'),
    ('fundacaocarloschagas', 'FCC', 1, 'CATALOGO'),
    ('cesgranrio', 'CESGRANRIO', 5, 'CATALOGO'),
    ('fundacaocesgranrio', 'CESGRANRIO', 5, 'CATALOGO'),
    ('fgv', 'FGV', 63, 'CATALOGO'),
    ('fundacaogetuliovargas', 'FGV', 63, 'CATALOGO'),
    ('objetiva', 'OBJETIVA', 121, 'CATALOGO'),
    ('vunesp', 'VUNESP', 152, 'CATALOGO'),
    ('fundacaovunesp', 'VUNESP', 152, 'CATALOGO'),
    ('iades', 'IADES', 276, 'CATALOGO'),
    ('fundacaolasalle', 'Fundação La Salle', 367, 'CATALOGO'),
    ('lasalle', 'Fundação La Salle', 367, 'CATALOGO'),
    ('institutoaocp', 'INSTITUTO AOCP', 379, 'CATALOGO'),
    ('aocp', 'INSTITUTO AOCP', 379, 'CATALOGO'),
    ('institutomais', 'INSTITUTO MAIS', 493, 'CATALOGO'),
    ('idib', 'IDIB', 1019, 'CATALOGO'),
    ('idcap', 'IDCAP', 1703, 'CATALOGO'),
    ('institutoavalia', 'Instituto Avalia', 3682, 'CATALOGO'),
    ('iset', 'ISET', 4024, 'CATALOGO');

-- D-156: o número da questão no QConcursos, no erro anotado depois do lote.
ALTER TABLE erro ADD COLUMN questao_externa VARCHAR(20);
ALTER TABLE erro ADD CONSTRAINT ck_erro_questao_externa CHECK (questao_externa IS NULL OR questao_externa ~ '^Q[0-9]{4,10}$');

-- D-152: quantas questões cabem num foco; também o tempo do lote rápido sem foco (D-133).
INSERT INTO parametro (chave, valor, descricao) VALUES
    ('minutos_por_questao', '3', 'Minutos por questão: a sugestão do atalho do QConcursos e o tempo do lote rápido sem foco (01_DOMINIO §3.24, D-152)');
