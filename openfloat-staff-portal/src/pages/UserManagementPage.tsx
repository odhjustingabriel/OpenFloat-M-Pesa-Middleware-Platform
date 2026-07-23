import React from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { zodResolver } from '@hookform/resolvers/zod';
import { fetchUsers, createUser, deleteUser, updateUserRole } from '../api/queries';
import type { User } from '../types/domain';

const createUserSchema = z.object({
  username: z.string().min(3, 'Username must be at least 3 characters'),
  email: z.string().email('Invalid email address'),
  password: z.string().min(8, 'Password must be at least 8 characters'),
  role: z.enum(['ADMIN', 'STAFF']),
});

type CreateUserFormData = z.infer<typeof createUserSchema>;

export default function UserManagementPage() {
  const queryClient = useQueryClient();
  const [searchTerm, setSearchTerm] = React.useState('');
  const [roleFilter, setRoleFilter] = React.useState<string>('ALL');
  const [showCreateModal, setShowCreateModal] = React.useState(false);

  const { data: users = [], isLoading } = useQuery({
    queryKey: ['users'],
    queryFn: fetchUsers,
  });

  const createMutation = useMutation({
    mutationFn: createUser,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] });
      setShowCreateModal(false);
      reset();
    },
  });

  const deleteMutation = useMutation({
    mutationFn: deleteUser,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] });
    },
  });

  const roleMutation = useMutation({
    mutationFn: ({ userId, role }: { userId: string; role: string }) =>
      updateUserRole(userId, role),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] });
    },
  });

  const {
    register,
    handleSubmit,
    formState: { errors },
    reset,
  } = useForm<CreateUserFormData>({
    resolver: zodResolver(createUserSchema),
    defaultValues: { role: 'STAFF' },
  });

  const filteredUsers = React.useMemo(() => {
    return users.filter((u: User) => {
      if (roleFilter !== 'ALL' && u.role !== roleFilter) return false;
      if (searchTerm) {
        const term = searchTerm.toLowerCase();
        const searchable = `${u.username} ${u.email} ${u.role}`.toLowerCase();
        if (!searchable.includes(term)) return false;
      }
      return true;
    });
  }, [users, roleFilter, searchTerm]);

  return (
    <>
      {/* ─── Toolbar ─── */}
      <div className="card" style={{ marginBottom: '1rem' }}>
        <div className="toolbar">
          <h3>
            User Management
            <span style={{ fontSize: '0.8rem', fontWeight: 400, color: 'var(--color-text-muted)', marginLeft: '0.75rem' }}>
              {filteredUsers.length} users
            </span>
          </h3>
          <div className="toolbar-actions">
            <button className="btn btn-primary btn-sm" onClick={() => setShowCreateModal(true)}>
              + Add User
            </button>
          </div>
        </div>

        <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap', marginTop: '0.75rem' }}>
          <input
            className="form-input"
            placeholder="Search username or email…"
            style={{ maxWidth: 280 }}
            value={searchTerm}
            onChange={(e: React.ChangeEvent<HTMLInputElement>) => setSearchTerm(e.target.value)}
          />
          <select
            className="form-input"
            style={{ maxWidth: 160 }}
            value={roleFilter}
            onChange={(e: React.ChangeEvent<HTMLSelectElement>) => setRoleFilter(e.target.value)}
          >
            <option value="ALL">All Roles</option>
            <option value="ADMIN">ADMIN</option>
            <option value="STAFF">STAFF</option>
          </select>
        </div>
      </div>

      {/* ─── Create User Modal / Inline Form ─── */}
      {showCreateModal && (
        <div className="card" style={{ marginBottom: '1rem', border: '1px solid var(--color-brand-400)' }}>
          <div className="toolbar">
            <h4 style={{ margin: 0 }}>Create New User</h4>
            <button className="btn btn-ghost btn-sm" onClick={() => setShowCreateModal(false)}>
              ✕ Close
            </button>
          </div>

          <form
            onSubmit={handleSubmit((data) => createMutation.mutate(data))}
            style={{ marginTop: '1rem' }}
          >
            <div className="form-row">
              <div className="form-group">
                <label htmlFor="username">Username</label>
                <input id="username" className="form-input" placeholder="johndoe" {...register('username')} />
                {errors.username && <span className="form-error">{errors.username.message}</span>}
              </div>

              <div className="form-group">
                <label htmlFor="email">Email</label>
                <input id="email" className="form-input" type="email" placeholder="john@company.com" {...register('email')} />
                {errors.email && <span className="form-error">{errors.email.message}</span>}
              </div>
            </div>

            <div className="form-row">
              <div className="form-group">
                <label htmlFor="password">Password</label>
                <input id="password" className="form-input" type="password" placeholder="••••••••" {...register('password')} />
                {errors.password && <span className="form-error">{errors.password.message}</span>}
              </div>

              <div className="form-group">
                <label htmlFor="role">Role</label>
                <select id="role" className="form-input" {...register('role')}>
                  <option value="STAFF">STAFF</option>
                  <option value="ADMIN">ADMIN</option>
                </select>
                {errors.role && <span className="form-error">{errors.role.message}</span>}
              </div>
            </div>

            <div style={{ display: 'flex', gap: '0.5rem', marginTop: '0.5rem' }}>
              <button type="submit" className="btn btn-primary btn-sm" disabled={createMutation.isPending}>
                {createMutation.isPending ? <span className="spinner" /> : 'Save User'}
              </button>
              <button
                type="button"
                className="btn btn-secondary btn-sm"
                onClick={() => setShowCreateModal(false)}
              >
                Cancel
              </button>
            </div>

            {createMutation.isError && (
              <div className="result-block error" style={{ marginTop: '0.75rem' }}>
                ❌ {(createMutation.error as Error)?.message ?? 'Failed to create user'}
              </div>
            )}
          </form>
        </div>
      )}

      {/* ─── User Table ─── */}
      <div className="card">
        {isLoading ? (
          <div className="loading-state">
            <span className="spinner" /> Loading user directory…
          </div>
        ) : filteredUsers.length === 0 ? (
          <div className="loading-state">No users found.</div>
        ) : (
          <table>
            <thead>
              <tr>
                <th>Username</th>
                <th>Email</th>
                <th>Role</th>
                <th>Status</th>
                <th>Last Login</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {filteredUsers.map((u: User) => (
                <tr key={u.id}>
                  <td style={{ fontWeight: 600 }}>{u.username}</td>
                  <td>{u.email}</td>
                  <td>
                    <span className={`pill ${u.role === 'ADMIN' ? 'pill-success' : 'pill-pending'}`}>
                      {u.role}
                    </span>
                  </td>
                  <td>
                    <span className="pill pill-success">
                      {u.status || 'ACTIVE'}
                    </span>
                  </td>
                  <td>{formatDateTime(u.lastLogin)}</td>
                  <td>
                    <div style={{ display: 'flex', gap: '0.5rem' }}>
                      <button
                        className="btn btn-ghost btn-sm"
                        style={{ fontSize: '0.75rem', padding: '0.2rem 0.5rem' }}
                        onClick={() =>
                          roleMutation.mutate({
                            userId: u.id,
                            role: u.role === 'ADMIN' ? 'STAFF' : 'ADMIN',
                          })
                        }
                      >
                        Set {u.role === 'ADMIN' ? 'STAFF' : 'ADMIN'}
                      </button>
                      <button
                        className="btn btn-ghost btn-sm"
                        style={{ fontSize: '0.75rem', padding: '0.2rem 0.5rem', color: 'var(--color-error)' }}
                        onClick={() => {
                          if (confirm(`Are you sure you want to delete user ${u.username}?`)) {
                            deleteMutation.mutate(u.id);
                          }
                        }}
                      >
                        Delete
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </>
  );
}

function formatDateTime(iso?: string): string {
  if (!iso) return 'Never';
  try {
    return new Date(iso).toLocaleString([], {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  } catch {
    return iso;
  }
}
