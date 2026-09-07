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
});
