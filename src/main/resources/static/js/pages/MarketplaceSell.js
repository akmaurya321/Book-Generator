import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { useState } from "react";
import { api } from "../api.js";

const fieldStyle = { display: "block", width: "100%", boxSizing: "border-box", padding: 12, border: "1px solid #CBD5E1", borderRadius: 8, font: "inherit", marginTop: 6 };
const buttonStyle = { padding: "11px 16px", border: 0, borderRadius: 8, background: "#E85D35", color: "white", fontWeight: 700, cursor: "pointer" };

export default function MarketplaceSell({ onNav }) {
  const [values, setValues] = useState({ listingType: "PROJECT_AND_DOCUMENTATION", title: "", description: "", category: "", technologies: "", ownershipConfirmed: false });
  const [project, setProject] = useState(null);
  const [document, setDocument] = useState(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const update = (key, value) => setValues(previous => ({ ...previous, [key]: value }));

  const submit = async (event) => {
    event.preventDefault();
    setBusy(true); setError(""); setMessage("");
    try {
      const form = new FormData();
      Object.entries(values).forEach(([key, value]) => form.append(key, String(value)));
      if (project) form.append("project", project);
      if (document) form.append("document", document);
      await api.marketplace.submit(form);
      setMessage("Your upload passed initial checks and is waiting for Marketplace review.");
      setProject(null); setDocument(null);
      event.target.reset();
      setValues(previous => ({ ...previous, ownershipConfirmed: false }));
    } catch (uploadError) {
      setError(uploadError.message || "Upload failed.");
    } finally {
      setBusy(false);
    }
  };

  const needsProject = values.listingType !== "DOCUMENTATION_ONLY";
  const needsDocument = values.listingType !== "PROJECT_ONLY";

  return _jsxs("main", { style: { minHeight: "100vh", background: "#F8FAFC", color: "#172033", padding: "32px 18px 70px", fontFamily: "Inter, system-ui, sans-serif" }, children: [
    _jsx("button", { type: "button", onClick: () => onNav("marketplace"), style: { border: 0, background: "none", color: "#E85D35", cursor: "pointer", fontWeight: 700 }, children: "← Marketplace" }),
    _jsxs("section", { style: { maxWidth: 720, margin: "20px auto", padding: "clamp(18px, 5vw, 38px)", background: "#fff", border: "1px solid #E2E8F0", borderRadius: 16 }, children: [
      _jsx("h1", { style: { marginTop: 0 }, children: "Share your work for free" }),
      _jsx("p", { style: { color: "#64748B", lineHeight: 1.6 }, children: "Uploads are checked and reviewed before they appear publicly. Never upload confidential or private material." }),
      error && _jsx("p", { role: "alert", style: { padding: 12, background: "#FEF2F2", color: "#B91C1C", borderRadius: 8 }, children: error }),
      message && _jsx("p", { role: "status", style: { padding: 12, background: "#ECFDF5", color: "#047857", borderRadius: 8 }, children: message }),
      _jsxs("form", { onSubmit: submit, children: [
        _jsxs("label", { style: { display: "block", marginBottom: 16 }, children: ["Content type", _jsxs("select", { value: values.listingType, onChange: event => update("listingType", event.target.value), style: fieldStyle, children: [
          _jsx("option", { value: "PROJECT_AND_DOCUMENTATION", children: "Project + Documentation" }),
          _jsx("option", { value: "PROJECT_ONLY", children: "Project Only" }),
          _jsx("option", { value: "DOCUMENTATION_ONLY", children: "Documentation Only" }),
        ] })] }),
        _jsxs("label", { style: { display: "block", marginBottom: 16 }, children: ["Title", _jsx("input", { required: true, maxLength: 180, value: values.title, onChange: event => update("title", event.target.value), style: fieldStyle })] }),
        _jsxs("label", { style: { display: "block", marginBottom: 16 }, children: ["Description", _jsx("textarea", { required: true, maxLength: 5000, rows: 5, value: values.description, onChange: event => update("description", event.target.value), style: fieldStyle })] }),
        _jsxs("label", { style: { display: "block", marginBottom: 16 }, children: ["Category", _jsx("input", { required: true, maxLength: 80, value: values.category, onChange: event => update("category", event.target.value), placeholder: "e.g. Web Development", style: fieldStyle })] }),
        _jsxs("label", { style: { display: "block", marginBottom: 16 }, children: ["Technologies (optional)", _jsx("input", { maxLength: 2000, value: values.technologies, onChange: event => update("technologies", event.target.value), placeholder: "Java, Spring Boot, React", style: fieldStyle })] }),
        needsProject && _jsxs("label", { style: { display: "block", marginBottom: 16 }, children: ["Project archive (ZIP, max 20 MB)", _jsx("input", { type: "file", accept: ".zip,application/zip", required: true, onChange: event => setProject(event.target.files?.[0] || null), style: fieldStyle })] }),
        needsDocument && _jsxs("label", { style: { display: "block", marginBottom: 16 }, children: ["Documentation (PDF or DOCX, max 20 MB)", _jsx("input", { type: "file", accept: ".pdf,.docx,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document", required: true, onChange: event => setDocument(event.target.files?.[0] || null), style: fieldStyle })] }),
        _jsxs("label", { style: { display: "flex", alignItems: "flex-start", gap: 10, margin: "22px 0", lineHeight: 1.5, fontSize: 13, color: "#475569" }, children: [
          _jsx("input", { type: "checkbox", checked: values.ownershipConfirmed, onChange: event => update("ownershipConfirmed", event.target.checked), required: true, style: { marginTop: 4 } }),
          "I have the rights to distribute this content. It contains no unauthorized copyrighted work, private/confidential code, credentials, personal data, or malicious material.",
        ] }),
        _jsx("button", { type: "submit", disabled: busy, style: { ...buttonStyle, opacity: busy ? 0.65 : 1 }, children: busy ? "Validating and uploading…" : "Submit for review" }),
      ] }),
    ] }),
  ] });
}
