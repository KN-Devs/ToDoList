export const API_BASE_URL = 'http://localhost:8080/api';

// Dérivée d'API_BASE_URL (qui est substituée au build sur Render, voir
// render.yaml) : même hôte, schéma ws(s) au lieu de http(s), sans le
// suffixe /api puisque l'endpoint STOMP /ws est monté à la racine.
export const WS_BASE_URL = API_BASE_URL.replace(/^http/, 'ws').replace(/\/api$/, '');
