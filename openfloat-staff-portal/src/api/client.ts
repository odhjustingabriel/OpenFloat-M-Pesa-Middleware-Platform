import axios from 'axios';
const tokenKey='openfloat.access_token';
export const api=axios.create({baseURL:import.meta.env.VITE_API_BASE_URL ?? '',timeout:15000});
api.interceptors.request.use((config)=>{const token=localStorage.getItem(tokenKey); if(token) config.headers.Authorization=`Bearer ${token}`; return config;});
export const auth={tokenKey,login(){const base=import.meta.env.VITE_AUTH_BASE_URL ?? ''; const clientId=import.meta.env.VITE_OAUTH_CLIENT_ID ?? 'openfloat-staff-portal'; const redirect=encodeURIComponent(`${location.origin}/oauth/callback`); const state=crypto.randomUUID(); sessionStorage.setItem('oauth_state',state); location.href=`${base}/oauth2/authorize?response_type=code&client_id=${clientId}&redirect_uri=${redirect}&scope=openid%20profile&state=${state}`;},logout(){localStorage.removeItem(tokenKey); location.href='/login';}};
