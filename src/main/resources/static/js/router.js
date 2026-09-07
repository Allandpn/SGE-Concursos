// js/router.js — roteamento por hash, navegação nunca vai ao servidor
// (ADR-030, 04_FRONTEND.md §2).

function renderizarRotaAtual() {
  const hash = window.location.hash || '#/hoje';
  const elHoje = document.getElementById('pagina-hoje');
  const elRecuperar = document.getElementById('pagina-recuperar');
  const matchRecuperar = hash.match(/^#\/recuperar\/(\d+)$/);

  if (matchRecuperar) {
    elHoje.hidden = true;
    elRecuperar.hidden = false;
    Alpine.$data(elRecuperar).abrir(Number(matchRecuperar[1]));
  } else {
    elRecuperar.hidden = true;
    elHoje.hidden = false;
    Alpine.$data(elHoje).init();
  }
}

window.addEventListener('hashchange', renderizarRotaAtual);

// alpine:initialized, não DOMContentLoaded: Alpine.$data só existe depois que
// o Alpine termina de montar os x-data da página (carregado com `defer`).
document.addEventListener('alpine:initialized', () => {
  if (!window.location.hash) {
    window.location.hash = '#/hoje'; // dispara hashchange, que já chama renderizarRotaAtual
  } else {
    renderizarRotaAtual();
  }
});
