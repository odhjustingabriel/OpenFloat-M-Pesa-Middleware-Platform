import React from 'react';
import { auth } from '../api/client';

export default function LoginPage() {
  return (
    <div className="login-page">
      <div className="card login-card">
        <div className="brand-icon">🔐</div>
        <h1>OpenFloat Staff Portal</h1>
        <p>
          Enterprise M-Pesa middleware operations console.
          <br />
          Sign in with your organizational account via OAuth2 PKCE.
        </p>

        <div className="login-buttons">
          <button className="btn btn-primary" onClick={auth.login}>
            🔑 Continue with OpenFloat Auth
          </button>

          <div className="login-divider">or</div>

          <button
            className="btn btn-secondary"
            onClick={() => {
              localStorage.setItem(auth.tokenKey, 'dev-token');
              localStorage.setItem(auth.roleKey, 'ADMIN');
              location.href = '/';
            }}
          >
            🧪 Use local demo session
          </button>
        </div>
      </div>
    </div>
  );
}
