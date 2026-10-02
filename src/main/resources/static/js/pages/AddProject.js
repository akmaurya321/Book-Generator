import { jsx as _jsx, jsxs as _jsxs, Fragment as _Fragment } from "react/jsx-runtime";
import { useRef, useState } from 'react';
import { useToast } from '../components/Toast.js';
function ProgressRing({ pct }) {
    const r = 28, c = 2 * Math.PI * r;
    const offset = c - (pct / 100) * c;
    return (_jsxs("svg", { width: "72", height: "72", viewBox: "0 0 72 72", children: [_jsx("circle", { cx: "36", cy: "36", r: r, fill: "none", stroke: "#E2E8F0", strokeWidth: "5" }), _jsx("circle", { cx: "36", cy: "36", r: r, fill: "none", stroke: "var(--primary)", strokeWidth: "5", strokeLinecap: "round", strokeDasharray: c, strokeDashoffset: offset, transform: "rotate(-90 36 36)", style: { transition: 'stroke-dashoffset .15s linear' } })] }));
}
export default function AddProject({ onNav, state, onState, onAnalyze }) {
    const [tab, setTab] = useState('zip');
    const [githubUrl, setGithubUrl] = useState(state.githubUrl);
    const [projectName, setProjectName] = useState(state.projectName);
    const [dragOver, setDragOver] = useState(false);
    const [file, setFile] = useState(null);
    const [urlError, setUrlError] = useState('');
    const [uploadState, setUploadState] = useState('idle');
    const [uploadPct, setUploadPct] = useState(0);
    const toast = useToast();
    const dragCounter = useRef(0);
    const selectFile = () => {
        const inp = document.createElement('input');
        inp.type = 'file';
        inp.accept = '.zip';
        inp.onchange = (e) => { if (e.target.files?.[0])
            handleFileSelected(e.target.files[0]); };
        inp.click();
    };
    const handleFileSelected = (f) => {
        if (f.size > 20 * 1024 * 1024) {
            toast.error('File too large', 'The backend accepts ZIP files up to 20 MB.');
            return;
        }
        setFile(f);
        setUploadState('uploaded'); setUploadPct(100);
    };
    const handleDrop = (e) => {
        e.preventDefault();
        dragCounter.current = 0;
        setDragOver(false);
        setUploadState('idle');
        const f = e.dataTransfer.files[0];
        if (f?.name.endsWith('.zip')) {
            handleFileSelected(f);
        }
        else {
            toast.error('Invalid file', 'Please upload a .zip file.');
        }
    };
    const handleDragEnter = (e) => {
        e.preventDefault();
        dragCounter.current++;
        setDragOver(true);
        setUploadState('dragging');
    };
    const handleDragLeave = (e) => {
        e.preventDefault();
        dragCounter.current--;
        if (dragCounter.current === 0) {
            setDragOver(false);
            setUploadState('idle');
        }
    };
    const handleAnalyze = () => {
        if (tab === 'github' && !githubUrl.trim()) { setUrlError('Please enter a GitHub URL.'); return; }
        if (tab === 'zip' && !file) { toast.error('Project ZIP required', 'Choose a project ZIP before analysis.'); return; }
        onState({ githubUrl, projectName, projectFile: file });
        onAnalyze({ githubUrl: tab === 'github' ? githubUrl : '', projectName, projectZip: tab === 'zip' ? file : null });
    };
    const formatSize = (bytes) => bytes > 1024 * 1024 ? `${(bytes / 1024 / 1024).toFixed(1)} MB` : `${(bytes / 1024).toFixed(0)} KB`;
    const canSubmit = tab === 'zip' ? uploadState === 'uploaded' : true;
    return (_jsxs("div", { style: { padding: 32, maxWidth: 660 }, children: [_jsxs("div", { style: { marginBottom: 26 }, className: "animate-fade-in", children: [_jsx("h1", { style: { fontSize: 22, fontWeight: 800, color: 'var(--text-primary)', margin: '0 0 6px', letterSpacing: -0.3 }, children: "Provide Your Project" }), _jsx("p", { style: { fontSize: 14.5, color: 'var(--text-secondary)', margin: 0 }, children: "Upload a ZIP file or link a GitHub repository for AI analysis." })] }), _jsx("div", { style: { display: 'flex', background: '#F1F5F9', borderRadius: 10, padding: 4, marginBottom: 24 }, children: [
                    { id: 'zip', icon: 'M13 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V9z M13 2v7h7', label: 'Upload ZIP' },
                    { id: 'github', icon: 'M9 19c-5 1.5-5-2.5-7-3m14 6v-3.87a3.37 3.37 0 00-.94-2.61c3.14-.35 6.44-1.54 6.44-7A5.44 5.44 0 0020 4.77 5.07 5.07 0 0019.91 1S18.73.65 16 2.48a13.38 13.38 0 00-7 0C6.27.65 5.09 1 5.09 1A5.07 5.07 0 005 4.77a5.44 5.44 0 00-1.5 3.78c0 5.42 3.3 6.61 6.44 7A3.37 3.37 0 009 18.13V22', label: 'GitHub Repository' },
                ].map(t => (_jsxs("button", { onClick: () => { setTab(t.id); setFile(null); setUploadState('idle'); setUploadPct(0); }, style: {
                        flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 7,
                        padding: '9px 0', borderRadius: 7, border: 'none', cursor: 'pointer',
                        fontWeight: 600, fontSize: 14, transition: 'all .15s',
                        background: tab === t.id ? 'white' : 'transparent',
                        color: tab === t.id ? 'var(--primary)' : 'var(--text-secondary)',
                        boxShadow: tab === t.id ? '0 1px 4px rgba(0,0,0,.08)' : 'none'
                    }, children: [_jsx("svg", { width: "15", height: "15", viewBox: "0 0 24 24", fill: "none", stroke: "currentColor", strokeWidth: "2", strokeLinecap: "round", strokeLinejoin: "round", children: _jsx("path", { d: t.icon }) }), t.label] }, t.id))) }), _jsxs("div", { style: { background: 'white', borderRadius: 14, border: '1px solid var(--border)', padding: 28 }, children: [tab === 'zip' ? (_jsxs(_Fragment, { children: [uploadState === 'uploading' && (_jsxs("div", { style: { textAlign: 'center', padding: '28px 0' }, className: "animate-fade-in", children: [_jsxs("div", { style: { position: 'relative', display: 'inline-block', marginBottom: 16 }, children: [_jsx(ProgressRing, { pct: uploadPct }), _jsxs("div", { style: { position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 11, fontWeight: 700, color: 'var(--primary)' }, children: [uploadPct, "%"] })] }), _jsxs("div", { style: { fontWeight: 700, fontSize: 15, color: 'var(--text-primary)', marginBottom: 4 }, children: ["Uploading ", file?.name] }), _jsxs("div", { style: { fontSize: 13, color: 'var(--text-muted)' }, children: [formatSize(file?.size || 0), " \u00B7 Please wait..."] }), _jsx("div", { style: { height: 4, background: '#F1F5F9', borderRadius: 10, overflow: 'hidden', maxWidth: 260, margin: '16px auto 0' }, children: _jsx("div", { style: { height: '100%', width: `${uploadPct}%`, background: 'var(--primary)', borderRadius: 10, transition: 'width .15s linear' } }) })] })), uploadState === 'uploaded' && file && (_jsxs("div", { style: { textAlign: 'center', padding: '24px 0' }, className: "animate-pop-in", children: [_jsx("div", { style: { width: 60, height: 60, borderRadius: '50%', background: 'var(--success-bg)', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 14px', border: '2px solid var(--success-border)' }, children: _jsx("svg", { width: "26", height: "26", viewBox: "0 0 24 24", fill: "none", stroke: "var(--success)", strokeWidth: "2.5", strokeLinecap: "round", strokeLinejoin: "round", children: _jsx("path", { d: "M20 6L9 17l-5-5", className: "animate-draw-check" }) }) }), _jsx("div", { style: { fontWeight: 700, fontSize: 16, color: 'var(--text-primary)', marginBottom: 4 }, children: file.name }), _jsxs("div", { style: { fontSize: 13.5, color: 'var(--text-muted)', marginBottom: 20 }, children: [formatSize(file.size), " \u00B7 Ready to analyze"] }), _jsx("button", { onClick: () => { setFile(null); setUploadState('idle'); setUploadPct(0); }, style: {
                                            padding: '7px 16px', background: 'none', border: '1.5px solid var(--border)',
                                            borderRadius: 8, fontSize: 13, fontWeight: 600, color: 'var(--text-secondary)', cursor: 'pointer', transition: 'all .15s'
                                        }, onMouseEnter: e => (e.currentTarget.style.background = '#F8FAFC'), onMouseLeave: e => (e.currentTarget.style.background = 'none'), children: "Change File" })] })), (uploadState === 'idle' || uploadState === 'dragging' || uploadState === 'selected') && uploadState !== 'selected' && (_jsxs("div", { onDragEnter: handleDragEnter, onDragLeave: handleDragLeave, onDragOver: e => e.preventDefault(), onDrop: handleDrop, onClick: selectFile, className: `upload-zone${dragOver ? ' drag-over' : ''}`, style: {
                                    border: `2.5px dashed ${dragOver ? 'var(--primary)' : '#CBD5E1'}`,
                                    borderRadius: 12, padding: '48px 24px', textAlign: 'center', cursor: 'pointer',
                                    background: dragOver ? 'var(--primary-light)' : '#FAFAFA',
                                }, children: [_jsx("div", { className: "upload-icon", style: {
                                            width: 56, height: 56, borderRadius: 14,
                                            background: dragOver ? 'var(--primary)' : '#F1F5F9',
                                            display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 16px',
                                            transition: 'all .2s'
                                        }, children: _jsx("svg", { width: "24", height: "24", viewBox: "0 0 24 24", fill: "none", stroke: dragOver ? 'white' : 'var(--text-muted)', strokeWidth: "2", strokeLinecap: "round", strokeLinejoin: "round", children: _jsx("path", { d: "M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4M17 8l-5-5-5 5M12 3v12" }) }) }), dragOver ? (_jsxs(_Fragment, { children: [_jsx("div", { style: { fontSize: 17, fontWeight: 700, color: 'var(--primary)', marginBottom: 6 }, children: "Drop it here!" }), _jsx("div", { style: { fontSize: 13.5, color: 'var(--primary)', opacity: 0.7 }, children: "Release to upload your project ZIP" })] })) : (_jsxs(_Fragment, { children: [_jsx("div", { style: { fontSize: 16, fontWeight: 700, color: 'var(--text-primary)', marginBottom: 6 }, children: "Drop your project ZIP here" }), _jsx("div", { style: { fontSize: 13.5, color: 'var(--text-secondary)', marginBottom: 14 }, children: "or click to browse files" }), _jsx("div", { style: { display: 'inline-flex', padding: '8px 18px', background: 'white', border: '1.5px solid var(--border)', borderRadius: 8, fontSize: 13.5, fontWeight: 600, color: 'var(--text-primary)' }, children: "Choose File" }), _jsx("div", { style: { fontSize: 12, color: 'var(--text-muted)', marginTop: 14 }, children: "Supports ZIP files up to 20 MB" })] }))] }))] })) : (_jsxs("div", { children: [_jsxs("div", { style: { marginBottom: 18 }, children: [_jsxs("label", { style: { display: 'flex', alignItems: 'center', gap: 6, fontSize: 13.5, fontWeight: 700, color: 'var(--text-primary)', marginBottom: 8 }, children: [_jsx("svg", { width: "15", height: "15", viewBox: "0 0 24 24", fill: "none", stroke: "currentColor", strokeWidth: "2", strokeLinecap: "round", strokeLinejoin: "round", children: _jsx("path", { d: "M9 19c-5 1.5-5-2.5-7-3m14 6v-3.87a3.37 3.37 0 00-.94-2.61c3.14-.35 6.44-1.54 6.44-7A5.44 5.44 0 0020 4.77 5.07 5.07 0 0019.91 1S18.73.65 16 2.48a13.38 13.38 0 00-7 0C6.27.65 5.09 1 5.09 1A5.07 5.07 0 005 4.77a5.44 5.44 0 00-1.5 3.78c0 5.42 3.3 6.61 6.44 7A3.37 3.37 0 009 18.13V22" }) }), "GitHub Repository URL"] }), _jsx("input", { type: "url", value: githubUrl, onChange: e => { setGithubUrl(e.target.value); setUrlError(''); }, placeholder: "https://github.com/username/repository", className: `input-base${urlError ? ' error' : ''}`, style: { display: 'block' } }), urlError && _jsx("div", { style: { fontSize: 12.5, color: 'var(--danger)', marginTop: 5 }, className: "animate-slide-down", children: urlError })] }), _jsxs("div", { children: [_jsxs("label", { style: { display: 'block', fontSize: 13.5, fontWeight: 700, color: 'var(--text-primary)', marginBottom: 8 }, children: ["Project Name ", _jsx("span", { style: { color: 'var(--text-muted)', fontWeight: 400 }, children: "(Optional)" })] }), _jsx("input", { type: "text", value: projectName, onChange: e => setProjectName(e.target.value), placeholder: "e.g. Student Management System", className: "input-base", style: { display: 'block' } }), _jsx("div", { style: { fontSize: 12, color: 'var(--text-muted)', marginTop: 5 }, children: "Leave blank to auto-detect from repository." })] })] })), _jsxs("div", { style: { marginTop: 24 }, children: [_jsxs("button", { onClick: handleAnalyze, disabled: !canSubmit, style: {
                                    width: '100%', padding: '13px 0', background: 'var(--primary)', color: 'white',
                                    border: 'none', borderRadius: 10, fontWeight: 700, fontSize: 15.5, cursor: 'pointer',
                                    display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 9,
                                    opacity: !canSubmit ? 0.5 : 1, transition: 'all .15s'
                                }, onMouseEnter: e => { if (canSubmit)
                                    e.currentTarget.style.background = 'var(--primary-hover)'; }, onMouseLeave: e => { e.currentTarget.style.background = 'var(--primary)'; }, children: [_jsx("svg", { width: "17", height: "17", viewBox: "0 0 24 24", fill: "none", stroke: "currentColor", strokeWidth: "2.5", strokeLinecap: "round", strokeLinejoin: "round", children: _jsx("path", { d: "M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z" }) }), tab === 'zip' && uploadState !== 'uploaded' ? 'Upload file first →' : 'Analyze Project →'] }), _jsx("div", { style: { fontSize: 12.5, color: 'var(--text-muted)', textAlign: 'center', marginTop: 10 }, children: "Analysis runs in the background \u2014 you can safely close this page" })] })] })] }));
}
