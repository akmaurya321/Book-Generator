const JSON_HEADERS = { 'Content-Type': 'application/json' };

async function request(url, options = {}) {
  const response = await fetch(url, { credentials: 'same-origin', ...options });
  const contentType = response.headers.get('content-type') || '';
  const body = contentType.includes('application/json')
    ? await response.json().catch(() => ({}))
    : await response.text();
  if (!response.ok) {
    const message = body?.message || body?.error || (typeof body === 'string' && body) || `Request failed (${response.status})`;
    const error = new Error(message);
    error.status = response.status;
    error.body = body;
    throw error;
  }
  return body;
}

export const api = {
  auth: {
    config: () => request('/api/auth/config', { cache: 'no-store' }),
    me: () => request('/api/auth/me', { cache: 'no-store' }),
    login: (email, password) => request('/api/auth/login', { method: 'POST', headers: JSON_HEADERS, body: JSON.stringify({ email, password }) }),
    register: (email, name, password) => request('/api/auth/register', { method: 'POST', headers: JSON_HEADERS, body: JSON.stringify({ email, name, password }) }),
    uploadAvatar: image => {
      const form = new FormData();
      form.append('image', image);
      return request('/api/auth/avatar', { method: 'POST', body: form });
    },
    logout: () => request('/api/auth/logout', { method: 'POST' }),
    usage: () => request('/api/auth/usage', { cache: 'no-store' }),
    usageSummary: () => request('/api/auth/usage/summary', { cache: 'no-store' }),
  },
  documentation: {
    template: () => request('/api/documentation/template', { cache: 'no-store' }),
    jobs: () => request('/api/documentation/jobs', { cache: 'no-store' }),
    analyze: ({ githubUrl, projectName, projectZip }) => {
      const form = new FormData();
      if (githubUrl?.trim()) form.append('githubUrl', githubUrl.trim());
      if (projectName?.trim()) form.append('projectName', projectName.trim());
      if (projectZip) form.append('projectZip', projectZip);
      return request('/api/documentation/analyze', { method: 'POST', body: form });
    },
    analysis: jobId => request(`/api/documentation/${encodeURIComponent(jobId)}/analysis`, { cache: 'no-store' }),
    contextSuggestions: jobId => request(`/api/documentation/${encodeURIComponent(jobId)}/context-suggestions`, { method: 'POST' }),
    status: jobId => request(`/api/documentation/${encodeURIComponent(jobId)}/status`, { cache: 'no-store' }),
    preview: jobId => request(`/api/documentation/${encodeURIComponent(jobId)}/preview`, { cache: 'no-store' }),
    cancel: jobId => request(`/api/documentation/${encodeURIComponent(jobId)}/cancel`, { method: 'POST' }),
    delete: jobId => request(`/api/documentation/${encodeURIComponent(jobId)}`, { method: 'DELETE' }),
    generate: (jobId, payload) => request(`/api/documentation/${encodeURIComponent(jobId)}/generate-v1`, { method: 'POST', headers: JSON_HEADERS, body: JSON.stringify(payload) }),
    uploadFrontMatter: (jobId, field, file) => {
      const form = new FormData(); form.append('field', field); form.append('image', file);
      return request(`/api/documentation/${encodeURIComponent(jobId)}/assets/front-matter`, { method: 'POST', body: form });
    },
    uploadImage: (jobId, sectionId, file, caption) => {
      const form = new FormData(); form.append('sectionId', sectionId); form.append('image', file);
      if (caption) form.append('caption', caption);
      return request(`/api/documentation/${encodeURIComponent(jobId)}/assets/image`, { method: 'POST', body: form });
    },
    downloadPdfUrl: jobId => `/api/documentation/${encodeURIComponent(jobId)}/download/pdf`,
    downloadDocxUrl: jobId => `/api/documentation/${encodeURIComponent(jobId)}/download/docx`,
    viewPdfUrl: jobId => `/api/documentation/${encodeURIComponent(jobId)}/view/pdf`,
  },
};

export async function pollUntil(jobId, predicate, { interval = 1200, onStatus, onPollingError } = {}) {
  let retryDelay = interval;
  while (true) {
    let status;
    try {
      status = await api.documentation.status(jobId);
    } catch (error) {
      if (error.status && error.status < 500) throw error;
      onPollingError?.(error);
      await new Promise(resolve => setTimeout(resolve, retryDelay));
      retryDelay = Math.min(retryDelay * 2, 15000);
      continue;
    }
    retryDelay = interval;
    onStatus?.(status);
    if (predicate(status)) return status;
    await new Promise(resolve => setTimeout(resolve, interval));
  }
}
