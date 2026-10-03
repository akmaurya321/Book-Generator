import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { useEffect, useState } from "react";
import { api } from "../api.js";

const PROJECT_TYPES = [
  ["MINOR_PROJECT", "Minor Project"],
  ["MAJOR_FINAL_YEAR_PROJECT", "Major / Final Year Project"],
  ["CAPSTONE", "Capstone"],
  ["BACHELOR_PROJECT", "Bachelor Project"],
  ["BACHELOR_THESIS", "Bachelor Thesis"],
];

const controlStyle = {
  boxSizing: "border-box",
  width: "100%",
  minWidth: 0,
  border: "1px solid var(--border)",
  borderRadius: 8,
  padding: "9px 10px",
  color: "var(--text-primary)",
  background: "white",
  font: "inherit",
};

function Button({ children, onClick, type = "button", disabled = false, secondary = false }) {
  return _jsx("button", {
    type,
    onClick,
    disabled,
    style: {
      border: secondary ? "1px solid var(--border)" : 0,
      borderRadius: 8,
      padding: "9px 12px",
      background: secondary ? "white" : "var(--primary)",
      color: secondary ? "var(--text-primary)" : "white",
      fontWeight: 700,
      cursor: disabled ? "not-allowed" : "pointer",
      opacity: disabled ? 0.55 : 1,
    },
    children,
  });
}

function TemplateUploadForm({ onSubmit, busy, versionId, onCancel }) {
  return _jsxs("form", {
    onSubmit,
    style: { display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(190px, 1fr))", gap: 10, padding: 16, background: "#F8FAFC", borderRadius: 12, marginTop: 14 },
    children: [
      _jsx("input", { name: "templateId", placeholder: "Stable template ID (optional)", style: controlStyle }),
      _jsx("input", { name: "country", placeholder: "Country", required: true, style: controlStyle }),
      _jsx("input", { name: "state", placeholder: "State", style: controlStyle }),
      _jsx("input", { name: "region", placeholder: "Region", style: controlStyle }),
      _jsx("input", { name: "city", placeholder: "City", style: controlStyle }),
      _jsx("input", { name: "college", placeholder: "College / institution", required: true, style: controlStyle }),
      _jsx("input", { name: "university", placeholder: "University", style: controlStyle }),
      _jsx("input", { name: "aliases", placeholder: "Aliases, comma separated", style: controlStyle }),
      _jsx("input", { name: "department", placeholder: "Department", required: true, style: controlStyle }),
      _jsx("input", { name: "degree", placeholder: "Degree", required: true, style: controlStyle }),
      _jsxs("select", { name: "projectType", required: true, defaultValue: "", style: controlStyle, children: [
        _jsx("option", { value: "", disabled: true, children: "Choose project type" }),
        PROJECT_TYPES.map(([value, label]) => _jsx("option", { value, children: label }, value)),
      ] }),
      _jsx("input", { name: "sourceUrl", type: "url", placeholder: "Source URL", style: controlStyle }),
      _jsx("input", { name: "license", placeholder: "License / permission", style: controlStyle }),
      _jsxs("label", { style: { display: "grid", gap: 5, fontSize: 12, color: "var(--text-secondary)" }, children: [
        "DOCX or PDF source template",
        _jsx("input", { name: "template", type: "file", accept: ".docx,.pdf", required: true }),
      ] }),
      _jsxs("label", { style: { display: "grid", gap: 5, fontSize: 12, color: "var(--text-secondary)" }, children: [
        "Optional preview (PDF / PNG / JPEG)",
        _jsx("input", { name: "preview", type: "file", accept: ".pdf,.png,.jpg,.jpeg" }),
      ] }),
      _jsxs("div", { style: { display: "flex", alignItems: "center", gap: 9, gridColumn: "1 / -1" }, children: [
        _jsx(Button, { type: "submit", disabled: busy, children: busy ? "Uploading and scanning…" : versionId ? "Create immutable version" : "Upload, analyze and submit for review" }),
        _jsx(Button, { secondary: true, onClick: onCancel, children: "Cancel" }),
      ] }),
    ],
  });
}

export default function TemplateAdmin({ onNav, state }) {
  const [rows, setRows] = useState([]);
  const [status, setStatus] = useState("");
  const [query, setQuery] = useState("");
  const [uploadOpen, setUploadOpen] = useState(false);
  const [versionOf, setVersionOf] = useState(null);
  const [audit, setAudit] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const isAdmin = state.user?.roles?.includes("ROLE_ADMIN");
  const load = async (nextStatus = status, nextQuery = query) => {
    setError("");
    try {
      setRows(await api.templates.adminList({ status: nextStatus, query: nextQuery }));
    } catch (loadError) {
      setError(loadError.message || "Unable to load template records.");
    }
  };

  useEffect(() => { if (isAdmin) load(); }, [isAdmin]);

  const submitUpload = async (event) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const templateId = form.get("templateId");
    form.delete("templateId");
    setBusy(true);
    setError("");
    setNotice("");
    try {
      if (versionOf) await api.templates.createVersion(versionOf.templateId, form);
      else if (templateId) {
        form.append("templateId", templateId);
        await api.templates.create(form);
      } else {
        await api.templates.create(form);
      }
      setNotice(versionOf ? "New version uploaded for validation and review." : "Template uploaded, scanned and submitted for review.");
      setUploadOpen(false);
      setVersionOf(null);
      await load();
    } catch (submitError) {
      setError(submitError.message || "Unable to save the template.");
    } finally {
      setBusy(false);
    }
  };

  const perform = async (item, action) => {
    setBusy(true);
    setError("");
    setNotice("");
    try {
      if (action === "approve") await api.templates.approve(item.id);
      if (action === "publish") await api.templates.publish(item.id);
      if (action === "reject") {
        const reason = window.prompt("Reason for rejection:");
        if (reason == null || !reason.trim()) return;
        await api.templates.reject(item.id, reason.trim());
      }
      if (action === "suspend") {
        const reason = window.prompt("Reason for suspending this template:");
        if (reason == null || !reason.trim()) return;
        await api.templates.suspend(item.id, reason.trim());
      }
      if (action === "archive") {
        const reason = window.prompt("Archive reason (optional):") || "";
        await api.templates.archive(item.id, reason);
      }
      setNotice(`Template ${action} action completed.`);
      await load();
    } catch (actionError) {
      setError(actionError.message || `Unable to ${action} this template.`);
    } finally {
      setBusy(false);
    }
  };

  const showAudit = async (item) => {
    setError("");
    try {
      setAudit({ item, events: await api.templates.audit(item.id) });
    } catch (auditError) {
      setError(auditError.message || "Unable to load the audit history.");
    }
  };

  if (!isAdmin) return _jsxs("main", { style: { padding: 32, maxWidth: 720 }, children: [
    _jsx("h1", { children: "Template administration" }),
    _jsx("p", { role: "alert", children: "This page requires the administrator role." }),
    _jsx(Button, { secondary: true, onClick: () => onNav("templates"), children: "Back to Template Library" }),
  ] });

  return _jsxs("main", { style: { padding: "26px clamp(14px, 3vw, 32px)", maxWidth: 1280, margin: "0 auto" }, children: [
    _jsxs("header", { style: { display: "flex", justifyContent: "space-between", gap: 12, alignItems: "flex-start", flexWrap: "wrap", marginBottom: 20 }, children: [
      _jsxs("div", { children: [
        _jsx("h1", { style: { margin: "0 0 5px", fontSize: 23 }, children: "Template Management" }),
        _jsx("p", { style: { margin: 0, color: "var(--text-secondary)", fontSize: 13 }, children: "Review scans and format analysis, approve, publish, suspend, archive and version templates. Only published versions are student-selectable." }),
      ] }),
      _jsx(Button, { onClick: () => { setVersionOf(null); setUploadOpen((open) => !open); }, children: uploadOpen ? "Close upload" : "Upload new template" }),
    ] }),
    error && _jsx("div", { role: "alert", style: alertStyle, children: error }),
    notice && _jsx("div", { role: "status", style: { ...alertStyle, borderColor: "#86EFAC", color: "#166534", background: "#F0FDF4" }, children: notice }),
    uploadOpen && _jsx(TemplateUploadForm, { onSubmit: submitUpload, busy, versionId: versionOf?.id, onCancel: () => { setUploadOpen(false); setVersionOf(null); } }),
    _jsxs("form", { onSubmit: (event) => { event.preventDefault(); load(status, query); }, style: { display: "flex", flexWrap: "wrap", gap: 9, margin: "20px 0 14px" }, children: [
      _jsx("select", { value: status, onChange: (event) => setStatus(event.target.value), "aria-label": "Filter by status", style: { ...controlStyle, maxWidth: 220 }, children: [
        _jsx("option", { value: "", children: "All lifecycle states" }),
        ["PROCESSING", "UNDER_REVIEW", "APPROVED", "PUBLISHED", "REJECTED", "SUSPENDED", "ARCHIVED"].map((value) => _jsx("option", { value, children: value.replaceAll("_", " ") }, value)),
      ] }),
      _jsx("input", { value: query, onChange: (event) => setQuery(event.target.value), placeholder: "Search template ID or institution", style: { ...controlStyle, maxWidth: 360 } }),
      _jsx(Button, { type: "submit", children: "Filter" }),
    ] }),
    rows.length === 0
      ? _jsx("div", { style: panelStyle, children: "No templates found. Uploaded records will appear here after validation." })
      : _jsx("div", { style: { display: "grid", gap: 12 }, children: rows.map((item) => _jsxs("article", { style: panelStyle, children: [
          _jsxs("div", { style: { display: "flex", justifyContent: "space-between", flexWrap: "wrap", gap: 12 }, children: [
            _jsxs("div", { children: [
              _jsxs("h2", { style: { fontSize: 16, margin: "0 0 5px" }, children: [item.college || item.templateId, " ", _jsxs("small", { style: { color: "var(--text-muted)", fontSize: 12, fontWeight: 500 }, children: ["v", item.version] })] }),
              _jsxs("div", { style: { color: "var(--text-secondary)", fontSize: 12 }, children: [item.templateId, " · ", item.country || "Country not specified", item.projectType ? ` · ${item.projectType.replaceAll("_", " ")}` : ""] }),
            ] }),
            _jsx("strong", { style: { color: item.status === "PUBLISHED" ? "#166534" : "var(--primary)", fontSize: 12 }, children: item.status }),
          ] }),
          _jsxs("div", { style: { display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(150px, 1fr))", gap: 8, marginTop: 14, fontSize: 12 }, children: [
            _jsxs("div", { children: [_jsx("b", { children: "Security scan" }), _jsx("div", { children: item.securityScanStatus })] }),
            _jsxs("div", { children: [_jsx("b", { children: "Format validation" }), _jsx("div", { children: item.validationStatus })] }),
            _jsxs("div", { children: [_jsx("b", { children: "Verification" }), _jsx("div", { children: item.verificationStatus })] }),
            _jsxs("div", { children: [_jsx("b", { children: "Source" }), _jsx("div", { children: item.sourceType })] }),
          ] }),
          _jsx("p", { style: { fontSize: 13, color: "var(--text-secondary)", lineHeight: 1.5, margin: "12px 0" }, children: item.previewText || "No extracted preview text." }),
          _jsxs("details", { children: [
            _jsx("summary", { style: { cursor: "pointer", fontWeight: 700, fontSize: 13 }, children: "Extracted format and scan metadata" }),
            _jsx("pre", { style: { whiteSpace: "pre-wrap", overflowWrap: "anywhere", background: "#F8FAFC", padding: 12, borderRadius: 8, fontSize: 11 }, children: JSON.stringify({ format: item.formatSchema, analysis: item.analysisMetadata }, null, 2) }),
          ] }),
          _jsxs("div", { style: { display: "flex", gap: 8, flexWrap: "wrap", marginTop: 13 }, children: [
            _jsx("a", { href: api.templates.sourceUrl(item.id), target: "_blank", rel: "noreferrer", style: linkStyle, children: "Preview source file" }),
            _jsx(Button, { secondary: true, onClick: () => showAudit(item), children: "Audit history" }),
            _jsx(Button, { secondary: true, onClick: () => { setVersionOf(item); setUploadOpen(true); window.scrollTo({ top: 0, behavior: "smooth" }); }, children: "Create new version" }),
            item.status === "UNDER_REVIEW" && _jsx(Button, { disabled: busy, onClick: () => perform(item, "approve"), children: "Approve" }),
            item.status === "UNDER_REVIEW" && _jsx(Button, { secondary: true, disabled: busy, onClick: () => perform(item, "reject"), children: "Reject" }),
            item.status === "APPROVED" && _jsx(Button, { disabled: busy, onClick: () => perform(item, "publish"), children: "Publish" }),
            item.status === "PUBLISHED" && _jsx(Button, { secondary: true, disabled: busy, onClick: () => perform(item, "suspend"), children: "Suspend" }),
            ["PUBLISHED", "SUSPENDED", "REJECTED"].includes(item.status) && _jsx(Button, { secondary: true, disabled: busy, onClick: () => perform(item, "archive"), children: "Archive" }),
          ] }),
        ] }, item.id)) }),
    audit && _jsxs("section", { style: { ...panelStyle, marginTop: 18 }, children: [
      _jsxs("div", { style: { display: "flex", justifyContent: "space-between" }, children: [
        _jsxs("strong", { children: ["Audit · ", audit.item.college || audit.item.templateId, " v", audit.item.version] }),
        _jsx("button", { type: "button", onClick: () => setAudit(null), "aria-label": "Close audit history", children: "Close" }),
      ] }),
      audit.events.map((event, index) => _jsxs("div", { style: { borderTop: "1px solid var(--border)", padding: "9px 0", fontSize: 12 }, children: [
        _jsxs("strong", { children: [event.action, " · ", event.oldStatus || "—", " → ", event.newStatus || "—"] }),
        _jsxs("div", { style: { color: "var(--text-secondary)" }, children: [event.createdAt, event.reason ? ` · ${event.reason}` : ""] }),
      ] }, `${event.createdAt}-${index}`)),
    ] }),
    _jsx("div", { style: { marginTop: 18 }, children: _jsx(Button, { secondary: true, onClick: () => onNav("templates"), children: "Open student library view" }) }),
  ] });
}

const panelStyle = { background: "white", border: "1px solid var(--border)", borderRadius: 13, padding: 16 };
const alertStyle = { ...panelStyle, marginBottom: 12, color: "#991B1B", background: "#FEF2F2", borderColor: "#FCA5A5" };
const linkStyle = { display: "inline-flex", alignItems: "center", border: "1px solid var(--border)", borderRadius: 8, padding: "9px 12px", color: "var(--primary)", textDecoration: "none", fontWeight: 700, fontSize: 13 };
