// js/modo-teste.js — o modo de teste na tela (docs/04_FRONTEND.md §8C; ADR-061; docs/SPRINT-41-MODO-DE-TESTE.md §3).
// O servidor diz se este aparelho está no teste e qual é o "hoje" do teste; a tela passa a usar essa data (api.js, hojeISO).
// Cada gesto da faixa recarrega o app, para tudo ler a data nova.

const estadoModoTeste = fetch('/api/modo-teste', { credentials: 'same-origin', cache: 'no-store' })
  .then((r) => (r.ok ? r.json() : null))
  .catch(() => null)
  .then((s) => {
    if (s && s.ativo) {
      HOJE_DO_TESTE = s.hoje;
      document.documentElement.classList.add('modo-teste');
    }
    return s;
  });

document.addEventListener('alpine:init', () => {
  Alpine.store('teste', {
    estado: null,
    menuAberto: false,
    escolhendoData: false,
    data: '',
    trabalhando: false,
    erro: null,

    get ativo() {
      return !!(this.estado && this.estado.ativo);
    },

    get rotulo() {
      return this.ativo ? 'MODO DE TESTE · ' + hojeHumano() : '';
    },

    async fazer(caminho, corpo) {
      this.trabalhando = true;
      this.erro = null;
      try {
        await api('/modo-teste/' + caminho, { method: 'POST', body: corpo ? JSON.stringify(corpo) : undefined });
        window.location.reload();
      } catch (e) {
        this.erro = e.message;
        this.trabalhando = false;
      }
    },

    avancar(dias) {
      return this.fazer('avancar', { dias });
    },

    irParaData() {
      if (!this.data) { this.erro = 'Escolha a data.'; return; }
      return this.fazer('data', { data: this.data });
    },

    hojeDeVerdade() {
      return this.fazer('hoje');
    },

    recomecar() {
      if (!window.confirm('Recomeçar o teste? O que foi feito nele é trocado por uma cópia nova dos dados reais.')) return;
      return this.fazer('recomecar');
    },

    entrar() {
      return this.fazer('entrar');
    },

    sair() {
      return this.fazer('sair');
    },
  });
  estadoModoTeste.then((s) => { Alpine.store('teste').estado = s; });
});
