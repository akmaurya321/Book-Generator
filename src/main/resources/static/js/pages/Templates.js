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

const cardStyle = {
  background: "white",
  border: "1px solid var(--border)",
  borderRadius: 14,
  padding: 18,
};

function ActionButton({ children, onClick, disabled = false, secondary = false, type = "button" }) {
  return _jsx("button", {
    type,
    disabled,
    onClick,
    style: {
      border: secondary ? "1px solid var(--border)" : 0,
      borderRadius: 8,
      padding: "9px 13px",
      background: secondary ? "white" : "var(--primary)",
      color: secondary ? "var(--text-primary)" : "white",
      fontWeight: 700,
      cursor: disabled ? "not-allowed" : "pointer",
      opacity: disabled ? 0.55 : 1,
    },
    children,
  });
}

export default function Templates({ onNav, state, onState }) {
  const [filters, setFilters] = useState({ query: "", country: "", state: "", department: "", degree: "", projectType: "" });
  const [result, setResult] = useState({ items: [], totalItems: 0, page: 0, totalPages: 0 });
  const [submissions, setSubmissions] = useState([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [privateFormat, setPrivateFormat] = useState(null);
  const [communityMessage, setCommunityMessage] = useState("");

  const loadTemplates = async (selectedFilters = filters) => {
    setBusy(true);
    setError("");
    try {
      setResult(await api.templates.search(selectedFilters));
    } catch (loadError) {
      setError(loadError.message || "Unable to load published templates.");
    } finally {
      setBusy(false);
    }
  };

  useEffect(() => {
    loadTemplates();
    api.templates.mySubmissions().then(setSubmissions).catch((loadError) => setError(loadError.message || "Unable to load your template submissions."));
  }, []);

  const updateFilter = (event) => {
    setFilters((current) => ({ ...current, [event.target.name]: event.target.value }));
  };

  const chooseTemplate = (template) => {
    onState({
      selectedCatalogTemplateId: template.templateId,
      selectedCatalogTemplateVersion: template.version,
      selectedCatalogTemplate: template,
      selectedTemplate: `${template.college || template.templateId} · v${template.version}`,
      usePrivateFormat: false,
    });
    if (state.currentProjectId) onNav("student-details");
  };

  const uploadPrivate = async (event) => {
    event.preventDefault();
    const file = event.currentTarget.elements.namedItem("privateTemplate").files?.[0];
    if (!file || !state.currentProjectId) return;
    setBusy(true);
    setError("");
    try {
      const response = await api.documentation.uploadPrivateTemplateFormat(state.currentProjectId, file);
      setPrivateFormat(response);
      onState({
        usePrivateFormat: true,
        selectedCatalogTemplateId: "",
        selectedCatalogTemplateVersion: null,
        selectedCatalogTemplate: null,
        selectedTemplate: "Private uploaded format",
        privateFormatAnalysis: response,
      });
    } catch (uploadError) {
      setError(uploadError.message || "Unable to analyze this private file.");
    } finally {
      setBusy(false);
    }
  };

  const submitCommunity = async (event) => {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    setBusy(true);
    setError("");
    setCommunityMessage("");
    try {
      const response = await api.templates.submit(form);
      setCommunityMessage(`Submitted for review. Current status: ${response.status}. It will remain private until reviewed and published.`);
      setSubmissions((current) => [response, ...current]);
      formElement.reset();
    } catch (submitError) {
      setError(submitError.message || "Unable to submit the template for review.");
    } finally {
      setBusy(false);
    }
  };

  return _jsxs("main", {
    className: "templates-page",
    style: { padding: "28px clamp(16px, 4vw, 36px)", maxWidth: 1180, margin: "0 auto" },
    children: [
      _jsx("h1", { style: { fontSize: 24, fontWeight: 800, margin: "0 0 6px" }, children: "Template Library" }),
      _jsx("p", { style: { color: "var(--text-secondary)", margin: "0 0 22px", lineHeight: 1.6 }, children: "Choose a published, versioned document format. Your project sections and generation flow remain unchanged." }),
      error && _jsx("div", { role: "alert", style: { ...cardStyle, marginBottom: 16, borderColor: "#FCA5A5", color: "#991B1B" }, children: error }),
      _jsxs("form", {
        onSubmit: (event) => { event.preventDefault(); loadTemplates({ ...filters, page: 0 }); },
        style: { ...cardStyle, display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(150px, 1fr))", gap: 10, marginBottom: 18 },
        children: [
          [["query", "Search college, city or alias"], ["country", "Country"], ["state", "State"], ["department", "Department"], ["degree", "Degree"]].map(([name, placeholder]) =>
            _jsx("input", { name, value: filters[name], onChange: updateFilter, placeholder, "aria-label": placeholder, style: inputStyle }, name)),
          _jsxs("select", { name: "projectType", value: filters.projectType, onChange: updateFilter, "aria-label": "Project type", style: inputStyle, children: [
            _jsx("option", { value: "", children: "All project types" }),
            PROJECT_TYPES.map(([value, label]) => _jsx("option", { value, children: label }, value)),
          ] }),
          _jsx(ActionButton, { type: "submit", disabled: busy, children: busy ? "Loading…" : "Search templates" }),
        ],
      }),
      _jsxs("div", { style: { display: "flex", justifyContent: "space-between", alignItems: "center", margin: "0 2px 12px" }, children: [
        _jsxs("span", { style: { color: "var(--text-secondary)", fontSize: 13 }, children: [result.totalItems || 0, " published template version(s)"] }),
        result.totalPages > 1 && _jsxs("span", { style: { display: "flex", gap: 8 }, children: [
          _jsx(ActionButton, { secondary: true, disabled: result.page <= 0 || busy, onClick: () => loadTemplates({ ...filters, page: result.page - 1 }), children: "Previous" }),
          _jsxs("span", { style: { alignSelf: "center", fontSize: 13 }, children: [result.page + 1, " / ", result.totalPages] }),
          _jsx(ActionButton, { secondary: true, disabled: result.page + 1 >= result.totalPages || busy, onClick: () => loadTemplates({ ...filters, page: result.page + 1 }), children: "Next" }),
        ] }),
      ] }),
      result.items?.length
        ? _jsx("div", { style: { display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(265px, 1fr))", gap: 13 }, children: result.items.map((item) => _jsxs("article", {
            style: { ...cardStyle, borderColor: state.selectedCatalogTemplateId === item.templateId && state.selectedCatalogTemplateVersion === item.version ? "var(--primary)" : "var(--border)" },
            children: [
              _jsxs("div", { style: { display: "flex", justifyContent: "space-between", gap: 12 }, children: [
                _jsx("strong", { style: { fontSize: 15 }, children: item.college || (item.templateId === "universal-template" ? "Universal Template" : item.templateId) }),
                _jsxs("span", { style: { color: "var(--text-muted)", fontSize: 12, whiteSpace: "nowrap" }, children: ["v", item.version] }),
              ] }),
              _jsx("div", { style: { color: "var(--text-secondary)", fontSize: 13, margin: "8px 0 12px" }, children: [item.university, item.city, item.state, item.country].filter(Boolean).join(" · ") || "Universal format" }),
              _jsxs("div", { style: { color: "var(--text-muted)", fontSize: 12, marginBottom: 14 }, children: [item.degree || "Any degree", item.department ? ` · ${item.department}` : "", item.projectType ? ` · ${item.projectType.replaceAll("_", " ")}` : " · All project types"] }),
              _jsx(ActionButton, { onClick: () => chooseTemplate(item), children: state.currentProjectId ? "Use this version" : "Select for next project" }),
            ],
          }, `${item.templateId}-${item.version}`)) })
        : _jsx("div", { style: { ...cardStyle, color: "var(--text-secondary)" }, children: busy ? "Loading templates…" : "No published templates match these filters. The Universal Template remains available when no specific catalog template is selected." }),
      state.currentProjectId && _jsxs("section", { style: { ...cardStyle, marginTop: 22 }, children: [
        _jsx("h2", { style: { margin: "0 0 6px", fontSize: 17 }, children: "Use a private DOCX or PDF format" }),
        _jsx("p", { style: { margin: "0 0 12px", color: "var(--text-secondary)", fontSize: 13, lineHeight: 1.6 }, children: "The file is security-checked and analyzed for this project only. It is not added to the public library or shared with other students." }),
        _jsxs("form", { onSubmit: uploadPrivate, style: { display: "flex", gap: 10, flexWrap: "wrap", alignItems: "center" }, children: [
          _jsx("input", { type: "file", name: "privateTemplate", accept: ".docx,.pdf,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document", required: true, "aria-label": "Private DOCX or PDF format" }),
          _jsx(ActionButton, { type: "submit", disabled: busy, children: busy ? "Analyzing…" : "Analyze privately" }),
        ] }),
        (privateFormat || state.privateFormatAnalysis) && _jsx("p", { role: "status", style: { color: "#166534", fontSize: 13 }, children: "Private format ready for this project. Select it on Final Review to apply its formatting." }),
      ] }),
      _jsxs("details", { style: { ...cardStyle, marginTop: 16 }, children: [
        _jsx("summary", { style: { cursor: "pointer", fontWeight: 750 }, children: "Submit a template for community review" }),
        _jsx("p", { style: { color: "var(--text-secondary)", fontSize: 13, lineHeight: 1.6 }, children: "Submissions stay private and cannot be selected by others unless an administrator approves and publishes them. Confirm that you have permission to share the uploaded document." }),
        _jsxs("form", { onSubmit: submitCommunity, style: { display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(190px, 1fr))", gap: 10 }, children: [
          _jsx("input", { name: "country", placeholder: "Country", required: true, style: inputStyle }),
          _jsx("input", { name: "state", placeholder: "State / region", style: inputStyle }),
          _jsx("input", { name: "city", placeholder: "City", style: inputStyle }),
          _jsx("input", { name: "college", placeholder: "College / institution", required: true, style: inputStyle }),
          _jsx("input", { name: "university", placeholder: "University", style: inputStyle }),
          _jsx("input", { name: "aliases", placeholder: "Search aliases (comma separated)", style: inputStyle }),
          _jsx("input", { name: "department", placeholder: "Department", required: true, style: inputStyle }),
          _jsx("input", { name: "degree", placeholder: "Degree", required: true, style: inputStyle }),
          _jsxs("select", { name: "projectType", required: true, defaultValue: "", style: inputStyle, children: [
            _jsx("option", { value: "", disabled: true, children: "Choose project type" }),
            PROJECT_TYPES.map(([value, label]) => _jsx("option", { value, children: label }, value)),
          ] }),
          _jsx("input", { name: "sourceUrl", type: "url", placeholder: "Public source URL (optional)", style: inputStyle }),
          _jsx("input", { name: "license", placeholder: "License / permission details", style: inputStyle }),
          _jsx("input", { name: "template", type: "file", accept: ".docx,.pdf", required: true, "aria-label": "Community template file" }),
          _jsxs("label", { style: { display: "flex", alignItems: "center", gap: 8, gridColumn: "1 / -1", fontSize: 13 }, children: [
            _jsx("input", { name: "ownershipConfirmed", type: "checkbox", value: "true", required: true }),
            "I confirm I own this template or have permission to share it for community use.",
          ] }),
          _jsx(ActionButton, { type: "submit", disabled: busy, children: busy ? "Submitting…" : "Submit for review" }),
        ] }),
        communityMessage && _jsx("p", { role: "status", style: { color: "#166534", fontSize: 13 }, children: communityMessage }),
      ] }),
      submissions.length > 0 && _jsxs("section", { style: { marginTop: 22 }, children: [
        _jsx("h2", { style: { fontSize: 17 }, children: "My template submissions" }),
        _jsx("div", { style: { display: "grid", gap: 8 }, children: submissions.map((item) => _jsxs("div", { style: { ...cardStyle, display: "flex", justifyContent: "space-between", gap: 12 }, children: [
          _jsx("strong", { children: item.college || item.templateId }),
          _jsxs("span", { style: { color: "var(--text-secondary)", fontSize: 13 }, children: [item.status, " · v", item.version] }),
        ] }, item.id)) }),
      ] }),
      state.currentProjectId && _jsx("div", { style: { marginTop: 20 }, children: _jsx(ActionButton, { secondary: true, onClick: () => onNav("student-details"), children: "Continue project setup" }) }),
    ],
  });
}

const inputStyle = {
  minWidth: 0,
  border: "1px solid var(--border)",
  borderRadius: 8,
  padding: "9px 10px",
  background: "white",
  color: "var(--text-primary)",
  font: "inherit",
};
