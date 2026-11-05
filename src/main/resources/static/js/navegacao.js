// js/navegacao.js — a navegação principal (docs/04_FRONTEND.md §8B): barra inferior no celular, menu lateral no computador,
// "Mais" com o que não cabe na barra, e o modo foco das telas de estudo (sem navegação). O router diz onde se está.

const ITENS_NAVEGACAO = [
  { id: 'hoje', nome: 'Hoje', href: '#/hoje', icone: 'i-hoje', barra: true },
  { id: 'assuntos', nome: 'Assuntos', href: '#/assuntos', icone: 'i-assuntos', barra: true },
  { id: 'progresso', nome: 'Progresso', href: '#/progresso', icone: 'i-progresso', barra: true },
  { id: 'concursos', nome: 'Concursos', href: '#/concursos', icone: 'i-concursos', barra: false },
  { id: 'erros', nome: 'Erros', href: '#/erros', icone: 'i-erros', barra: false },
  { id: 'ajustes', nome: 'Ajustes', href: '#/ajustes', icone: 'i-ajustes', barra: false },
];

document.addEventListener('alpine:init', () => {
  Alpine.store('nav', {
    itens: ITENS_NAVEGACAO,
    rota: 'hoje',
    foco: false,
    maisAberto: false,

    get naBarra() {
      return this.itens.filter((i) => i.barra);
    },

    get noMais() {
      return this.itens.filter((i) => !i.barra);
    },

    // "Mais" fica marcado quando a tela atual é uma das que moram nele.
    get maisAtivo() {
      return this.noMais.some((i) => i.id === this.rota);
    },

    get titulo() {
      const item = this.itens.find((i) => i.id === this.rota);
      return item ? item.nome : 'eAgora';
    },

    ir(rota, foco) {
      this.rota = rota;
      this.foco = foco;
      this.maisAberto = false;
    },
  });
});

// ADR-060: Ajustes → Aparência. "aparelho" segue prefers-color-scheme; "claro" e "escuro" fixam a classe em <html>.
document.addEventListener('alpine:init', () => {
  Alpine.data('aparenciaAjustes', () => ({
    opcoes: [['claro', 'Claro'], ['escuro', 'Escuro'], ['aparelho', 'Seguir o aparelho']],
    escolha: 'aparelho',

    init() {
      try {
        const v = window.localStorage.getItem('aparencia');
        this.escolha = v === 'claro' || v === 'escuro' ? v : 'aparelho';
      } catch (e) {
        this.escolha = 'aparelho';
      }
    },

    escolher(v) {
      this.escolha = v;
      const raiz = document.documentElement;
      raiz.classList.remove('claro', 'escuro');
      if (v !== 'aparelho') raiz.classList.add(v);
      try {
        if (v === 'aparelho') window.localStorage.removeItem('aparencia');
        else window.localStorage.setItem('aparencia', v);
      } catch (e) {
        // sem armazenamento: vale até fechar
      }
    },
  }));
});

// Ajustes → Versão do app: a casca que o service worker deste aparelho usa e a que o servidor tem agora.
document.addEventListener('alpine:init', () => {
  Alpine.data('versaoApp', () => ({
    instalada: null,
    servidor: null,
    atualizando: false,

    init() {
      this.ler();
    },

    async ler() {
      try {
        const sw = navigator.serviceWorker && navigator.serviceWorker.controller;
        if (sw) {
          const canal = new MessageChannel();
          this.instalada = await new Promise((resolver) => {
            canal.port1.onmessage = (e) => resolver(e.data);
            sw.postMessage('versao', [canal.port2]);
            setTimeout(() => resolver(null), 2000);
          });
        }
      } catch (e) {
        this.instalada = null;
      }
      try {
        const texto = await (await fetch('/sw.js', { cache: 'no-store' })).text();
        const m = texto.match(/CASCA_VERSAO = '([^']+)'/);
        this.servidor = m ? m[1] : null;
      } catch (e) {
        this.servidor = null;
      }
    },

    async atualizar() {
      this.atualizando = true;
      try {
        const registro = navigator.serviceWorker && await navigator.serviceWorker.getRegistration();
        if (registro) await registro.update();
      } catch (e) {
        // sem service worker: o recarregar abaixo já busca do servidor
      }
      window.location.reload();
    },
  }));
});
