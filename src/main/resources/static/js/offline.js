// js/offline.js — ler sem rede (D-119, ADR-052; docs/SPRINT-31-LEITOR.md §5).
//
// Dois lugares, separados de propósito:
// - o cache "material-v1" (do service worker) guarda os PDFs dos blocos; o navegador pode esvaziá-lo, e então o
//   bloco volta a pedir rede;
// - o IndexedDB "eagora" guarda os dados de leitura de cada bloco guardado ("blocos") e a fila de mudanças de
//   marcação ainda não enviadas ("fila"). A fila nunca é limpa por falta de espaço.

const CACHE_MATERIAL = 'material-v1';
const LIMITE_BLOCOS = 30;

function abrirBanco() {
  return new Promise((resolver, rejeitar) => {
    const pedido = indexedDB.open('eagora', 1);
    pedido.onupgradeneeded = () => {
      const db = pedido.result;
      if (!db.objectStoreNames.contains('blocos')) db.createObjectStore('blocos', { keyPath: 'segmentoId' });
      if (!db.objectStoreNames.contains('fila')) db.createObjectStore('fila', { keyPath: 'ordem', autoIncrement: true });
    };
    pedido.onsuccess = () => resolver(pedido.result);
    pedido.onerror = () => rejeitar(pedido.error);
  });
}

async function noBanco(deposito, modo, fazer) {
  const db = await abrirBanco();
  return new Promise((resolver, rejeitar) => {
    const transacao = db.transaction(deposito, modo);
    const resultado = fazer(transacao.objectStore(deposito));
    transacao.oncomplete = () => resolver(resultado && 'result' in resultado ? resultado.result : undefined);
    transacao.onerror = () => rejeitar(transacao.error);
  });
}

function ehMaterialLocal(arquivo) {
  return typeof arquivo === 'string' && arquivo.startsWith('/material/');
}

const offline = {
  // --- Blocos guardados ----------------------------------------------------------------------------------------

  async bloco(segmentoId) {
    try {
      return await noBanco('blocos', 'readonly', (d) => d.get(Number(segmentoId)));
    } catch (e) {
      return undefined;
    }
  },

  async guardados() {
    try {
      const todos = await noBanco('blocos', 'readonly', (d) => d.getAll());
      return new Set((todos || []).map((b) => b.segmentoId));
    } catch (e) {
      return new Set();
    }
  },

  // Guarda o PDF e os dados de leitura de um bloco. `deHoje` marca os do plano do dia (os últimos a sair pelo limite).
  async guardar(segmentoId, deHoje = false) {
    const leitura = await api(`/segmentos/${segmentoId}/leitura`);
    if (!ehMaterialLocal(leitura.arquivo) || !('caches' in window)) return false;
    const cache = await caches.open(CACHE_MATERIAL);
    if (!(await cache.match(leitura.arquivo))) {
      const resposta = await fetch(leitura.arquivo, { credentials: 'same-origin' });
      if (!resposta.ok) return false;
      await cache.put(leitura.arquivo, resposta);
    }
    await noBanco('blocos', 'readwrite', (d) => d.put({ segmentoId: Number(segmentoId), leitura, guardadoEm: Date.now(), deHoje: deHoje ? hojeISO() : null }));
    await this.respeitarLimite();
    return true;
  },

  // Os dados de leitura mudam (marcações, última página): quem já está guardado se atualiza ao abrir com rede.
  async atualizarLeitura(leitura) {
    const atual = await this.bloco(leitura.segmentoId);
    if (!atual) return;
    // Cópia simples: a leitura vem do estado do Alpine (Proxy), que o IndexedDB não clona.
    await noBanco('blocos', 'readwrite', (d) => d.put({ ...atual, leitura: JSON.parse(JSON.stringify(leitura)) }));
  },

  async respeitarLimite() {
    const todos = (await noBanco('blocos', 'readonly', (d) => d.getAll())) || [];
    if (todos.length <= LIMITE_BLOCOS) return;
    const hoje = hojeISO();
    const saem = todos.filter((b) => b.deHoje !== hoje).sort((a, b) => a.guardadoEm - b.guardadoEm).slice(0, todos.length - LIMITE_BLOCOS);
    const cache = await caches.open(CACHE_MATERIAL);
    for (const b of saem) {
      await cache.delete(b.leitura.arquivo);
      await noBanco('blocos', 'readwrite', (d) => d.delete(b.segmentoId));
    }
  },

  // D-119: os blocos do plano de hoje são guardados sozinhos quando há rede. Falhar aqui nunca atrapalha a tela.
  async guardarDeHoje(segmentoIds) {
    if (!navigator.onLine) return;
    for (const id of segmentoIds.filter(Boolean)) {
      try {
        await this.guardar(id, true);
      } catch (e) {
        // sem rede no meio, PDF fora do servidor: tenta na próxima abertura de Hoje
      }
    }
  },

  // --- Fila de mudanças de marcação (D-118) ---------------------------------------------------------------------

  async enfileirar(mudanca) {
    await noBanco('fila', 'readwrite', (d) => d.add(mudanca));
  },

  async fila() {
    try {
      return (await noBanco('fila', 'readonly', (d) => d.getAll())) || [];
    } catch (e) {
      return [];
    }
  },

  // Manda a fila em ordem. Sai da fila o que o servidor aplicou, já tinha ou nunca vai aceitar; a mudança de uma
  // marcação que ainda não chegou fica para a próxima vez. Devolve quantas restam.
  async enviar() {
    const fila = await this.fila();
    if (!fila.length || !navigator.onLine) return fila.length;
    let resultado;
    try {
      resultado = await api('/marcacoes/mudancas', { method: 'POST', body: JSON.stringify(fila.map(({ ordem, ...m }) => m)) });
    } catch (e) {
      return fila.length;
    }
    const sai = new Set([...resultado.aplicadas, ...resultado.repetidas,
      ...resultado.recusadas.filter((r) => r.codigo !== 'MARCACAO_DESCONHECIDA').map((r) => r.mudancaId)]);
    const ordens = fila.filter((m) => sai.has(m.mudancaId)).map((m) => m.ordem);
    if (ordens.length) await noBanco('fila', 'readwrite', (d) => ordens.forEach((o) => d.delete(o)));
    return fila.length - ordens.length;
  },
};

window.addEventListener('online', () => { offline.enviar(); });
