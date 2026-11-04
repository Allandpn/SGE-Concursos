// js/leitor.js — o Leitor (J-10; 01_DOMINIO §3.17, D-114 a D-121; ADR-052; docs/SPRINT-31-LEITOR.md §4).
//
// Cada página é um elemento próprio: canvas do PDF.js, a camada de texto dele por cima (para selecionar) e, entre as duas,
// a camada das marcações. Toda posição é guardada em fração da página, então serve em qualquer largura e zoom.
// Passar as páginas (J-10, Sprint 35): rolagem contínua (padrão; cada página desenhada ao chegar perto da tela) ou uma
// página por vez, deslizando para o lado. Pinça amplia; o contador abre "ir para a página".
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
  // `paginas`: uma entrada por página — o elemento, as camadas, o tamanho em escala 1 e se está desenhada.
  const pdf = { doc: null, geracao: 0, paginas: [], observador: null };
  const MODO_PADRAO = 'continua';

  function lerModo() {
    try { return window.localStorage.getItem('leitor.modo') === 'pagina' ? 'pagina' : MODO_PADRAO; } catch (e) { return MODO_PADRAO; }
  }

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
    modo: MODO_PADRAO, // 'continua' | 'pagina' — guardado no aparelho (J-10)
    irParaAberto: false,
    irParaNumero: '',
    _gesto: null, // deslizar ou pinça em andamento

    init() {
      this.modo = lerModo();
      window.addEventListener('online', () => { this.online = true; this.enviarFila(); });
      window.addEventListener('offline', () => { this.online = false; });
      document.addEventListener('selectionchange', () => this.lerSelecao());
      window.addEventListener('resize', () => { if (pdf.doc) this.redimensionar(); });
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
      this.desmontarPaginas();
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
      await this.montarPaginas();
      this.posicionarEm(this.pagina);
      this.enviarFila();
      this._timerFila = setInterval(() => this.enviarFila(), 30000);
    },

    parar() {
      clearInterval(this._timerFila);
      this._timerFila = null;
      this.irParaAberto = false;
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

    // --- Páginas -----------------------------------------------------------------------------------------------

    desmontarPaginas() {
      if (pdf.observador) pdf.observador.disconnect();
      pdf.observador = null;
      pdf.paginas = [];
      pdf.geracao++;
      if (this.$refs.paginas) this.$refs.paginas.innerHTML = '';
    },

    // Um elemento por página, com o tamanho certo desde já (a rolagem precisa da altura de todas); o desenho vem depois.
    async montarPaginas() {
      this.desmontarPaginas();
      const conteudo = this.$refs.paginas;
      for (let n = 1; n <= this.totalPaginas; n++) {
        const pagina = await pdf.doc.getPage(n);
        const base = pagina.getViewport({ scale: 1 });
        const el = document.createElement('div');
        el.className = 'leitor-pagina';
        el.dataset.pagina = String(n);
        el.innerHTML = '<canvas></canvas><div class="leitor-marcas"></div><div class="textLayer"></div><div class="leitor-pinos"></div>';
        const p = { n, el, canvas: el.children[0], marcas: el.children[1], texto: el.children[2], pinos: el.children[3],
          largura: base.width, altura: base.height, escala: 1, desenhada: false, desenhando: false, tarefa: null };
        el.addEventListener('click', (e) => this.tocarPagina(e, p));
        el.addEventListener('dblclick', (e) => { e.preventDefault(); this.criarNotaDePagina(this.pontoNaPagina(e, p), p.n); });
        el.addEventListener('touchstart', (e) => { if (e.touches.length === 1) this.comecarToque(e, p); else this.cancelarToque(); }, { passive: true });
        el.addEventListener('touchend', () => this.cancelarToque());
        el.addEventListener('touchmove', () => this.cancelarToque(), { passive: true });
        conteudo.appendChild(el);
        pdf.paginas.push(p);
      }
      this.diagramar();
      // Rolagem contínua: desenha o que está a até uma tela e meia; solta o canvas do que se afastou (memória do celular).
      pdf.observador = new IntersectionObserver((entradas) => {
        for (const e of entradas) {
          const p = pdf.paginas[Number(e.target.dataset.pagina) - 1];
          if (!p) continue;
          if (e.isIntersecting) this.desenharPagina(p);
          else this.soltarPagina(p);
        }
      }, { root: this.$refs.rolagem, rootMargin: '150% 0px' });
      pdf.paginas.forEach((p) => pdf.observador.observe(p.el));
      this.mostrarModo();
    },

    // Largura da tela × zoom; muda o tamanho de todas e marca para redesenhar.
    diagramar() {
      const largura = Math.max(this.$refs.rolagem.clientWidth - 18, 200);
      pdf.geracao++;
      for (const p of pdf.paginas) {
        p.escala = (largura / p.largura) * this.zoom;
        const w = Math.floor(p.largura * p.escala);
        const h = Math.floor(p.altura * p.escala);
        p.el.style.width = `${w}px`;
        p.el.style.height = `${h}px`;
        p.el.style.setProperty('--scale-factor', String(p.escala));
        this.soltarPagina(p);
      }
    },

    // Uma página por vez: só a atual aparece. Rolagem contínua: todas, uma embaixo da outra.
    mostrarModo() {
      for (const p of pdf.paginas) p.el.style.display = this.modo === 'pagina' && p.n !== this.pagina ? 'none' : '';
      if (this.modo === 'pagina') {
        const atual = pdf.paginas[this.pagina - 1];
        if (atual) this.desenharPagina(atual);
      }
    },

    async desenharPagina(p) {
      if (p.desenhada || p.desenhando || p.el.style.display === 'none') return;
      p.desenhando = true;
      const geracao = pdf.geracao;
      try {
        const pagina = await pdf.doc.getPage(p.n);
        const vista = pagina.getViewport({ scale: p.escala });
        const dpr = window.devicePixelRatio || 1;
        p.canvas.width = Math.floor(vista.width * dpr);
        p.canvas.height = Math.floor(vista.height * dpr);
        p.canvas.style.width = `${Math.floor(vista.width)}px`;
        p.canvas.style.height = `${Math.floor(vista.height)}px`;
        p.tarefa = pagina.render({ canvasContext: p.canvas.getContext('2d'), viewport: vista, transform: dpr !== 1 ? [dpr, 0, 0, dpr, 0, 0] : null });
        await p.tarefa.promise;
        if (geracao !== pdf.geracao) return;
        p.texto.innerHTML = '';
        const lib = await carregarPdfjs();
        const texto = new lib.TextLayer({ textContentSource: pagina.streamTextContent(), container: p.texto, viewport: vista });
        await texto.render();
        if (geracao !== pdf.geracao) return;
        p.desenhada = true;
        this.desenharMarcacoesDa(p);
      } catch (e) {
        // desenho cancelado (zoom, outra tela): a próxima chamada desenha de novo
      } finally {
        p.desenhando = false;
        p.tarefa = null;
        // O zoom mudou no meio: se a página ainda está à vista, desenha na escala nova.
        if (!p.desenhada && geracao !== pdf.geracao && this.paginaVisivel(p)) this.desenharPagina(p);
      }
    },

    soltarPagina(p) {
      if (p.tarefa) { try { p.tarefa.cancel(); } catch (e) { /* já terminou */ } }
      p.desenhada = false;
      p.canvas.width = 0;
      p.canvas.height = 0;
      p.texto.innerHTML = '';
      p.marcas.innerHTML = '';
      p.pinos.innerHTML = '';
    },

    paginaVisivel(p) {
      if (p.el.style.display === 'none') return false;
      const r = this.$refs.rolagem.getBoundingClientRect();
      const q = p.el.getBoundingClientRect();
      return q.bottom > r.top - r.height * 1.5 && q.top < r.bottom + r.height * 1.5;
    },

    // Rolagem contínua: a página mais visível é a do contador e a que o leitor guarda (D-121).
    aoRolar() {
      if (this.modo !== 'continua' || !pdf.paginas.length || this._gesto) return;
      const r = this.$refs.rolagem.getBoundingClientRect();
      let melhor = this.pagina;
      let area = 0;
      for (const p of pdf.paginas) {
        const q = p.el.getBoundingClientRect();
        const visivel = Math.min(q.bottom, r.bottom) - Math.max(q.top, r.top);
        if (visivel > area) { area = visivel; melhor = p.n; }
      }
      if (melhor !== this.pagina) {
        this.pagina = melhor;
        this.guardarPaginaDepois();
      }
    },

    posicionarEm(n) {
      const p = pdf.paginas[n - 1];
      if (!p) return;
      if (this.modo === 'pagina') {
        this.mostrarModo();
        this.$refs.rolagem.scrollTop = 0;
      } else {
        this.$refs.rolagem.scrollTop = p.el.offsetTop - 8;
      }
    },

    irPara(n) {
      const alvo = Math.min(Math.max(1, Number(n) || 1), this.totalPaginas);
      this.selecao = null;
      this.painel = null;
      this.listaAberta = false;
      this.irParaAberto = false;
      const mudou = alvo !== this.pagina;
      this.pagina = alvo;
      this.posicionarEm(alvo);
      if (mudou) this.guardarPaginaDepois();
    },

    // D-121: guarda a página (com rede); sem rede, só o que está aberto.
    guardarPaginaDepois() {
      clearTimeout(this._timerPagina);
      this._timerPagina = setTimeout(() => {
        if (navigator.onLine) api(`/segmentos/${this.segmentoId}/leitura`, { method: 'PUT', body: JSON.stringify({ pagina: this.pagina }) }).catch(() => {});
      }, 800);
    },

    abrirIrPara() {
      this.irParaNumero = String(this.pagina);
      this.irParaAberto = true;
      this.menuAberto = false;
    },

    escolherModo(modo) {
      this.menuAberto = false;
      if (modo === this.modo) return;
      this.modo = modo;
      try { window.localStorage.setItem('leitor.modo', modo); } catch (e) { /* sem armazenamento: vale só agora */ }
      this.mostrarModo();
      this.$nextTick(() => this.posicionarEm(this.pagina));
    },

    // Zoom pelos botões ou pela pinça: mantém a mesma página à vista.
    mudarZoom(fator) {
      const novo = Math.min(Math.max(this.zoom * fator, 0.6), 4);
      if (Math.abs(novo - this.zoom) < 0.01) return;
      const rolagem = this.$refs.rolagem;
      const p = pdf.paginas[this.pagina - 1];
      const dentro = p && this.modo === 'continua' ? (rolagem.scrollTop - p.el.offsetTop) / Math.max(p.el.offsetHeight, 1) : 0;
      this.zoom = novo;
      this.diagramar();
      if (p && this.modo === 'continua') rolagem.scrollTop = p.el.offsetTop + dentro * p.el.offsetHeight;
      for (const q of pdf.paginas) if (this.paginaVisivel(q)) this.desenharPagina(q);
    },

    redimensionar() {
      const p = pdf.paginas[this.pagina - 1];
      this.diagramar();
      if (p) this.posicionarEm(this.pagina);
      for (const q of pdf.paginas) if (this.paginaVisivel(q)) this.desenharPagina(q);
    },

    // --- Gestos: pinça (os dois modos) e deslizar (uma página por vez) -----------------------------------------

    gestoInicio(e) {
      if (e.touches.length === 2) {
        const [a, b] = e.touches;
        const caixa = this.$refs.paginas.getBoundingClientRect();
        this._gesto = { tipo: 'pinca', d0: Math.hypot(a.clientX - b.clientX, a.clientY - b.clientY), fator: 1,
          ox: (a.clientX + b.clientX) / 2 - caixa.left, oy: (a.clientY + b.clientY) / 2 - caixa.top };
        this.cancelarToque();
      } else if (e.touches.length === 1 && this.modo === 'pagina') {
        const t = e.touches[0];
        this._gesto = { tipo: 'deslizar', x: t.clientX, y: t.clientY, t: Date.now() };
      }
    },

    gestoMovimento(e) {
      const g = this._gesto;
      if (!g || g.tipo !== 'pinca' || e.touches.length !== 2) return;
      const [a, b] = e.touches;
      g.fator = Math.min(Math.max(Math.hypot(a.clientX - b.clientX, a.clientY - b.clientY) / Math.max(g.d0, 1), 0.5), 3);
      // Durante o gesto, só uma escala visual; o desenho de verdade vem ao soltar.
      Object.assign(this.$refs.paginas.style, { transformOrigin: `${g.ox}px ${g.oy}px`, transform: `scale(${g.fator})` });
    },

    gestoFim(e) {
      const g = this._gesto;
      if (!g) return;
      if (g.tipo === 'pinca') {
        if (e.touches.length > 0) return; // ainda há dedo na tela
        this._gesto = null;
        this.$refs.paginas.style.transform = '';
        if (Math.abs(g.fator - 1) > 0.05) this.mudarZoom(g.fator);
        return;
      }
      this._gesto = null;
      const t = e.changedTouches[0];
      const dx = t.clientX - g.x;
      const dy = t.clientY - g.y;
      const rolagem = this.$refs.rolagem;
      const ampliada = rolagem.scrollWidth > rolagem.clientWidth + 4;
      const selecionando = window.getSelection() && !window.getSelection().isCollapsed;
      // Deslizar de verdade: rápido, mais para o lado que para cima; ignorado ao selecionar texto ou com a página ampliada.
      if (selecionando || ampliada || this.selecao || this.painel) return;
      if (Math.abs(dx) < 60 || Math.abs(dy) > Math.abs(dx) * 0.6 || Date.now() - g.t > 700) return;
      this.irPara(this.pagina + (dx < 0 ? 1 : -1));
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
      for (const p of pdf.paginas) if (p.desenhada) this.desenharMarcacoesDa(p);
    },

    desenharMarcacoesDa(p) {
      const camada = p.marcas;
      const pinos = p.pinos;
      camada.innerHTML = '';
      pinos.innerHTML = '';
      for (const m of this.vigentes.filter((x) => x.pagina === p.n)) {
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
      const p = sel && sel.anchorNode ? pdf.paginas.find((x) => x.texto.contains(sel.anchorNode)) : null;
      if (!sel || sel.isCollapsed || !sel.rangeCount || !p) {
        if (this.selecao && (!sel || sel.isCollapsed)) setTimeout(() => { if (window.getSelection().isCollapsed) this.selecao = null; }, 250);
        return;
      }
      const citacao = sel.toString().replace(/\s+/g, ' ').trim();
      if (!citacao) return;
      const caixa = p.el.getBoundingClientRect();
      const rets = [...sel.getRangeAt(0).getClientRects()]
        .map((r) => [(r.left - caixa.left) / caixa.width, (r.top - caixa.top) / caixa.height, r.width / caixa.width, r.height / caixa.height])
        .filter((r) => r[0] > -0.01 && r[1] > -0.01 && r[0] < 1 && r[1] < 1);
      const retangulos = juntarRetangulos(rets);
      if (!retangulos.length) return;
      const ultimo = retangulos[retangulos.length - 1];
      // A barra de cores fica logo abaixo do trecho, em pixels dentro da área das páginas (vale nos dois modos).
      this.selecao = { pagina: p.n, retangulos, citacao: citacao.slice(0, 2000),
        left: p.el.offsetLeft + Math.min(ultimo[0], 0.55) * p.el.offsetWidth, top: p.el.offsetTop + (ultimo[1] + ultimo[3]) * p.el.offsetHeight + 6 };
    },

    async marcar(cor, comNota = false) {
      if (!this.selecao) return;
      const chave = crypto.randomUUID();
      await this.mudar({
        marcacaoChave: chave, segmentoId: this.segmentoId, pagina: this.selecao.pagina, tipo: 'DESTAQUE',
        retangulos: this.selecao.retangulos, citacao: this.selecao.citacao, cor,
      });
      window.getSelection().removeAllRanges();
      this.selecao = null;
      if (comNota) this.abrirPainel(this.marcacoes[chave]);
    },

    // Toque fora de texto: em cima de um destaque abre o painel; tocar e segurar (ou clique duplo) cria nota de página.
    tocarPagina(evento, p) {
      if (this.selecao || (window.getSelection() && !window.getSelection().isCollapsed)) return;
      const ponto = this.pontoNaPagina(evento, p);
      const alvo = this.vigentes.find((m) => m.tipo === 'DESTAQUE' && m.pagina === p.n
        && m.retangulos.some((r) => ponto.x >= r[0] && ponto.x <= r[0] + r[2] && ponto.y >= r[1] - 0.004 && ponto.y <= r[1] + r[3] + 0.004));
      if (alvo) this.abrirPainel(alvo);
      else if (this.painel) this.fecharPainel();
    },

    comecarToque(evento, p) {
      const ponto = this.pontoNaPagina(evento, p);
      clearTimeout(this._toque);
      this._toque = setTimeout(() => {
        if (window.getSelection() && !window.getSelection().isCollapsed) return;
        this.criarNotaDePagina(ponto, p.n);
      }, 650);
    },

    cancelarToque() {
      clearTimeout(this._toque);
    },

    pontoNaPagina(evento, p) {
      const caixa = p.el.getBoundingClientRect();
      const t = evento.touches ? evento.touches[0] : evento;
      return { x: Math.min(Math.max((t.clientX - caixa.left) / caixa.width, 0), 1), y: Math.min(Math.max((t.clientY - caixa.top) / caixa.height, 0), 1) };
    },

    criarNotaDePagina(ponto, n) {
      this.selecao = null;
      this.painel = { novo: true, tipo: 'NOTA_PAGINA', pagina: n, posicaoX: Math.round(ponto.x * 10000) / 10000, posicaoY: Math.round(ponto.y * 10000) / 10000 };
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
          await this.mudar({ marcacaoChave: crypto.randomUUID(), segmentoId: this.segmentoId, pagina: m.pagina, tipo: 'NOTA_PAGINA',
            posicaoX: m.posicaoX, posicaoY: m.posicaoY, texto });
        }
      } else if (m.tipo === 'DESTAQUE') {
        const nota = this.notaDe(m);
        if (nota && nota.texto !== texto) await this.mudar({ marcacaoChave: nota.chave, texto: texto || null, removida: !texto });
        else if (!nota && texto) {
          await this.mudar({ marcacaoChave: crypto.randomUUID(), segmentoId: this.segmentoId, pagina: m.pagina, tipo: 'NOTA_TRECHO',
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
      this.irPara(m.pagina);
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
