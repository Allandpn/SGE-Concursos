// js/pages.js — um componente Alpine por página (09_CODE_STYLE §8).

document.addEventListener('alpine:init', () => {

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
    blocoConteudo: null,

    async init() {
      this.carregando = true;
      this.erro = null;
      try {
        const plano = await api('/turno/plano');
        this.fila = plano.filaRecuperacao;
        this.blocoConteudo = plano.blocoConteudo;
      } catch (e) {
        this.erro = e.message;
      } finally {
        this.carregando = false;
      }
    },

    get vazio() {
      return this.fila.length === 0 && !this.blocoConteudo;
    },

    abrirRecuperacao(item) {
      Alpine.store('recuperacao').selecionar(item);
      window.location.hash = `#/recuperar/${item.assuntoId}`;
    },

    abrirRegistroConteudo(assunto) {
      Alpine.store('registrar').selecionar(assunto);
      window.location.hash = `#/registrar/conteudo/${assunto.id}`;
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
    tentativaId: null,
    inicioEm: null,
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
      this.erro = null;
      this.tentativaId = crypto.randomUUID(); // D-45: gerado ao abrir a tela
      this.inicioEm = Date.now();

      if (!this.semDados) {
        try {
          this.segmentos = await api(`/assuntos/${assuntoId}/segmentos`);
        } catch (e) {
          // Lista de segmentos é auxiliar, não bloqueia o registro — se
          // falhar, o campo de segmento simplesmente não aparece (§7A).
          this.segmentos = [];
        }
      }
    },

    selecionarTipo(tipo) {
      this.tipo = tipo;
      this.segmentoId = null;
      this.feitas = null;
      this.certas = null;
      this.previsao = null;
    },

    get temSegmentos() {
      return this.segmentos.length > 0;
    },

    async registrar() {
      this.erro = null;
      this.enviando = true;

      const tempoMinutos = Math.max(1, Math.round((Date.now() - this.inicioEm) / 60000));
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
    grupos: [], // [{ disciplina, assuntos: [{...AssuntoResponse, fase}] }]

    async init() {
      this.carregando = true;
      this.erro = null;
      try {
        const disciplinas = await api('/disciplinas');
        this.grupos = await Promise.all(disciplinas.map(async (disciplina) => {
          const assuntos = await api(`/assuntos?disciplinaId=${disciplina.id}`);
          const comFase = await Promise.all(assuntos.map(async (assunto) => {
            const { fase } = await api(`/assuntos/${assunto.id}/fase`);
            return { ...assunto, fase };
          }));
          return { disciplina, assuntos: comFase };
        }));
      } catch (e) {
        this.erro = e.message;
      } finally {
        this.carregando = false;
      }
    },

    get vazio() {
      return this.grupos.every((g) => g.assuntos.length === 0);
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
    retencao: [],
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

    async init() {
      this.carregando = true;
      this.erro = null;
      try {
        const disciplinas = await api('/disciplinas');
        this.disciplinasPorId = Object.fromEntries(disciplinas.map((d) => [d.id, d.nome]));
        await this.carregarM1();
        this.m3 = await api('/metricas/m3');
        this.m4 = await api('/metricas/m4');
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
});
