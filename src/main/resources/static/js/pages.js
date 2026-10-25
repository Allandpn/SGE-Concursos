// js/pages.js — um componente Alpine por página (09_CODE_STYLE §8).

// Rótulo amigável por chave de parâmetro (04_FRONTEND §7E) — a tela
// Ajustes nunca expõe a chave crua ao usuário. Chave nova na tabela
// `parametro` sem entrada aqui cai no fallback (a própria chave) em vez de
// quebrar — ver paginaAjustes.rotulo().
const ROTULOS_PARAMETRO = {
  teto_diario_recuperacoes: 'Recuperações por dia',
  teto_global_frente: 'Assuntos na frente (global)',
  teto_disciplina_frente: 'Assuntos na frente (por disciplina)',
  limiar_sucesso_multipla_escolha: 'Sucesso · múltipla escolha',
  limiar_parcial_multipla_escolha: 'Parcial · múltipla escolha',
  limiar_sucesso_certo_errado: 'Sucesso · certo/errado',
  limiar_parcial_certo_errado: 'Parcial · certo/errado',
  limiar_sucesso_flashcards: 'Sucesso · flashcards',
  limiar_parcial_flashcards: 'Parcial · flashcards',
  intervalo_nivel_1: 'Intervalo nível 1',
  intervalo_nivel_2: 'Intervalo nível 2',
  intervalo_nivel_3: 'Intervalo nível 3',
  intervalo_nivel_4: 'Intervalo nível 4',
  intervalo_nivel_5: 'Intervalo nível 5',
  intervalo_nivel_6: 'Intervalo nível 6',
  intervalo_manutencao_dias: 'Manutenção (consolidado)',
  janela_tolerancia_percentual: 'Janela de tolerância',
  lote_minimo_questoes: 'Lote mínimo de questões',
  duracao_bloco_min: 'Duração sugerida do bloco (min)',
};

document.addEventListener('alpine:init', () => {

  // IA em uso e o aviso de assinatura parada (ADR-045, docs/SPRINT-19-MATERIAL-AUTOMATICO.md
  // §3A). Um store só para Hoje, Concursos e Ajustes mostrarem o mesmo aviso. Auxiliar:
  // falhar aqui nunca esconde a tela.
  Alpine.store('ia', {
    config: null,
    erro: null,
    trocando: false,

    async carregar() {
      try {
        this.config = await api('/ia/configuracao');
      } catch (e) {
        this.config = null;
      }
    },

    get assinaturaParou() {
      return !!(this.config && this.config.provedor === 'CLAUDE' && this.config.claude.falha);
    },

    get podeUsarOpenRouter() {
      return !!(this.config && this.config.openrouter.configurado);
    },

    async escolher(provedor) {
      this.erro = null;
      this.trocando = true;
      try {
        this.config = await api('/ia/configuracao', { method: 'PUT', body: JSON.stringify({ provedor }) });
      } catch (e) {
        this.erro = e.message;
      } finally {
        this.trocando = false;
      }
    },
  });

  // Handoff Hoje → Recuperar (04_FRONTEND.md §6, "decisão de código, não de
  // arquitetura"): a tela Recuperar não busca nível/data prevista de novo —
  // usa o que a fila do turno já trouxe. Store Alpine global, não parâmetro
  // na URL, porque o dado inteiro (FilaRecuperacaoItemResponse) precisa
  // sobreviver à navegação sem virar query string.
  Alpine.store('recuperacao', {
    item: null,
    selecionar(item) {
      this.item = item;
    },
  });

  // Mesmo handoff, pro card "Conteúdo novo" de Hoje → Registrar (04_FRONTEND
  // §7A) — carrega o AssuntoResponse, não o FilaRecuperacaoItemResponse do
  // store acima (formas diferentes, stores separados).
  Alpine.store('registrar', {
    item: null,
    selecionar(item) {
      this.item = item;
    },
  });

  // Mesmo handoff, pra lista de Assuntos → detalhe (04_FRONTEND §7B). Não
  // existe GET /api/assuntos/{id} — a lista já monta assunto+disciplina+fase
  // pra desenhar a própria linha, então passa isso adiante em vez de o
  // detalhe buscar de novo. Consequência aceita, mesma de Recuperar/
  // Registrar: abrir a URL direto sem passar pela lista cai em semDados.
  Alpine.store('assuntoDetalhe', {
    item: null,
    selecionar(item) {
      this.item = item;
    },
  });

  // Mesmo handoff, pra Erros (04_FRONTEND §7C) — escrito por Recuperar
  // (etapa 2) e pelo detalhe de Assuntos, os dois pontos de entrada
  // contextuais desta sprint. Só { assuntoId, nome } — sessaoId fica de
  // fora nesta sprint (ver §7C do doc técnico pra entender por quê).
  Alpine.store('erro', {
    item: null,
    selecionar(item) {
      this.item = item;
    },
  });

  Alpine.data('paginaHoje', () => ({
    carregando: true,
    erro: null,
    fila: [],
    reestudos: [],
    blocoConteudo: null,
    blocoContinuacao: false,
    segmentoSugerido: null,
    alertaRepresamento: false,
    diasSemEstudar: null,
    represado: 0,
    estimativaDiasParaNormalizar: 0,
    vencidasAlemDoTeto: 0,
    proximoBloco: null,
    antecipaveis: [],
    mostrarAntecipaveis: false,
    // "Drenar mais" (02_JORNADAS §4.3): cada pedido soma um teto diário à fila,
    // só nesta tela; voltar para Hoje recomeça do teto normal (D-40).
    extraRecuperacoes: 0,
    passoDrenar: 0,
    // 02_JORNADAS §4.4: o concurso-alvo no topo. Falhar aqui não esconde o plano.
    concurso: null,
    concursoCarregado: false,

    async init() {
      this.extraRecuperacoes = 0;
      this.mostrarAntecipaveis = false;
      this.carregarConcurso();
      await this.carregar();
    },

    carregarConcurso() {
      api('/concursos/ativo')
        .then((c) => { this.concurso = c; this.concursoCarregado = true; })
        .catch(() => { this.concursoCarregado = false; });
    },

    // Os dias vêm do servidor (derivado lá, 02_JORNADAS §4.4).
    get textoProva() {
      const dias = this.concurso?.diasParaProva;
      if (dias === null || dias === undefined) return 'sem data de prova';
      if (dias === 0) return 'prova hoje';
      if (dias < 0) return 'a prova já passou';
      return dias === 1 ? 'prova amanhã' : `prova em ${dias} dias`;
    },

    async drenarMais() {
      this.extraRecuperacoes += this.passoDrenar;
      await this.carregar();
    },

    async carregar() {
      this.carregando = true;
      this.erro = null;
      Alpine.store('ia').carregar(); // aviso de assinatura parada (ADR-045), sem esperar
      try {
        const plano = await api(`/turno/plano?extraRecuperacoes=${this.extraRecuperacoes}`);
        this.fila = plano.filaRecuperacao;
        this.reestudos = plano.reestudos;
        this.blocoConteudo = plano.blocoConteudo;
        this.blocoContinuacao = plano.blocoContinuacao;
        this.segmentoSugerido = plano.segmentoSugerido;
        this.alertaRepresamento = plano.alertaRepresamento;
        this.diasSemEstudar = plano.diasSemEstudar;
        this.represado = plano.represado;
        this.estimativaDiasParaNormalizar = plano.estimativaDiasParaNormalizar;
        this.vencidasAlemDoTeto = plano.vencidasAlemDoTeto;
        this.proximoBloco = plano.proximoBloco;
        this.antecipaveis = plano.antecipaveis;
        if (this.extraRecuperacoes === 0) this.passoDrenar = this.fila.length; // a fila sem pedido = o teto diário
      } catch (e) {
        this.erro = e.message;
      } finally {
        this.carregando = false;
      }
    },

    get vazio() {
      return this.fila.length === 0 && this.reestudos.length === 0 && !this.blocoConteudo;
    },

    // J-4: dois dias ou mais sem nenhuma sessão — limiar desta implementação
    // (docs/SPRINT-17-USO-REAL.md §6.3). Some sozinha no primeiro registro do dia.
    get mostrarRetorno() {
      return this.diasSemEstudar !== null && this.diasSemEstudar >= 2;
    },

    get temAdiantar() {
      return this.vencidasAlemDoTeto > 0 || !!this.proximoBloco || this.antecipaveis.length > 0;
    },

    get descricaoProximoBloco() {
      return descreverSegmento(this.proximoBloco);
    },

    abrirOutroBloco() {
      this.abrirRegistroConteudo({ ...this.blocoConteudo, segmentoSugeridoId: this.proximoBloco.id });
    },

    abrirRecuperacao(item) {
      Alpine.store('recuperacao').selecionar(item);
      window.location.hash = `#/recuperar/${item.assuntoId}`;
    },

    abrirRegistroConteudo(assunto) {
      Alpine.store('registrar').selecionar(assunto);
      window.location.hash = `#/registrar/conteudo/${assunto.id}`;
    },

    // D-55: o card leva o segmento sugerido pra Registrar já vir com ele marcado.
    abrirBlocoConteudo() {
      this.abrirRegistroConteudo({ ...this.blocoConteudo, segmentoSugeridoId: this.segmentoSugerido?.id ?? null });
    },

    get descricaoSegmento() {
      return descreverSegmento(this.segmentoSugerido);
    },

    get linkMaterial() {
      const arquivo = this.segmentoSugerido?.arquivo;
      // Link do Drive (legado) ou PDF servido pelo próprio SGE (ADR-044).
      return arquivo && (/^https?:\/\//.test(arquivo) || arquivo.startsWith('/material/')) ? arquivo : null;
    },

    // D-07/D-11: reestudo é uma sessão ESTUDO do mesmo assunto — mesma tela
    // de Registrar conteúdo (docs/SPRINT-16-CORRECOES-MULTIEDITAL.md §4).
    abrirReestudo(item) {
      this.abrirRegistroConteudo({ id: item.assuntoId, nome: item.nome });
    },
  }));

  Alpine.data('paginaRecuperar', () => ({
    etapa: 1,
    assuntoId: null,
    nome: null,
    nivel: null,
    dataPrevista: null,
    previsao: null,
    resultado: null,
    tentativaId: null,
    inicioEm: null,
    enviando: false,
    erro: null,
    semDados: false,

    // Chamado pelo router a cada navegação para #/recuperar/{id} — inclusive
    // quando confirmar() volta pra cá depois de uma falha de envio (§5.1).
    // Reabrir o MESMO assunto não pode resetar o que já foi respondido, ou
    // "reenviar sem perder o que foi digitado" quebra.
    abrir(assuntoId) {
      if (this.assuntoId === assuntoId && this.tentativaId) return;

      const item = Alpine.store('recuperacao').item;
      this.semDados = !item || item.assuntoId !== assuntoId;

      this.assuntoId = assuntoId;
      this.nome = this.semDados ? null : item.nome;
      this.nivel = this.semDados ? null : item.nivel;
      this.dataPrevista = this.semDados ? null : item.dataPrevista;
      this.etapa = 1;
      this.previsao = null;
      this.resultado = null;
      this.erro = null;
      this.tentativaId = crypto.randomUUID(); // D-45: gerado ao abrir a tela, não ao enviar
      this.inicioEm = Date.now();
    },

    prever(valor) {
      this.previsao = valor;
      this.etapa = 2;
    },

    async confirmar(valor) {
      this.resultado = valor;
      this.erro = null;
      this.enviando = true;

      // D-29: tempo é medido, nunca constante — aqui é o tempo de tela
      // (abrir → confirmar), já que a Recuperar não tem campo próprio de
      // duração no wireframe (02_JORNADAS §4.1).
      const tempoMinutos = Math.max(1, Math.round((Date.now() - this.inicioEm) / 60000));

      // Otimista (02_JORNADAS §5.1): volta pra Hoje antes da resposta confirmar.
      window.location.hash = '#/hoje';

      try {
        await api('/sessoes', {
          method: 'POST',
          body: JSON.stringify({
            assuntoId: this.assuntoId,
            tipo: 'RECUPERACAO',
            data: hojeISO(),
            tempoMinutos,
            previsaoReconstrucao: this.previsao,
            resultado: this.resultado,
            tentativaId: this.tentativaId,
          }),
        });
      } catch (e) {
        // Falha não destrutiva: volta pra cá com previsão/resultado intactos.
        // Reenviar usa o MESMO tentativaId (D-45) — nunca duplica, mesmo que
        // a primeira tentativa tenha gravado e só a resposta tenha se perdido.
        this.erro = e.message;
        window.location.hash = `#/recuperar/${this.assuntoId}`;
      } finally {
        this.enviando = false;
      }
    },

    reenviar() {
      this.confirmar(this.resultado);
    },

    // Link "+ registrar um erro" da etapa 2 (04_FRONTEND §7C) — aparece
    // ANTES de confirmar(), então não existe sessaoId nenhum ainda; leva
    // só o assunto de contexto, mesmo mecanismo de store de Hoje→Registrar.
    abrirErro() {
      Alpine.store('erro').selecionar({ assuntoId: this.assuntoId, nome: this.nome });
      window.location.hash = `#/erros/${this.assuntoId}`;
    },
  }));

  Alpine.data('paginaRegistrar', () => ({
    tipo: 'conteudo', // conteudo | questoes | flashcards
    assuntoId: null,
    nome: null,
    segmentos: [],
    segmentoId: null,
    formato: 'MULTIPLA_ESCOLHA',
    feitas: null,
    certas: null,
    previsao: null,
    tempo: null,
    tempoEditado: false,
    duracaoBloco: null,
    tentativaId: null,
    enviando: false,
    erro: null,
    semDados: false,

    // Chamado pelo router a cada navegação para #/registrar/{tipo}/{id} —
    // inclusive quando registrar() volta pra cá depois de uma falha de envio.
    // Mesmo cuidado de paginaRecuperar.abrir: reabrir o MESMO assunto não
    // pode resetar o que já foi preenchido.
    async abrir(tipo, assuntoId) {
      this.tipo = tipo;
      if (this.assuntoId === assuntoId && this.tentativaId) return;

      const item = Alpine.store('registrar').item;
      this.semDados = !item || item.id !== assuntoId;

      this.assuntoId = assuntoId;
      this.nome = this.semDados ? null : item.nome;
      this.segmentos = [];
      this.segmentoId = null;
      this.feitas = null;
      this.certas = null;
      this.previsao = null;
      this.tempo = null;
      this.tempoEditado = false;
      this.erro = null;
      this.tentativaId = crypto.randomUUID(); // D-45: gerado ao abrir a tela

      if (!this.semDados) {
        // D-29: a duração de bloco é parâmetro — lida a cada abertura, então
        // mudar em Ajustes vale na próxima vez, sem recarregar o app.
        api('/parametros')
          .then((ps) => {
            const p = ps.find((x) => x.chave === 'duracao_bloco_min');
            this.duracaoBloco = p ? Number(p.valor) : null;
            this.sugerirTempo();
          })
          .catch(() => { this.duracaoBloco = null; });
        try {
          this.segmentos = await api(`/assuntos/${assuntoId}/segmentos`);
          // D-55: vindo do card de Hoje, o segmento sugerido já vem marcado.
          if (item.segmentoSugeridoId && this.segmentos.some((s) => s.id === item.segmentoSugeridoId)) {
            this.segmentoId = String(item.segmentoSugeridoId);
          }
        } catch (e) {
          // Lista de segmentos é auxiliar, não bloqueia o registro — se
          // falhar, o campo de segmento simplesmente não aparece (§7A).
          this.segmentos = [];
        }
        this.sugerirTempo();
      }
    },

    // D-29 / 01_DOMINIO §7.7: em conteúdo, o tempo vem preenchido com a
    // estimativa do bloco escolhido ou, sem ela, com a duração parametrizada.
    // Depois que o usuário mexe no campo, nada mais o sobrescreve. Questões e
    // flashcards ficam vazios — não há estimativa honesta para um lote livre.
    sugerirTempo() {
      if (this.tempoEditado) return;
      if (this.tipo !== 'conteudo') {
        this.tempo = null;
        return;
      }
      const segmento = this.segmentos.find((s) => String(s.id) === this.segmentoId);
      this.tempo = segmento?.tempoEstimadoMin ?? this.duracaoBloco;
    },

    rotuloSegmento(s) {
      return descreverSegmento(s);
    },

    selecionarTipo(tipo) {
      this.tipo = tipo;
      this.segmentoId = null;
      this.feitas = null;
      this.certas = null;
      this.previsao = null;
      this.tempoEditado = false;
      this.sugerirTempo();
    },

    get temSegmentos() {
      return this.segmentos.length > 0;
    },

    async registrar() {
      this.erro = null;

      // 01_DOMINIO §7.7: o tempo de bloco e de lote é declarado, não o de tela
      // — a leitura e as questões aconteceram antes de abrir esta tela.
      const tempoMinutos = Number(this.tempo);
      if (!Number.isInteger(tempoMinutos) || tempoMinutos < 1) {
        this.erro = 'Informe o tempo em minutos.';
        return;
      }

      this.enviando = true;
      const corpoBase = {
        assuntoId: this.assuntoId,
        data: hojeISO(),
        tempoMinutos,
        tentativaId: this.tentativaId,
      };
      // x-model de <select> sempre devolve string — "" quando "Nenhum em
      // especial" está selecionado (nunca null; um <option value="null">
      // literal já foi tentado e vira a STRING "null" no DOM, bug real
      // achado testando isto no navegador).
      const corpo = this.tipo === 'conteudo'
        ? { ...corpoBase, tipo: 'ESTUDO', segmentoId: this.segmentoId ? Number(this.segmentoId) : null }
        : {
            ...corpoBase,
            tipo: this.tipo === 'questoes' ? 'QUESTOES' : 'FLASHCARDS',
            formato: this.tipo === 'questoes' ? this.formato : null,
            questoesTotal: this.feitas === null ? null : Number(this.feitas),
            questoesCorretas: this.certas === null ? null : Number(this.certas),
            previsaoPercentual: this.previsao === null ? null : Number(this.previsao),
          };

      // Otimista (02_JORNADAS §5.1): volta pra Hoje antes da resposta confirmar.
      window.location.hash = '#/hoje';

      try {
        await api('/sessoes', { method: 'POST', body: JSON.stringify(corpo) });
      } catch (e) {
        // Falha não destrutiva: volta pra cá com os campos intactos.
        // Reenviar usa o MESMO tentativaId (D-45) — nunca duplica.
        this.erro = e.message;
        window.location.hash = `#/registrar/${this.tipo}/${this.assuntoId}`;
      } finally {
        this.enviando = false;
      }
    },
  }));

  Alpine.data('paginaAssuntos', () => ({
    carregando: true,
    erro: null,
    grupos: [], // [{ disciplina, resumo, assuntos: [{...AssuntoResponse, fase}] }]
    abertas: {}, // disciplinaId → expandida (02_JORNADAS §4.5)

    async init() {
      this.carregando = true;
      this.erro = null;
      try {
        const [disciplinas, resumos] = await Promise.all([api('/disciplinas'), api('/disciplinas/resumo')]);
        const resumoPorId = Object.fromEntries(resumos.map((r) => [r.id, r]));
        this.grupos = await Promise.all(disciplinas.map(async (disciplina) => {
          const assuntos = await api(`/assuntos?disciplinaId=${disciplina.id}`);
          const comFase = await Promise.all(assuntos.map(async (assunto) => {
            const { fase } = await api(`/assuntos/${assunto.id}/fase`);
            return { ...assunto, fase };
          }));
          return { disciplina, resumo: resumoPorId[disciplina.id] ?? null, assuntos: comFase };
        }));
      } catch (e) {
        this.erro = e.message;
      } finally {
        this.carregando = false;
      }
    },

    get vazio() {
      return this.grupos.length === 0;
    },

    alternar(disciplinaId) {
      this.abertas = { ...this.abertas, [disciplinaId]: !this.abertas[disciplinaId] };
    },

    contagem(grupo) {
      const r = grupo.resumo;
      if (!r) return `${grupo.assuntos.length} assuntos`;
      return `${r.backlog} no backlog · ${r.frente} na frente · ${r.consolidados} ${r.consolidados === 1 ? 'consolidado' : 'consolidados'}`;
    },

    abrirDetalhe(disciplina, assuntoComFase) {
      Alpine.store('assuntoDetalhe').selecionar({ disciplina, assunto: assuntoComFase });
      window.location.hash = `#/assuntos/${assuntoComFase.id}`;
    },
  }));

  Alpine.data('paginaAssuntoDetalhe', () => ({
    assuntoId: null,
    nome: null,
    disciplinaNome: null,
    peso: null,
    fase: null,
    segmentos: [],
    lacuna: null,
    refazendo: false,
    refeito: false,
    erroRefazer: null,
    retencao: [],
    partesEdital: [], // 01_DOMINIO §3.12 — as partes do edital deste assunto e o aviso de cada uma
    carregando: false,
    semDados: false,

    // Chamado pelo router a cada navegação para #/assuntos/{id}. Sem
    // GET /api/assuntos/{id} (§7B do doc técnico), os dados do próprio
    // assunto vêm do store escrito pela lista — só os segmentos são um
    // fetch novo.
    async abrir(assuntoId) {
      const item = Alpine.store('assuntoDetalhe').item;
      this.semDados = !item || item.assunto.id !== assuntoId;
      if (this.semDados) return;

      this.assuntoId = assuntoId;
      this.nome = item.assunto.nome;
      this.disciplinaNome = item.disciplina.nome;
      this.peso = item.assunto.peso;
      this.fase = item.assunto.fase;
      this.lacuna = null;
      this.refeito = false;
      this.erroRefazer = null;
      this.carregando = true;
      try {
        this.segmentos = await api(`/assuntos/${assuntoId}/segmentos`);
      } catch (e) {
        // Segmentos são auxiliares, não bloqueiam o detalhe (mesmo
        // princípio de paginaRegistrar §7A) — se falhar, a lista de
        // material simplesmente não aparece.
        this.segmentos = [];
      } finally {
        this.carregando = false;
      }

      // D-65: sem segmento, o motivo de o acervo não ensinar o assunto, se houver.
      // 404 é o normal (sem lacuna); qualquer falha só esconde a linha.
      if (this.segmentos.length === 0) {
        try {
          this.lacuna = await api(`/assuntos/${assuntoId}/lacuna`);
        } catch (e) {
          this.lacuna = null;
        }
      }

      // Sprint 25 — as partes do edital; auxiliar, vazio se o assunto não nasceu de uma conferência.
      try {
        this.partesEdital = await api(`/assuntos/${assuntoId}/partes-do-edital`);
      } catch (e) {
        this.partesEdital = [];
      }

      // M-2, adicionado na Sprint 14 (04_FRONTEND §7B) — mesmo princípio
      // de auxiliar, não bloqueia o detalhe se falhar.
      try {
        const m2 = await api(`/metricas/m2?assuntoId=${assuntoId}`);
        this.retencao = m2.series;
      } catch (e) {
        this.retencao = [];
      }
    },

    get temSegmentos() {
      return this.segmentos.length > 0;
    },

    // D-66: arquiva os segmentos (e descarta a lacuna) para o material ser preparado
    // de novo. Nada é apagado; o progresso de leitura deste assunto recomeça.
    async refazerMaterial() {
      if (!window.confirm('Preparar de novo o material deste assunto? O material atual sai do turno e a leitura dele recomeça pelos blocos novos.')) return;
      this.refazendo = true;
      this.erroRefazer = null;
      try {
        await api(`/assuntos/${this.assuntoId}/material/refazer`, { method: 'POST' });
        this.segmentos = [];
        this.lacuna = null;
        this.refeito = true;
      } catch (e) {
        this.erroRefazer = e.message;
      } finally {
        this.refazendo = false;
      }
    },

    get temRetencao() {
      return this.retencao.length > 0;
    },

    // Mesmo mecanismo de abrirRegistroConteudo (Hoje §6/§7A) — escreve no
    // MESMO store que paginaRegistrar.abrir() lê, só que a partir do
    // detalhe de Assuntos em vez do card de Hoje. Fecha a pendência da
    // Sprint 11: agora Questões/Flashcards/Conteúdo em qualquer assunto
    // têm de onde partir.
    irRegistrar(tipo) {
      Alpine.store('registrar').selecionar({ id: this.assuntoId, nome: this.nome });
      window.location.hash = `#/registrar/${tipo}/${this.assuntoId}`;
    },

    // Quarta ação do detalhe, ao lado de Conteúdo/Questões/Flashcards
    // (04_FRONTEND §7C) — mesmo mecanismo de store de irRegistrar.
    irErros() {
      Alpine.store('erro').selecionar({ assuntoId: this.assuntoId, nome: this.nome });
      window.location.hash = `#/erros/${this.assuntoId}`;
    },
  }));

  Alpine.data('paginaErros', () => ({
    assuntoId: null,
    nome: null,
    semDados: false,
    descricao: '',
    causa: null,
    confianca: null,
    enviando: false,
    erroEnvio: null,
    erros: [],
    carregandoLista: true,

    // Chamado pelo router a cada navegação para #/erros/{assuntoId}. Sem
    // GET /api/assuntos/{id} (mesma ausência de §7B), nome/assunto vêm do
    // store escrito por Recuperar ou pelo detalhe de Assuntos.
    async abrir(assuntoId) {
      const item = Alpine.store('erro').item;
      this.semDados = !item || item.assuntoId !== assuntoId;
      if (this.semDados) return;

      this.assuntoId = assuntoId;
      this.nome = item.nome;
      this.descricao = '';
      this.causa = null;
      this.confianca = null;
      this.erroEnvio = null;
      await this.carregarLista();
    },

    async carregarLista() {
      this.carregandoLista = true;
      try {
        this.erros = await api(`/erros?assuntoId=${this.assuntoId}`);
      } catch (e) {
        this.erros = [];
      } finally {
        this.carregandoLista = false;
      }
    },

    // Não otimista, de propósito — esta tela não navega embora depois de
    // registrar (04_FRONTEND §7C, "não é otimista, e é deliberado"): espera
    // o POST, limpa o formulário e atualiza a lista só depois de confirmar.
    async registrar() {
      this.erroEnvio = null;
      this.enviando = true;
      try {
        await api('/erros', {
          method: 'POST',
          body: JSON.stringify({
            assuntoId: this.assuntoId,
            sessaoId: null,
            descricao: this.descricao,
            causa: this.causa,
            confianca: this.confianca,
          }),
        });
        this.descricao = '';
        this.causa = null;
        this.confianca = null;
        await this.carregarLista();
      } catch (e) {
        this.erroEnvio = e.message;
      } finally {
        this.enviando = false;
      }
    },
  }));

  Alpine.data('paginaProgresso', () => ({
    carregando: true,
    erro: null,
    janela: 'GLOBAL',
    disciplinasPorId: {},
    m1: null,
    m3: null,
    m4: null,
    frente: null,
    divisoes: [],

    async init() {
      this.carregando = true;
      this.erro = null;
      try {
        const disciplinas = await api('/disciplinas');
        this.disciplinasPorId = Object.fromEntries(disciplinas.map((d) => [d.id, d.nome]));
        await this.carregarM1();
        this.m3 = await api('/metricas/m3');
        this.m4 = await api('/metricas/m4');
        // 02_JORNADAS J-3: a frente da semana e as sugestões de divisão (D-35).
        this.frente = await api('/frente');
        this.divisoes = await api('/frente/sugestoes-divisao');
      } catch (e) {
        this.erro = e.message;
      } finally {
        this.carregando = false;
      }
    },

    async carregarM1() {
      this.m1 = await api(`/metricas/m1?janela=${this.janela}`);
    },

    async trocarJanela(janela) {
      this.janela = janela;
      await this.carregarM1();
    },

    // GLOBAL vem com disciplinaId nulo (soma entre disciplinas, D-19);
    // POR_DISCIPLINA precisa do nome de verdade (04_FRONTEND §7D).
    rotuloLinha(linha) {
      return linha.disciplinaId === null ? 'Global' : (this.disciplinasPorId[linha.disciplinaId] ?? '—');
    },
  }));

  Alpine.data('paginaAjustes', () => ({
    carregando: true,
    erro: null,
    parametros: [],
    draft: {}, // cópia editável por chave — só o que o <input> lê/escreve
    salvando: {},
    erroPorChave: {},
    // Ajustes → IA (ADR-045, docs/SPRINT-19-MATERIAL-AUTOMATICO.md §3A). Token e chave
    // digitados só vão no PUT; a resposta nunca os traz de volta.
    modelos: [],
    tokenNovo: '',
    claudeModelo: 'sonnet',
    chaveNova: '',
    modeloEscolhido: '',
    salvandoIa: false,
    erroIa: null,
    salvoIa: false,
    async init() {
      this.carregando = true;
      this.erro = null;
      try {
        this.parametros = await api('/parametros');
        this.draft = Object.fromEntries(this.parametros.map((p) => [p.chave, p.valor]));
      } catch (e) {
        this.erro = e.message;
      } finally {
        this.carregando = false;
      }
      this.carregarIa();
    },

    // Auxiliar: falhar aqui não esconde os parâmetros.
    async carregarIa() {
      await Alpine.store('ia').carregar();
      const config = Alpine.store('ia').config;
      if (config) {
        this.claudeModelo = config.claude.modelo;
        this.modeloEscolhido = config.openrouter.modelo;
      } else {
        this.erroIa = 'Não foi possível ler a configuração de IA.';
      }
      try {
        this.modelos = await api('/ia/modelos');
      } catch (e) {
        this.modelos = []; // sem a lista, o modelo atual continua no seletor
      }
    },

    rotuloModelo(m) {
      const preco = m.precoEntradaPorMilhao == null ? ''
        : ` · US$ ${m.precoEntradaPorMilhao.toFixed(2)} / ${m.precoSaidaPorMilhao?.toFixed(2) ?? '?'} por milhão`;
      return m.nome + preco;
    },

    get opcoesModelo() {
      // O modelo em uso aparece mesmo que a lista não tenha carregado.
      if (this.modelos.some((m) => m.id === this.modeloEscolhido) || !this.modeloEscolhido) return this.modelos;
      return [{ id: this.modeloEscolhido, nome: this.modeloEscolhido, precoEntradaPorMilhao: null }, ...this.modelos];
    },

    async salvarIa(provedor) {
      this.erroIa = null;
      this.salvoIa = false;
      this.salvandoIa = true;
      try {
        Alpine.store('ia').config = await api('/ia/configuracao', {
          method: 'PUT',
          body: JSON.stringify({
            provedor: provedor ?? null,
            claudeToken: this.tokenNovo || null,
            claudeModelo: this.claudeModelo || null,
            chave: this.chaveNova || null,
            modelo: this.modeloEscolhido || null,
          }),
        });
        this.tokenNovo = '';
        this.chaveNova = '';
        this.salvoIa = true;
      } catch (e) {
        this.erroIa = e.message;
      } finally {
        this.salvandoIa = false;
      }
    },

    rotulo(chave) {
      return ROTULOS_PARAMETRO[chave] ?? chave;
    },

    // Seções por prefixo de chave, mesmo agrupamento do wireframe de
    // referência (04_FRONTEND §7E); 'Turno' nasceu com duracao_bloco_min (§7H).
    get grupos() {
      const nomeDoGrupo = (chave) => {
        if (chave.startsWith('teto_')) return 'Tetos';
        if (chave.startsWith('limiar_')) return 'Limiares de resultado';
        if (chave.startsWith('duracao_')) return 'Turno';
        return 'Escada';
      };
      const porGrupo = {};
      for (const p of this.parametros) {
        const grupo = nomeDoGrupo(p.chave);
        (porGrupo[grupo] ??= []).push(p);
      }
      return ['Tetos', 'Turno', 'Limiares de resultado', 'Escada']
        .filter((nome) => porGrupo[nome])
        .map((nome) => ({ nome, itens: porGrupo[nome] }));
    },

    // Cada linha salva sozinha, sem otimismo (não há navegação pra
    // antecipar, 04_FRONTEND §7E). `draft` é o que o <input> lê/escreve
    // (x-model); `parametro.valor` só muda quando o PATCH confirma. Em
    // caso de falha, reatribuir draft[chave] força o Alpine a reescrever o
    // campo — só reatribuir p.valor (que nunca mudou) não dispara nada, o
    // valor "novo" seria igual ao antigo e o Alpine não re-renderiza uma
    // propriedade que não mudou (achado testando isto no navegador: o
    // campo ficava com o texto inválido preso, mesmo com o estado certo).
    async salvar(parametro, valorNovo) {
      if (valorNovo === parametro.valor) return;
      this.erroPorChave[parametro.chave] = null;
      this.salvando[parametro.chave] = true;
      try {
        const atualizado = await api(`/parametros/${parametro.chave}`, {
          method: 'PATCH',
          body: JSON.stringify({ valor: valorNovo }),
        });
        parametro.valor = atualizado.valor;
        this.draft[parametro.chave] = atualizado.valor;
      } catch (e) {
        this.erroPorChave[parametro.chave] = e.message;
        this.draft[parametro.chave] = parametro.valor;
      } finally {
        this.salvando[parametro.chave] = false;
      }
    },
  }));

  // Concursos — concurso-alvo, histórico, edital novo (02_JORNADAS J-5, 04_FRONTEND §7J).
  Alpine.data('paginaConcursos', () => ({
    carregando: true,
    erro: null,
    erroAcao: null,
    concursos: [],
    ia: null,
    analises: [],
    reativando: null,
    refazendo: false,
    // SPRINT-19 §5A — "Ver por assunto": null = fechada, [] = aberta e vazia.
    porAssunto: null,
    soSemMaterial: false,
    carregandoLista: false,
    avisos: null, // Sprint 25 — o que a conferência deixou por resolver no concurso-alvo

    async init() {
      this.carregando = true;
      this.erro = null;
      this.erroAcao = null;
      try {
        Alpine.store('ia').carregar(); // aviso de assinatura parada (ADR-045), sem esperar
        const [concursos, ia, analises] = await Promise.all([
          api('/concursos'), api('/editais/ia'), api('/editais/analises'),
        ]);
        this.concursos = concursos;
        this.ia = ia;
        // Só as que ainda pedem algo do usuário; confirmada já está no histórico.
        this.analises = analises.filter((a) => a.status !== 'CONFIRMADA');
        this.avisos = null;
        if (this.ativo) {
          try {
            this.avisos = await api(`/concursos/${this.ativo.id}/avisos-de-conferencia`);
          } catch (e) {
            this.avisos = null; // auxiliar: sem o aviso, a tela segue
          }
        }
      } catch (e) {
        this.erro = e.message;
      } finally {
        this.carregando = false;
      }
    },

    get ativo() {
      return this.concursos.find((c) => c.ativo) ?? null;
    },

    // "12 partes sem filtro de questões" — não bloqueia nada, fica até a pessoa resolver.
    get textoAvisos() {
      const a = this.avisos;
      if (!a || !a.analiseId) return '';
      const partes = [];
      if (a.partesSemNo) partes.push(`${a.partesSemNo} ${a.partesSemNo === 1 ? 'parte sem filtro' : 'partes sem filtro'} de questões`);
      if (a.vinculosAmplosOuParciais) partes.push(`${a.vinculosAmplosOuParciais} ${a.vinculosAmplosOuParciais === 1 ? 'vínculo' : 'vínculos'} largo(s) ou parcial(is)`);
      if (a.nosQueMudaram) partes.push(`${a.nosQueMudaram} ${a.nosQueMudaram === 1 ? 'nó mudou' : 'nós mudaram'} na árvore`);
      return partes.join(' · ');
    },

    get anteriores() {
      return this.concursos.filter((c) => !c.ativo);
    },

    // SPRINT-19 §5A — abre/fecha a lista do material, assunto a assunto.
    async verPorAssunto(c) {
      if (this.porAssunto) { this.porAssunto = null; return; }
      this.carregandoLista = true;
      this.erroAcao = null;
      try {
        this.porAssunto = await api(`/concursos/${c.id}/material`);
      } catch (e) {
        this.erroAcao = e.message;
      } finally {
        this.carregandoLista = false;
      }
    },

    // Agrupa por disciplina na ordem do edital; dentro dela, os sem material primeiro.
    get gruposMaterial() {
      const grupos = new Map();
      for (const a of this.porAssunto ?? []) {
        if (this.soSemMaterial && a.situacao !== 'SEM_MATERIAL') continue;
        if (!grupos.has(a.disciplina)) grupos.set(a.disciplina, []);
        grupos.get(a.disciplina).push(a);
      }
      const peso = (a) => (a.situacao === 'SEM_MATERIAL' ? 0 : 1);
      return [...grupos].map(([disciplina, assuntos]) => ({
        disciplina,
        assuntos: assuntos.map((a, i) => [a, i]).sort((x, y) => peso(x[0]) - peso(y[0]) || x[1] - y[1]).map((x) => x[0]),
      }));
    },

    minutosTexto(min) {
      if (min < 60) return `${min} min`;
      const h = Math.floor(min / 60);
      return min % 60 ? `${h} h ${min % 60} min` : `${h} h`;
    },

    textoAssuntoMaterial(a) {
      if (a.situacao === 'SEM_MATERIAL') return 'Sem material no acervo: ' + a.motivo;
      if (a.situacao === 'A_PREPARAR') return 'A preparar';
      const partes = [`${a.segmentos} ${a.segmentos === 1 ? 'segmento' : 'segmentos'}`, this.minutosTexto(a.minutos)];
      if (a.paginas) partes.push(`${a.paginas} págs.`);
      return partes.join(' · ');
    },

    // 01_DOMINIO §3.10 — andamento do material preparado sozinho.
    textoMaterial(m) {
      if (!m) return '';
      const total = m.prontos + m.semMaterial + m.aPreparar;
      const partes = [`Material: ${m.prontos} de ${total} prontos`];
      if (m.semMaterial) partes.push(`${m.semMaterial} sem material no acervo`);
      if (m.aPreparar) partes.push(`${m.aPreparar} a preparar`);
      return partes.join(' · ');
    },

    detalhes(c) {
      const prova = c.dataProva ? 'prova em ' + dataBR(c.dataProva) : 'sem data de prova';
      return [c.banca, prova].filter(Boolean).join(' · ');
    },

    periodo(c) {
      return dataInstante(c.ativadoEm) + ' – ' + (c.desativadoEm ? dataInstante(c.desativadoEm) : 'hoje');
    },

    rotuloStatus(status) {
      return {
        LENDO_EDITAL: 'Lendo o edital…',
        ESCOLHER_CARGO: 'Escolha o cargo',
        SEPARANDO_ASSUNTOS: 'Lendo o conteúdo programático…',
        EM_CONFERENCIA: 'Conferir o edital',
        FALHOU: 'Falhou — toque para tentar de novo',
      }[status] ?? status;
    },

    // D-66: prepara de novo o material de todo o concurso — confirma, porque a
    // leitura em curso recomeça pelos blocos novos. Nada é apagado.
    async refazerMaterial(c) {
      const feitos = c.material.prontos + c.material.semMaterial;
      if (!window.confirm(`Preparar de novo o material de ${feitos} assunto(s) de ${c.nome}? O material atual sai do turno e é refeito em segundo plano.`)) return;
      this.refazendo = true;
      this.erroAcao = null;
      try {
        await api(`/concursos/${c.id}/material/refazer`, { method: 'POST' });
        await this.init();
      } catch (e) {
        this.erroAcao = e.message;
      } finally {
        this.refazendo = false;
      }
    },

    // D-60: reativar muda peso e ordem — pede confirmação, nunca toca o estudo.
    async reativar(c) {
      if (!window.confirm(`Reativar ${c.nome}? Peso e ordem dos assuntos voltam a ser os deste edital; o estudo feito não muda.`)) return;
      this.reativando = c.id;
      this.erroAcao = null;
      try {
        await api(`/concursos/${c.id}/ativar`, { method: 'POST' });
        await this.init();
      } catch (e) {
        this.erroAcao = e.message;
      } finally {
        this.reativando = null;
      }
    },
  }));

  // Novo edital — análise por IA em etapas; nada gravado até confirmar (D-62).
  Alpine.data('paginaEdital', () => ({
    modo: 'novo', // novo | analise
    id: null,
    analise: null,
    carregando: false,
    erro: null,
    erroAcao: null,
    enviando: false,
    arquivo: null,
    cargo: null,
    timer: null,

    abrirNovo() {
      this.parar();
      this.modo = 'novo';
      this.analise = null;
      this.id = null;
      this.arquivo = null;
      this.erroAcao = null;
      this.enviando = false;
    },

    async abrir(id) {
      this.parar();
      this.modo = 'analise';
      if (this.id !== id) {
        this.analise = null;
        this.cargo = null;
      }
      this.id = id;
      this.erroAcao = null;
      await this.carregar();
    },

    parar() {
      if (this.timer) clearTimeout(this.timer);
      this.timer = null;
    },

    async carregar() {
      this.carregando = !this.analise;
      this.erro = null;
      try {
        this.aplicar(await api(`/editais/analises/${this.id}`));
      } catch (e) {
        this.erro = e.message;
      } finally {
        this.carregando = false;
      }
    },

    // Enquanto a IA trabalha, consulta a cada 3 s — só se a tela ainda é esta.
    aplicar(analise) {
      this.analise = analise;
      if (analise.cargos?.length === 1 && !this.cargo) this.cargo = analise.cargos[0];
      this.parar();
      if (['LENDO_EDITAL', 'SEPARANDO_ASSUNTOS'].includes(analise.status)
          && window.location.hash === `#/concursos/analise/${analise.id}`) {
        this.timer = setTimeout(() => this.carregar(), 3000);
      }
    },

    escolherArquivo(evento) {
      this.arquivo = evento.target.files[0] ?? null;
    },

    async enviar() {
      if (!this.arquivo) {
        this.erroAcao = 'Escolha o PDF do edital.';
        return;
      }
      this.enviando = true;
      this.erroAcao = null;
      try {
        const analise = await apiEnviarArquivo('/editais/analises', 'arquivo', this.arquivo);
        window.location.hash = `#/concursos/analise/${analise.id}`;
      } catch (e) {
        this.erroAcao = e.message;
      } finally {
        this.enviando = false;
      }
    },

    async escolherCargo() {
      this.enviando = true;
      this.erroAcao = null;
      try {
        this.aplicar(await api(`/editais/analises/${this.id}/cargo`, {
          method: 'POST', body: JSON.stringify({ cargo: this.cargo }),
        }));
      } catch (e) {
        this.erroAcao = e.message;
      } finally {
        this.enviando = false;
      }
    },

    async tentarDeNovo() {
      this.enviando = true;
      this.erroAcao = null;
      try {
        this.aplicar(await api(`/editais/analises/${this.id}/tentar-de-novo`, { method: 'POST' }));
      } catch (e) {
        this.erroAcao = e.message;
      } finally {
        this.enviando = false;
      }
    },
  }));

  // Ajustes → Árvore do QConcursos (D-90): atualizar é gesto da pessoa, e o impacto vem antes.
  Alpine.data('arvoreAjustes', () => ({
    versoes: [],
    impacto: null,
    carregando: true,
    trabalhando: false,
    erro: null,

    async init() {
      this.carregando = true;
      this.erro = null;
      try {
        this.versoes = await api('/arvore/versoes');
      } catch (e) {
        this.erro = e.message;
      } finally {
        this.carregando = false;
      }
    },

    async ver(v) {
      this.trabalhando = true;
      this.erro = null;
      try {
        this.impacto = await api(`/arvore/versoes/${v.id}/impacto`);
      } catch (e) {
        this.erro = e.message;
      } finally {
        this.trabalhando = false;
      }
    },

    async adotar(v) {
      if (!window.confirm(`Adotar a árvore ${v.rotulo}? As partes cujo nó saiu ou mudou de lugar voltam à fila para você reconfirmar. Nenhum nó é trocado sozinho.`)) return;
      this.trabalhando = true;
      this.erro = null;
      try {
        await api(`/arvore/versoes/${v.id}/adotar?confirmar=true`, { method: 'POST' });
        this.impacto = null;
        await this.init();
      } catch (e) {
        this.erro = e.message;
      } finally {
        this.trabalhando = false;
      }
    },
  }));

  // Conferência do edital (01_DOMINIO §3.12, D-86 a D-92; docs/SPRINT-25-CONFERENCIA-EDITAL.md §10).
  // A IA propõe, a regra confere, a pessoa resolve; cada gesto vira decisão.
  Alpine.data('paginaConferencia', () => ({
    id: null,
    resumo: null,
    disciplinas: [],
    fila: [],
    decisoes: [],
    aba: 'fila',
    abertas: {},
    carregando: false,
    trabalhando: false,
    erro: null,
    erroAcao: null,
    erroPainel: null,
    lendo: false, // o planejamento está refazendo a leitura (Reprocessar)
    timerLeitura: null,
    painel: null, // { tipo: 'parte'|'assunto'|'trecho'|'decisao', modo, ... }
    formulario: {},
    picker: { q: '', resultados: [], ramo: [], filhos: [], selecionados: [], natureza: 'EXATO' },
    buscaSeq: 0,

    async abrir(id) {
      if (this.id !== id) { this.resumo = null; this.painel = null; this.abertas = {}; }
      this.id = id;
      this.erroAcao = null;
      await this.recarregar(true);
    },

    async recarregar(inicial = false) {
      this.carregando = inicial && !this.resumo;
      this.erro = null;
      try {
        const base = `/editais/analises/${this.id}/conferencia`;
        const [resumo, disciplinas, fila] = await Promise.all([api(base), api(`${base}/disciplinas`), api(`${base}/fila`)]);
        this.lendo = false;
        clearTimeout(this.timerLeitura);
        this.resumo = resumo;
        this.disciplinas = disciplinas;
        this.fila = fila;
        if (inicial) this.aba = fila.length ? 'fila' : 'disciplinas';
        if (this.painel) this.atualizarPainel();
      } catch (e) {
        if (e.codigo === 'ETAPA_INVALIDA' && this.id) {
          // Reprocessando: a conferência volta quando o planejamento entregar. Consulta de novo em alguns segundos.
          this.lendo = true;
          clearTimeout(this.timerLeitura);
          this.timerLeitura = setTimeout(() => this.recarregar(), 5000);
        } else {
          this.erro = e.message;
        }
      } finally {
        this.carregando = false;
      }
    },

    // Roda uma ação, mostra o erro como veio e recarrega o que depende dela.
    async agir(fazer, { fecharPainel = false } = {}) {
      this.trabalhando = true;
      this.erroAcao = null;
      this.erroPainel = null;
      try {
        await fazer();
        await this.recarregar();
        if (fecharPainel) this.painel = null;
        return true;
      } catch (e) {
        if (this.painel) this.erroPainel = e.message; else this.erroAcao = e.message;
        return false;
      } finally {
        this.trabalhando = false;
      }
    },

    pedido(caminho, metodo, corpo) {
      return api(`/editais/analises/${this.id}${caminho}`, { method: metodo, body: corpo === undefined ? undefined : JSON.stringify(corpo) });
    },

    // ------------------------------------------------ rótulos
    rotuloTipo(tipo) {
      return {
        LINHA_FORA: 'Linhas fora de qualquer disciplina', SEM_ASSUNTO: 'Trecho do edital sem assunto',
        ASSUNTO_RECUSADO: 'Assunto recusado pela conferência', ALERTA_DE_PARTE: 'Alerta na divisão do assunto',
        PARTE_SEM_NO: 'Parte sem nó', VINCULO_AMPLO_OU_PARCIAL: 'Vínculo mais amplo ou parcial',
        NO_TROCADO_NO_REFINO: 'Nó trocado pela segunda checagem', NO_MUDOU_NA_ARVORE: 'O nó mudou na árvore',
        NAO_APARECE_MAIS: 'Não aparece mais no reprocessamento',
      }[tipo] ?? tipo;
    },
    rotuloNatureza(n) {
      return { EXATO: 'exato', MAIS_AMPLO: 'mais amplo', PARCIAL: 'parcial' }[n] ?? 'sem vínculo';
    },
    rotuloOrigem(o) {
      return { IA: 'proposto pela IA', REFINO: 'trocado na segunda checagem', DECISAO: 'vindo de uma decisão sua anterior', PESSOA: 'escolhido por você' }[o] ?? o;
    },
    rotuloEstado(p) {
      if (p.noMudou) return 'reconfirmar';
      return { PROPOSTA: 'por conferir', ACEITA: 'aceita', DECIDIDA: 'decidida', DESCARTADA: 'descartada' }[p.estado] ?? p.estado;
    },

    alternarAssunto(id) { this.abertas = { ...this.abertas, [id]: !this.abertas[id] }; },

    // ------------------------------------------------ painel
    fechar() { this.painel = null; this.erroPainel = null; },

    achar(assuntoId) {
      for (const d of this.disciplinas) for (const a of d.assuntos) if (a.id === assuntoId) return { d, a };
      return null;
    },

    abrirParte(parte, assunto) {
      this.erroPainel = null;
      this.formulario = { comentario: parte.comentario ?? '', paraTodos: false };
      this.painel = { tipo: 'parte', modo: 'ver', titulo: 'Parte do edital', parte, assunto };
    },

    abrirItem(it) {
      this.erroPainel = null;
      if (it.parteId) {
        const achado = this.achar(it.assuntoId);
        const parte = achado?.a.partes.find((p) => p.id === it.parteId);
        if (parte) this.abrirParte(parte, achado.a);
      } else if (it.assuntoId) {
        this.abrirAssunto(this.achar(it.assuntoId).a);
      } else {
        this.formulario = { motivo: '', texto: (it.detalhe ?? []).join(' '), disciplinaId: '', novaDisciplina: '' };
        this.painel = { tipo: 'trecho', modo: 'ver', titulo: this.rotuloTipo(it.tipo), item: it };
      }
    },

    abrirAssunto(assunto) {
      this.formulario = {};
      this.painel = { tipo: 'assunto', modo: 'ver', titulo: 'Assunto do edital', assunto };
    },

    abrirPartesDoAssunto() {
      this.abertas = { ...this.abertas, [this.painel.assunto.id]: true };
      this.aba = 'disciplinas';
      this.painel = null;
    },

    // Depois de cada ação o painel aponta para o dado novo (a parte pode ter sido trocada por outra).
    atualizarPainel() {
      const p = this.painel;
      if (p.tipo === 'parte') {
        const achado = this.achar(p.assunto.id);
        const parte = achado?.a.partes.find((x) => x.id === p.parte.id);
        if (!achado || !parte || !parte.aparece || parte.estado === 'DESCARTADA') { this.painel = null; return; }
        p.assunto = achado.a;
        p.parte = parte;
      } else if (p.tipo === 'assunto') {
        const achado = this.achar(p.assunto.id);
        if (achado) p.assunto = achado.a;
      } else if (p.tipo === 'trecho' && !this.fila.some((i) => i.trechoId === p.item.trechoId)) {
        this.painel = null;
      }
    },

    // ------------------------------------------------ ações sobre a parte
    // D-87: "todos os editais" mostra antes quantas partes de outros editais mudariam e só segue com a confirmação.
    async escopo(nos, natureza) {
      if (!this.formulario.paraTodos) return { escopo: 'EDITAL' };
      const q = new URLSearchParams();
      (nos ?? []).forEach((n) => q.append('nos', n));
      if (natureza) q.set('natureza', natureza);
      const previa = await this.pedido(`/partes/${this.painel.parte.id}/escopo-todos?${q}`, 'GET');
      const quantas = previa.partes === 1 ? '1 parte de outro edital' : `${previa.partes} partes de outros editais`;
      const exemplos = previa.exemplos.length ? `\n\nPor exemplo:\n- ${previa.exemplos.join('\n- ')}` : '';
      if (!window.confirm(`Valer para todos os editais muda ${quantas}, que ainda não foram conferidas. O que você já decidiu em outros editais não muda.${exemplos}\n\nConfirmar?`)) {
        throw new Error('Você escolheu não valer para todos os editais.');
      }
      return { escopo: 'TODOS', confirmarTodos: true };
    },
    aceitar() {
      return this.agir(async () => this.pedido(`/partes/${this.painel.parte.id}/aceitar`, 'POST',
        await this.escopo(this.painel.parte.nos.map((n) => n.id), this.painel.parte.natureza)), { fecharPainel: true });
    },
    semNo() {
      return this.agir(async () => this.pedido(`/partes/${this.painel.parte.id}/sem-no`, 'POST', await this.escopo([], null)), { fecharPainel: true });
    },
    reprocessar() {
      if (!window.confirm('Ler o edital de novo? O que você decidiu fica como está; a IA propõe só o que é novo.')) return;
      return this.agir(() => this.pedido('/conferencia/reprocessar', 'POST'));
    },
    comentar() { return this.agir(() => this.pedido(`/partes/${this.painel.parte.id}/comentario`, 'POST', { texto: this.formulario.comentario })); },
    descartar() { return this.agir(() => this.pedido(`/partes/${this.painel.parte.id}/descartar`, 'POST', { motivo: this.formulario.motivo }), { fecharPainel: true }); },
    salvarEdicao() {
      const f = this.formulario;
      return this.agir(() => this.pedido(`/partes/${this.painel.parte.id}`, 'PUT', { texto: f.texto, contexto: f.contexto || null, herdado: f.herdado || null }), { fecharPainel: true });
    },
    dividir() {
      const partes = this.formulario.partes.filter((t) => t.trim()).map((texto) => ({ texto }));
      return this.agir(() => this.pedido(`/partes/${this.painel.parte.id}/dividir`, 'POST', { partes }), { fecharPainel: true });
    },
    juntar() {
      const f = this.formulario;
      return this.agir(() => this.pedido(`/partes/${this.painel.parte.id}/juntar`, 'POST', { outraParteId: Number(f.outra), texto: f.texto }), { fecharPainel: true });
    },

    // ------------------------------------------------ ações sobre assunto, disciplina e trecho
    aceitarAssunto() { return this.agir(() => this.pedido(`/assuntos/${this.painel.assunto.id}/aceitar`, 'POST'), { fecharPainel: true }); },
    corrigirAssunto() { return this.agir(() => this.pedido(`/assuntos/${this.painel.assunto.id}`, 'PUT', { texto: this.formulario.texto }), { fecharPainel: true }); },
    aceitarExatos(d) { return this.agir(() => this.pedido(`/disciplinas/${d.id}/aceitar-exatos`, 'POST')); },
    aceitarAmplos(d) {
      if (!window.confirm(`Aceitar de uma vez os vínculos mais amplos ou parciais de ${d.nome}? Você aceita o nó largo: as questões do QConcursos continuam a um toque, só que mais abrangentes.`)) return;
      return this.agir(() => this.pedido(`/disciplinas/${d.id}/aceitar-amplos`, 'POST'));
    },
    trocarPeso(d, peso) { return this.agir(() => this.pedido(`/disciplinas/${d.id}/peso`, 'PUT', { peso })); },
    resolverTrecho(acao) {
      const f = this.formulario;
      const corpo = acao === 'DESCARTAR'
        ? { acao, motivo: f.motivo }
        : { acao, texto: f.texto, disciplinaId: f.disciplinaId ? Number(f.disciplinaId) : null, novaDisciplina: f.novaDisciplina || null };
      return this.agir(() => this.pedido(`/trechos/${this.painel.item.trechoId}/resolver`, 'POST', corpo), { fecharPainel: true });
    },

    async concluir() {
      if (!window.confirm('Concluir a conferência? Os assuntos passam a existir, o concurso vira o alvo e o material é preparado. Você continua podendo trocar nós depois.')) return;
      this.trabalhando = true;
      this.erroAcao = null;
      try {
        await this.pedido('/conferencia/concluir', 'POST');
        window.location.hash = '#/concursos';
      } catch (e) {
        this.erroAcao = e.message;
        window.scrollTo(0, 0);
        await this.recarregar();
      } finally {
        this.trabalhando = false;
      }
    },

    // ------------------------------------------------ seletor de nó
    async iniciarTrocaDeNo() {
      const p = this.painel.parte;
      this.picker = { q: '', resultados: [], ramo: [], filhos: [], selecionados: p.nos.filter((n) => n.nome).map((n) => ({ ...n })), natureza: p.natureza ?? 'EXATO' };
      this.painel.modo = 'nos';
      await this.carregarRamo();
      // O que o sistema propôs e a pessoa não adotou entra como sugestão para a busca.
      if (p.propostos.length) this.picker.resultados = p.propostos.filter((n) => n.nome);
    },

    async carregarRamo() {
      try {
        const topo = this.picker.ramo.at(-1);
        this.picker.filhos = topo ? await api(`/arvore/nos/${topo.id}/filhos`) : await api('/arvore/raizes');
      } catch (e) {
        this.erroPainel = e.message;
      }
    },
    async descer(n) { this.picker.ramo.push(n); await this.carregarRamo(); },
    async subir() { this.picker.ramo.pop(); await this.carregarRamo(); },

    async buscar() {
      const q = this.picker.q.trim();
      const seq = ++this.buscaSeq;
      if (q.length < 2) { this.picker.resultados = []; return; }
      try {
        const achados = await api(`/arvore/busca?q=${encodeURIComponent(q)}&limite=30`);
        if (seq === this.buscaSeq) this.picker.resultados = achados;
      } catch (e) {
        this.erroPainel = e.message;
      }
    },

    alternarNo(n) {
      const i = this.picker.selecionados.findIndex((x) => x.id === n.id);
      if (i >= 0) { this.picker.selecionados.splice(i, 1); return; }
      if (this.picker.selecionados.length >= 3) { this.erroPainel = 'Uma parte liga a até 3 nós.'; return; }
      this.erroPainel = null;
      this.picker.selecionados.push(n.caminho ? n : { ...n, caminho: [...this.picker.ramo.map((r) => r.nome), n.nome] });
    },

    gravarNos() {
      const corpo = { nos: this.picker.selecionados.map((n) => n.id), natureza: this.picker.natureza };
      if (this.painel.tipo === 'decisao') {
        return this.agirDecisao(() => api(`/decisoes/${this.painel.decisao.id}`, { method: 'PUT', body: JSON.stringify({ ...corpo, comentario: this.painel.decisao.comentario }) }));
      }
      return this.agir(async () => this.pedido(`/partes/${this.painel.parte.id}/nos`, 'PUT',
        { ...corpo, ...(await this.escopo(corpo.nos, corpo.natureza)) }), { fecharPainel: true });
    },

    // ------------------------------------------------ decisões
    async abrirDecisoes() {
      this.aba = 'decisoes';
      await this.carregarDecisoes();
    },
    async carregarDecisoes() {
      try {
        this.decisoes = await api('/decisoes');
      } catch (e) {
        this.erroAcao = e.message;
      }
    },
    async agirDecisao(fazer) {
      this.trabalhando = true;
      this.erroPainel = null;
      try {
        await fazer();
        this.painel = null;
        await this.carregarDecisoes();
        await this.recarregar();
      } catch (e) {
        if (this.painel) this.erroPainel = e.message; else this.erroAcao = e.message;
      } finally {
        this.trabalhando = false;
      }
    },
    async abrirDecisao(d) {
      this.erroPainel = null;
      this.painel = { tipo: 'decisao', modo: 'nos', titulo: 'Decisão: ' + d.parte, decisao: d };
      this.picker = { q: '', resultados: [], ramo: [], filhos: [], selecionados: d.nos.filter((n) => n.nome).map((n) => ({ ...n })), natureza: d.natureza ?? 'EXATO' };
      await this.carregarRamo();
    },
    revogar(d) {
      if (!window.confirm(`Revogar a decisão sobre "${d.parte}"? Ela deixa de valer antes da IA; as partes já ligadas não mudam.`)) return;
      return this.agirDecisao(() => api(`/decisoes/${d.id}`, { method: 'DELETE' }));
    },
  }));
});
