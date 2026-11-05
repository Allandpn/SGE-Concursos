// js/erros.js — a lista geral de erros (04_FRONTEND §8B, "As demais telas"; Sprint 40). A lista por assunto (#/erros/{id}) continua.

// CAUSAS_ERRO vem de js/qconcursos.js (a mesma lista do lote rápido).

document.addEventListener('alpine:init', () => {
  Alpine.data('paginaErrosGerais', () => ({
    erros: [],
    disciplinas: [],
    disciplinaId: '',
    causa: '',
    naoResolvidos: true,
    carregando: false,
    erro: null,
    causas: CAUSAS_ERRO,

    async abrir() {
      try {
        this.disciplinas = await api('/disciplinas');
      } catch (e) {
        this.disciplinas = [];
      }
      await this.carregar();
    },

    async carregar() {
      this.carregando = true;
      this.erro = null;
      const q = new URLSearchParams();
      if (this.disciplinaId) q.set('disciplinaId', this.disciplinaId);
      if (this.causa) q.set('causa', this.causa);
      if (this.naoResolvidos) q.set('naoResolvidos', 'true');
      try {
        this.erros = await api('/erros/todos?' + q.toString());
      } catch (e) {
        this.erro = e.message;
      } finally {
        this.carregando = false;
      }
    },

    rotuloCausa(c) {
      return (CAUSAS_ERRO.find((x) => x[0] === c) || [c, c])[1];
    },

    quando(instante) {
      const d = new Date(instante);
      return dataHumana(`${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`);
    },

    async resolver(e) {
      const novo = !e.resolvido;
      try {
        await api(`/erros/${e.id}/resolvido`, { method: 'POST', body: JSON.stringify({ resolvido: novo }) });
        e.resolvido = novo;
        if (novo && this.naoResolvidos) this.erros = this.erros.filter((x) => x.id !== e.id);
      } catch (x) {
        this.erro = x.message;
      }
    },

    abrirAssunto(e) {
      Alpine.store('erro').selecionar({ assuntoId: e.assuntoId, nome: e.assunto });
      window.location.hash = `#/erros/${e.assuntoId}`;
    },
  }));
});
