import { jsx as _jsx, jsxs as _jsxs } from 'react/jsx-runtime';
import { useState } from 'react';
import { api } from '../api.js';
import { useToast } from '../components/Toast.js';

const neutralButton = {
  padding: '7px 11px',
  border: '1px solid var(--border)',
  borderRadius: 8,
  background: 'white',
  color: 'var(--text-secondary)',
  fontWeight: 600,
  cursor: 'pointer',
};
const RESUMABLE_STATUSES = new Set([
  'ANALYZING_PROJECT',
  'INDEXING_PROJECT',
  'WAITING_FOR_INDEXING',
  'RECOVERING_INDEXING',
  'WAITING_FOR_USER_CONFIGURATION',
  'QUEUED_FOR_GENERATION',
  'GENERATING_DOCUMENTATION',
  'VALIDATING_ASSETS',
  'ASSEMBLING_DOCUMENT',
  'VALIDATING_DOCUMENT',
  'PREPARING_PDF',
  'RECOVERING',
]);

export default function MyProjects({ onNav, state, onState }) {
  const jobs = state.jobs || [];
  const toast = useToast();
  const [confirmingJob, setConfirmingJob] = useState(null);
  const [deletingJob, setDeletingJob] = useState(null);

  const deleteJob = async (event, job) => {
    event.stopPropagation();
    setDeletingJob(job.jobId);
    try {
      await api.documentation.delete(job.jobId);
      onState({ jobs: jobs.filter(item => item.jobId !== job.jobId) });
      setConfirmingJob(null);
      toast.success('Project deleted', 'The project and its generated files were removed.');
    } catch (error) {
      toast.error('Unable to delete project', error.message || 'Please try again.');
    } finally {
      setDeletingJob(null);
    }
  };

  return _jsxs('div', {
    className: 'my-projects-page',
    style: { padding: 32 },
    children: [
      _jsxs('div', {
        style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 22 },
        children: [
          _jsxs('div', {
            children: [
              _jsx('h1', { style: { fontSize: 22, fontWeight: 800, margin: '0 0 5px' }, children: 'My Projects' }),
              _jsx('p', { style: { fontSize: 14, color: 'var(--text-secondary)', margin: 0 }, children: 'Documentation jobs returned by your account.' }),
            ],
          }),
          _jsx('button', {
            onClick: () => onNav('add-project'),
            style: { padding: '10px 18px', background: 'var(--primary)', color: 'white', border: 0, borderRadius: 9, fontWeight: 700 },
            children: '+ New Project',
          }),
        ],
      }),
      _jsx('div', {
        className: 'my-projects-table-card',
        style: { background: 'white', border: '1px solid var(--border)', borderRadius: 14, overflow: 'hidden' },
        children: jobs.length
          ? _jsxs('table', {
              className: 'my-projects-table',
              style: { width: '100%', borderCollapse: 'collapse' },
              children: [
                _jsx('thead', {
                  children: _jsx('tr', {
                    style: { background: '#F8FAFC' },
                    children: ['Project', 'Status', 'Progress', 'Updated', 'Files', ''].map(header =>
                      _jsx('th', {
                        style: { padding: 11, textAlign: 'left', fontSize: 11, color: 'var(--text-muted)' },
                        children: header,
                      }, header),
                    ),
                  }),
                }),
                _jsx('tbody', {
                  children: jobs.map(job => _jsxs('tr', {
                    className: 'my-projects-row',
                    style: { borderTop: '1px solid #F1F5F9', cursor: 'pointer' },
                    onClick: () => {
                      onState?.({ currentProjectId: job.jobId });
                      onNav('project-details');
                    },
                    children: [
                      _jsx('td', { style: { padding: 14, fontWeight: 700 }, children: job.projectName || job.jobId }),
                      _jsx('td', { style: { padding: 14 }, children: _jsx('span', { className: 'project-status-pill', 'data-status': job.status, children: job.status }) }),
                      _jsx('td', { style: { padding: 14 }, children: `${Number(job.progress || 0)}%` }),
                      _jsx('td', { style: { padding: 14, color: 'var(--text-secondary)' }, children: job.updatedAt ? new Date(job.updatedAt).toLocaleString() : '—' }),
                      _jsx('td', { style: { padding: 14 }, children: [job.hasPdf ? 'PDF' : '', job.hasDocx ? 'DOCX' : ''].filter(Boolean).join(' + ') || '—' }),
                      _jsx('td', {
                        style: { padding: 8, textAlign: 'right', whiteSpace: 'nowrap' },
                        children: _jsxs('div', {
                          style: { display: 'flex', justifyContent: 'flex-end', alignItems: 'center', gap: 6 },
                          children: [
                            job.status === 'COMPLETED' && job.hasDocx
                              ? _jsx('button', {
                                  type: 'button',
                                  onClick: event => {
                                    event.stopPropagation();
                                    onState?.({ currentProjectId: job.jobId });
                                    onNav('document-editor');
                                  },
                                  style: { ...neutralButton, color: 'var(--primary)' },
                                  children: 'Edit document',
                                })
                              : null,
                            _jsx('button', {
                              type: 'button',
                              onClick: event => {
                                event.stopPropagation();
                                onResume?.(job.jobId);
                              },
                              style: { ...neutralButton, color: 'var(--primary)' },
                              children: RESUMABLE_STATUSES.has(job.status) ? 'Resume' : 'Open',
                            }),
                            confirmingJob === job.jobId
                              ? _jsxs('div', {
                                  style: { display: 'flex', justifyContent: 'flex-end', alignItems: 'center', gap: 6 },
                                  children: [
                                    _jsx('span', { style: { marginRight: 4, fontSize: 12, color: 'var(--text-secondary)' }, children: 'Delete this project?' }),
                                    _jsx('button', {
                                      type: 'button',
                                      disabled: deletingJob === job.jobId,
                                      onClick: event => deleteJob(event, job),
                                      style: { ...neutralButton, color: 'var(--primary)', borderColor: 'var(--primary-border)' },
                                      children: deletingJob === job.jobId ? 'Deleting…' : 'Yes',
                                    }),
                                    _jsx('button', {
                                      type: 'button',
                                      disabled: deletingJob === job.jobId,
                                      onClick: event => {
                                        event.stopPropagation();
                                        setConfirmingJob(null);
                                      },
                                      style: neutralButton,
                                      children: 'No',
                                    }),
                                  ],
                                })
                              : _jsx('button', {
                                  type: 'button',
                                  onClick: event => {
                                    event.stopPropagation();
                                    setConfirmingJob(job.jobId);
                                  },
                                  style: neutralButton,
                                  children: 'Delete',
                                }),
                          ],
                        }),
                      }),
                    ],
                  }, job.jobId)),
                }),
              ],
            })
          : _jsx('div', { style: { padding: 50, textAlign: 'center', color: 'var(--text-muted)' }, children: 'No documentation jobs yet.' }),
      }),
    ],
  });
}
