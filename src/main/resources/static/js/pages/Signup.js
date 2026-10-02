import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { useState } from 'react';
function PasswordStrength({ password }) {
    const checks = [
        { label: 'At least 12 characters', ok: password.length >= 12 },
        { label: 'Uppercase letter', ok: /[A-Z]/.test(password) },
        { label: 'Number', ok: /[0-9]/.test(password) },
    ];
    const strength = checks.filter(c => c.ok).length;
    const colors = ['#E2E8F0', '#DC2626', '#D97706', '#16A34A'];
    const labels = ['', 'Weak', 'Fair', 'Strong'];
    if (!password)
        return null;
    return (_jsxs("div", { style: { marginTop: 8 }, children: [_jsxs("div", { style: { display: 'flex', gap: 4, marginBottom: 6 }, children: [[0, 1, 2].map(i => (_jsx("div", { style: { flex: 1, height: 3, borderRadius: 2, background: i < strength ? colors[strength] : '#E2E8F0', transition: 'background .2s' } }, i))), _jsx("span", { style: { fontSize: 11.5, fontWeight: 600, color: colors[strength], marginLeft: 6, alignSelf: 'center', whiteSpace: 'nowrap' }, children: labels[strength] })] }), _jsx("div", { style: { display: 'flex', gap: 12 }, children: checks.map(c => (_jsxs("div", { style: { display: 'flex', alignItems: 'center', gap: 4, fontSize: 11.5, color: c.ok ? 'var(--success)' : 'var(--text-muted)' }, children: [_jsx("svg", { width: "10", height: "10", viewBox: "0 0 24 24", fill: "none", stroke: "currentColor", strokeWidth: "3", strokeLinecap: "round", strokeLinejoin: "round", children: c.ok ? _jsx("path", { d: "M20 6L9 17l-5-5" }) : _jsx("path", { d: "M18 6L6 18M6 6l12 12" }) }), c.label] }, c.label))) })] }));
}
export default function Signup({ onNav, onSignup, onGoogle, googleEnabled }) {
    const [name, setName] = useState('');
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [confirm, setConfirm] = useState('');
    const [showPass, setShowPass] = useState(false);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const handleCreate = async (e) => {
        e.preventDefault();
        if (!name || !email || !password) { setError('Please fill in all fields.'); setLoading(false); return; }
        setLoading(true); setError('');
        try { await onSignup(email.trim(), name.trim(), password); }
        catch (error) { setError(error.message || 'Unable to create account.'); }
        finally { setLoading(false); }
    };
    const handleGoogle = () => onGoogle?.();
    return (_jsxs("div", { className: "auth-page auth-signup-page", style: { minHeight: '100vh', display: 'grid', gridTemplateColumns: '1fr 1fr' }, children: [_jsxs("div", { style: { background: 'var(--primary)', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', padding: 60, position: 'relative', overflow: 'hidden' }, children: [[...Array(3)].map((_, i) => (_jsx("div", { style: { position: 'absolute', borderRadius: '50%', border: '1px solid rgba(255,255,255,.1)', width: [300, 450, 600][i], height: [300, 450, 600][i], top: '50%', left: '50%', transform: 'translate(-50%, -50%)' } }, i))), _jsxs("div", { style: { position: 'relative', zIndex: 1, textAlign: 'center', maxWidth: 340 }, children: [_jsx("div", { style: { width: 52, height: 52, borderRadius: 14, background: 'rgba(255,255,255,.15)', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 20px' }, children: _jsx("svg", { width: "26", height: "26", viewBox: "0 0 24 24", fill: "none", stroke: "white", strokeWidth: "2.5", strokeLinecap: "round", strokeLinejoin: "round", children: _jsx("path", { d: "M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5" }) }) }), _jsx("div", { style: { fontSize: 26, fontWeight: 900, color: 'white', marginBottom: 12 }, children: "DocGen AI" }), _jsx("div", { style: { fontSize: 15, color: 'rgba(255,255,255,.75)', lineHeight: 1.7 }, children: "Join students turning their project work into polished academic reports with DocGen AI." })] })] }), _jsx("div", { style: { display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '48px', background: '#FAFBFF' }, children: _jsxs("div", { style: { width: '100%', maxWidth: 400 }, className: "animate-fade-in", children: [_jsx("h1", { style: { fontSize: 24, fontWeight: 800, color: 'var(--text-primary)', margin: '0 0 4px' }, children: "Create your account" }), _jsx("p", { style: { fontSize: 14, color: 'var(--text-secondary)', margin: '0 0 26px' }, children: "Start generating professional documentation for free." }), error && (_jsx("div", { style: { background: 'var(--danger-bg)', border: '1px solid var(--danger-border)', borderRadius: 9, padding: '10px 14px', marginBottom: 16, fontSize: 13.5, color: 'var(--danger-text)' }, children: error })), _jsxs("form", { onSubmit: handleCreate, children: [[
                                    { label: 'Full Name', value: name, setter: setName, type: 'text', placeholder: 'Your full name' },
                                    { label: 'College Email', value: email, setter: setEmail, type: 'email', placeholder: 'you@college.edu' },
                                ].map(({ label, value, setter, type, placeholder }) => (_jsxs("div", { style: { marginBottom: 14 }, children: [_jsx("label", { style: { display: 'block', fontSize: 13, fontWeight: 600, color: 'var(--text-primary)', marginBottom: 6 }, children: label }), _jsx("input", { type: type, value: value, onChange: e => setter(e.target.value), placeholder: placeholder, className: "input-base", style: { display: 'block' } })] }, label))), _jsxs("div", { style: { marginBottom: 14 }, children: [_jsx("label", { style: { display: 'block', fontSize: 13, fontWeight: 600, color: 'var(--text-primary)', marginBottom: 6 }, children: "Password" }), _jsxs("div", { style: { position: 'relative' }, children: [_jsx("input", { type: showPass ? 'text' : 'password', value: password, onChange: e => setPassword(e.target.value), placeholder: "Create a strong password", className: "input-base", style: { display: 'block', paddingRight: 42 } }), _jsx("button", { type: "button", onClick: () => setShowPass(!showPass), style: { position: 'absolute', right: 11, top: '50%', transform: 'translateY(-50%)', background: 'none', border: 'none', cursor: 'pointer', color: 'var(--text-muted)' }, children: _jsxs("svg", { width: "16", height: "16", viewBox: "0 0 24 24", fill: "none", stroke: "currentColor", strokeWidth: "2", strokeLinecap: "round", strokeLinejoin: "round", children: [_jsx("path", { d: "M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" }), _jsx("circle", { cx: "12", cy: "12", r: "3" })] }) })] }), _jsx(PasswordStrength, { password: password })] }), _jsxs("div", { style: { marginBottom: 22 }, children: [_jsx("label", { style: { display: 'block', fontSize: 13, fontWeight: 600, color: 'var(--text-primary)', marginBottom: 6 }, children: "Confirm Password" }), _jsx("input", { type: "password", value: confirm, onChange: e => setConfirm(e.target.value), placeholder: "Confirm your password", className: `input-base${confirm && password !== confirm ? ' error' : ''}`, style: { display: 'block' } }), confirm && password !== confirm && _jsx("div", { style: { fontSize: 12, color: 'var(--danger)', marginTop: 5 }, children: "Passwords do not match" })] }), _jsx("button", { type: "submit", disabled: loading || !name || !email || !password || password !== confirm, style: {
                                        width: '100%', padding: '12px 0', background: 'var(--primary)', color: 'white',
                                        border: 'none', borderRadius: 9, fontWeight: 700, fontSize: 15, cursor: 'pointer',
                                        transition: 'all .15s', opacity: loading ? 0.8 : 1
                                    }, onMouseEnter: e => { if (!loading)
                                        e.currentTarget.style.background = 'var(--primary-hover)'; }, onMouseLeave: e => { e.currentTarget.style.background = 'var(--primary)'; }, children: loading ? 'Creating account...' : 'Create Account' })] }), _jsxs("div", { style: { textAlign: 'center', marginTop: 18, fontSize: 13.5, color: 'var(--text-secondary)' }, children: ["Already have an account?", ' ', _jsx("button", { onClick: () => onNav('login'), style: { color: 'var(--primary)', fontWeight: 700, background: 'none', border: 'none', cursor: 'pointer' }, children: "Sign in" })] })] }) })] }));
}
