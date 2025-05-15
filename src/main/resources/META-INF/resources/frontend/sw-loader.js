/**
 * Service Worker Loader Script
 * This file is automatically loaded by Vaadin and registers the service worker.
 */

// Wait for the window to load
window.addEventListener('load', function() {
  // Load our custom service worker registration script
  if ('serviceWorker' in navigator) {
    try {
      const script = document.createElement('script');
      script.src = '/sw-register.js';
      script.async = true;
      document.head.appendChild(script);
      console.log('Service worker registration script loaded');
    } catch (e) {
      console.error('Error loading service worker registration script:', e);
    }
  }
});
