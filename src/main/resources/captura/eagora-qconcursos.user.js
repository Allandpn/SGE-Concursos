// ==UserScript==
// @name         eAgora · captura do QConcursos
// @namespace    __SGE__
// @version      1.0.0
// @description  Envia ao eAgora cada questão que você responde no QConcursos (01_DOMINIO §3.28; ADR-063). Só envia; não lê nada do eAgora.
// @match        https://www.qconcursos.com/questoes-de-concursos/*
// @match        https://www.qconcursos.com/questoes*
// @grant        GM_xmlhttpRequest
// @grant        GM_getValue
// @grant        GM_setValue
// @connect      __HOST__
// @run-at       document-idle
// ==/UserScript==

(function () {
  'use strict';

  const SGE = '__SGE__';
  const CHAVE = 'eagora.chave';
  const FILA = 'eagora.fila';
  const CODIGO = /\bQ\d{4,10}\b/g;
  // As pistas do resultado, como o QConcursos as escreve. Se mudarem, a questão vira diagnóstico (D-171).
  const ACERTOU = [/Parab[ée]ns!?\s*Voc[êe] acertou/i, /Voc[êe] acertou/i, /Resposta correta/i];
  const ERROU = [/Voc[êe] errou/i, /Incorreta\.?\s*Gabarito/i, /Resposta incorreta/i];
  const enviadas = new Set();
  let contagem = 0;
  let pendente = null; // o recorte de uma questão não entendida

  // --- envio ---------------------------------------------------------------------------------------------------------
  function enviar(caminho, corpo) {
    return new Promise((resolve) => {
      const chave = GM_getValue(CHAVE, '');
      if (!chave) { resolve({ ok: false, motivo: 'sem chave' }); return; }
      GM_xmlhttpRequest({
        method: 'POST', url: SGE + caminho, anonymous: true, timeout: 15000,
        headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + chave },
        data: JSON.stringify(corpo),
        onload: (r) => {
          let j = null;
          try { j = JSON.parse(r.responseText); } catch (e) { /* sem corpo */ }
          resolve({ ok: r.status >= 200 && r.status < 300, status: r.status, corpo: j });
        },
        onerror: () => resolve({ ok: false, motivo: 'rede' }),
        ontimeout: () => resolve({ ok: false, motivo: 'tempo' }),
      });
    });
  }

  function fila() { try { return JSON.parse(GM_getValue(FILA, '[]')); } catch (e) { return []; } }
  function guardarFila(f) { GM_setValue(FILA, JSON.stringify(f.slice(-200))); }

  async function enviarQuestao(q) {
    const r = await enviar('/captura/qconcursos/respostas', q);
    if (r.ok) {
      contagem++;
      selo(r.corpo && r.corpo.assunto ? q.codigo + ' → ' + r.corpo.assunto : q.codigo + ' na caixa do eAgora');
    } else if (r.status === 401) {
      selo('A chave foi recusada. Gere outra em Ajustes do eAgora.', true);
    } else if (r.status && r.status < 500) {
      selo('O eAgora recusou ' + q.codigo + ' (' + r.status + ').', true);
    } else {
      const f = fila(); f.push(q); guardarFila(f);
      selo('Sem conexão com o eAgora: ' + f.length + ' na fila.', true);
    }
  }

  async function esvaziarFila() {
    const f = fila();
    if (!f.length) return;
    guardarFila([]);
    for (const q of f) await enviarQuestao(q);
  }

  // --- leitura da questão ----------------------------------------------------------------------------------------------
  function codigosEm(el) {
    return [...new Set(((el && el.innerText) || '').match(CODIGO) || [])];
  }

  /** O menor ancestral que tem um número Q só. */
  function questaoDe(no) {
    let el = no;
    while (el && el !== document.body) {
      const c = codigosEm(el);
      if (c.length === 1) return el;
      if (c.length > 1) return null;
      el = el.parentElement;
    }
    return null;
  }

  function primeiro(regexes, texto) { return regexes.some((r) => r.test(texto)); }

  function ler(el, textoDoResultado) {
    const texto = el.innerText || '';
    const codigo = codigosEm(el)[0];
    const acertou = primeiro(ACERTOU, textoDoResultado) ? true : primeiro(ERROU, textoDoResultado) ? false : null;
    if (!codigo || acertou === null) return null;
    const gab = textoDoResultado.match(/Gabarito(?: oficial da banca)?:?\s*([A-E]|Certo|Errado)\b/i) || texto.match(/Gabarito(?: oficial da banca)?:?\s*([A-E]|Certo|Errado)\b/i);
    const ano = texto.match(/Ano:\s*(\d{4})/);
    const banca = texto.match(/Banca:\s*([^\n\r]+?)(?=\s+Órgão:|\s+Prova:|\n|$)/);
    const links = [...el.querySelectorAll('a[href]')];
    const disc = links.filter((a) => /disciplin/i.test(a.getAttribute('href'))).map((a) => a.innerText.trim()).filter(Boolean);
    const assuntoLinks = links.filter((a) => /assunto|subject/i.test(a.getAttribute('href')));
    const assuntos = (assuntoLinks.length ? assuntoLinks.map((a) => a.innerText.trim()) : disc.slice(1)).filter(Boolean);
    const nos = assuntoLinks.map((a) => (a.getAttribute('href').match(/(?:subject_ids%5B%5D=|subject_ids\[\]=|assuntos?\/)(\d+)/) || [])[1])
      .filter(Boolean).map(Number);
    const marcada = el.querySelector('input[type="radio"]:checked');
    return {
      codigo, acertou, resolvidaEm: new Date().toISOString(),
      disciplina: disc[0] || null, assuntos, nos,
      banca: banca ? banca[1].trim() : null, ano: ano ? Number(ano[1]) : null,
      alternativa: marcada ? (marcada.value || null) : null, gabarito: gab ? gab[1] : null,
      endereco: location.href,
    };
  }

  function recorte(el) {
    const copia = el.cloneNode(true);
    copia.querySelectorAll('script, style, svg, img, iframe, input[type="hidden"]').forEach((n) => n.remove());
    return copia.outerHTML.replace(/\s+/g, ' ').slice(0, 20000);
  }

  function examinar(no) {
    const texto = no.innerText || '';
    if (!primeiro(ACERTOU, texto) && !primeiro(ERROU, texto)) return;
    const el = questaoDe(no);
    if (!el) { naoEntendi(no.parentElement || no, 'resultado sem número Q único'); return; }
    const q = ler(el, texto);
    if (!q) { naoEntendi(el, 'não li o número ou o acerto'); return; }
    const chave = q.codigo + (q.acertou ? '+' : '-');
    if (enviadas.has(chave)) return;
    enviadas.add(chave);
    enviarQuestao(q);
  }

  function naoEntendi(el, motivo) {
    pendente = { motivo, endereco: location.href, recorte: recorte(el) };
    selo('Não entendi esta questão.', true, true);
  }

  async function enviarDiagnostico(alvo) {
    const d = alvo || pendente;
    if (!d) return;
    const r = await enviar('/captura/qconcursos/diagnosticos', d);
    pendente = null;
    selo(r.ok ? 'Diagnóstico enviado. Obrigado!' : 'Não consegui enviar o diagnóstico.', !r.ok);
  }

  /** "Diagnóstico desta página": o primeiro bloco com número Q, para acertar a leitura sem esperar um erro. */
  function diagnosticoDaPagina() {
    const candidatos = [...document.querySelectorAll('article, section, li, div')].filter((e) => codigosEm(e).length === 1);
    const menor = candidatos.sort((a, b) => a.innerText.length - b.innerText.length).find((e) => e.innerText.length > 200) || candidatos[0];
    enviarDiagnostico({ motivo: 'pedido na página', endereco: location.href, recorte: recorte(menor || document.body) });
  }

  // --- o selo --------------------------------------------------------------------------------------------------------
  let caixa;
  function selo(mensagem, alerta, comDiagnostico) {
    if (!caixa) {
      caixa = document.createElement('div');
      caixa.style.cssText = 'position:fixed;right:12px;bottom:12px;z-index:2147483647;max-width:240px;background:#0f1514;color:#e6eeec;'
        + 'font:13px/1.35 system-ui,sans-serif;border-radius:12px;padding:10px 12px;box-shadow:0 6px 20px rgba(0,0,0,.35)';
      document.body.appendChild(caixa);
    }
    caixa.innerHTML = '';
    const t = document.createElement('div');
    t.style.fontWeight = '700';
    t.textContent = 'eAgora · ' + contagem + (contagem === 1 ? ' enviada' : ' enviadas');
    caixa.appendChild(t);
    if (!GM_getValue(CHAVE, '')) {
      const p = document.createElement('div'); p.textContent = 'Cole a chave de envio (Ajustes › Captura do QConcursos):'; caixa.appendChild(p);
      const i = document.createElement('input'); i.type = 'password'; i.style.cssText = 'width:100%;margin:6px 0;padding:6px;border-radius:6px;border:0';
      caixa.appendChild(i);
      const b = botao('Guardar', () => { GM_setValue(CHAVE, i.value.trim()); selo('Chave guardada.'); esvaziarFila(); });
      caixa.appendChild(b);
      return;
    }
    if (mensagem) {
      const m = document.createElement('div'); m.textContent = mensagem; if (alerta) m.style.color = '#ffb38a'; caixa.appendChild(m);
    }
    if (comDiagnostico) caixa.appendChild(botao('Enviar diagnóstico', () => enviarDiagnostico()));
    const menu = document.createElement('div'); menu.style.cssText = 'margin-top:6px;display:flex;gap:10px;flex-wrap:wrap';
    menu.appendChild(link('Diagnóstico desta página', diagnosticoDaPagina));
    menu.appendChild(link('Trocar a chave', () => { GM_setValue(CHAVE, ''); selo(); }));
    caixa.appendChild(menu);
  }

  function botao(texto, fazer) {
    const b = document.createElement('button'); b.type = 'button'; b.textContent = texto;
    b.style.cssText = 'margin-top:6px;min-height:36px;padding:0 10px;border:0;border-radius:8px;background:#2bb7c4;color:#06272b;font-weight:700;cursor:pointer';
    b.addEventListener('click', fazer); return b;
  }

  function link(texto, fazer) {
    const a = document.createElement('a'); a.href = '#'; a.textContent = texto; a.style.cssText = 'color:#9fe3ea;text-decoration:underline;font-size:12px';
    a.addEventListener('click', (e) => { e.preventDefault(); fazer(); }); return a;
  }

  // --- começo --------------------------------------------------------------------------------------------------------
  new MutationObserver((mudancas) => {
    for (const m of mudancas) {
      for (const no of m.addedNodes) {
        if (no.nodeType === 1) examinar(no);
        else if (no.nodeType === 3 && no.parentElement) examinar(no.parentElement);
      }
      if (m.type === 'characterData' && m.target.parentElement) examinar(m.target.parentElement);
    }
  }).observe(document.body, { childList: true, subtree: true, characterData: true });
  selo();
  esvaziarFila();
})();
