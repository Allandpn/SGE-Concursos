// sw.js — service worker da casca estática (ADR-038, docs/SPRINT-16-CORRECOES-MULTIEDITAL.md §4).
//
// Guarda só HTML/JS/CSS/ícones/vendor, para o app abrir rápido e com rede
// instável. /api/** NUNCA passa pelo cache nem é enfileirado: dado de estudo
// sempre vem do servidor, e gravação sem rede falha do jeito não destrutivo
// de sempre (02_JORNADAS §5.1 — sem fila offline).
//
// TROQUE CASCA_VERSAO a cada mudança em qualquer arquivo estático, senão os
// aparelhos continuam com a casca antiga.
const CASCA_VERSAO = 'casca-2026-10-26-2';

const CASCA = [
  './',
  'index.html',
  'manifest.webmanifest',
  'css/styles.css',
  'js/api.js',
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
  evento.waitUntil(caches.open(CASCA_VERSAO).then((cache) => cache.addAll(CASCA)));
  self.skipWaiting();
});

self.addEventListener('activate', (evento) => {
  evento.waitUntil(
    caches.keys()
      .then((nomes) => Promise.all(nomes.filter((n) => n !== CASCA_VERSAO).map((n) => caches.delete(n))))
      .then(() => self.clients.claim()),
  );
});

self.addEventListener('fetch', (evento) => {
  const url = new URL(evento.request.url);
  if (evento.request.method !== 'GET' || url.origin !== self.location.origin) return;
  if (url.pathname.startsWith('/api/') || url.pathname.startsWith('/actuator/')) return; // sempre rede
  // PDFs do material (ADR-044): grandes e servidos pelo próprio SGE — nunca na casca.
  if (url.pathname.startsWith('/material/')) return;

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
