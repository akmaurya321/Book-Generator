import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
const STEPS = [
    { num: '01', label: 'Source', pages: ['add-project'] },
    { num: '02', label: 'Analyze', pages: ['analyzing', 'analysis-result'] },
    { num: '03', label: 'Configure', pages: ['template-recommendation', 'template-preview', 'student-details', 'configure-sections', 'additional-info'] },
    { num: '04', label: 'Generate', pages: ['final-review', 'generating'] },
    { num: '05', label: 'Download', pages: ['completed'] },
];
export default function WorkflowLayout({ page, children, onNav }) {
    const currentStepIdx = STEPS.findIndex(s => s.pages.includes(page));
    return (_jsxs("div", { className: "workflow-layout", style: { display: 'flex', minHeight: 'calc(100vh - var(--header-h))' }, children: [_jsxs("div", { className: "workflow-sidebar", style: {
                    width: 'var(--workflow-sidebar-w)', background: 'white', borderRight: '1px solid var(--border)',
                    padding: '28px 16px', display: 'flex', flexDirection: 'column', gap: 0, flexShrink: 0
                }, children: [_jsx("button", { type: "button", onClick: () => onNav("dashboard"), style: { marginBottom: 24, padding: "9px 10px", border: "1px solid var(--border)", borderRadius: 8, background: "white", color: "var(--text-secondary)", fontSize: 12.5, fontWeight: 600, cursor: "pointer", textAlign: "left" }, children: "← Dashboard" }), _jsx("div", { style: { fontSize: 11, fontWeight: 700, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: 1, marginBottom: 20 }, children: "Progress" }), STEPS.map((step, i) => {
                        const isCompleted = i < currentStepIdx;
                        const isActive = i === currentStepIdx;
                        const isFuture = i > currentStepIdx;
                        return (_jsxs("div", { className: "workflow-step", style: { display: 'flex', gap: 12, marginBottom: i < STEPS.length - 1 ? 0 : 0 }, children: [_jsxs("div", { className: "workflow-step-marker", style: { display: 'flex', flexDirection: 'column', alignItems: 'center', width: 28, flexShrink: 0 }, children: [_jsx("div", { style: {
                                                width: 28, height: 28, borderRadius: '50%', flexShrink: 0,
                                                display: 'flex', alignItems: 'center', justifyContent: 'center',
                                                background: isCompleted ? 'var(--success)' : isActive ? 'var(--primary)' : '#F1F5F9',
                                                border: isActive ? '2px solid var(--primary)' : 'none',
                                                transition: 'all .2s', zIndex: 1
                                            }, children: isCompleted ? (_jsx("svg", { width: "13", height: "13", viewBox: "0 0 24 24", fill: "none", stroke: "white", strokeWidth: "3", strokeLinecap: "round", strokeLinejoin: "round", children: _jsx("path", { d: "M20 6L9 17l-5-5" }) })) : (_jsx("span", { style: { fontSize: 11, fontWeight: 800, color: isActive ? 'white' : 'var(--text-muted)' }, children: step.num })) }), i < STEPS.length - 1 && (_jsx("div", { style: { width: 2, flex: 1, minHeight: 28, background: isCompleted ? 'var(--success)' : 'var(--border)', margin: '2px 0', transition: 'background .2s' } }))] }), _jsxs("div", { style: { paddingBottom: i < STEPS.length - 1 ? 28 : 0, paddingTop: 3 }, children: [_jsx("div", { style: {
                                                fontSize: 13.5, fontWeight: isActive ? 700 : 500,
                                                color: isCompleted ? 'var(--success)' : isActive ? 'var(--primary)' : 'var(--text-muted)'
                                            }, children: step.label }), isActive && (_jsx("div", { style: { fontSize: 11.5, color: 'var(--primary)', marginTop: 2, opacity: 0.75 }, children: "In progress" })), isCompleted && (_jsx("div", { style: { fontSize: 11.5, color: 'var(--success)', marginTop: 2 }, children: "Completed" }))] })] }, step.num));
                    })] }), _jsx("div", { className: `workflow-content workflow-page-${page}`, style: { flex: 1, overflowY: 'auto' }, children: children })] }));
}
