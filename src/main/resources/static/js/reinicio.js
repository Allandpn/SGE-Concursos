// js/reinicio.js — reiniciar o progresso de um concurso ou de tudo, e desfazer (04_FRONTEND §8E; 01_DOMINIO §3.27, D-163 a D-165).

document.addEventListener('alpine:init', () => {
  Alpine.store('reinicio', {
    aberto: false,
    previa: null,
    palavra: '',
    trabalhando: false,
    erro: null,
    aviso: null, // { texto, id }
    lista: [],

    get liberado() {
      return this.palavra.trim() === 'REINICIAR' && this.previa && this.previa.sessoes > 0 && !this.trabalhando;
    },

    async abrir(concursoId) {
      this.aberto = true;
      this.previa = null;
      this.palavra = '';
      this.erro = null;
      try {
        this.previa = await api('/reinicios/previa' + (concursoId ? '?concursoId=' + concursoId : ''));
      } catch (e) {
        this.erro = e.message;
      }
    },

    fechar() {
      if (!this.trabalhando) this.aberto = false;
    },

    async confirmar() {
      if (!this.liberado) return;
      this.trabalhando = true;
      this.erro = null;
      try {
        const r = await api('/reinicios', { method: 'POST', body: JSON.stringify({ concursoId: this.previa.concursoId, confirmacao: this.palavra.trim() }) });
        this.aberto = false;
        this.mostrar(`Progresso reiniciado: ${r.sessoes} ${r.sessoes === 1 ? 'sessão' : 'sessões'} em ${r.assuntos} ${r.assuntos === 1 ? 'assunto' : 'assuntos'}.`, r.id);
        this.depois();
      } catch (e) {
        this.erro = e.message;
      } finally {
        this.trabalhando = false;
      }
    },

    async desfazer(id) {
      this.trabalhando = true;
      try {
        const r = await api(`/reinicios/${id}/desfazer`, { method: 'POST' });
        this.mostrar(`Reinício desfeito: ${r.sessoes} ${r.sessoes === 1 ? 'sessão voltou' : 'sessões voltaram'}.`, null);
        this.depois();
      } catch (e) {
        this.mostrar(e.message, null);
      } finally {
        this.trabalhando = false;
      }
    },

    mostrar(texto, id) {
      this.aviso = { texto, id };
      clearTimeout(this._t);
      this._t = setTimeout(() => { this.aviso = null; }, id ? 10000 : 5000);
    },

    depois() {
      this.carregarLista();
      window.dispatchEvent(new CustomEvent('sge:sessao-registrada'));
    },

    async carregarLista() {
      try {
        this.lista = await api('/reinicios');
      } catch (e) {
        this.lista = [];
      }
    },

    quando(instante) {
      const d = new Date(instante);
      const iso = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
      return dataHumana(iso) + ', ' + d.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
    },
  });
});
