import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { useState } from "react";
import { api } from "../api.js";
import CoverPage from "../components/CoverPage.js";

function Section({ title, children }) {
  return _jsxs("section", { className: "student-details-section", children: [
    _jsx("h2", { children: title }),
    _jsx("div", { className: "student-details-section-content", children }),
  ] });
}

function Field({ label, required = false, children }) {
  return _jsxs("label", { className: "student-details-field", children: [
    _jsxs("span", { children: [label, required && _jsx("b", { children: "*" })] }),
    children,
  ] });
}

function Input({ value, onChange, placeholder, type = "text" }) {
  return _jsx("input", {
    type,
    value: value || "",
    onChange: (event) => onChange(event.target.value),
    placeholder,
    className: "input-base",
  });
}

function Grid({ children }) {
  return _jsx("div", { className: "student-details-grid", children });
}

function Icon({ path }) {
  return _jsx("svg", {
    width: 14,
    height: 14,
    viewBox: "0 0 24 24",
    fill: "none",
    stroke: "currentColor",
    strokeWidth: 2.5,
    strokeLinecap: "round",
    strokeLinejoin: "round",
    children: _jsx("path", { d: path }),
  });
}

export default function StudentDetails({ onNav, state, onState }) {
  const [info, setInfo] = useState(state.studentInfo || {});
  const [assetBusy, setAssetBusy] = useState("");
  const [assetError, setAssetError] = useState("");

  const updateInfo = (update) => {
    const next = typeof update === "function" ? update(info) : { ...info, ...update };
    setInfo(next);
    onState({ studentInfo: next });
  };

  const set = (key, value) => updateInfo((previous) => ({ ...previous, [key]: value }));

  const setCoverLayout = (coverLayout) => {
    updateInfo((previous) => ({ ...previous, coverLayout }));
  };

  const addMember = () => updateInfo((previous) => ({
    ...previous,
    teamMembers: [...(previous.teamMembers || []), { name: "", roll: "" }],
  }));

  const setMember = (index, key, value) => updateInfo((previous) => ({
    ...previous,
    teamMembers: (previous.teamMembers || []).map((member, current) =>
      current === index ? { ...member, [key]: value } : member),
  }));

  const removeMember = (index) => updateInfo((previous) => ({
    ...previous,
    teamMembers: (previous.teamMembers || []).filter((_, current) => current !== index),
  }));

  const uploadFrontMatter = async (field, file) => {
    if (!file || !state.currentProjectId) return;
    setAssetError("");
    setAssetBusy(field);
    try {
      const asset = await api.documentation.uploadFrontMatter(state.currentProjectId, field, file);
      const property = {
        collegeLogo: "collegeLogoAssetId",
        guideSignature: "guideSignatureAssetId",
        hodSignature: "hodSignatureAssetId",
      }[field];
      updateInfo((previous) => ({ ...previous, [property]: asset.assetId }));
    } catch (error) {
      setAssetError(error.message || "Unable to upload asset.");
    } finally {
      setAssetBusy("");
    }
  };

  const handleContinue = () => {
    const required = [
      ["projectTitle", "Project title"],
      ["course", "Course / Degree"],
      ["department", "Department"],
      ["academicYear", "Academic year"],
      ["studentName", "Student name"],
      ["rollNumber", "Roll number"],
      ["collegeName", "College name"],
      ["universityName", "University name"],
      ["guideName", "Guide name"],
    ];
    const missing = required.find(([key]) => !String(info[key] || "").trim());
    if (missing) {
      setAssetError(`${missing[1]} is required.`);
      return;
    }
    setAssetError("");
    onState({ studentInfo: info });
    onNav("configure-sections");
  };

  const assetField = (label, field, assetIdKey) => _jsx(Field, {
    label,
    children: _jsxs("div", { className: "student-asset-control", children: [
      _jsx("input", {
        type: "file",
        accept: "image/png,image/jpeg",
        disabled: !!assetBusy || !state.currentProjectId,
        onChange: (event) => uploadFrontMatter(field, event.target.files?.[0]),
        className: "input-base",
      }),
      info[assetIdKey] && _jsx("small", { children: `Uploaded: ${info[assetIdKey]}` }),
    ] }),
  }, field);

  return _jsxs("div", { className: "student-details-layout", children: [
    _jsxs("header", { className: "student-details-header", children: [
      _jsx("h1", { children: "Project & Student Details" }),
      _jsx("p", { children: "Add your college and submission details, then arrange the cover to match your institution's format." }),
    ] }),
    _jsxs("div", { className: "student-details-form", children: [
      _jsxs(Section, { title: "Project Details", children: [
        _jsx(Field, { label: "Project Title", required: true, children: _jsx(Input, { value: info.projectTitle, onChange: (value) => set("projectTitle", value), placeholder: "e.g. Student Management System" }) }),
        _jsxs(Grid, { children: [
          _jsx(Field, { label: "Course / Degree", required: true, children: _jsx(Input, { value: info.course, onChange: (value) => set("course", value), placeholder: "e.g. B.E. Computer Science" }) }),
          _jsx(Field, { label: "Department", required: true, children: _jsx(Input, { value: info.department, onChange: (value) => set("department", value), placeholder: "e.g. Computer Science & Engineering" }) }),
          _jsx(Field, { label: "Academic Year", required: true, children: _jsx(Input, { value: info.academicYear, onChange: (value) => set("academicYear", value), placeholder: "e.g. 2026–27" }) }),
          _jsx(Field, { label: "Submission Date", children: _jsx(Input, { value: info.submissionDate, onChange: (value) => set("submissionDate", value), type: "date" }) }),
        ] }),
      ] }),
      _jsxs(Section, { title: "Student Information", children: [
        _jsxs(Grid, { children: [
          _jsx(Field, { label: "Student Name", required: true, children: _jsx(Input, { value: info.studentName, onChange: (value) => set("studentName", value), placeholder: "Full name" }) }),
          _jsx(Field, { label: "Roll Number", required: true, children: _jsx(Input, { value: info.rollNumber, onChange: (value) => set("rollNumber", value), placeholder: "e.g. CS2026001" }) }),
          _jsx(Field, { label: "Enrollment Number", children: _jsx(Input, { value: info.enrollmentNumber, onChange: (value) => set("enrollmentNumber", value), placeholder: "University enrollment number" }) }),
        ] }),
        _jsxs("div", { className: "student-team-members", children: [
          _jsxs("div", { className: "student-team-heading", children: [
            _jsx("strong", { children: "Team Members" }),
            _jsx("button", { type: "button", onClick: addMember, children: "+ Add Member" }),
          ] }),
          (info.teamMembers || []).map((member, index) => _jsxs("div", { className: "student-team-row", children: [
            _jsx(Input, { value: member.name, onChange: (value) => setMember(index, "name", value), placeholder: `Team member ${index + 1} name` }),
            _jsx("button", { type: "button", "aria-label": "Remove team member", onClick: () => removeMember(index), children: "×" }),
          ] }, index)),
          !(info.teamMembers || []).length && _jsx("small", { children: "No additional team members added." }),
        ] }),
      ] }),
      _jsxs(Section, { title: "Institution Details", children: [
        _jsxs(Grid, { children: [
          _jsx(Field, { label: "College Name", required: true, children: _jsx(Input, { value: info.collegeName, onChange: (value) => set("collegeName", value), placeholder: "e.g. Government Engineering College" }) }),
          _jsx(Field, { label: "University Name", required: true, children: _jsx(Input, { value: info.universityName, onChange: (value) => set("universityName", value), placeholder: "e.g. Gujarat Technological University" }) }),
        ] }),
      ] }),
      _jsxs(Section, { title: "Project Guide", children: [
        _jsxs(Grid, { children: [
          _jsx(Field, { label: "Guide Name", required: true, children: _jsx(Input, { value: info.guideName, onChange: (value) => set("guideName", value), placeholder: "e.g. Prof. Rahul Sharma" }) }),
          _jsx(Field, { label: "Guide Designation", children: _jsx(Input, { value: info.guideDesignation, onChange: (value) => set("guideDesignation", value), placeholder: "e.g. Assistant Professor" }) }),
        ] }),
      ] }),
      _jsxs(Section, { title: "Head of Department", children: [
        _jsxs(Grid, { children: [
          _jsx(Field, { label: "HOD Name", children: _jsx(Input, { value: info.hodName, onChange: (value) => set("hodName", value), placeholder: "e.g. Dr. Priya Patel" }) }),
          _jsx(Field, { label: "HOD Designation", children: _jsx(Input, { value: info.hodDesignation, onChange: (value) => set("hodDesignation", value), placeholder: "e.g. Professor & Head" }) }),
        ] }),
      ] }),
      _jsxs(Section, { title: "Logos and Signatures", children: [
        _jsx("p", { className: "student-assets-note", children: "Optional images are included on the generated cover or front matter. You can crop and position the college logo in the preview." }),
        _jsxs(Grid, { children: [
          assetField("College Logo", "collegeLogo", "collegeLogoAssetId"),
          assetField("Guide Signature", "guideSignature", "guideSignatureAssetId"),
          assetField("HOD Signature", "hodSignature", "hodSignatureAssetId"),
        ] }),
        assetBusy && _jsx("p", { className: "student-asset-status", children: `Uploading ${assetBusy}...` }),
        assetError && _jsx("p", { className: "student-asset-error", children: assetError }),
      ] }),
      _jsxs("div", { className: "student-details-actions", children: [
        _jsx("button", { type: "button", className: "student-back-button", onClick: () => onNav("template-recommendation"), children: "← Back to Template" }),
        _jsxs("button", { type: "button", className: "student-continue-button", onClick: handleContinue, children: ["Continue to Sections", _jsx(Icon, { path: "M5 12h14M12 5l7 7-7 7" })] }),
      ] }),
    ] }),
    _jsxs("aside", { className: "student-details-preview", children: [
      _jsx(CoverPage, {
        info,
        projectName: state.projectName,
        jobId: state.currentProjectId,
        editable: true,
        onLayoutChange: setCoverLayout,
      }),
      _jsx("p", { className: "student-preview-note", children: "This cover updates as you type. Drag the title, logo, or details; use the controls to resize and crop the logo." }),
    ] }),
  ] });
}
