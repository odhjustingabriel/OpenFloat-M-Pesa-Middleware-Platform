import axios from 'axios';

const TOKEN_KEY = 'openfloat.access_token';
const ROLE_KEY = 'openfloat.role';

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '',
  timeout: 15_000,
  headers: { 'Content-Type': 'application/json' },
});

// Attach JWT Bearer token to every outgoing request
api.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY);
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Redirect to login on 401
api.interceptors.response.use(
  (res) => res,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem(TOKEN_KEY);
      localStorage.removeItem(ROLE_KEY);
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

export const auth = {
  tokenKey: TOKEN_KEY,
  roleKey: ROLE_KEY,

  /** Initiate OAuth2 PKCE authorization code flow */
  login() {
    const base = import.meta.env.VITE_AUTH_BASE_URL ?? '';
    const clientId = import.meta.env.VITE_OAUTH_CLIENT_ID ?? 'openfloat-staff-portal';
    const redirect = encodeURIComponent(`${location.origin}/oauth/callback`);
    const state = crypto.randomUUID();
    sessionStorage.setItem('oauth_state', state);

    // Generate PKCE code verifier + challenge
    const verifier = generateCodeVerifier();
    sessionStorage.setItem('pkce_verifier', verifier);

    generateCodeChallenge(verifier).then((challenge) => {
      location.href =
        `${base}/oauth2/authorize` +
        `?response_type=code` +
        `&client_id=${clientId}` +
        `&redirect_uri=${redirect}` +
        `&scope=openid%20profile` +
        `&state=${state}` +
        `&code_challenge=${challenge}` +
        `&code_challenge_method=S256`;
    });
  },

  logout() {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(ROLE_KEY);
    location.href = '/login';
  },

  isAuthenticated(): boolean {
    return !!localStorage.getItem(TOKEN_KEY);
  },

  getRole(): string {
    return localStorage.getItem(ROLE_KEY) ?? 'STAFF';
  },
};

/* ── PKCE Helpers ─────────────────────────────────── */
function generateCodeVerifier(): string {
  const array = new Uint8Array(32);
  crypto.getRandomValues(array);
  return base64UrlEncode(array);
}

async function generateCodeChallenge(verifier: string): Promise<string> {
  const encoder = new TextEncoder();
  const data = encoder.encode(verifier);
  const digest = await crypto.subtle.digest('SHA-256', data);
  return base64UrlEncode(new Uint8Array(digest));
}

function base64UrlEncode(buffer: Uint8Array): string {
  let binary = '';
  for (const byte of buffer) {
    binary += String.fromCharCode(byte);
  }
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}
