// js/prova.js — verificar o material com uma prova (J-6) e resolver a prova (J-7).
// 01_DOMINIO §3.11, D-67 a D-85; docs/SPRINT-24-PROVA-GABARITO.md §4 e §9; 04_FRONTEND §7M.
// Nenhuma tela mostra a resposta do gabarito antes da entrega (D-80): a tentativa não traz o campo, e só a
// conferência (antes de julgar) mostra 3 questões de amostra com o gabarito, para pegar uma troca grosseira (J-6 3).

const ROTULO_STATUS_PROVA = {
  LENDO: 'lendo a prova…',
  AGUARDANDO_GABARITO: 'aguardando o gabarito',
  CONFERIR: 'pronta para conferir',
  JULGANDO: 'julgando',
  PRONTA: 'pronta',
  FALHOU: 'falhou',
};

const ROTULO_VEREDITO = { RESPONDIVEL: 'o material sustenta', PARCIAL: 'sustenta em parte', NAO_RESPONDIVEL: 'o material não sustenta' };

const ROTULO_MOTIVO = { SEM_TEXTO: 'sem texto (figura ou tabela)', ANULADA: 'anulada', SEM_GABARITO: 'sem gabarito', OCR_DUVIDOSO: 'leitura duvidosa' };

function textoStatusProva(p) {
  if (!p) return '';
  if (p.status === 'JULGANDO' || p.status === 'PRONTA') return `${p.julgadas} de ${p.avaliaveis} questões julgadas`;
  return ROTULO_STATUS_PROVA[p.status] || p.status;
}

function placarTexto(placar) {
  return placar ? `${placar.corretas} de ${placar.total}` : '';
}

async function enviarFormulario(caminho, dados) {
  return api(caminho, { method: 'POST', body: dados, headers: {} });
}

document.addEventListener('alpine:init', () => {
  // ---------------------------------------------------------------------------------------------------------------
  // J-6: envio, acompanhamento, conferência e relatório.
  Alpine.data('paginaProva', () => ({
    modo: null, // 'nova' | 'detalhe' | 'relatorio'
    id: null,
    prova: null, // Detalhe
    relatorio: null,
    assuntos: [],
    carregando: false,
    trabalhando: false,
    erro: null,
    aviso: null,
    arquivoProva: null,
    arquivoGabarito: null,
    gabaritoTexto: '',
    tipo: '',
    edicao: { nome: '', banca: '', ano: '' },
    _timer: null,

    parar() {
      clearTimeout(this._timer);
      this._timer = null;
    },

    abrirNova() {
      this.parar();
      this.modo = 'nova';
      this.id = null;
      this.erro = null;
      this.aviso = null;
      this.arquivoProva = null;
      this.arquivoGabarito = null;
      this.gabaritoTexto = '';
      this.tipo = '';
    },

    async abrir(id) {
      this.parar();
      this.modo = 'detalhe';
      this.id = id;
      this.erro = null;
      this.aviso = null;
      this.prova = null;
      this.arquivoGabarito = null;
      this.gabaritoTexto = '';
      await this.carregar();
    },

    async abrirRelatorio(id) {
      this.parar();
      this.modo = 'relatorio';
      this.id = id;
      this.erro = null;
      this.aviso = null;
      this.relatorio = null;
      this.carregando = true;
      try {
        this.relatorio = await api(`/provas/${id}/relatorio`);
      } catch (e) {
        this.erro = e.message;
      } finally {
        this.carregando = false;
      }
    },

    async carregar() {
      this.carregando = !this.prova;
      try {
        this.prova = await api(`/provas/${this.id}`);
        const r = this.prova.resumo;
        this.edicao = { nome: r.nome || '', banca: r.banca || '', ano: r.ano || '' };
        if (r.status === 'CONFERIR' && !this.assuntos.length) {
          this.assuntos = await this.carregarAssuntos();
        }
      } catch (e) {
        this.erro = e.message;
      } finally {
        this.carregando = false;
      }
      // Em segundo plano (LENDO, JULGANDO): acompanha a cada 10 s enquanto a tela está aberta (J-6 5).
      if (this.prova && ['LENDO', 'JULGANDO'].includes(this.prova.resumo.status) && this.modo === 'detalhe') {
        this._timer = setTimeout(() => this.carregar(), 10000);
      }
    },

    // As opções para corrigir a atribuição, por disciplina. Assunto fora da cobertura do alvo o servidor recusa (D-74).
    async carregarAssuntos() {
      try {
        const disciplinas = await api('/disciplinas');
        const grupos = await Promise.all(disciplinas.map(async (d) => ({ disciplina: d.nome, assuntos: await api(`/assuntos?disciplinaId=${d.id}`) })));
        return grupos.filter((g) => g.assuntos.length);
      } catch (e) {
        return [];
      }
    },

    get status() {
      return this.prova ? this.prova.resumo.status : null;
    },

    textoStatus() {
      return textoStatusProva(this.prova?.resumo);
    },

    escolherArquivo(campo, evento) {
      this[campo] = evento.target.files[0] ?? null;
    },

    // J-6 1-2: prova em PDF; gabarito em PDF ou texto colado; tipo só se a pessoa souber.
    async enviar() {
      this.erro = null;
      if (!this.arquivoProva) {
        this.erro = 'Escolha o PDF da prova.';
        return;
      }
      const dados = new FormData();
      dados.append('prova', this.arquivoProva);
      if (this.arquivoGabarito) dados.append('gabarito', this.arquivoGabarito);
      else if (this.gabaritoTexto.trim()) dados.append('gabaritoTexto', this.gabaritoTexto.trim());
      if (this.tipo.trim()) dados.append('tipo', this.tipo.trim());
      this.trabalhando = true;
      try {
        const resumo = await enviarFormulario('/provas', dados);
        window.location.hash = `#/provas/${resumo.id}`;
      } catch (e) {
        this.erro = e.message;
      } finally {
        this.trabalhando = false;
      }
    },

    async anexarGabarito() {
      this.erro = null;
      const dados = new FormData();
      if (this.arquivoGabarito) dados.append('gabarito', this.arquivoGabarito);
      else if (this.gabaritoTexto.trim()) dados.append('gabaritoTexto', this.gabaritoTexto.trim());
      else {
        this.erro = 'Escolha o PDF do gabarito ou cole o texto.';
        return;
      }
      this.trabalhando = true;
      try {
        await enviarFormulario(`/provas/${this.id}/gabarito`, dados);
        await this.carregar();
      } catch (e) {
        this.erro = e.message; // D-82: gabarito que não casa é recusado e diz a diferença
      } finally {
        this.trabalhando = false;
      }
    },

    async salvarDados() {
      this.erro = null;
      try {
        await api(`/provas/${this.id}`, { method: 'PATCH', body: JSON.stringify({
          nome: this.edicao.nome || null, banca: this.edicao.banca || null, ano: this.edicao.ano ? Number(this.edicao.ano) : null }) });
        this.aviso = 'Dados da prova salvos.';
      } catch (e) {
        this.erro = e.message;
      }
    },

    // J-6 4: só o duvidoso; "fora do edital" não é erro (D-74).
    async corrigirAssunto(d, valor) {
      this.erro = null;
      try {
        await api(`/provas/${this.id}/questoes/${d.numero}/assunto`, { method: 'PUT', body: JSON.stringify({ assuntoId: valor ? Number(valor) : null }) });
        d.resolvida = true;
      } catch (e) {
        this.erro = e.message;
      }
    },

    async confirmar() {
      this.erro = null;
      this.trabalhando = true;
      try {
        await api(`/provas/${this.id}/confirmar`, { method: 'POST' });
        await this.carregar();
      } catch (e) {
        this.erro = e.message;
      } finally {
        this.trabalhando = false;
      }
    },

    async tentarDeNovo() {
      this.trabalhando = true;
      try {
        await api(`/provas/${this.id}/tentar-de-novo`, { method: 'POST' });
        await this.carregar();
      } catch (e) {
        this.erro = e.message;
      } finally {
        this.trabalhando = false;
      }
    },

    // J-6 7 (D-79): julga só o que mudou; a prova não é relida.
    async analisarDeNovo() {
      this.trabalhando = true;
      this.aviso = null;
      try {
        const r = await api(`/provas/${this.id}/analisar-de-novo`, { method: 'POST' });
        this.aviso = r.aJulgar > 0 ? `${r.aJulgar} questões voltaram para o julgamento.` : 'Nada mudou no material desde o último julgamento.';
        if (r.aJulgar > 0) window.location.hash = `#/provas/${this.id}`;
      } catch (e) {
        this.erro = e.message;
      } finally {
        this.trabalhando = false;
      }
    },

    // Relatório ----------------------------------------------------------------------------------------------------
    rotuloVeredito(v) {
      return v ? ROTULO_VEREDITO[v] : 'sem veredito';
    },

    rotuloMotivo(m) {
      return ROTULO_MOTIVO[m] || m;
    },

    textoCobertura(c) {
      if (!c) return '';
      const partes = [`${c.respondiveis} de ${c.avaliaveis} avaliáveis o material sustenta`];
      if (c.parciais) partes.push(`${c.parciais} em parte`);
      if (c.naoRespondiveis) partes.push(`${c.naoRespondiveis} não`);
      if (c.semVeredito) partes.push(`${c.semVeredito} ainda sem veredito`);
      return partes.join(' · ');
    },

    textoDeclaradas(d) {
      if (!d) return '';
      const partes = [];
      if (d.foraDoEdital) partes.push(`${d.foraDoEdital} fora do edital`);
      if (d.anuladas) partes.push(`${d.anuladas} anuladas`);
      if (d.semGabarito) partes.push(`${d.semGabarito} sem gabarito`);
      if (d.semTexto) partes.push(`${d.semTexto} sem texto`);
      if (d.ocrDuvidoso) partes.push(`${d.ocrDuvidoso} com leitura duvidosa`);
      return partes.length ? `À parte: ${partes.join(' · ')}` : '';
    },

    placarTexto,
    textoStatusProva,
  }));

  // ---------------------------------------------------------------------------------------------------------------
  // J-7: resolver a prova — uma questão por tela; a mesma tentativa conta uma vez (D-80).
  Alpine.data('paginaResolver', () => ({
    id: null,
    tentativaId: null,
    detalhe: null, // TentativaDetalhe
    indice: 0,
    nota: null,
    carregando: false,
    erro: null,
    salvando: false,
    cronometrar: false,
    limiteMinutos: 120,
    agora: Date.now(),
    _relogio: null,

    chave() {
      return `eagora-tentativa-prova-${this.id}`;
    },

    lerTentativa() {
      try { return localStorage.getItem(this.chave()); } catch (e) { return null; }
    },

    guardarTentativa(valor) {
      try {
        if (valor) localStorage.setItem(this.chave(), valor);
        else localStorage.removeItem(this.chave());
      } catch (e) { /* sem armazenamento: a tentativa recomeça se a tela fechar */ }
    },

    parar() {
      clearInterval(this._relogio);
      this._relogio = null;
    },

    async abrir(id) {
      this.parar();
      this.id = id;
      this.erro = null;
      this.nota = null;
      this.detalhe = null;
      this.indice = 0;
      this.tentativaId = this.lerTentativa();
      if (this.tentativaId) await this.carregar();
    },

    // J-7 1-2: abre (ou retoma) com o tentativaId gerado aqui (D-80).
    async comecar() {
      this.erro = null;
      const tentativaId = crypto.randomUUID();
      this.carregando = true;
      try {
        await api(`/provas/${this.id}/tentativas`, { method: 'POST', body: JSON.stringify({
          tentativaId, cronometrada: this.cronometrar, limiteMinutos: this.cronometrar ? Number(this.limiteMinutos) : null }) });
        this.tentativaId = tentativaId;
        this.guardarTentativa(tentativaId);
        await this.carregar();
      } catch (e) {
        this.erro = e.message; // D-81: sem gabarito, não resolve
      } finally {
        this.carregando = false;
      }
    },

    async carregar() {
      this.carregando = true;
      try {
        this.detalhe = await api(`/provas/${this.id}/tentativas/${this.tentativaId}`);
        if (this.detalhe.tentativa.entregueEm && !this.nota) {
          this.nota = await api(`/provas/${this.id}/tentativas/${this.tentativaId}/entregar`, { method: 'POST' }); // idempotente (D-80)
        }
        const primeira = this.detalhe.questoes.findIndex((q) => !q.alternativaMarcada);
        this.indice = this.detalhe.tentativa.entregueEm ? 0 : Math.max(primeira, 0);
        this.parar();
        if (this.detalhe.tentativa.cronometrada && !this.detalhe.tentativa.entregueEm) {
          this._relogio = setInterval(() => { this.agora = Date.now(); }, 1000);
        }
      } catch (e) {
        if (e.status === 404) {
          this.guardarTentativa(null);
          this.tentativaId = null;
        } else {
          this.erro = e.message;
        }
      } finally {
        this.carregando = false;
      }
    },

    get questao() {
      return this.detalhe ? this.detalhe.questoes[this.indice] : null;
    },

    get entregue() {
      return !!this.detalhe?.tentativa.entregueEm;
    },

    get respondidas() {
      return this.detalhe ? this.detalhe.questoes.filter((q) => q.alternativaMarcada).length : 0;
    },

    get restante() {
      const t = this.detalhe?.tentativa;
      if (!t || !t.cronometrada || !t.limiteMinutos) return null;
      const fim = new Date(t.iniciadaEm).getTime() + t.limiteMinutos * 60000;
      return Math.max(0, Math.round((fim - this.agora) / 1000));
    },

    textoRestante() {
      const s = this.restante;
      if (s === null) return '';
      const h = Math.floor(s / 3600);
      const m = Math.floor((s % 3600) / 60);
      return s === 0 ? 'tempo esgotado' : `${h ? h + 'h ' : ''}${String(m).padStart(2, '0')}min`;
    },

    ir(i) {
      if (!this.detalhe) return;
      this.indice = Math.min(Math.max(i, 0), this.detalhe.questoes.length - 1);
      window.scrollTo(0, 0);
    },

    // Um toque: marca (ou desmarca) e grava; a navegação é local (≤ 1 s, J-7).
    async responder(letra) {
      const q = this.questao;
      if (!q || this.entregue) return;
      const anterior = q.alternativaMarcada;
      q.alternativaMarcada = anterior === letra ? null : letra;
      await this.gravar(q, () => { q.alternativaMarcada = anterior; });
    },

    async alternarRevisar() {
      const q = this.questao;
      if (!q || this.entregue) return;
      q.marcadaRevisar = !q.marcadaRevisar;
      await this.gravar(q, () => { q.marcadaRevisar = !q.marcadaRevisar; });
    },

    async gravar(q, desfazer) {
      this.erro = null;
      this.salvando = true;
      try {
        await api(`/provas/${this.id}/tentativas/${this.tentativaId}/respostas/${q.numero}`, { method: 'PUT',
          body: JSON.stringify({ alternativa: q.alternativaMarcada, marcadaRevisar: q.marcadaRevisar }) });
      } catch (e) {
        desfazer();
        this.erro = e.message;
      } finally {
        this.salvando = false;
      }
    },

    // J-7 3: corrige pelo gabarito e mostra a nota (D-71: sempre com denominador).
    async entregar() {
      const faltam = this.detalhe.questoes.length - this.respondidas;
      if (!confirm(faltam ? `Entregar com ${faltam} questões sem resposta?` : 'Entregar a prova?')) return;
      this.carregando = true;
      try {
        this.nota = await api(`/provas/${this.id}/tentativas/${this.tentativaId}/entregar`, { method: 'POST' });
        this.parar();
        this.detalhe = await api(`/provas/${this.id}/tentativas/${this.tentativaId}`);
        this.indice = 0;
      } catch (e) {
        this.erro = e.message;
      } finally {
        this.carregando = false;
      }
    },

    resolverDeNovo() {
      this.guardarTentativa(null);
      this.tentativaId = null;
      this.detalhe = null;
      this.nota = null;
    },

    correcaoDe(numero) {
      return this.detalhe?.correcao?.find((c) => c.numero === numero) || null;
    },

    get erros() {
      return (this.detalhe?.correcao || []).filter((c) => c.valeNaNota && c.acertou === false);
    },

    classeNavegador(q, i) {
      if (i === this.indice) return 'border-gray-900 font-semibold';
      if (this.entregue) {
        const c = this.correcaoDe(q.numero);
        if (c && c.acertou === true) return 'border-green-600 bg-green-50';
        if (c && c.acertou === false) return 'border-red-500 bg-red-50';
        return 'border-gray-200 text-gray-400';
      }
      if (q.marcadaRevisar) return 'border-alerta-600 bg-alerta-50';
      return q.alternativaMarcada ? 'border-primary-600 bg-primary-50' : 'border-gray-200';
    },

    rotuloVeredito(v) {
      return v ? ROTULO_VEREDITO[v] : '';
    },

    placarTexto,
  }));
});
