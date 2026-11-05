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

    // "8 questões · FGV · múltipla escolha · 2021–2026 · não resolvidas", com o aviso quando o filtro é mais largo.
    detalhe(a) {
      if (!a) return '';
      const partes = [`${a.sugeridas} questões`];
      partes.push(a.banca || 'sem filtro de banca');
      if (a.formato) partes.push(a.formato === 'CERTO_ERRADO' ? 'certo/errado' : 'múltipla escolha');
      const anos = this.anos(a);
      if (anos) partes.push(anos);
      partes.push('não resolvidas');
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
    antes: null,
    antesDeIr(a, nome) {
      if (!a) return;
      this.antes = { a, nome, previsao: null };
    },
    ir() {
      const x = this.antes;
      if (!x) return;
      this.sair(x.a, x.nome, x.previsao);
      this.antes = null;
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
