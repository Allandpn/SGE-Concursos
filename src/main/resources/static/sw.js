// sw.js — service worker da casca estática (ADR-038, docs/SPRINT-16-CORRECOES-MULTIEDITAL.md §4).
//
// Guarda só HTML/JS/CSS/ícones/vendor, para o app abrir rápido e com rede
// instável. /api/** NUNCA passa pelo cache: dado de estudo sempre vem do
// servidor, e gravação sem rede falha do jeito não destrutivo de sempre
// (02_JORNADAS §5.1). A única exceção é do leitor (ADR-052): os PDFs de bloco
// guardados no cache "material-v1" e a fila de marcações no IndexedDB
// (js/offline.js), que não passa por aqui.
//
// TROQUE CASCA_VERSAO a cada mudança em qualquer arquivo estático, senão os
// aparelhos continuam com a casca antiga.
const CASCA_VERSAO = 'casca-2026-11-02-2';
// ADR-052: os PDFs guardados para ler sem rede ficam num cache próprio, que a troca de casca não apaga.
const CACHE_MATERIAL = 'material-v1';

const CASCA = [
  './',
  'index.html',
  'manifest.webmanifest',
  'css/styles.css',
  'js/api.js',
  'js/entrar.js',
  'js/offline.js',
  'js/leitor.js',
  'js/prova.js',
  'vendor/pdfjs-4.10.38/pdf.min.mjs',
  'vendor/pdfjs-4.10.38/pdf.worker.min.mjs',
  'entrar.html',
  'js/pages.js',
  'js/router.js',
  'vendor/alpinejs-3.14.3.min.js',
  'vendor/tailwindcss-play-3.4.16.js',
  'vendor/fonts/open-sans.css',
  'vendor/fonts/open-sans-latin-400-normal.woff2',
  'vendor/fonts/open-sans-latin-500-normal.woff2',
  'vendor/fonts/open-sans-latin-600-normal.woff2',
  'vendor/fonts/open-sans-latin-700-normal.woff2',
  'icons/icon.svg',
  'icons/icon-192.png',
  'icons/icon-512.png',
  'icons/icon-maskable-512.png',
  'icons/apple-touch-icon.png',
];

self.addEventListener('install', (evento) => {
  // Sem sessão, as telas redirecionam para a porta (D-106): com redirect 'error' a instalação falha em vez de guardar a
  // porta no lugar do index.html, e o navegador tenta de novo na próxima abertura, já com sessão.
  evento.waitUntil(caches.open(CASCA_VERSAO).then((cache) => cache.addAll(CASCA.map((u) => new Request(u, { redirect: 'error' })))));
  self.skipWaiting();
});

self.addEventListener('activate', (evento) => {
  evento.waitUntil(
    caches.keys()
      .then((nomes) => Promise.all(nomes.filter((n) => n !== CASCA_VERSAO && n !== CACHE_MATERIAL).map((n) => caches.delete(n))))
      .then(() => self.clients.claim()),
  );
});

self.addEventListener('fetch', (evento) => {
  const url = new URL(evento.request.url);
  if (evento.request.method !== 'GET' || url.origin !== self.location.origin) return;
  if (url.pathname.startsWith('/api/') || url.pathname.startsWith('/actuator/')) return; // sempre rede
  // PDFs do material (ADR-044): nunca na casca. Os guardados para ler sem rede (D-119, ADR-052) saem do cache
  // próprio; o resto vem da rede, como sempre.
  if (url.pathname.startsWith('/material/')) {
    evento.respondWith(caches.open(CACHE_MATERIAL).then((c) => c.match(evento.request)).then((guardado) => guardado || fetch(evento.request)));
    return;
  }

  // Rede primeiro: com servidor no ar, a casca vem sempre atualizada; sem
  // rede, cai no cache (navegação cai no index.html).
  evento.respondWith(
    fetch(evento.request)
      .then((resposta) => {
        if (resposta.ok) {
          const copia = resposta.clone();
          caches.open(CASCA_VERSAO).then((cache) => cache.put(evento.request, copia));
        }
        return resposta;
      })
      .catch(() => caches.match(evento.request)
        .then((emCache) => emCache || (evento.request.mode === 'navigate' ? caches.match('index.html') : undefined))),
  );
});

// Avisos de processo concluído (D-96, ADR-048): a carga vem cifrada do servidor como {titulo, corpo, url}.
self.addEventListener('push', (evento) => {
  let dados = {};
  try {
    dados = evento.data ? evento.data.json() : {};
  } catch (e) {
    dados = { corpo: evento.data ? evento.data.text() : '' };
  }
  evento.waitUntil(self.registration.showNotification(dados.titulo || 'eAgora', {
    body: dados.corpo || '',
    icon: 'icons/icon-192.png',
    badge: 'icons/icon-192.png',
    data: { url: dados.url || '/#/hoje' },
  }));
});

self.addEventListener('notificationclick', (evento) => {
  evento.notification.close();
  const alvo = new URL((evento.notification.data && evento.notification.data.url) || '/#/hoje', self.location.origin).href;
  evento.waitUntil(
    clients.matchAll({ type: 'window', includeUncontrolled: true }).then((janelas) => {
      for (const janela of janelas) {
        if ('focus' in janela) return janela.focus().then((c) => (c && 'navigate' in c ? c.navigate(alvo) : c));
      }
      return clients.openWindow(alvo);
    }),
  );
});
