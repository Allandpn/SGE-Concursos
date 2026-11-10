// js/qconcursos.js — o atalho para o QConcursos, a saída e a volta, e o lote rápido global
// (docs/SPRINT-36-QCONCURSOS.md §3; 01_DOMINIO §3.24, D-133, D-150 a D-156; ADR-058).
//
// O lote rápido mora aqui, na raiz, porque a volta do QConcursos pode acontecer em qualquer tela. "Fiz questões" em Hoje abre o
// mesmo painel. A saída fica só no aparelho (localStorage), nunca no servidor (ADR-058).

const QC_SAIDA = 'qc.saida';
const QC_VALIDADE_MS = 6 * 60 * 60 * 1000; // D-153: depois de 6 h, a saída é esquecida
const QC_MINIMO_MS = 60 * 1000; // voltar em menos de 1 min não abre nada (tocou sem querer)

function lerSaidaQc() {
  try {
    const v = window.localStorage.getItem(QC_SAIDA);
    return v ? JSON.parse(v) : null;
  } catch (e) {
    return null;
  }
}

function gravarSaidaQc(saida) {
  try {
    if (saida) window.localStorage.setItem(QC_SAIDA, JSON.stringify(saida));
    else window.localStorage.removeItem(QC_SAIDA);
  } catch (e) {
    // sem armazenamento (aba privada): o atalho ainda abre; só a volta não abre sozinha
  }
}

const CAUSAS_ERRO = [
  ['FALTA_CONHECIMENTO', 'Falta de conhecimento'], ['ESQUECIMENTO', 'Esquecimento'], ['INTERPRETACAO', 'Interpretação'],
  ['DESATENCAO', 'Desatenção'], ['PEGADINHA', 'Pegadinha'], ['CHUTE', 'Chute'], ['GESTAO_TEMPO', 'Gestão do tempo'],
];

document.addEventListener('alpine:init', () => {
  // D-173 (Sprint 45): ligar o assunto a nós da árvore do QConcursos, pela busca da árvore vigente.
  Alpine.store('vinculoQc', {
    aberto: false,
    assuntoId: null,
    nome: '',
    busca: '',
    resultados: [],
    escolhidos: [], // [{id, nome, caminho}]
    buscando: false,
    salvando: false,
    erro: null,
    semArvore: false,
    _espera: null,
    _pedido: 0,

    async abrir(assuntoId, nome) {
      Object.assign(this, { aberto: true, assuntoId, nome: nome || '', busca: nome || '', resultados: [], escolhidos: [], erro: null, semArvore: false });
      try {
        const atuais = await api(`/assuntos/${assuntoId}/qconcursos-vinculo`);
        this.escolhidos = atuais.filter((n) => n.naArvore).map((n) => ({ id: n.noId, nome: n.nome, caminho: n.caminho }));
      } catch (e) {
        this.escolhidos = [];
      }
      this.buscar();
    },

    fechar() {
      this.aberto = false;
    },

    digitou() {
      clearTimeout(this._espera);
      this._espera = setTimeout(() => this.buscar(), 300);
    },

    async buscar() {
      const q = this.busca.trim();
      const pedido = ++this._pedido;
      if (q.length < 2) { this.resultados = []; return; }
      this.buscando = true;
      try {
        const achados = await api(`/arvore/busca?q=${encodeURIComponent(q)}&limite=20`);
        if (pedido === this._pedido) { this.resultados = achados; this.semArvore = false; }
      } catch (e) {
        if (pedido !== this._pedido) return;
        this.resultados = [];
        if (e.codigo === 'ARVORE_NAO_CARREGADA') this.semArvore = true;
        else this.erro = e.message;
      } finally {
        if (pedido === this._pedido) this.buscando = false;
      }
    },

    escolhido(no) {
      return this.escolhidos.some((e) => e.id === no.id);
    },

    alternar(no) {
      if (this.escolhido(no)) this.escolhidos = this.escolhidos.filter((e) => e.id !== no.id);
      else if (this.escolhidos.length < 3) this.escolhidos = [...this.escolhidos, { id: no.id, nome: no.nome, caminho: no.caminho }];
    },

    get rotuloBotao() {
      if (!this.escolhidos.length) return 'Escolha um assunto do QConcursos';
      return this.escolhidos.length === 1 ? `Ligar a “${this.escolhidos[0].nome}”` : `Ligar aos ${this.escolhidos.length} escolhidos`;
    },

    async salvar() {
      if (!this.escolhidos.length) return;
      this.salvando = true;
      this.erro = null;
      try {
        await api(`/assuntos/${this.assuntoId}/qconcursos-vinculo`, { method: 'PUT', body: JSON.stringify({ noIds: this.escolhidos.map((e) => e.id) }) });
        await Alpine.store('qc').recarregar(this.assuntoId);
        document.dispatchEvent(new CustomEvent('sge:vinculo-qc', { detail: { assuntoId: this.assuntoId } }));
        this.aberto = false;
      } catch (e) {
        this.erro = e.message;
      } finally {
        this.salvando = false;
      }
    },

    async tirar(assuntoId) {
      try {
        await api(`/assuntos/${assuntoId}/qconcursos-vinculo`, { method: 'DELETE' });
        await Alpine.store('qc').recarregar(assuntoId);
        document.dispatchEvent(new CustomEvent('sge:vinculo-qc', { detail: { assuntoId } }));
      } catch (e) {
        // o vínculo segue; a tela mostra o que está no servidor na próxima carga
      }
    },
  });

  // D-175 e D-176 (Sprint 49, 04_FRONTEND §8L): o filtro do QConcursos — o padrão de Ajustes e a exceção do concurso, editados
  // na mesma folha. Na exceção, só o que difere do padrão é enviado (nulo segue o padrão).
  Alpine.store('filtroQc', {
    padrao: null,
    concursos: {}, // id → {concursoId, excecao, efetivo}
    aberto: false,
    concursoId: null,
    nomeConcurso: '',
    f: null, // o que a folha mostra e edita
    salvando: false,
    erro: null,
    colar: null, // {texto, lendo, erro, achados: [{tipo, numero, nome}]}

    NIVEIS: [[1, 'Fundamental'], [2, 'Médio'], [3, 'Superior']],
    DIFICULDADES: [[1, 'Muito fácil'], [2, 'Fácil'], [3, 'Médio'], [4, 'Difícil'], [5, 'Muito difícil']],
    ANOS: [[3, '3'], [5, '5'], [10, '10'], [0, 'Todos']],
    BANCAS: [['CONCURSO', 'Do concurso'], ['QUALQUER', 'Qualquer']],
    FORMATOS: [['BANCA', 'Da banca'], ['MULTIPLA_ESCOLHA', 'Múltipla'], ['CERTO_ERRADO', 'C/E'], ['QUALQUER', 'Qualquer']],
    ORDENS: [['QC', 'Padrão do QC'], ['RECENTES', 'Recentes'], ['DIFICEIS', 'Difíceis']],
    TIPOS: { CARGO: 'Cargo', FORMACAO: 'Formação', ATUACAO: 'Atuação', INSTITUICAO: 'Instituição' },
    CAMPOS: ['niveis', 'banca', 'anos', 'formato', 'dificuldades', 'ordem', 'tirarResolvidas', 'tirarAnuladas', 'tirarDesatualizadas', 'soComentadas', 'aprendidos'],
    // As opções que o QConcursos mostra (catálogo de 2026-11-03 do planejamento); sugestão de nome, o número é o que vale (D-176).
    SUGESTOES: {
      ATUACAO: ['Administrativa', 'Advocacia Pública', 'Aeronáutica', 'Agência Reguladora', 'Bancária', 'Conselho Profissional', 'Controle e Gestão',
        'Defensoria Pública', 'Educação', 'ENEM', 'Exército', 'Fiscal', 'Forças Armadas', 'Jurídica', 'Legislativa', 'Marinha', 'Ministério Público',
        'OAB', 'Outras', 'Policial', 'Prefeitura', 'Saúde', 'Segurança Pública', 'Tribunal', 'Tribunal de Contas e Controladoria', 'Vestibular'],
      FORMACAO: ['Administração', 'Arquitetura e Urbanismo', 'Arquivologia', 'Biblioteconomia', 'Biologia', 'Ciências Atuariais', 'Ciências Contábeis',
        'Comunicação Social', 'Direito', 'Economia', 'Educação Física', 'Enfermagem', 'Engenharia', 'Engenharia Civil', 'Engenharia Elétrica',
        'Engenharia Mecânica', 'Estatística', 'Farmácia', 'Física', 'Geografia', 'História', 'Letras', 'Matemática', 'Medicina', 'Nutrição',
        'Odontologia', 'Pedagogia', 'Psicologia', 'Química', 'Saúde Pública', 'Serviço Social', 'Tecnologia da Informação'],
    },

    async carregarPadrao() {
      this.padrao = await api('/qconcursos/filtro');
      return this.padrao;
    },

    async carregarConcurso(id) {
      const d = await api(`/concursos/${id}/qconcursos-filtro`);
      this.concursos = { ...this.concursos, [id]: d };
      return d;
    },

    async abrir(concursoId = null, nome = '') {
      this.erro = null;
      this.colar = null;
      try {
        await this.carregarPadrao();
        const base = concursoId ? (await this.carregarConcurso(concursoId)).efetivo : this.padrao;
        this.f = JSON.parse(JSON.stringify(base));
        Object.assign(this, { concursoId, nomeConcurso: nome || '', aberto: true });
      } catch (e) {
        this.erro = e.message;
      }
    },

    fechar() {
      this.aberto = false;
      this.colar = null;
    },

    tem(campo, v) {
      return !!this.f && (this.f[campo] || []).includes(v);
    },
    alternar(campo, v) {
      const l = this.f[campo] || [];
      this.f[campo] = l.includes(v) ? l.filter((x) => x !== v) : [...l, v].sort((a, b) => a - b);
      this.salvar();
    },
    escolher(campo, v) {
      if (this.f[campo] === v) return;
      this.f[campo] = v;
      this.salvar();
    },
    marcar(campo) {
      this.f[campo] = !this.f[campo];
      this.salvar();
    },
    tirarAprendido(a) {
      this.f.aprendidos = this.f.aprendidos.filter((x) => !(x.tipo === a.tipo && x.numero === a.numero));
      this.salvar();
    },

    chave(campo, v) {
      if (campo === 'aprendidos') return (v || []).map((a) => a.tipo + ':' + a.numero).sort().join(',');
      return JSON.stringify(v);
    },
    // Na exceção: o campo difere do padrão ("deste concurso").
    doConcurso(campo) {
      return !!this.concursoId && !!this.padrao && !!this.f && this.chave(campo, this.f[campo]) !== this.chave(campo, this.padrao[campo]);
    },

    async salvar() {
      this.salvando = true;
      this.erro = null;
      try {
        if (!this.concursoId) {
          this.padrao = await api('/qconcursos/filtro', { method: 'PUT', body: JSON.stringify(this.f) });
        } else {
          const excecao = {};
          this.CAMPOS.forEach((c) => { if (this.doConcurso(c)) excecao[c] = this.f[c]; });
          const d = await api(`/concursos/${this.concursoId}/qconcursos-filtro`, { method: 'PUT', body: JSON.stringify(excecao) });
          this.concursos = { ...this.concursos, [this.concursoId]: d };
        }
        Alpine.store('qc').esquecer(); // os atalhos já carregados levavam o filtro antigo
      } catch (e) {
        this.erro = e.message;
      } finally {
        this.salvando = false;
      }
    },

    async voltarAoPadrao(id) {
      this.erro = null;
      try {
        await api(`/concursos/${id}/qconcursos-filtro`, { method: 'DELETE' });
        await this.carregarConcurso(id);
        Alpine.store('qc').esquecer();
        if (this.aberto && this.concursoId === id) this.f = JSON.parse(JSON.stringify(this.concursos[id].efetivo));
      } catch (e) {
        this.erro = e.message;
      }
    },

    // D-176: lê os números do endereço; o nome já guardado vem preenchido, o resto a pessoa dá.
    abrirColar() {
      this.colar = { texto: '', lendo: false, erro: null, achados: [] };
    },
    async lerEndereco() {
      const c = this.colar;
      if (!c) return;
      c.erro = null;
      c.lendo = true;
      try {
        const achados = await api('/qconcursos/filtro/endereco', { method: 'POST', body: JSON.stringify({ endereco: c.texto }) });
        c.achados = achados.map((a) => ({ ...a, nome: a.nome || '' }));
      } catch (e) {
        c.erro = e.message;
      } finally {
        c.lendo = false;
      }
    },
    usarAchados() {
      const c = this.colar;
      if (!c || !c.achados.length) return;
      if (c.achados.some((a) => !a.nome.trim())) {
        c.erro = 'Dê um nome a cada número.';
        return;
      }
      const lista = [...(this.f.aprendidos || [])];
      c.achados.forEach((a) => {
        const i = lista.findIndex((x) => x.tipo === a.tipo && x.numero === a.numero);
        const item = { tipo: a.tipo, numero: a.numero, nome: a.nome.trim() };
        if (i >= 0) lista[i] = item; else lista.push(item);
      });
      this.f.aprendidos = lista;
      this.colar = null;
      this.salvar();
    },

    // O resumo de um filtro, em marcas curtas (Ajustes e o cartão do concurso).
    resumo(f) {
      if (!f) return [];
      const nome = (lista, v) => (lista.find((x) => x[0] === v) || [, v])[1];
      const r = [];
      r.push(f.niveis.length ? f.niveis.map((n) => nome(this.NIVEIS, n)).join(' e ') : 'Qualquer nível');
      r.push(f.banca === 'CONCURSO' ? 'Banca do concurso' : 'Qualquer banca');
      r.push(f.anos ? `Últimos ${f.anos} anos` : 'Todos os anos');
      r.push(f.formato === 'BANCA' ? 'Formato da banca' : f.formato === 'QUALQUER' ? 'Qualquer formato' : nome(this.FORMATOS, f.formato));
      if (f.dificuldades.length) r.push(f.dificuldades.map((n) => nome(this.DIFICULDADES, n)).join(', '));
      if (f.ordem !== 'QC') r.push(f.ordem === 'RECENTES' ? 'Mais recentes' : 'Mais difíceis');
      if (f.soComentadas) r.push('Com gabarito comentado');
      (f.aprendidos || []).forEach((a) => r.push(`${this.TIPOS[a.tipo]}: ${a.nome || 'nº ' + a.numero}`));
      return r;
    },
  });

  // D-150 a D-152: os atalhos vêm do servidor, em lote, e ficam guardados por assunto até a próxima carga da tela.
  Alpine.store('qc', {
    atalhos: {},

    async carregar(ids) {
      const faltam = [...new Set((ids || []).filter((id) => id != null))].filter((id) => !(id in this.atalhos));
      if (!faltam.length) return;
      try {
        const lista = await api(`/qconcursos/atalhos?assuntoIds=${faltam.join(',')}`);
        const novo = { ...this.atalhos };
        faltam.forEach((id) => { novo[id] = null; });
        lista.forEach((a) => { novo[a.assuntoId] = a; });
        this.atalhos = novo;
      } catch (e) {
        // auxiliar: sem atalho, a tela segue igual
      }
    },

    de(assuntoId) {
      return this.atalhos[assuntoId] || null;
    },

    // D-173: já perguntou e o assunto não tem para onde ir (nem vínculo, nem edital, nem disciplina que case).
    semAtalho(assuntoId) {
      return assuntoId != null && assuntoId in this.atalhos && this.atalhos[assuntoId] === null;
    },

    async recarregar(assuntoId) {
      const novo = { ...this.atalhos };
      delete novo[assuntoId];
      this.atalhos = novo;
      await this.carregar([assuntoId]);
    },

    esquecer() {
      this.atalhos = {};
    },

    anos(a) {
      const anos = [...a.url.matchAll(/publication_year%5B%5D=(\d{4})/g)].map((m) => Number(m[1]));
      return anos.length ? `${Math.min(...anos)}–${Math.max(...anos)}` : '';
    },

    // "8 questões · FGV · Certo/errado · 2021–2026 · Superior", com o aviso quando o filtro é mais largo (D-175: as marcas do filtro).
    detalhe(a, semMarcas = false) {
      if (!a) return '';
      const partes = [`${a.sugeridas} questões`];
      if (a.avisos.includes('SEM_BANCA')) partes.push('sem filtro de banca');
      if (!semMarcas) (a.marcas || []).forEach((m) => partes.push(m.rotulo));
      if (a.avisos.includes('DISCIPLINA')) partes.push('filtro pela disciplina inteira');
      if (a.avisos.includes('NO_FORA_DA_ARVORE')) partes.push('o assunto ligado saiu da árvore do QConcursos: ligue de novo');
      return partes.join(' · ');
    },

    largo(a) {
      return !!a && (a.avisos.includes('DISCIPLINA') || a.avisos.includes('SEM_BANCA') || a.avisos.includes('NO_FORA_DA_ARVORE'));
    },

    // D-173: o filtro pela disciplina inteira (ou um nó que saiu da árvore) pede ligar o assunto.
    pedeVinculo(a) {
      return !!a && !a.vinculado && (a.avisos.includes('DISCIPLINA') || a.avisos.includes('NO_FORA_DA_ARVORE'));
    },

    // D-153: tocar no atalho guarda a saída; o próprio <a target="_blank"> abre o QConcursos.
    sair(a, nome, previsao) {
      gravarSaidaQc({ assuntoId: a.assuntoId, nome, saiuEm: Date.now(), previsao: previsao ?? null });
    },

    // D-168: antes de ir, a previsão (M-3); o link da folha é o que abre o QConcursos (um gesto do usuário, sem bloqueio de janela).
    // §8K (Sprint 48): a folha "Questões" — a porta única: o filtro, o vínculo, a previsão, abrir e "já fiz".
    antes: null, // {assuntoId, nome, previsao}
    abrirQuestoes(item) {
      if (!item || item.assuntoId == null) return;
      this.antes = { assuntoId: item.assuntoId, nome: item.nome, previsao: null, desligadas: [] };
      this.carregar([item.assuntoId]);
    },
    antesDeIr(a, nome) { // compatível com os chamadores antigos
      if (a) this.abrirQuestoes({ assuntoId: a.assuntoId, nome });
    },
    get atalhoDaFolha() {
      return this.antes ? this.de(this.antes.assuntoId) : null;
    },
    // D-177: uma marca desligada tira os parâmetros dela só deste endereço, nesta vez; fechar a folha volta tudo.
    desligada(m) {
      return !!this.antes && (this.antes.desligadas || []).includes(m.chave);
    },
    alternarMarca(m) {
      if (!this.antes) return;
      const d = this.antes.desligadas || [];
      this.antes.desligadas = d.includes(m.chave) ? d.filter((c) => c !== m.chave) : [...d, m.chave];
    },
    get urlDaFolha() {
      const a = this.atalhoDaFolha;
      if (!a) return '';
      const fora = new Set((a.marcas || []).filter((m) => this.desligada(m)).flatMap((m) => m.parametros));
      if (!fora.size) return a.url;
      const [base, query] = a.url.split('?');
      return base + '?' + (query || '').split('&').filter((p) => !fora.has(p)).join('&');
    },
    ir() {
      const x = this.antes;
      const a = this.atalhoDaFolha;
      if (!x || !a) return;
      this.sair(a, x.nome, x.previsao);
      this.antes = null;
    },
    jaFiz() {
      const x = this.antes;
      if (!x) return;
      this.antes = null;
      Alpine.store('lote').abrir({ assuntoId: x.assuntoId, nome: x.nome });
    },

    encerrar() {
      gravarSaidaQc(null);
    },

    verificarVolta() {
      const saida = lerSaidaQc();
      if (!saida) return;
      const passou = Date.now() - saida.saiuEm;
      if (!(passou >= 0) || passou > QC_VALIDADE_MS) {
        gravarSaidaQc(null);
        return;
      }
      if (passou < QC_MINIMO_MS || Alpine.store('lote').visivel) return;
      Alpine.store('lote').abrir({ assuntoId: saida.assuntoId, nome: saida.nome }, { minutos: Math.max(1, Math.round(passou / 60000)), saida });
    },
  });

  // D-133 e D-156: o lote rápido — assunto, formato e tempo preenchidos; feitas, certas e a previsão; depois, os erros.
  // `visivel` abre e fecha o painel; `atual` fica até o próximo abrir, para o painel nunca ler um objeto apagado.
  Alpine.store('lote', {
    atual: null,
    visivel: false,
    causas: CAUSAS_ERRO,

    async abrir(item, opcoes = {}) {
      const daVolta = opcoes.minutos != null;
      this.atual = {
        assuntoId: item.assuntoId, nome: item.nome, formato: 'MULTIPLA_ESCOLHA', tempo: daVolta ? opcoes.minutos : null,
        doFoco: false, daVolta, fasesIds: [], previsao: null, feitas: null, certas: null, erro: null, enviando: false,
        tentativaId: crypto.randomUUID(), limiares: null, porQuestao: 3,
        etapa: 'lote', sessaoId: null, erradas: 0, anotados: 0, novoErro: null, resumo: '',
        previsaoNaSaida: false, capturadas: [], avisoCaptura: null,
      };
      // D-168: a previsão feita na saída vale; a captura preenche feitas e certas.
      const saida = opcoes.saida;
      if (saida && saida.previsao != null) { this.atual.previsao = saida.previsao; this.atual.previsaoNaSaida = true; }
      this.visivel = true;
      const [sugerido, livres, parametros, capturadas] = await Promise.all([
        api(`/assuntos/${item.assuntoId}/lote-sugerido`).catch(() => null),
        api(`/foco/livres?assuntoId=${item.assuntoId}`).catch(() => null),
        api('/parametros').catch(() => []),
        saida ? api(`/captura/desde?assuntoId=${item.assuntoId}&desde=${encodeURIComponent(new Date(saida.saiuEm).toISOString())}`).catch(() => null) : null,
      ]);
      const l = this.atual;
      if (!this.visivel || l.assuntoId !== item.assuntoId) return;
      if (sugerido) l.formato = sugerido.formato;
      // D-174: na volta do atalho com "questões por vez", o lote já espera esse número (a captura, quando houver, manda).
      const porVez = Number((parametros.find((x) => x.chave === 'questoes_por_vez') || {}).valor);
      if (daVolta && porVez > 0 && !(capturadas && capturadas.feitas > 0)) l.feitas = porVez;
      if (capturadas && capturadas.feitas > 0) {
        l.feitas = capturadas.feitas;
        l.certas = capturadas.certas;
        l.capturadas = capturadas.ids;
        if (capturadas.formato) l.formato = capturadas.formato;
        const hora = new Date(saida.saiuEm).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
        l.avisoCaptura = `${capturadas.feitas} ${capturadas.feitas === 1 ? 'capturada' : 'capturadas'} desde ${hora} · confira`;
      }
      if (livres && livres.minutos > 0) { l.tempo = livres.minutos; l.doFoco = true; l.fasesIds = livres.fasesIds; }
      const sufixo = l.formato === 'CERTO_ERRADO' ? 'certo_errado' : 'multipla_escolha';
      const valor = (chave) => Number((parametros.find((x) => x.chave === chave) || {}).valor);
      l.limiares = { sucesso: valor(`limiar_sucesso_${sufixo}`), parcial: valor(`limiar_parcial_${sufixo}`) };
      const porQuestao = valor('minutos_por_questao');
      if (porQuestao > 0) l.porQuestao = porQuestao;
    },

    // D-152: sem foco e fora da volta, o tempo é feitas × minutos por questão.
    atualizarTempo() {
      const l = this.atual;
      if (l && !l.doFoco && !l.daVolta) {
        const n = Number(l.feitas);
        l.tempo = n > 0 ? Math.max(1, Math.round(n * l.porQuestao)) : null;
      }
    },

    rotuloTempo() {
      const l = this.atual;
      if (!l) return '';
      const origem = l.doFoco ? ' (do Pomodoro)' : l.daVolta ? ' (desde a saída)' : '';
      return `${l.formato === 'CERTO_ERRADO' ? 'Certo/errado' : 'Múltipla escolha'} · tempo ${l.tempo || '—'} min${origem}`;
    },

    texto() {
      const l = this.atual;
      if (!l) return '';
      const feitas = Number(l.feitas);
      const certas = Number(l.certas);
      if (!(feitas > 0) || l.certas === null || l.certas === '' || certas > feitas) return '';
      const pct = Math.round((certas * 100) / feitas);
      if (!l.limiares || Number.isNaN(l.limiares.sucesso)) return `${pct}%`;
      const r = pct >= l.limiares.sucesso ? 'reconstruí (a revisão sobe)' : pct >= l.limiares.parcial ? 'faltou um pedaço (a revisão repete o nível)' : 'não veio (volta para o reestudo)';
      return `${pct}% · ${r}`;
    },

    async registrar() {
      const l = this.atual;
      l.erro = null;
      const feitas = Number(l.feitas);
      const certas = Number(l.certas);
      if (!(feitas > 0) || l.certas === null || l.certas === '' || certas < 0 || certas > feitas) { l.erro = 'Informe quantas fez e quantas acertou.'; return; }
      if (l.previsao === null) { l.erro = 'Toque na previsão.'; return; }
      l.enviando = true;
      try {
        const sessao = await api('/sessoes', { method: 'POST', body: JSON.stringify({
          assuntoId: l.assuntoId, tipo: 'QUESTOES', data: hojeISO(),
          tempoMinutos: Math.max(1, Number(l.tempo) || Math.round(feitas * l.porQuestao)),
          formato: l.formato, questoesTotal: feitas, questoesCorretas: certas, previsaoPercentual: l.previsao, tentativaId: l.tentativaId,
          fasesIds: l.fasesIds.length ? l.fasesIds : undefined }) });
        if (l.daVolta) Alpine.store('qc').encerrar();
        // D-168: as capturadas que preencheram o lote saem da caixa, ligadas a esta sessão.
        if (l.capturadas.length && sessao && sessao.id) {
          api('/captura/marcar', { method: 'POST', body: JSON.stringify({ ids: l.capturadas, sessaoId: sessao.id }) }).catch(() => {});
        }
        window.dispatchEvent(new CustomEvent('sge:sessao-registrada', { detail: { assuntoId: l.assuntoId } }));
        // D-156: com erradas, "Anotar erros" no mesmo painel (opcional)
        if (feitas - certas > 0 && sessao && sessao.id) {
          l.etapa = 'erros';
          l.sessaoId = sessao.id;
          l.erradas = feitas - certas;
          l.resumo = `${certas} de ${feitas} (${Math.round((certas * 100) / feitas)}%)`;
          l.novoErro = this.erroEmBranco();
        } else {
          this.visivel = false;
        }
      } catch (e) {
        l.erro = e.message; // o mesmo tentativaId no reenvio: nunca duplica (D-45)
      } finally {
        l.enviando = false;
      }
    },

    // D-153: "Não fiz" descarta a saída; o ✕ só fecha (a volta abre de novo).
    naoFiz() {
      Alpine.store('qc').encerrar();
      this.visivel = false;
    },

    fechar() {
      this.visivel = false;
    },

    maisCampos() {
      const l = this.atual;
      this.visivel = false;
      Alpine.store('registrar').selecionar({ id: l.assuntoId, nome: l.nome });
      window.location.hash = `#/registrar/questoes/${l.assuntoId}`;
    },

    erroEmBranco() {
      return { questao: '', descricao: '', causa: null, confianca: null };
    },

    async salvarErro() {
      const l = this.atual;
      const e = l.novoErro;
      l.erro = null;
      if (!e.descricao.trim()) { l.erro = 'Escreva em uma frase por que errou.'; return; }
      if (!e.causa) { l.erro = 'Escolha a causa.'; return; }
      if (!e.confianca) { l.erro = 'Escolha a confiança que você tinha ao errar.'; return; }
      l.enviando = true;
      try {
        await api('/erros', { method: 'POST', body: JSON.stringify({
          assuntoId: l.assuntoId, sessaoId: l.sessaoId, descricao: e.descricao.trim(), causa: e.causa, confianca: e.confianca,
          questaoExterna: e.questao.trim() || null }) });
        l.anotados += 1;
        if (l.anotados >= l.erradas) this.visivel = false;
        else l.novoErro = this.erroEmBranco();
      } catch (err) {
        l.erro = err.message;
      } finally {
        l.enviando = false;
      }
    },
  });

  // D-153: a volta — a página fica à vista de novo (fechar a aba do Chrome no celular, voltar à aba no computador).
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible') Alpine.store('qc').verificarVolta();
  });
});

document.addEventListener('alpine:initialized', () => {
  Alpine.store('qc').verificarVolta();
});

// O link da questão anotada (D-156): a busca do QConcursos pelo número.
function linkQuestaoQc(numero) {
  return `https://www.qconcursos.com/questoes-de-concursos/questoes?q=${encodeURIComponent(numero)}`;
}
