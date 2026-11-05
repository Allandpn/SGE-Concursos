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
  const elAjustes = document.getElementById('pagina-ajustes');
  const elSessoes = document.getElementById('pagina-sessoes');
  const elConcursos = document.getElementById('pagina-concursos');
  const elEdital = document.getElementById('pagina-edital');
  const elConferencia = document.getElementById('pagina-conferencia');
  const elLeitor = document.getElementById('pagina-leitor');
  const elProva = document.getElementById('pagina-prova');
  const elResolver = document.getElementById('pagina-resolver');
  const elSimulado = document.getElementById('pagina-simulado');
  const matchSimulado = hash === '#/registrar/simulado'; // D-148: o simulado não tem assunto
  const matchRecuperar = hash.match(/^#\/recuperar\/(\d+)$/);
  const matchRegistrar = hash.match(/^#\/registrar\/(conteudo|questoes|flashcards|discursiva)\/(\d+)$/);
  const matchAssuntoDetalhe = hash.match(/^#\/assuntos\/(\d+)$/);
  const matchAssuntos = hash === '#/assuntos';
  const matchErros = hash.match(/^#\/erros\/(\d+)$/);
  const matchProgresso = hash === '#/progresso';
  const matchAjustes = hash === '#/ajustes';
  const matchSessoes = hash === '#/sessoes'; // §8D
  const matchConcursos = hash === '#/concursos';
  const matchEditalNovo = hash === '#/concursos/novo';
  const matchAnalise = hash.match(/^#\/concursos\/analise\/(\d+)$/);
  const matchConferencia = hash.match(/^#\/concursos\/analise\/(\d+)\/conferencia$/);
  const matchProvaNova = hash === '#/provas/nova';
  const matchProva = hash.match(/^#\/provas\/(\d+)$/);
  const matchRelatorioProva = hash.match(/^#\/provas\/(\d+)\/relatorio$/);
  const matchResolver = hash.match(/^#\/provas\/(\d+)\/resolver$/);
  const matchLeitor = hash.match(/^#\/ler\/(\d+)(?:\?(.*))?$/); // ?p=N (D-121) e ?previa=1 (D-158)
  const paramsLeitor = matchLeitor ? new URLSearchParams(matchLeitor[2] || '') : null;

  // §8B: onde se está (a marca da navegação) e o modo foco das telas de estudo, sem navegação.
  const foco = !!(matchRecuperar || matchRegistrar || matchLeitor);
  const rotaNav = matchAssuntos || matchAssuntoDetalhe || matchErros ? 'assuntos'
    : matchProgresso || matchSimulado ? 'progresso'
    : matchConcursos || matchEditalNovo || matchAnalise || matchConferencia || matchProvaNova || matchProva || matchRelatorioProva || matchResolver ? 'concursos'
    : matchAjustes || matchSessoes ? 'ajustes'
    : foco ? 'estudo' : 'hoje';
  Alpine.store('nav').ir(rotaNav, foco);

  elHoje.hidden = true;
  elRecuperar.hidden = true;
  elRegistrar.hidden = true;
  elAssuntos.hidden = true;
  elAssuntoDetalhe.hidden = true;
  elErros.hidden = true;
  elProgresso.hidden = true;
  elAjustes.hidden = true;
  elSessoes.hidden = true;
  elConcursos.hidden = true;
  elEdital.hidden = true;
  elConferencia.hidden = true;
  elLeitor.hidden = true;
  elProva.hidden = true;
  elResolver.hidden = true;
  elSimulado.hidden = true;
  if (!matchProva) Alpine.$data(elProva).parar(); // o acompanhamento da prova só vive na tela dela
  if (!matchResolver) Alpine.$data(elResolver).parar();
  if (!matchLeitor) Alpine.$data(elLeitor).parar(); // o envio periódico da fila só vive com o leitor aberto
  // A conferência tem mais coisa por linha: usa a largura da tela em computador; o resto segue como celular.
  const principal = document.querySelector('main');
  principal.classList.toggle('max-w-md', !matchConferencia && !matchLeitor);
  principal.classList.toggle('max-w-3xl', !!matchConferencia || !!matchLeitor);
  Alpine.$data(elEdital).parar(); // a consulta periódica da análise só vive na tela dela
  // D-157: Hoje ganha a coluna dos próximos 7 dias no computador; a largura volta ao sair de Hoje (ajustada no fim, abaixo).
  principal.classList.remove('lg:max-w-5xl');
  // D-159: o ensaio só vive nas telas de Recuperar e Registrar; sair delas o encerra.
  if (!matchRecuperar && !matchRegistrar) Alpine.store('ensaio').sair();

  if (matchSessoes) {
    elSessoes.hidden = false;
    Alpine.$data(elSessoes).abrir();
  } else if (matchSimulado) {
    elSimulado.hidden = false;
    Alpine.$data(elSimulado).abrir();
  } else if (matchProvaNova) {
    elProva.hidden = false;
    Alpine.$data(elProva).abrirNova();
  } else if (matchProva) {
    elProva.hidden = false;
    Alpine.$data(elProva).abrir(Number(matchProva[1]));
  } else if (matchRelatorioProva) {
    elProva.hidden = false;
    Alpine.$data(elProva).abrirRelatorio(Number(matchRelatorioProva[1]));
  } else if (matchResolver) {
    elResolver.hidden = false;
    Alpine.$data(elResolver).abrir(Number(matchResolver[1]));
  } else if (matchLeitor) {
    elLeitor.hidden = false;
    Alpine.$data(elLeitor).abrir(Number(matchLeitor[1]), paramsLeitor.get('p') ? Number(paramsLeitor.get('p')) : null, paramsLeitor.get('previa') === '1');
  } else if (matchRecuperar) {
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
  } else if (matchAjustes) {
    elAjustes.hidden = false;
    Alpine.$data(elAjustes).init();
  } else if (matchConcursos) {
    elConcursos.hidden = false;
    Alpine.$data(elConcursos).init();
  } else if (matchEditalNovo) {
    elEdital.hidden = false;
    Alpine.$data(elEdital).abrirNovo();
  } else if (matchConferencia) {
    elConferencia.hidden = false;
    Alpine.$data(elConferencia).abrir(Number(matchConferencia[1]));
  } else if (matchAnalise) {
    elEdital.hidden = false;
    Alpine.$data(elEdital).abrir(Number(matchAnalise[1]));
  } else {
    elHoje.hidden = false;
    principal.classList.add('lg:max-w-5xl');
    Alpine.$data(elHoje).init();
  }
}

window.addEventListener('hashchange', renderizarRotaAtual);

// alpine:initialized, não DOMContentLoaded: Alpine.$data só existe depois que
// o Alpine termina de montar os x-data da página (carregado com `defer`).
document.addEventListener('alpine:initialized', async () => {
  await estadoModoTeste; // ADR-061: a data do teste vale desde a primeira tela
  if (!window.location.hash) {
    window.location.hash = '#/hoje'; // dispara hashchange, que já chama renderizarRotaAtual
  } else {
    renderizarRotaAtual();
  }
});
