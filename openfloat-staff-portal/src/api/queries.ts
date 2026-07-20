import { api } from './client';
import type { Transaction, User } from '../types/domain';
export async function fetchTransactions(params:Record<string,string|number|undefined>={}){const {data}=await api.get('/api/v1/transactions',{params}); return data.data?.content ?? data.data?.items ?? [] as Transaction[];}
export async function fetchTransaction(id:string){const {data}=await api.get(`/api/v1/transactions/${id}`); return data.data as Transaction;}
export async function initiateStkPush(payload:{msisdn:string; amount:number; accountReference:string; paybill:string}){const {data}=await api.post('/api/v1/payments/stk-push',payload); return data.data;}
export async function fetchUsers(){const {data}=await api.get('/api/v1/users'); return data.data as User[];}
export async function createUser(payload:{username:string;email:string;password:string;role:string}){const {data}=await api.post('/api/v1/users',payload); return data.data as User;}
