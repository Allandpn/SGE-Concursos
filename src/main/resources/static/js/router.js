// js/router.js — roteamento por hash, navegação nunca vai ao servidor
// (ADR-030, 04_FRONTEND.md §2).

function renderizarRotaAtual() {
  const hash = window.location.hash || '#/hoje';
  const elHoje = document.getElementById('pagina-hoje');
  const elRecuperar = document.getElementById('pagina-recuperar');
  const elRegistrar = document.getElementById('pagina-registrar');
  const elAssuntos = document.getElementById('pagina-assuntos');
  const elAssuntoDetalhe = document.getElementById('pagina-assunto-detalhe');
  const elErros = document.getElementById('pagina-erros');
  const elProgresso = document.getElementById('pagina-progresso');
  const matchRecuperar = hash.match(/^#\/recuperar\/(\d+)$/);
  const matchRegistrar = hash.match(/^#\/registrar\/(conteudo|questoes|flashcards)\/(\d+)$/);
  const matchAssuntoDetalhe = hash.match(/^#\/assuntos\/(\d+)$/);
  const matchAssuntos = hash === '#/assuntos';
  const matchErros = hash.match(/^#\/erros\/(\d+)$/);
  const matchProgresso = hash === '#/progresso';

  elHoje.hidden = true;
  elRecuperar.hidden = true;
  elRegistrar.hidden = true;
  elAssuntos.hidden = true;
  elAssuntoDetalhe.hidden = true;
  elErros.hidden = true;
  elProgresso.hidden = true;

  if (matchRecuperar) {
    elRecuperar.hidden = false;
    Alpine.$data(elRecuperar).abrir(Number(matchRecuperar[1]));
  } else if (matchRegistrar) {
    elRegistrar.hidden = false;
    Alpine.$data(elRegistrar).abrir(matchRegistrar[1], Number(matchRegistrar[2]));
  } else if (matchAssuntoDetalhe) {
    elAssuntoDetalhe.hidden = false;
    Alpine.$data(elAssuntoDetalhe).abrir(Number(matchAssuntoDetalhe[1]));
  } else if (matchAssuntos) {
    elAssuntos.hidden = false;
    Alpine.$data(elAssuntos).init();
  } else if (matchErros) {
    elErros.hidden = false;
    Alpine.$data(elErros).abrir(Number(matchErros[1]));
  } else if (matchProgresso) {
    elProgresso.hidden = false;
    Alpine.$data(elProgresso).init();
  } else {
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
