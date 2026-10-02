import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { useState } from "react";
import CoverPage from "../components/CoverPage.js";

const STAGES = [
  ["WAITING_FOR_INDEXING", "Embedding and indexing project files"],
  ["QUEUED_FOR_GENERATION", "Queued"],
  ["GENERATING_DOCUMENTATION", "Generating chapters"],
  ["VALIDATING_ASSETS", "Validating assets"],
  ["ASSEMBLING_DOCUMENT", "Assembling document"],
  ["VALIDATING_DOCUMENT", "Validating document"],
  ["PREPARING_PDF", "Preparing PDF"],
  ["COMPLETED", "Completed"],
];

function GeneratedPage({ item, chapter, index }) {
  const title = item.title || item.sectionTitle || chapter || `Generated section ${index + 1}`;
  const body = String(item.content || item.text || "");
  const invalidMarker = "[Not validated]";
  const notValidated = body.startsWith(invalidMarker);
  const visibleBody = notValidated ? body.slice(invalidMarker.length).trimStart() : body;
  return _jsxs("article", { className: "generation-document-page", children: [
    chapter && _jsx("div", { className: "generation-page-chapter", children: chapter }),
    _jsx("h2", { children: title }),
    notValidated && _jsx("div", { className: "generation-not-validated", children: "Not validated" }),
    visibleBody
      ? _jsx("p", { children: visibleBody })
      : _jsx("p", { className: "generation-page-waiting", children: "This section is being prepared…" }),
  ] });
}

export default function GenerationProgress({ state, onCancel }) {
  const [cancelPending, setCancelPending] = useState(false);
  const status = state.generationStatus || "";
  const progress = Math.max(0, Math.min(100, Number(state.generationProgress || 0)));
  const stage = Math.max(0, STAGES.findIndex(([id]) => id === status));
  const preview = state.preview || {};
  const frontMatter = Array.isArray(preview.frontMatter) ? preview.frontMatter : [];
  const chapters = Object.entries(preview.chapters || {});
  const generatedCount = frontMatter.length + chapters.reduce((count, [, items]) => count + (items || []).length, 0);
  const selectedSections = (state.sections || []).filter((section) => section.enabled);
  const generatedTitles = chapters.flatMap(([, items]) => (items || [])
    .map((item) => String(item.title || "").replace(/^chapter\s+\d+\s*[-—:.]?\s*/i, "").trim().toLowerCase())
    .filter(Boolean));
  const generatedChapterIds = new Set(chapters.map(([chapterId]) => chapterId));
  const pendingSections = status === "COMPLETED" ? [] : selectedSections.filter((section) => {
    if (generatedChapterIds.has(section.id) || generatedChapterIds.has(section.chapterId)) return false;
    const normalizedTitle = String(section.title || "").replace(/^chapter\s+\d+\s*[-—:.]?\s*/i, "").trim().toLowerCase();
    return !normalizedTitle || !generatedTitles.some((title) =>
      title === normalizedTitle || title.includes(normalizedTitle) || normalizedTitle.includes(title));
  });
  const chapterLabels = new Map();
  (state.sections || []).forEach((section) => {
    if (section.chapter && section.id && section.title) chapterLabels.set(section.id, section.title);
  });
  (state.sections || []).forEach((section) => {
    if (section.chapterId && section.title && !chapterLabels.has(section.chapterId))
      chapterLabels.set(section.chapterId, section.title);
  });
  let index = 0;

  return _jsxs("div", { className: "generation-progress-layout", children: [
    _jsxs("aside", { className: "generation-progress-panel", children: [
      _jsxs("div", { className: "generation-progress-summary", children: [
        _jsx("div", { className: "generation-progress-kicker", children: "Generation Progress" }),
        _jsxs("div", { className: "generation-progress-heading", children: [
          _jsxs("div", { className: "generation-progress-ring", style: { "--generation-progress": `${progress}%` }, children: [_jsx("span", { children: `${progress}%` })] }),
          _jsxs("div", { children: [
            _jsx("strong", { children: status === "FAILED" ? "Generation failed" : status === "CANCELLED" ? "Generation cancelled" : status === "WAITING_FOR_INDEXING" || status === "RECOVERING_INDEXING" ? "Waiting for full project indexing" : "Generating your report" }),
            _jsx("p", { children: state.generationMessage || "The document is being assembled as each chapter is ready." }),
          ] }),
        ] }),
        _jsxs("div", { className: "generation-preview-count", children: [
          _jsx("strong", { children: generatedCount }),
          _jsxs("span", { children: ["sections added to preview", selectedSections.length > 0 && ` of ${selectedSections.length}`] }),
        ] }),
      ] }),
      _jsxs("div", { className: "generation-progress-stages", children: [
        _jsx("div", { className: "generation-progress-kicker", children: "Stages" }),
        STAGES.map(([id, label], stageIndex) => _jsxs("div", { className: `generation-stage${stageIndex < stage ? " is-done" : stageIndex === stage ? " is-current" : ""}`, children: [
          _jsx("span", { children: stageIndex < stage ? "✓" : stageIndex === stage ? "•" : "○" }),
          _jsx("span", { children: label }),
        ] }, id)),
      ] }),
      status !== "COMPLETED" && status !== "FAILED" && status !== "CANCELLED"
        && _jsx("div", { className: "generation-cancel-area", children: _jsx("button", {
          type: "button",
          onClick: async () => {
            setCancelPending(true);
            try {
              await onCancel?.();
            } finally {
              setCancelPending(false);
            }
          },
          disabled: cancelPending,
          children: cancelPending ? "Cancelling…" : "Cancel Generation",
        }) }),
    ] }),
    _jsxs("main", { className: "generation-preview-panel", children: [
      _jsxs("div", { className: "generation-preview-header", children: [
        _jsxs("div", { children: [
          _jsx("span", { className: "generation-live-pill", children: "Live document preview" }),
          _jsx("h1", { children: state.studentInfo?.projectTitle || state.projectName || "Your Project Report" }),
        ] }),
        _jsx("span", { className: "generation-preview-item-count", children: `${generatedCount} sections ready` }),
      ] }),
      state.generationPreviewError && _jsx("div", {
        className: "generation-preview-error",
        role: "status",
        children: `The document is still generating, but the live preview could not refresh: ${state.generationPreviewError}`,
      }),
      _jsx("div", { className: "generation-cover-preview", children: _jsx(CoverPage, {
        info: state.studentInfo || {},
        projectName: state.projectName,
        jobId: state.currentProjectId,
        editable: false,
      }) }),
      selectedSections.length > 0 && _jsxs("section", { className: "generation-pending-list", children: [
        _jsx("h2", { children: "Selected sections" }),
        pendingSections.map((section) => _jsx("span", { className: "generation-pending-section", children: section.title }, section.id)),
      ] }),
      frontMatter.map((item) => _jsx(GeneratedPage, { item, index: index++ }, `front-${item.id || index}`)),
      chapters.map(([chapterId, items]) => (items || []).map((item) => {
        const chapter = chapterLabels.get(chapterId) || chapterId;
        return _jsx(GeneratedPage, { item, chapter, index: index++ }, `${chapterId}-${item.id || index}`);
      })),
      generatedCount === 0 && _jsx("article", { className: "generation-document-page generation-page-empty", children: [
        _jsx("h2", { children: "Building your document" }),
        _jsx("p", { children: "Your cover page is ready. The first generated chapter will appear here as soon as it is complete." }),
      ] }),
      status === "COMPLETED" && _jsx("div", { className: "generation-preview-finished", children: "All sections are assembled. Opening the finished document downloads…" }),
    ] }),
  ] });
}
