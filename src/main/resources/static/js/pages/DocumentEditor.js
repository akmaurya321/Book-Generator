import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { useEffect, useState } from "react";
import { api } from "../api.js";

const buttonStyle = {
  padding: "9px 13px",
  border: "1px solid #D8DEE8",
  borderRadius: 8,
  background: "#fff",
  color: "#334155",
  fontWeight: 650,
  cursor: "pointer",
};

const INSTRUCTIONS = {
  clarity: "Improve clarity and readability without changing the meaning.",
  formal: "Rewrite in a polished, formal academic tone without adding unsupported facts.",
  concise: "Make the passage more concise while preserving all important details.",
};

export default function DocumentEditor({ onNav, state }) {
  const jobId = state.currentProjectId;
  const [document, setDocument] = useState(null);
  const [selection, setSelection] = useState(null);
  const [instructionType, setInstructionType] = useState("clarity");
  const [customInstruction, setCustomInstruction] = useState("");
  const [alternatives, setAlternatives] = useState([]);
  const [chosenAlternative, setChosenAlternative] = useState(-1);
  const [loading, setLoading] = useState(true);
  const [working, setWorking] = useState(false);
  const [error, setError] = useState("");
  const [status, setStatus] = useState("");

  const loadDocument = async () => {
    if (!jobId) {
      setError("Choose a completed project before opening the editor.");
      setLoading(false);
      return;
    }
    setLoading(true);
    setError("");
    try {
      const loaded = await api.documentation.editor(jobId);
      setDocument(loaded);
      setSelection(null);
      setAlternatives([]);
      setChosenAlternative(-1);
    } catch (loadError) {
      setError(loadError.message || "Unable to load this document.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadDocument();
  }, [jobId]);

  const selectRange = (paragraph, event) => {
    const field = event.currentTarget;
    const startOffset = field.selectionStart;
    const endOffset = field.selectionEnd;
    if (endOffset <= startOffset) return;
    setSelection({
      paragraphIndex: paragraph.index,
      startOffset,
      endOffset,
      selectedText: paragraph.text.slice(startOffset, endOffset),
    });
    setAlternatives([]);
    setChosenAlternative(-1);
    setStatus("");
  };

  const generateAlternatives = async (event) => {
    event.preventDefault();
    if (!selection) return;
    const instruction = instructionType === "other"
      ? customInstruction.trim()
      : INSTRUCTIONS[instructionType];
    if (!instruction) {
      setError("Describe how you want the selected text changed.");
      return;
    }
    setWorking(true);
    setError("");
    setStatus("");
    try {
      const result = await api.documentation.transformSelection(jobId, {
        ...selection,
        instruction,
      });
      if (!Array.isArray(result.alternatives) || result.alternatives.length !== 2) {
        throw new Error("The editor did not receive exactly two alternatives. Please try again.");
      }
      setAlternatives(result.alternatives);
    } catch (transformError) {
      setError(transformError.message || "Unable to generate alternatives.");
    } finally {
      setWorking(false);
    }
  };

  const saveSelectedAlternative = async () => {
    if (!selection || chosenAlternative < 0 || !document) return;
    setWorking(true);
    setError("");
    setStatus("");
    try {
      const updated = await api.documentation.saveEdit(jobId, {
        version: document.version,
        ...selection,
        replacement: alternatives[chosenAlternative],
      });
      setDocument(updated);
      setSelection(null);
      setAlternatives([]);
      setChosenAlternative(-1);
      setStatus("Your edit has been saved. Undo is available.");
    } catch (saveError) {
      setError(saveError.message || "Unable to save this edit.");
      if (saveError.status === 409) await loadDocument();
    } finally {
      setWorking(false);
    }
  };

  const moveHistory = async (redo) => {
    if (!document) return;
    setWorking(true);
    setError("");
    setStatus("");
    try {
      const updated = redo
        ? await api.documentation.redoEdit(jobId, document.version)
        : await api.documentation.undoEdit(jobId, document.version);
      setDocument(updated);
      setSelection(null);
      setAlternatives([]);
      setChosenAlternative(-1);
      setStatus(redo ? "Edit restored." : "Last edit undone.");
    } catch (historyError) {
      setError(historyError.message || `Unable to ${redo ? "redo" : "undo"} this edit.`);
      if (historyError.status === 409) await loadDocument();
    } finally {
      setWorking(false);
    }
  };

  if (loading) return _jsx("div", { style: { padding: 32 }, children: "Loading document editor…" });
  if (error && !document) return _jsxs("div", { style: { padding: 32 }, children: [
    _jsx("p", { role: "alert", children: error }),
    _jsx("button", { type: "button", style: buttonStyle, onClick: () => onNav("my-projects"), children: "Back to projects" }),
  ] });

  return _jsxs("main", {
    style: { maxWidth: 1320, margin: "0 auto", padding: "24px clamp(14px, 3vw, 36px)", color: "#172033" },
    children: [
      _jsxs("header", { style: { display: "flex", flexWrap: "wrap", alignItems: "center", justifyContent: "space-between", gap: 14, marginBottom: 20 }, children: [
        _jsxs("div", { children: [
          _jsx("button", { type: "button", style: { ...buttonStyle, marginBottom: 12 }, onClick: () => onNav("my-projects"), children: "← Back to projects" }),
          _jsx("h1", { style: { fontSize: 25, margin: 0 }, children: "Document editor" }),
          _jsx("p", { style: { color: "#64748B", margin: "6px 0 0" }, children: "Select text in a page, ask for two alternatives, then apply and save one." }),
        ] }),
        _jsxs("div", { style: { display: "flex", flexWrap: "wrap", gap: 8 }, children: [
          _jsx("button", { type: "button", style: buttonStyle, disabled: working || !document?.canUndo, onClick: () => moveHistory(false), children: "Undo" }),
          _jsx("button", { type: "button", style: buttonStyle, disabled: working || !document?.canRedo, onClick: () => moveHistory(true), children: "Redo" }),
          _jsx("a", { href: api.documentation.downloadDocxUrl(jobId), style: { ...buttonStyle, textDecoration: "none" }, children: "Export DOCX" }),
          _jsx("a", { href: api.documentation.downloadPdfUrl(jobId), style: { ...buttonStyle, textDecoration: "none" }, children: "Export PDF" }),
          _jsx("a", { href: `${api.documentation.viewPdfUrl(jobId)}?version=${document?.version ?? 0}`, target: "_blank", rel: "noreferrer", style: { ...buttonStyle, textDecoration: "none" }, children: "Preview PDF" }),
        ] }),
      ] }),
      error && _jsx("p", { role: "alert", style: { padding: 12, background: "#FEF2F2", color: "#B91C1C", borderRadius: 8 }, children: error }),
      status && _jsx("p", { role: "status", style: { padding: 12, background: "#F0FDF4", color: "#166534", borderRadius: 8 }, children: status }),
      _jsxs("div", { style: { display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(min(100%, 360px), 1fr))", alignItems: "start", gap: 20 }, children: [
        _jsx("section", { "aria-label": "Paginated document preview", style: { display: "grid", gap: 18 }, children:
          (document?.pages || []).map((page) => _jsxs("article", {
            style: { minHeight: 650, padding: "clamp(22px, 5vw, 64px)", background: "white", border: "1px solid #E2E8F0", borderRadius: 8, boxShadow: "0 8px 28px rgba(15,23,42,.07)" },
            children: [
              _jsx("div", { style: { marginBottom: 24, color: "#94A3B8", fontSize: 11, textAlign: "right", textTransform: "uppercase", letterSpacing: ".08em" }, children: `Page ${page.pageNumber}` }),
              page.paragraphs.map((paragraph) => _jsx("textarea", {
                readOnly: true,
                "aria-label": `Document paragraph ${paragraph.index + 1}`,
                value: paragraph.text || "",
                rows: Math.max(1, Math.min(12, (paragraph.text || "").split("\n").length + Math.ceil((paragraph.text || "").length / 95))),
                onSelect: event => selectRange(paragraph, event),
                onMouseUp: event => selectRange(paragraph, event),
                style: {
                  display: "block", width: "100%", boxSizing: "border-box", resize: "none", overflow: "hidden",
                  marginBottom: 14, padding: 0, border: 0, outline: "none", background: "transparent",
                  color: "#263449", fontFamily: "Georgia, serif", fontSize: paragraph.style?.toLowerCase().includes("heading") ? 18 : 15,
                  fontWeight: paragraph.style?.toLowerCase().includes("heading") ? 700 : 400, lineHeight: 1.8,
                  cursor: "text", whiteSpace: "pre-wrap",
                },
              }, paragraph.index)),
            ],
          }, page.pageNumber)),
        }),
        _jsxs("aside", { style: { position: "sticky", top: 16, padding: 18, background: "white", border: "1px solid #E2E8F0", borderRadius: 10 }, children: [
          _jsx("h2", { style: { margin: "0 0 8px", fontSize: 17 }, children: "AI editing" }),
          selection
            ? _jsxs("p", { style: { color: "#64748B", fontSize: 13, lineHeight: 1.5 }, children: ["Selected ", selection.selectedText.length, " characters."] })
            : _jsx("p", { style: { color: "#64748B", fontSize: 13, lineHeight: 1.5 }, children: "Highlight a passage in one paragraph to begin." }),
          selection && _jsxs("form", { onSubmit: generateAlternatives, children: [
            _jsx("label", { htmlFor: "editor-instruction-type", style: { display: "block", fontSize: 12, fontWeight: 700, margin: "14px 0 6px" }, children: "Editing instruction" }),
            _jsxs("select", { id: "editor-instruction-type", value: instructionType, onChange: event => setInstructionType(event.target.value), style: { width: "100%", padding: 10, border: "1px solid #CBD5E1", borderRadius: 7 }, children: [
              _jsx("option", { value: "clarity", children: "Improve clarity" }),
              _jsx("option", { value: "formal", children: "Make more formal" }),
              _jsx("option", { value: "concise", children: "Make concise" }),
              _jsx("option", { value: "other", children: "Other (custom instruction)" }),
            ] }),
            instructionType === "other" && _jsx("textarea", {
              value: customInstruction,
              onChange: event => setCustomInstruction(event.target.value),
              maxLength: 1000,
              placeholder: "Describe the change you want",
              rows: 4,
              style: { width: "100%", boxSizing: "border-box", marginTop: 8, padding: 10, border: "1px solid #CBD5E1", borderRadius: 7, font: "inherit" },
            }),
            _jsx("button", { type: "submit", disabled: working, style: { ...buttonStyle, width: "100%", marginTop: 10, background: "#1D4ED8", color: "white", borderColor: "#1D4ED8" }, children: working ? "Working…" : "Generate 2 alternatives" }),
          ] }),
            alternatives.length === 2 && _jsxs("div", { style: { marginTop: 18 }, children: [
            _jsx("h3", { style: { margin: "0 0 8px", fontSize: 14 }, children: "Choose one alternative" }),
            alternatives.map((alternative, index) => _jsxs("label", {
              style: { display: "flex", gap: 8, padding: 10, marginBottom: 8, border: `1px solid ${chosenAlternative === index ? "#2563EB" : "#E2E8F0"}`, borderRadius: 8, cursor: "pointer", lineHeight: 1.5, fontSize: 13 },
              children: [
                _jsx("input", { type: "radio", name: "editor-alternative", checked: chosenAlternative === index, onChange: () => setChosenAlternative(index) }),
                _jsxs("span", { children: [_jsx("strong", { children: `Option ${index + 1}` }), _jsx("br", {}), alternative] }),
              ],
            }, index)),
            _jsx("button", { type: "button", disabled: working || chosenAlternative < 0, onClick: saveSelectedAlternative, style: { ...buttonStyle, width: "100%", background: "#0F766E", color: "white", borderColor: "#0F766E" }, children: working ? "Saving…" : "Apply and save" }),
          ] }),
          _jsx("p", { style: { margin: "14px 0 0", color: "#94A3B8", fontSize: 11 }, children: `Document version ${document?.version ?? 0}. Changes are checked against this version before they are saved.` }),
        ] }),
      ] }),
    ],
  });
}
