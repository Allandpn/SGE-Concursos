// js/captura.js — a captura do QConcursos na tela (04_FRONTEND §8F; 01_DOMINIO §3.28, D-166 a D-171; ADR-063):
// a caixa em Hoje (as soltas, por assunto) e, em Ajustes, as chaves de envio, o userscript e os diagnósticos.

document.addEventListener('alpine:init', () => {
  Alpine.store('caixaCaptura', {
    ultimo: null,
    mostrar(texto) {
      this.ultimo = texto;
      clearTimeout(this._t);
      this._t = setTimeout(() => { this.ultimo = null; }, 8000);
    },
  });

  // D-169: a caixa. Registrar pede a previsão antes de mostrar o acerto.
  Alpine.data('caixaCaptura', () => ({
    grupos: [],
    assuntos: [],
    registrando: null, // { grupo, previsao, tempo, resultado, erro, enviando }
    pareando: null, // { grupo, assuntoId, erro }
    erro: null,

    async init() {
      await this.carregar();
    },

    async carregar() {
      try {
        this.grupos = await api('/captura/caixa');
      } catch (e) {
        this.grupos = [];
      }
    },

    chave(g) {
      return g.assuntoId != null ? 'a' + g.assuntoId : 'n' + g.nomeQc;
    },

    dias(g) {
      if (!g.primeiroDia) return '';
      return g.primeiroDia === g.ultimoDia ? dataHumana(g.ultimoDia) : `${dataHumana(g.primeiroDia)} a ${dataHumana(g.ultimoDia)}`;
    },

    abrirRegistro(g) {
      this.pareando = null;
      this.registrando = { grupo: g, previsao: null, resultado: null, erro: null, enviando: false };
    },

    async registrar() {
      const r = this.registrando;
      if (!r || r.previsao === null) { if (r) r.erro = 'Toque na previsão.'; return; }
      r.enviando = true;
      r.erro = null;
      try {
        const res = await api('/captura/caixa/registrar', { method: 'POST', body: JSON.stringify({ assuntoId: r.grupo.assuntoId, previsaoPercentual: r.previsao }) });
        // Hoje recarrega depois do registro (e a caixa nasce de novo): o resultado fica num store, por alguns segundos.
        Alpine.store('caixaCaptura').mostrar(`${r.grupo.assunto}: ${res.certas} de ${res.feitas} certas · registrado`);
        this.registrando = null;
        window.dispatchEvent(new CustomEvent('sge:sessao-registrada', { detail: { assuntoId: r.grupo.assuntoId } }));
        await this.carregar();
      } catch (e) {
        r.erro = e.message;
      } finally {
        r.enviando = false;
      }
    },

    async descartar(g) {
      if (!window.confirm(`Descartar ${g.questoes} ${g.questoes === 1 ? 'questão' : 'questões'} de ${g.assunto || g.nomeQc}? Elas saem da caixa sem entrar na escada.`)) return;
      await api('/captura/caixa/descartar', { method: 'POST', body: JSON.stringify({ assuntoId: g.assuntoId, nomeQc: g.nomeQc }) }).catch(() => {});
      await this.carregar();
    },

    async abrirPar(g) {
      this.registrando = null;
      this.pareando = { grupo: g, assuntoId: '', erro: null };
      if (!this.assuntos.length) {
        try {
          const painel = await api('/assuntos/painel');
          this.assuntos = painel.flatMap((d) => d.assuntos.map((i) => ({ id: i.assunto.id, nome: i.assunto.nome, disciplina: d.disciplina.nome })));
        } catch (e) {
          this.pareando.erro = e.message;
        }
      }
    },

    async parear() {
      const p = this.pareando;
      if (!p || !p.assuntoId) { if (p) p.erro = 'Escolha o assunto.'; return; }
      try {
        await api('/captura/pares', { method: 'POST', body: JSON.stringify({ nomeQc: p.grupo.nomeQc, assuntoId: Number(p.assuntoId) }) });
        this.pareando = null;
        await this.carregar();
      } catch (e) {
        p.erro = e.message;
      }
    },
  }));

  // D-170 e D-171: Ajustes › Captura do QConcursos.
  Alpine.data('capturaAjustes', () => ({
    chaves: [],
    diagnosticos: [],
    nome: '',
    criada: null,
    copiado: false,
    erro: null,
    verDiagnosticos: false,

    async init() {
      this.nome = navigator.userAgent.includes('Android') ? 'Celular' : 'Computador';
      await this.carregar();
    },

    async carregar() {
      try {
        [this.chaves, this.diagnosticos] = await Promise.all([api('/captura/chaves'), api('/captura/diagnosticos')]);
      } catch (e) {
        this.erro = e.message;
      }
    },

    async gerar() {
      this.erro = null;
      this.copiado = false;
      try {
        this.criada = await api('/captura/chaves', { method: 'POST', body: JSON.stringify({ nome: this.nome }) });
        await this.carregar();
      } catch (e) {
        this.erro = e.message;
      }
    },

    async copiar() {
      try {
        await navigator.clipboard.writeText(this.criada.chave);
        this.copiado = true;
      } catch (e) {
        this.erro = 'Não consegui copiar; selecione a chave e copie à mão.';
      }
    },

    async revogar(c) {
      if (!window.confirm(`Revogar a chave "${c.nome}"? O userscript daquele navegador para de enviar na hora.`)) return;
      await api(`/captura/chaves/${c.id}/revogar`, { method: 'POST' }).catch((e) => { this.erro = e.message; });
      await this.carregar();
    },

    async apagarDiagnostico(d) {
      await api(`/captura/diagnosticos/${d.id}`, { method: 'DELETE' }).catch(() => {});
      await this.carregar();
    },

    quando(instante) {
      if (!instante) return 'nunca enviou';
      const d = new Date(instante);
      const iso = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
      return dataHumana(iso) + ', ' + d.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
    },
  }));
});
