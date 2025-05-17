/**
 * JWT Injector Script
 * 
 * This script intercepts all AJAX requests made by Vaadin and adds the JWT token 
 * stored in localStorage to each request's headers.
 */
(function() {
    const JWT_TOKEN_KEY = 'answer42_jwt_token';
    const SESSION_HEARTBEAT_INTERVAL = 60000; // 1 minute
    let heartbeatTimer = null;

    // Store the token in localStorage and sessionStorage
    window.storeJwtToken = function(token) {
        if (token) {
            localStorage.setItem(JWT_TOKEN_KEY, token);
            sessionStorage.setItem(JWT_TOKEN_KEY, token);
            console.debug('JWT token stored in localStorage and sessionStorage');
            startHeartbeat();
        }
    };
    
    // Remove the token from localStorage
    window.clearJwtToken = function() {
        localStorage.removeItem(JWT_TOKEN_KEY);
        sessionStorage.removeItem(JWT_TOKEN_KEY);
        console.debug('JWT token removed from localStorage and sessionStorage');
        stopHeartbeat();
    };
    
    // Heartbeat to keep the session alive
    function startHeartbeat() {
        stopHeartbeat(); // Clear any existing heartbeat
        
        heartbeatTimer = setInterval(function() {
            const token = localStorage.getItem(JWT_TOKEN_KEY);
            if (token) {
                // Send a heartbeat request to keep the session alive
                fetch('/heartbeat', {
                    method: 'GET',
                    headers: {
                        'Authorization': 'Bearer ' + token
                    }
                }).catch(err => {
                    console.debug('Heartbeat error (this is usually ok):', err);
                });
            } else {
                stopHeartbeat();
            }
        }, SESSION_HEARTBEAT_INTERVAL);
    }
    
    function stopHeartbeat() {
        if (heartbeatTimer) {
            clearInterval(heartbeatTimer);
            heartbeatTimer = null;
        }
    }
    
    // Initialize heartbeat if we have a token
    if (localStorage.getItem(JWT_TOKEN_KEY)) {
        startHeartbeat();
    }
    
    // Add token to all Vaadin requests
    const originalOpen = XMLHttpRequest.prototype.open;
    XMLHttpRequest.prototype.open = function() {
        originalOpen.apply(this, arguments);
        
        const url = arguments[1] || '';
        this._url = url; // Store URL for later use
        
        this.addEventListener('readystatechange', function() {
            if (this.readyState === 1) { // 1 = OPENED
                const token = localStorage.getItem(JWT_TOKEN_KEY);
                if (token && shouldAddToken(this)) {
                    this.setRequestHeader('Authorization', 'Bearer ' + token);
                }
            }
        });
    };
    
    // Advanced check to determine if we should add the token to this request
    function shouldAddToken(xhr) {
        const url = xhr._url || '';
        
        // Always add token to our application view routes
        if (url === '/' || 
            url.startsWith('/papers') || 
            url.startsWith('/dashboard') || 
            url.startsWith('/projects') || 
            url.startsWith('/profile') || 
            url.startsWith('/settings') || 
            url.startsWith('/ai-chat')) {
            return true;
        }
        
        // Add token to Vaadin-related URLs that aren't static resources
        if (url.includes('?v-r=') || url.includes('?v-uiId=')) {
            return true;
        }
        
        // Don't add token to auth endpoints (they handle auth themselves)
        if (url.includes('/api/auth/')) {
            return false;
        }
        
        // Only add token to same-origin requests
        if (url.startsWith('http') && !url.startsWith(window.location.origin)) {
            return false;
        }
        
        // Skip static resources
        const skipPatterns = [
            '/VAADIN/', 
            '/frontend/', 
            '/sw.js', 
            '/manifest.webmanifest',
            '.js', 
            '.css', 
            '.png', 
            '.jpg', 
            '.gif', 
            '.svg', 
            '.ico'
        ];
        
        for (const pattern of skipPatterns) {
            if (url.includes(pattern)) {
                return false;
            }
        }
        
        // Default to adding the token for other application requests
        return true;
    }
    
    // Add a global event listener to persist authentication during view navigation
    window.addEventListener('vaadin-router-location-changed', function(event) {
        console.debug('Navigation event detected', event.detail.location);
        // Ensure token is still available after navigation
        const token = localStorage.getItem(JWT_TOKEN_KEY);
        if (token) {
            // Ensure the token is stored in sessionStorage as well
            sessionStorage.setItem(JWT_TOKEN_KEY, token);
        }
    });
})();
