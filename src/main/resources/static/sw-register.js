/**
 * Service Worker Registration Script
 * This file handles the registration and lifecycle management of the service worker.
 */

// Check if service workers are supported
if ('serviceWorker' in navigator) {
  window.addEventListener('load', function() {
    // Define options for service worker registration
    const options = {
      scope: '/'
    };

    // Register the service worker with error handling
    navigator.serviceWorker.register('/sw.js', options)
      .then(function(registration) {
        console.log('ServiceWorker registration successful with scope: ', registration.scope);
        
        // Handle service worker updates
        registration.addEventListener('updatefound', function() {
          const newWorker = registration.installing;
          console.log('Service worker update found!');
          
          newWorker.addEventListener('statechange', function() {
            console.log('Service worker state: ' + newWorker.state);
          });
        });
      })
      .catch(function(error) {
        console.error('ServiceWorker registration failed: ', error);
      });

    // Handle service worker controller changes
    navigator.serviceWorker.addEventListener('controllerchange', function() {
      console.log('Service worker controller changed');
    });
  });
}
