// js/sessoes.js — Sessões registradas: tirar da escada e pôr de volta (04_FRONTEND §8D; 01_DOMINIO §3.26, D-160 a D-162).

const ROTULO_TIPO_SESSAO = { ESTUDO: 'Leitura', RECUPERACAO: 'Recuperação', QUESTOES: 'Questões', FLASHCARDS: 'Flashcards', DISCURSIVA: 'Discursiva' };
const ROTULO_RESULTADO_SESSAO = { SUCESSO: 'Sucesso', PARCIAL: 'Em parte', FALHA: 'Não veio' };

document.addEventListener('alpine:init', () => {
  Alpine.data('paginaSessoes', () => ({
    sessoes: [],
    ate: null,
    temAnteriores: false,
    marcadas: [],
    carregando: false,
    trabalhando: false,
    confirmando: false,
    aviso: null,
    erro: null,

    async abrir() {
      this.sessoes = [];
      this.marcadas = [];
      this.confirmando = false;
      this.aviso = null;
      this.erro = null;
      await this.carregar(null);
    },

    async carregar(ate) {
      this.carregando = true;
      try {
        const p = await api('/sessoes/registradas' + (ate ? '?ate=' + ate : ''));
        this.sessoes = ate ? this.sessoes.concat(p.sessoes) : p.sessoes;
        this.ate = p.de;
        this.temAnteriores = p.temAnteriores;
      } catch (e) {
        this.erro = e.message;
      } finally {
        this.carregando = false;
      }
    },

    verAnteriores() {
      const d = dataLocal(this.ate);
      d.setDate(d.getDate() - 1);
      return this.carregar(`${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`);
    },

    get dias() {
      const grupos = [];
      for (const s of this.sessoes) {
        const ultimo = grupos[grupos.length - 1];
        if (ultimo && ultimo.data === s.data) ultimo.sessoes.push(s);
        else grupos.push({ data: s.data, sessoes: [s] });
      }
      return grupos;
    },

    descricao(s) {
      let t = ROTULO_TIPO_SESSAO[s.tipo] || s.tipo;
      if (s.questoesTotal) t += ` ${s.questoesCorretas}/${s.questoesTotal}`;
      return `${t} · ${s.tempoMinutos} min`;
    },

    resultado(s) {
      return ROTULO_RESULTADO_SESSAO[s.resultado] || '';
    },

    marcada(s) {
      return this.marcadas.includes(s.id);
    },

    alternar(s) {
      if (!s.ativa) return;
      this.marcadas = this.marcada(s) ? this.marcadas.filter((id) => id !== s.id) : this.marcadas.concat(s.id);
    },

    /** Os assuntos que terão a escada refeita, com quantas sessões de cada. */
    get afetados() {
      const conta = new Map();
      for (const s of this.sessoes) if (this.marcada(s)) conta.set(s.assunto, (conta.get(s.assunto) || 0) + 1);
      return [...conta.entries()].map(([assunto, n]) => `${assunto} (${n} ${n === 1 ? 'sessão' : 'sessões'})`);
    },

    async tirar() {
      await this.gesto('tirar', this.marcadas);
    },

    async porDeVolta(s) {
      await this.gesto('por-de-volta', [s.id]);
    },

    async gesto(qual, ids) {
      this.trabalhando = true;
      this.erro = null;
      try {
        const r = await api('/sessoes/registradas/' + qual, { method: 'POST', body: JSON.stringify({ ids }) });
        for (const s of this.sessoes) if (ids.includes(s.id)) s.ativa = qual !== 'tirar';
        this.marcadas = [];
        this.confirmando = false;
        this.aviso = r.assuntos.length ? 'Escada refeita: ' + r.assuntos.join(', ') + '.' : 'Nada mudou.';
        window.dispatchEvent(new CustomEvent('sge:sessao-registrada'));
        setTimeout(() => { this.aviso = null; }, 5000);
      } catch (e) {
        this.erro = e.message;
      } finally {
        this.trabalhando = false;
      }
    },
  }));
});
