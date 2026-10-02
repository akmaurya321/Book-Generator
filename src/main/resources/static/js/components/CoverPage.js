import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { useEffect, useRef, useState } from "react";

const DEFAULT_LAYOUT = {
  titleX: 50, titleY: 15, titleScale: 1,
  logoX: 50, logoY: 31, logoScale: 1, logoZoom: 1, logoCropX: 50, logoCropY: 50,
  bodyX: 50, bodyY: 55, bodyScale: 1,
};
const LAYOUT_BOUNDS = {
  titleX: [10, 90], titleY: [6, 22], titleScale: [0.7, 1.5],
  logoX: [10, 90], logoY: [24, 43], logoScale: [0.5, 1.8], logoZoom: [1, 2.5],
  logoCropX: [0, 100], logoCropY: [0, 100],
  bodyX: [10, 90], bodyY: [44, 68], bodyScale: [0.7, 1.5],
};

const BLOCKS = {
  title: { x: "titleX", y: "titleY", scale: "titleScale", label: "Title" },
  logo: { x: "logoX", y: "logoY", scale: "logoScale", label: "College logo" },
  body: { x: "bodyX", y: "bodyY", scale: "bodyScale", label: "College and student details" },
};
const VERTICAL_BOUNDS = {
  title: [6, 22],
  logo: [24, 43],
  body: [44, 68],
};

function number(value, fallback) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function initialLayout(value) {
  return Object.fromEntries(
    Object.entries(DEFAULT_LAYOUT).map(([key, fallback]) => {
      const [minimum, maximum] = LAYOUT_BOUNDS[key];
      return [key, Math.max(minimum, Math.min(maximum, number(value?.[key], fallback)))];
    }),
  );
}

function text(value, fallback = "") {
  return String(value || "").trim() || fallback;
}

export default function CoverPage({ info = {}, projectName = "", jobId, editable = false, onLayoutChange }) {
  const [layout, setLayout] = useState(() => initialLayout(info.coverLayout));
  const [selected, setSelected] = useState("logo");
  const pageRef = useRef(null);
  const dragRef = useRef(null);
  const layoutRef = useRef(layout);

  useEffect(() => {
    const next = initialLayout(info.coverLayout);
    layoutRef.current = next;
    setLayout(next);
  }, [info.coverLayout]);

  const applyLayout = (next, persist = false) => {
    layoutRef.current = next;
    setLayout(next);
    if (persist) onLayoutChange?.(next);
  };

  const dragStart = (key, event) => {
    if (!editable) return;
    event.preventDefault();
    event.stopPropagation();
    setSelected(key);
    dragRef.current = key;
    event.currentTarget.setPointerCapture(event.pointerId);
  };

  const dragMove = (event) => {
    const key = dragRef.current;
    const page = pageRef.current;
    if (!editable || !key || !page) return;
    const rect = page.getBoundingClientRect();
    const block = BLOCKS[key];
    const horizontalPosition = ((event.clientX - rect.left) / rect.width) * 100;
    const verticalPosition = ((event.clientY - rect.top) / rect.height) * 100;
    const [minimumY, maximumY] = VERTICAL_BOUNDS[key];
    const next = {
      ...layoutRef.current,
      [block.x]: horizontalPosition < 35 ? 20 : horizontalPosition > 65 ? 80 : 50,
      [block.y]: Math.max(minimumY, Math.min(maximumY, verticalPosition)),
    };
    applyLayout(next);
  };

  const dragEnd = () => {
    if (!dragRef.current) return;
    dragRef.current = null;
    onLayoutChange?.(layoutRef.current);
  };

  const setValue = (key, value) => {
    const next = { ...layoutRef.current, [key]: Number(value) };
    applyLayout(next, true);
  };

  const positioned = (key, extra = {}) => {
    const block = BLOCKS[key];
    const x = layout[block.x];
    const wideBlock = key !== "logo";
    return {
      position: "absolute",
      left: wideBlock ? `${x === 20 ? 8 : x === 80 ? 92 : 50}%` : `${x}%`,
      top: `${layout[block.y]}%`,
      transform: `translate(${wideBlock && x === 20 ? "0" : wideBlock && x === 80 ? "-100%" : "-50%"}, 0) scale(${layout[block.scale]})`,
      transformOrigin: "top center",
      textAlign: wideBlock && x === 20 ? "left" : wideBlock && x === 80 ? "right" : "center",
      touchAction: editable ? "none" : "auto",
      cursor: editable ? "grab" : "default",
      outline: editable && selected === key ? "2px solid #6366f1" : "none",
      outlineOffset: 4,
      borderRadius: 3,
      ...extra,
    };
  };

  const logoUrl = jobId && info.collegeLogoAssetId
    ? `/api/documentation/${encodeURIComponent(jobId)}/assets/front-matter/${encodeURIComponent(info.collegeLogoAssetId)}`
    : "";
  const selectedBlock = BLOCKS[selected];
  const selectedScale = layout[selectedBlock.scale];
  const scaleBounds = selected === "logo" ? [0.5, 1.8] : [0.7, 1.5];

  return _jsxs("div", { className: "cover-preview-shell", children: [
    editable && _jsxs("div", { className: "cover-editor-toolbar", children: [
      _jsx("div", { className: "cover-editor-title", children: "Cover page layout" }),
      _jsx("div", { className: "cover-editor-help", children: "Drag an item on the page to move it. Choose an item to resize or crop." }),
      _jsxs("div", { className: "cover-editor-controls", children: [
        _jsxs("label", { children: ["Adjusting ", _jsx("strong", { children: selectedBlock.label })] }),
        _jsx("input", {
          "aria-label": `Resize ${selectedBlock.label}`,
          type: "range",
          min: scaleBounds[0],
          max: scaleBounds[1],
          step: 0.05,
          value: selectedScale,
          onChange: (event) => setValue(selectedBlock.scale, event.target.value),
        }),
        _jsx("span", { children: `${Math.round(selectedScale * 100)}%` }),
      ] }),
      selected === "logo" && _jsxs("div", { className: "cover-crop-controls", children: [
        _jsxs("label", { children: ["Crop left / right", _jsx("input", { type: "range", min: 0, max: 100, value: layout.logoCropX, onChange: (event) => setValue("logoCropX", event.target.value) })] }),
        _jsxs("label", { children: ["Crop top / bottom", _jsx("input", { type: "range", min: 0, max: 100, value: layout.logoCropY, onChange: (event) => setValue("logoCropY", event.target.value) })] }),
        _jsxs("label", { children: ["Zoom", _jsx("input", { type: "range", min: 1, max: 2.5, step: 0.05, value: layout.logoZoom, onChange: (event) => setValue("logoZoom", event.target.value) })] }),
      ] }),
    ] }),
    _jsxs("div", {
      ref: pageRef,
      className: `cover-preview-page${editable ? " is-editable" : ""}`,
      onPointerMove: dragMove,
      onPointerUp: dragEnd,
      onPointerCancel: dragEnd,
      children: [
        _jsxs("div", {
          className: `cover-preview-block cover-preview-heading${editable && selected === "title" ? " is-selected" : ""}`,
          style: positioned("title"),
          onPointerDown: (event) => dragStart("title", event),
          onClick: () => editable && setSelected("title"),
          children: [
            _jsx("div", { className: "cover-preview-kicker", children: "PROJECT REPORT" }),
            _jsx("h2", { children: text(info.projectTitle, projectName || "Your Project Title") }),
            _jsx("div", { className: "cover-preview-subtitle", children: "Submitted in partial fulfillment of academic requirements" }),
          ],
        }),
        _jsx("div", {
          className: `cover-preview-block cover-preview-logo${editable && selected === "logo" ? " is-selected" : ""}`,
          style: positioned("logo"),
          onPointerDown: (event) => dragStart("logo", event),
          onClick: () => editable && setSelected("logo"),
          children: logoUrl
            ? _jsx("img", {
                src: logoUrl,
                alt: "College logo",
                draggable: false,
                style: {
                  width: 55,
                  height: 55,
                  objectFit: "cover",
                  objectPosition: `${layout.logoCropX}% ${layout.logoCropY}%`,
                  transform: `scale(${layout.logoZoom})`,
                },
              })
            : _jsxs("div", { className: "cover-logo-placeholder", children: [
                _jsx("span", { children: "Add college logo" }),
                editable && _jsx("small", { children: "Upload in the form" }),
              ] }),
        }),
        _jsxs("div", {
          className: `cover-preview-block cover-preview-body${editable && selected === "body" ? " is-selected" : ""}`,
          style: positioned("body"),
          onPointerDown: (event) => dragStart("body", event),
          onClick: () => editable && setSelected("body"),
          children: [
            _jsx("strong", { children: text(info.collegeName, "College / Institution name") }),
            text(info.universityName) && _jsx("div", { children: text(info.universityName) }),
            text(info.department) && _jsx("div", { children: text(info.department) }),
            _jsx("div", { className: "cover-preview-label", children: "Submitted by" }),
            _jsx("strong", { children: text(info.studentName, "Student name") }),
            _jsx("div", { children: [
              text(info.course),
              info.rollNumber && `Roll No. ${info.rollNumber}`,
              info.enrollmentNumber && `Enrollment No. ${info.enrollmentNumber}`,
              info.academicYear && `Academic year ${info.academicYear}`,
            ].filter(Boolean).join(" · ") }),
            (info.teamMembers || []).filter((member) => text(typeof member === "string" ? member : member?.name)).length > 0
              && _jsx("div", { children: (info.teamMembers || []).map((member) => text(typeof member === "string" ? member : member?.name)).filter(Boolean).join(", ") }),
            _jsx("div", { className: "cover-preview-label", children: "Guided by" }),
            _jsx("strong", { children: text(info.guideName, "Project guide") }),
            text(info.guideDesignation) && _jsx("div", { children: text(info.guideDesignation) }),
            info.guideSignatureAssetId && jobId && _jsx("img", {
              className: "cover-preview-signature",
              src: `/api/documentation/${encodeURIComponent(jobId)}/assets/front-matter/${encodeURIComponent(info.guideSignatureAssetId)}`,
              alt: "Guide signature",
            }),
            text(info.hodName) && _jsxs("div", { className: "cover-preview-hod", children: [_jsx("strong", { children: text(info.hodName) }), text(info.hodDesignation) && _jsx("div", { children: text(info.hodDesignation) })] }),
            info.hodSignatureAssetId && jobId && _jsx("img", {
              className: "cover-preview-signature",
              src: `/api/documentation/${encodeURIComponent(jobId)}/assets/front-matter/${encodeURIComponent(info.hodSignatureAssetId)}`,
              alt: "HOD signature",
            }),
            info.submissionDate && _jsx("div", { className: "cover-preview-date", children: `Submitted ${info.submissionDate}` }),
          ],
        }),
        _jsx("div", { className: "cover-preview-footer", children: "Generated Project Documentation" }),
      ],
    }),
  ] });
}
