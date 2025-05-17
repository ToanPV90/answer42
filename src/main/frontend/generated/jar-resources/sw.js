/**
 * Service Worker for Answer42
 * This is a minimal service worker that doesn't cache content but prevents the offline message.
 */

// Service worker version
const VERSION = '1.0.0';

// Cache name for resources
const CACHE_NAME = 'answer42-cache-v1';

// Log installation and cache critical files
self.addEventListener('install', event => {
  console.log('Service worker installed', VERSION);
  // Cache the offline page and essential resources
  event.waitUntil(
    caches.open(CACHE_NAME).then(cache => {
      return cache.addAll([
        '/offline.html',
        '/images/answer42-logo.svg',
        '/favicon.ico',
        '/favicon.svg'
      ]);
    })
  );
  self.skipWaiting();
});

// Clean up previous caches on activation
self.addEventListener('activate', event => {
  console.log('Service worker activated', VERSION);
  event.waitUntil(
    caches.keys().then(cacheNames => {
      return Promise.all(
        cacheNames.filter(cacheName => {
          return cacheName !== CACHE_NAME;
        }).map(cacheName => {
          return caches.delete(cacheName);
        })
      );
    })
  );
  self.clients.claim();
});

// Intercept fetch requests
self.addEventListener('fetch', event => {
  if (event.request.mode === 'navigate') {
    // For navigation requests (HTML pages), try network first, then fallback to offline
    event.respondWith(
      fetch(event.request)
        .catch(() => {
          // Only return offline page if we can't reach the server
          return caches.match('/offline.html');
        })
    );
  } else {
    // For all other resources, just fetch from network without custom fallback
    // This prevents showing offline message for API/resource fetches
    event.respondWith(
      fetch(event.request)
        .catch(() => {
          return new Response('Resource unavailable', {
            status: 408,
            headers: { 'Content-Type': 'text/plain' }
          });
        })
    );
  }
});
