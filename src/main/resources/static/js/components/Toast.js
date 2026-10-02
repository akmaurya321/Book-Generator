import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
const ToastContext = createContext({
    show: () => { }, success: () => { }, error: () => { }, warning: () => { }, info: () => { }
});
const ICONS = {
    success: 'M20 6L9 17l-5-5',
    error: 'M18 6L6 18M6 6l12 12',
    warning: 'M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z M12 9v4M12 17h.01',
    info: 'M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z'
};
let uid = 0;
export function ToastProvider({ children }) {
    const [toasts, setToasts] = useState([]);
    const timers = useRef(new Map());
    const dismiss = useCallback((id) => {
        setToasts(prev => prev.map(t => t.id === id ? { ...t, exiting: true } : t));
        setTimeout(() => setToasts(prev => prev.filter(t => t.id !== id)), 220);
    }, []);
    const show = useCallback((type, title, message) => {
        const id = `toast-${++uid}`;
        setToasts(prev => [...prev, { id, type, title, message }]);
        const t = setTimeout(() => dismiss(id), 4200);
        timers.current.set(id, t);
    }, [dismiss]);
    const success = useCallback((title, msg) => show('success', title, msg), [show]);
    const error = useCallback((title, msg) => show('error', title, msg), [show]);
    const warning = useCallback((title, msg) => show('warning', title, msg), [show]);
    const info = useCallback((title, msg) => show('info', title, msg), [show]);
    useEffect(() => () => timers.current.forEach(clearTimeout), []);
    return (_jsxs(ToastContext.Provider, { value: { show, success, error, warning, info }, children: [children, _jsx("div", { className: "toast-container", children: toasts.map(toast => (_jsxs("div", { className: `toast toast-${toast.type}${toast.exiting ? ' exiting' : ''}`, children: [_jsx("div", { className: "toast-icon", children: _jsx("svg", { width: "12", height: "12", viewBox: "0 0 24 24", fill: "none", stroke: "currentColor", strokeWidth: "2.5", strokeLinecap: "round", strokeLinejoin: "round", children: _jsx("path", { d: ICONS[toast.type] }) }) }), _jsxs("div", { style: { flex: 1, minWidth: 0 }, children: [_jsx("div", { style: { fontWeight: 700, fontSize: 13.5, color: 'var(--text-primary)', marginBottom: toast.message ? 2 : 0 }, children: toast.title }), toast.message && _jsx("div", { style: { fontSize: 12.5, color: 'var(--text-secondary)', lineHeight: 1.5 }, children: toast.message })] }), _jsx("button", { onClick: () => dismiss(toast.id), style: { background: 'none', border: 'none', cursor: 'pointer', color: 'var(--text-muted)', padding: 2, flexShrink: 0, marginTop: -1 }, children: _jsx("svg", { width: "14", height: "14", viewBox: "0 0 24 24", fill: "none", stroke: "currentColor", strokeWidth: "2", strokeLinecap: "round", strokeLinejoin: "round", children: _jsx("path", { d: "M18 6L6 18M6 6l12 12" }) }) })] }, toast.id))) })] }));
}
export function useToast() {
    return useContext(ToastContext);
}
