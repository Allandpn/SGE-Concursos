// js/leitor.js — o Leitor (J-10; 01_DOMINIO §3.17, D-114 a D-121; ADR-052; docs/SPRINT-31-LEITOR.md §4).
//
// Uma página por vez: canvas do PDF.js, a camada de texto dele por cima (para selecionar) e, entre as duas, a camada
// das marcações. Toda posição é guardada em fração da página, então serve em qualquer largura e zoom.
// Cada gesto vira uma mudança com identificador do aparelho (D-118), aplicada aqui na hora e mandada pela fila.

const PDFJS = '/vendor/pdfjs-4.10.38/';
const CORES = {
  IMPORTANTE: { nome: 'importante', fundo: 'rgba(245, 215, 110, 0.55)', borda: '#c9a227' },
  DUVIDA: { nome: 'dúvida', fundo: 'rgba(242, 165, 154, 0.55)', borda: '#c4574a' },
  DECORAR: { nome: 'decorar', fundo: 'rgba(166, 220, 176, 0.6)', borda: '#3c8a4c' },
};
let pdfjsCarregado = null;

function carregarPdfjs() {
  if (!pdfjsCarregado) {
    pdfjsCarregado = import(`${PDFJS}pdf.min.mjs`).then((lib) => {
      lib.GlobalWorkerOptions.workerSrc = `${PDFJS}pdf.worker.min.mjs`;
      return lib;
    });
  }
  return pdfjsCarregado;
}

// Junta os retângulos de uma seleção que estão na mesma linha: a camada de texto quebra uma linha em vários spans.
function juntarRetangulos(rets) {
  const ordenados = rets.filter((r) => r[2] > 0.001 && r[3] > 0.001).sort((a, b) => a[1] - b[1] || a[0] - b[0]);
  const saida = [];
  for (const r of ordenados) {
    const u = saida[saida.length - 1];
    if (u && Math.abs(u[1] - r[1]) < r[3] * 0.5 && r[0] <= u[0] + u[2] + 0.01) {
      const fim = Math.max(u[0] + u[2], r[0] + r[2]);
      const topo = Math.min(u[1], r[1]);
      u[3] = Math.max(u[1] + u[3], r[1] + r[3]) - topo;
      u[1] = topo;
      u[2] = fim - u[0];
    } else {
      saida.push([...r]);
    }
  }
  return saida.map((r) => r.map((v) => Math.round(v * 10000) / 10000));
}

document.addEventListener('alpine:init', () => {
  // O documento e a renderização do PDF.js ficam fora do estado reativo: o Alpine os embrulharia num Proxy, e os campos
  // privados das classes do PDF.js (#campo) quebram atrás de um Proxy.
  const pdf = { doc: null, render: null, geracao: 0 };

  Alpine.data('paginaLeitor', () => ({
    segmentoId: null,
    leitura: null,
    carregando: false,
    erro: null,
    semArquivo: false,
    naoGuardado: false,
    online: navigator.onLine,
    guardado: false,
    guardando: false,
    pendentes: 0,
    pagina: 1,
    totalPaginas: 0,
    zoom: 1,
    marcacoes: {}, // chave → marcação (forma + estado vigente)
    selecao: null, // {retangulos, citacao, x, y}
    painel: null, // a marcação aberta (destaque ou nota de página)
    notaTexto: '',
    menuAberto: false,
    listaAberta: false,
    filtroCor: null,
    historico: null,
    _timerPagina: null,
    _timerFila: null,
    _toque: null,

    init() {
      window.addEventListener('online', () => { this.online = true; this.enviarFila(); });
      window.addEventListener('offline', () => { this.online = false; });
      document.addEventListener('selectionchange', () => this.lerSelecao());
      window.addEventListener('resize', () => { if (pdf.doc) this.desenhar(); });
    },

    // Chamado pelo router em #/ler/{id}.
    async abrir(segmentoId, paginaPedida = null) {
      this.parar();
      this.segmentoId = segmentoId;
      this.leitura = null;
      this.erro = null;
      this.semArquivo = false;
      this.naoGuardado = false;
      this.selecao = null;
      this.painel = null;
      this.menuAberto = false;
      this.listaAberta = false;
      this.historico = null;
      this.carregando = true;
      this.online = navigator.onLine;
      pdf.doc = null;
      const guardadoLocal = await offline.bloco(segmentoId);
      this.guardado = !!guardadoLocal;
      try {
        this.leitura = await api(`/segmentos/${segmentoId}/leitura`);
        offline.atualizarLeitura(this.leitura);
      } catch (e) {
        if (e.codigo === 'REDE_INDISPONIVEL' && guardadoLocal) {
          this.leitura = guardadoLocal.leitura;
        } else if (e.codigo === 'REDE_INDISPONIVEL') {
          this.naoGuardado = true;
          this.carregando = false;
          return;
        } else {
          this.erro = e.message;
          this.carregando = false;
          return;
        }
      }
      if (!this.leitura.arquivo || !this.leitura.arquivo.startsWith('/material/')) {
        this.semArquivo = true; // link antigo do Drive: só "Abrir fora do app"
        this.carregando = false;
        return;
      }
      await this.montarMarcacoes();
      try {
        const [lib, resposta] = await Promise.all([carregarPdfjs(), fetch(this.leitura.arquivo, { credentials: 'same-origin' })]);
        if (!resposta.ok) throw new Error(`O PDF do bloco não abriu (${resposta.status}).`);
        const dados = new Uint8Array(await resposta.arrayBuffer());
        pdf.doc = await lib.getDocument({ data: dados, standardFontDataUrl: `${PDFJS}standard_fonts/`, isEvalSupported: false }).promise;
      } catch (e) {
        this.carregando = false;
        if (!navigator.onLine) this.naoGuardado = true;
        else this.erro = e.message || 'O PDF do bloco não abriu.';
        return;
      }
      this.totalPaginas = pdf.doc.numPages;
      // D-128: o ponto que faltou abre na página dele; sem pedido, a última página aberta (D-121).
      this.pagina = Math.min(Math.max(paginaPedida || this.leitura.ultimaPagina || 1, 1), this.totalPaginas);
      this.carregando = false;
      await this.$nextTick();
      await this.desenhar();
      this.enviarFila();
      this._timerFila = setInterval(() => this.enviarFila(), 30000);
    },

    parar() {
      clearInterval(this._timerFila);
      this._timerFila = null;
    },

    // As marcações do servidor (ou as guardadas), com as mudanças ainda na fila por cima.
    async montarMarcacoes() {
      const mapa = {};
      for (const m of this.leitura.marcacoes || []) mapa[m.chave] = { ...m };
      const fila = (await offline.fila()).filter((m) => (m.segmentoId ?? this.segmentoId) === this.segmentoId || mapa[m.marcacaoChave]);
      fila.sort((a, b) => a.editadaEm.localeCompare(b.editadaEm));
      for (const m of fila) this.aplicarLocal(m, mapa);
      this.marcacoes = mapa;
      this.pendentes = (await offline.fila()).length;
    },

    aplicarLocal(m, mapa = this.marcacoes) {
      const atual = mapa[m.marcacaoChave];
      if (!atual && !m.tipo) return;
      if (atual && atual.editadaEm && atual.editadaEm > m.editadaEm) return; // D-117: vale a mais recente
      mapa[m.marcacaoChave] = {
        ...(atual || {
          chave: m.marcacaoChave, segmentoId: m.segmentoId, pagina: m.pagina, tipo: m.tipo, destaqueChave: m.destaqueChave ?? null,
          retangulos: m.retangulos ?? null, citacao: m.citacao ?? null, posicaoX: m.posicaoX ?? null, posicaoY: m.posicaoY ?? null,
        }),
        cor: m.cor ?? null,
        texto: m.texto ?? null,
        removida: !!m.removida,
        editadaEm: m.editadaEm,
      };
    },

    // Um gesto: aplica aqui, guarda na fila e tenta mandar.
    async mudar(m) {
      // Cópia simples: os arrays vindos do estado do Alpine são Proxies, e o IndexedDB não os clona.
      const mudanca = JSON.parse(JSON.stringify({ mudancaId: crypto.randomUUID(), editadaEm: new Date().toISOString(), removida: false, ...m }));
      this.aplicarLocal(mudanca);
      this.marcacoes = { ...this.marcacoes };
      await offline.enfileirar(mudanca);
      this.pendentes = (await offline.fila()).length;
      this.desenharMarcacoes();
      this.enviarFila();
    },

    async enviarFila() {
      this.pendentes = await offline.enviar();
    },

    // --- Página ------------------------------------------------------------------------------------------------

    async desenhar() {
      if (!pdf.doc) return;
      // Dois desenhos seguidos (abrir, zoom, girar a tela): o mais antigo desiste em vez de montar uma camada de texto velha.
      const geracao = ++pdf.geracao;
      const n = this.pagina;
      const pagina = await pdf.doc.getPage(n);
      const caixa = this.$refs.pagina;
      const largura = Math.max(caixa.parentElement.clientWidth - 2, 200);
      const base = pagina.getViewport({ scale: 1 });
      const escala = (largura / base.width) * this.zoom;
      const vista = pagina.getViewport({ scale: escala });
      const dpr = window.devicePixelRatio || 1;
      const canvas = this.$refs.canvas;
      canvas.width = Math.floor(vista.width * dpr);
      canvas.height = Math.floor(vista.height * dpr);
      canvas.style.width = `${Math.floor(vista.width)}px`;
      canvas.style.height = `${Math.floor(vista.height)}px`;
      caixa.style.width = `${Math.floor(vista.width)}px`;
      caixa.style.height = `${Math.floor(vista.height)}px`;
      caixa.style.setProperty('--scale-factor', String(escala));
      if (pdf.render) pdf.render.cancel();
      pdf.render = pagina.render({ canvasContext: canvas.getContext('2d'), viewport: vista, transform: dpr !== 1 ? [dpr, 0, 0, dpr, 0, 0] : null });
      try {
        await pdf.render.promise;
      } catch (e) {
        return; // outra página pediu desenho no meio
      }
      if (geracao !== pdf.geracao) return;
      const camada = this.$refs.texto;
      camada.innerHTML = '';
      const lib = await carregarPdfjs();
      const texto = new lib.TextLayer({ textContentSource: pagina.streamTextContent(), container: camada, viewport: vista });
      await texto.render();
      if (geracao !== pdf.geracao) return;
      this.desenharMarcacoes();
    },

    irPara(n) {
      const alvo = Math.min(Math.max(1, n), this.totalPaginas);
      if (alvo === this.pagina) return;
      this.pagina = alvo;
      this.selecao = null;
      this.painel = null;
      this.listaAberta = false;
      this.desenhar();
      this.$refs.rolagem.scrollTop = 0;
      // D-121: guarda a página (com rede); sem rede, só o que está aberto.
      clearTimeout(this._timerPagina);
      this._timerPagina = setTimeout(() => {
        if (navigator.onLine) api(`/segmentos/${this.segmentoId}/leitura`, { method: 'PUT', body: JSON.stringify({ pagina: this.pagina }) }).catch(() => {});
      }, 800);
    },

    mudarZoom(fator) {
      this.zoom = Math.min(Math.max(this.zoom * fator, 0.6), 3);
      this.desenhar();
    },

    get blocoAnterior() {
      const i = this.leitura ? this.leitura.blocos.findIndex((b) => b.id === this.segmentoId) : -1;
      return i > 0 ? this.leitura.blocos[i - 1] : null;
    },

    get blocoSeguinte() {
      const i = this.leitura ? this.leitura.blocos.findIndex((b) => b.id === this.segmentoId) : -1;
      return i >= 0 && i < this.leitura.blocos.length - 1 ? this.leitura.blocos[i + 1] : null;
    },

    // --- Marcações na página -----------------------------------------------------------------------------------

    get vigentes() {
      const removidos = new Set(Object.values(this.marcacoes).filter((m) => m.removida).map((m) => m.chave));
      return Object.values(this.marcacoes).filter((m) => !m.removida && !(m.destaqueChave && removidos.has(m.destaqueChave)));
    },

    notaDe(destaque) {
      return this.vigentes.find((m) => m.tipo === 'NOTA_TRECHO' && m.destaqueChave === destaque.chave) || null;
    },

    desenharMarcacoes() {
      const camada = this.$refs.marcas;
      const pinos = this.$refs.pinos;
      if (!camada || !pinos) return;
      camada.innerHTML = '';
      pinos.innerHTML = '';
      for (const m of this.vigentes.filter((x) => x.pagina === this.pagina)) {
        if (m.tipo === 'DESTAQUE') {
          const cor = CORES[m.cor] || CORES.IMPORTANTE;
          m.retangulos.forEach((r, i) => {
            const d = document.createElement('div');
            d.className = 'leitor-destaque';
            Object.assign(d.style, { left: `${r[0] * 100}%`, top: `${r[1] * 100}%`, width: `${r[2] * 100}%`, height: `${r[3] * 100}%`, background: cor.fundo });
            if (this.painel && this.painel.chave === m.chave) d.style.outline = `2px solid ${cor.borda}`;
            camada.appendChild(d);
            if (i === m.retangulos.length - 1 && this.notaDe(m)) {
              const b = this.pino('✎', r[0] + r[2], r[1], () => this.abrirPainel(m));
              b.classList.add('leitor-pino-nota');
              pinos.appendChild(b);
            }
          });
        } else if (m.tipo === 'NOTA_PAGINA') {
          pinos.appendChild(this.pino('✎', m.posicaoX, m.posicaoY, () => this.abrirPainel(m)));
        }
      }
    },

    pino(rotulo, x, y, acao) {
      const b = document.createElement('button');
      b.type = 'button';
      b.className = 'leitor-pino';
      b.textContent = rotulo;
      b.setAttribute('aria-label', 'Abrir nota');
      Object.assign(b.style, { left: `${x * 100}%`, top: `${y * 100}%` });
      b.addEventListener('click', (e) => { e.stopPropagation(); acao(); });
      return b;
    },

    // A seleção de texto na camada do PDF.js vira retângulos em fração da página.
    lerSelecao() {
      if (!pdf.doc || this.painel) return;
      const sel = window.getSelection();
      const camada = this.$refs.texto;
      if (!sel || sel.isCollapsed || !sel.rangeCount || !camada || !camada.contains(sel.anchorNode)) {
        if (this.selecao && (!sel || sel.isCollapsed)) setTimeout(() => { if (window.getSelection().isCollapsed) this.selecao = null; }, 250);
        return;
      }
      const citacao = sel.toString().replace(/\s+/g, ' ').trim();
      if (!citacao) return;
      const caixa = this.$refs.pagina.getBoundingClientRect();
      const rets = [...sel.getRangeAt(0).getClientRects()]
        .map((r) => [(r.left - caixa.left) / caixa.width, (r.top - caixa.top) / caixa.height, r.width / caixa.width, r.height / caixa.height])
        .filter((r) => r[0] > -0.01 && r[1] > -0.01 && r[0] < 1 && r[1] < 1);
      const retangulos = juntarRetangulos(rets);
      if (!retangulos.length) return;
      const ultimo = retangulos[retangulos.length - 1];
      this.selecao = { retangulos, citacao: citacao.slice(0, 2000), x: Math.min(ultimo[0], 0.55), y: ultimo[1] + ultimo[3] };
    },

    async marcar(cor, comNota = false) {
      if (!this.selecao) return;
      const chave = crypto.randomUUID();
      await this.mudar({
        marcacaoChave: chave, segmentoId: this.segmentoId, pagina: this.pagina, tipo: 'DESTAQUE',
        retangulos: this.selecao.retangulos, citacao: this.selecao.citacao, cor,
      });
      window.getSelection().removeAllRanges();
      this.selecao = null;
      if (comNota) this.abrirPainel(this.marcacoes[chave]);
    },

    // Toque fora de texto: em cima de um destaque abre o painel; tocar e segurar (ou clique duplo) cria nota de página.
    tocarPagina(evento) {
      if (this.selecao || (window.getSelection() && !window.getSelection().isCollapsed)) return;
      const ponto = this.pontoNaPagina(evento);
      const alvo = this.vigentes.find((m) => m.tipo === 'DESTAQUE' && m.pagina === this.pagina
        && m.retangulos.some((r) => ponto.x >= r[0] && ponto.x <= r[0] + r[2] && ponto.y >= r[1] - 0.004 && ponto.y <= r[1] + r[3] + 0.004));
      if (alvo) this.abrirPainel(alvo);
      else if (this.painel) this.fecharPainel();
    },

    comecarToque(evento) {
      const ponto = this.pontoNaPagina(evento);
      clearTimeout(this._toque);
      this._toque = setTimeout(() => {
        if (window.getSelection() && !window.getSelection().isCollapsed) return;
        this.criarNotaDePagina(ponto);
      }, 650);
    },

    cancelarToque() {
      clearTimeout(this._toque);
    },

    pontoNaPagina(evento) {
      const caixa = this.$refs.pagina.getBoundingClientRect();
      const t = evento.touches ? evento.touches[0] : evento;
      return { x: Math.min(Math.max((t.clientX - caixa.left) / caixa.width, 0), 1), y: Math.min(Math.max((t.clientY - caixa.top) / caixa.height, 0), 1) };
    },

    criarNotaDePagina(ponto) {
      this.selecao = null;
      this.painel = { novo: true, tipo: 'NOTA_PAGINA', posicaoX: Math.round(ponto.x * 10000) / 10000, posicaoY: Math.round(ponto.y * 10000) / 10000 };
      this.notaTexto = '';
    },

    abrirPainel(m) {
      window.getSelection().removeAllRanges();
      this.selecao = null;
      this.listaAberta = false;
      this.painel = m;
      this.notaTexto = m.tipo === 'DESTAQUE' ? (this.notaDe(m)?.texto || '') : (m.texto || '');
      this.desenharMarcacoes();
    },

    fecharPainel() {
      this.painel = null;
      this.desenharMarcacoes();
    },

    async trocarCor(cor) {
      const m = this.painel;
      if (!m || m.tipo !== 'DESTAQUE' || m.cor === cor) return;
      await this.mudar({ marcacaoChave: m.chave, cor, texto: null });
      this.painel = this.marcacoes[m.chave];
      this.desenharMarcacoes();
    },

    async salvarNota() {
      const m = this.painel;
      const texto = this.notaTexto.trim();
      if (m.novo) {
        if (texto) {
          await this.mudar({ marcacaoChave: crypto.randomUUID(), segmentoId: this.segmentoId, pagina: this.pagina, tipo: 'NOTA_PAGINA',
            posicaoX: m.posicaoX, posicaoY: m.posicaoY, texto });
        }
      } else if (m.tipo === 'DESTAQUE') {
        const nota = this.notaDe(m);
        if (nota && nota.texto !== texto) await this.mudar({ marcacaoChave: nota.chave, texto: texto || null, removida: !texto });
        else if (!nota && texto) {
          await this.mudar({ marcacaoChave: crypto.randomUUID(), segmentoId: this.segmentoId, pagina: this.pagina, tipo: 'NOTA_TRECHO',
            destaqueChave: m.chave, texto });
        }
      } else if (m.texto !== texto) {
        await this.mudar({ marcacaoChave: m.chave, texto: texto || m.texto, removida: !texto });
      }
      this.fecharPainel();
    },

    async remover() {
      const m = this.painel;
      if (!m || m.novo) { this.fecharPainel(); return; }
      await this.mudar({ marcacaoChave: m.chave, cor: m.cor, texto: m.texto, removida: true });
      this.fecharPainel();
    },

    rotuloCor(cor) {
      return CORES[cor] ? CORES[cor].nome : '';
    },

    corDe(cor) {
      return CORES[cor] ? CORES[cor].fundo : 'transparent';
    },

    bordaDe(cor) {
      return CORES[cor] ? CORES[cor].borda : '#0694a2';
    },

    // --- Lista, histórico, menu ------------------------------------------------------------------------------

    get lista() {
      const itens = this.vigentes.filter((m) => m.tipo !== 'NOTA_TRECHO')
        .filter((m) => !this.filtroCor || (this.filtroCor === 'NOTA' ? m.tipo === 'NOTA_PAGINA' || this.notaDe(m) : m.cor === this.filtroCor))
        .sort((a, b) => a.pagina - b.pagina || ((a.retangulos?.[0]?.[1] ?? a.posicaoY) - (b.retangulos?.[0]?.[1] ?? b.posicaoY)));
      const paginas = [];
      for (const m of itens) {
        let p = paginas.find((x) => x.pagina === m.pagina);
        if (!p) paginas.push((p = { pagina: m.pagina, itens: [] }));
        p.itens.push(m);
      }
      return paginas;
    },

    contar(cor) {
      const v = this.vigentes.filter((m) => m.tipo !== 'NOTA_TRECHO');
      if (!cor) return v.length;
      if (cor === 'NOTA') return v.filter((m) => m.tipo === 'NOTA_PAGINA' || this.notaDe(m)).length;
      return v.filter((m) => m.cor === cor).length;
    },

    irParaMarcacao(m) {
      this.listaAberta = false;
      if (m.pagina !== this.pagina) this.irPara(m.pagina);
      setTimeout(() => this.abrirPainel(m), 300);
    },

    async abrirHistorico() {
      this.menuAberto = false;
      this.historico = { carregando: true };
      try {
        this.historico = await api(`/segmentos/${this.segmentoId}/marcacoes/historico`);
      } catch (e) {
        this.historico = { erro: e.codigo === 'REDE_INDISPONIVEL' ? 'O histórico precisa de rede.' : e.message };
      }
    },

    async guardarSemRede() {
      this.guardando = true;
      try {
        this.guardado = await offline.guardar(this.segmentoId, false);
      } catch (e) {
        this.erro = 'Não foi possível guardar este bloco agora.';
      } finally {
        this.guardando = false;
        this.menuAberto = false;
      }
    },

    // D-120: "Terminei" abre o Registrar de conteúdo com o assunto e o bloco; só a confirmação grava.
    terminei() {
      if (!this.online) return;
      Alpine.store('registrar').selecionar({ id: this.leitura.assuntoId, nome: this.leitura.assuntoNome, segmentoSugeridoId: this.segmentoId });
      window.location.hash = `#/registrar/conteudo/${this.leitura.assuntoId}`;
    },

    voltar() {
      if (window.history.length > 1) window.history.back();
      else window.location.hash = '#/hoje';
    },
  }));
});
