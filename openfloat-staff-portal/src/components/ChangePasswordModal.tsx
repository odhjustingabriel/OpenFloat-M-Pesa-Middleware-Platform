import React, { useState } from 'react';
import { api, auth } from '../api/client';

export default function ChangePasswordModal() {
  const [oldPassword, setOldPassword] = useState('123456789'); // default
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (newPassword !== confirmPassword) {
      setError('New passwords do not match');
      return;
    }
    if (newPassword.length < 8) {
      setError('Password must be at least 8 characters long');
      return;
    }

    try {
      setLoading(true);
      setError(null);
      await api.post('/api/v1/users/me/password', {
        oldPassword,
        newPassword
      });
      // Force re-login to issue a new token without the requires_password_change claim
      auth.clear();
      auth.login();
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to update password');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{
      position: 'fixed',
      top: 0, left: 0, right: 0, bottom: 0,
      background: 'rgba(13, 31, 22, 0.85)',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      zIndex: 9999,
      backdropFilter: 'blur(8px)'
    }}>
      <div className="card" style={{ maxWidth: '480px', width: '100%', padding: '2.5rem' }}>
        <h2 style={{ marginTop: 0, color: 'var(--color-brand-500)', fontSize: '1.6rem' }}>Action Required</h2>
        <p style={{ color: 'var(--color-text-secondary)', marginBottom: '1.5rem', lineHeight: '1.5' }}>
          For security reasons, you must change your default password before accessing the system.
        </p>

        {error && (
          <div className="result-block failed" style={{ marginBottom: '1.5rem', fontSize: '0.9rem' }}>
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit}>
          <div className="form-group" style={{ marginBottom: '1rem' }}>
            <label>Current Password</label>
            <input 
              type="password" 
              className="form-input" 
              value={oldPassword} 
              onChange={e => setOldPassword(e.target.value)} 
              required 
            />
          </div>
          <div className="form-group" style={{ marginBottom: '1rem' }}>
            <label>New Password</label>
            <input 
              type="password" 
              className="form-input" 
              value={newPassword} 
              onChange={e => setNewPassword(e.target.value)} 
              required 
            />
          </div>
          <div className="form-group" style={{ marginBottom: '1.5rem' }}>
            <label>Confirm New Password</label>
            <input 
              type="password" 
              className="form-input" 
              value={confirmPassword} 
              onChange={e => setConfirmPassword(e.target.value)} 
              required 
            />
          </div>
          <button 
            type="submit" 
            className="btn btn-primary" 
            style={{ width: '100%', justifyContent: 'center' }}
            disabled={loading}
          >
            {loading ? <span className="spinner"></span> : 'Update Password & Continue'}
          </button>
        </form>
      </div>
    </div>
  );
}
