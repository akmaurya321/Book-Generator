import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { useState } from "react";

function TextArea({ value, onChange, placeholder, rows = 4 }) {
  return _jsx("textarea", {
    value,
    onChange: (event) => onChange(event.target.value),
    placeholder,
    rows,
    className: "input-base",
    style: {
      display: "block",
      width: "100%",
      boxSizing: "border-box",
      resize: "vertical",
      fontFamily: "inherit",
      lineHeight: 1.6,
    },
  });
}

function AIBanner({ suggestion, onAccept, onDismiss, onEdit }) {
  const [editValue, setEditValue] = useState(suggestion.text);
  const [isEditing, setIsEditing] = useState(false);
  if (suggestion.status === "dismissed" || suggestion.status === "accepted")
    return null;

  const neutralButton = {
    padding: "6px 12px",
    background: "white",
    color: "var(--text-secondary)",
    border: "1px solid var(--border)",
    borderRadius: 7,
    fontWeight: 600,
    fontSize: 12.5,
    cursor: "pointer",
  };
  const primaryButton = {
    ...neutralButton,
    padding: "6px 14px",
    background: "var(--primary)",
    color: "white",
    border: "1px solid var(--primary)",
  };

  return _jsxs("div", {
    style: {
      marginTop: 10,
      background: "var(--primary-light)",
      border: "1px solid var(--primary-border)",
      borderRadius: 10,
      overflow: "hidden",
    },
    children: [
      _jsxs("div", {
        style: {
          padding: "10px 14px",
          borderBottom: "1px solid var(--primary-border)",
          display: "flex",
          alignItems: "center",
          gap: 7,
          color: "var(--primary)",
        },
        children: [
          _jsx("svg", {
            width: "14",
            height: "14",
            viewBox: "0 0 24 24",
            fill: "none",
            stroke: "currentColor",
            strokeWidth: "2",
            strokeLinecap: "round",
            strokeLinejoin: "round",
            children: _jsx("path", {
              d: "M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z",
            }),
          }),
          _jsx("span", {
            style: { fontSize: 12, fontWeight: 700 },
            children: "AI Suggestion",
          }),
        ],
      }),
      _jsxs("div", {
        style: { padding: 12 },
        children: [
          isEditing
            ? _jsx("textarea", {
                value: editValue,
                onChange: (event) => setEditValue(event.target.value),
                rows: 4,
                className: "input-base",
                style: {
                  width: "100%",
                  boxSizing: "border-box",
                  resize: "vertical",
                  fontFamily: "inherit",
                  lineHeight: 1.6,
                },
              })
            : _jsx("p", {
                style: {
                  fontSize: 13.5,
                  color: "var(--text-primary)",
                  lineHeight: 1.65,
                  margin: "0 0 10px",
                  whiteSpace: "pre-line",
                },
                children: suggestion.text,
              }),
          _jsxs("div", {
            style: { display: "flex", flexWrap: "wrap", gap: 8, marginTop: isEditing ? 8 : 0 },
            children: isEditing
              ? [
                  _jsx("button", {
                    type: "button",
                    onClick: () => onEdit(editValue),
                    style: primaryButton,
                    children: "Accept Edited",
                  }),
                  _jsx("button", {
                    type: "button",
                    onClick: () => setIsEditing(false),
                    style: neutralButton,
                    children: "Back",
                  }),
                  _jsx("button", {
                    type: "button",
                    onClick: onDismiss,
                    style: neutralButton,
                    children: "Dismiss",
                  }),
                ]
              : [
                  _jsx("button", {
                    type: "button",
                    onClick: onAccept,
                    style: primaryButton,
                    children: "Accept",
                  }),
                  _jsx("button", {
                    type: "button",
                    onClick: () => setIsEditing(true),
                    style: neutralButton,
                    children: "Edit",
                  }),
                  _jsx("button", {
                    type: "button",
                    onClick: onDismiss,
                    style: neutralButton,
                    children: "Dismiss",
                  }),
                ],
          }),
        ],
      }),
    ],
  });
}

function QField({ label, hint, children }) {
  return _jsxs("div", {
    style: { marginBottom: 24 },
    children: [
      _jsx("label", {
        style: {
          display: "block",
          fontSize: 14,
          fontWeight: 700,
          color: "var(--text-primary)",
          marginBottom: 4,
        },
        children: label,
      }),
      hint &&
        _jsx("div", {
          style: { fontSize: 12.5, color: "var(--text-muted)", marginBottom: 8 },
          children: hint,
        }),
      children,
    ],
  });
}

export default function AdditionalInfo({ onNav, state, onState }) {
  const [ctx, setCtx] = useState(state.additionalContext);
  const [suggestions, setSuggestions] = useState(() => {
    const source = state.contextSuggestions || {};
    return Object.fromEntries(
      Object.entries(source)
        .filter(
          ([key, value]) =>
            key !== "source" &&
            (typeof value === "string"
              ? value.trim().length > 0
              : Array.isArray(value) && value.length > 0),
        )
        .map(([key, value]) => [
          key,
          {
            text: Array.isArray(value) ? value.join("\n") : value,
            status: "pending",
          },
        ]),
    );
  });
  const [objectivesText, setObjectivesText] = useState(
    ctx.objectives.join("\n"),
  );
  const requiredSections = (state.sections || []).filter(
    (section) => section.enabled && section.requiresAdditionalInformation,
  );
  const [additionalInformation, setAdditionalInformation] = useState(
    state.additionalInformation || {},
  );

  const setContextField = (key, value) => {
    const next = { ...ctx, [key]: value };
    setCtx(next);
    onState({ additionalContext: next });
  };
  const setObjectiveDraft = (value) => {
    setObjectivesText(value);
    setContextField(
      "objectives",
      value
        .split("\n")
        .map((line) => line.trim())
        .filter(Boolean),
    );
  };
  const acceptSuggestion = (key, value = suggestions[key].text) => {
    if (key === "objectives") setObjectiveDraft(value);
    else setContextField(key, value);
    setSuggestions((previous) => ({
      ...previous,
      [key]: { ...previous[key], status: "accepted" },
    }));
  };
  const dismissSuggestion = (key) =>
    setSuggestions((previous) => ({
      ...previous,
      [key]: { ...previous[key], status: "dismissed" },
    }));
  const handleContinue = () => {
    onState({
      additionalContext: ctx,
      additionalInformation,
    });
    onNav("final-review");
  };
  const updateAdditionalInformation = (key, value) => {
    const next = { ...additionalInformation, [key]: value };
    setAdditionalInformation(next);
    onState({ additionalInformation: next });
  };

  const suggestedField = (key, value, onChange, placeholder, rows = 4) =>
    _jsxs("div", {
      children: [
        _jsx(TextArea, { value, onChange, placeholder, rows }),
        suggestions[key] &&
          _jsx(AIBanner, {
            suggestion: suggestions[key],
            onAccept: () => acceptSuggestion(key),
            onEdit: (edited) => acceptSuggestion(key, edited),
            onDismiss: () => dismissSuggestion(key),
          }),
      ],
    });

  return _jsxs("div", {
    style: {
      padding: "28px 32px",
      maxWidth: 740,
      minHeight: "100%",
      boxSizing: "border-box",
    },
    children: [
      _jsxs("div", {
        style: { marginBottom: 26 },
        children: [
          _jsx("h1", {
            style: {
              fontSize: 22,
              fontWeight: 800,
              color: "var(--text-primary)",
              margin: "0 0 6px",
              letterSpacing: -0.3,
            },
            children: "Additional Information",
          }),
          _jsx("p", {
            style: { fontSize: 14.5, color: "var(--text-secondary)", margin: 0 },
            children:
              "Review AI suggestions for each field. Accept, edit, dismiss, or enter your own information.",
          }),
        ],
      }),
      _jsxs("div", {
        style: {
          background: "white",
          borderRadius: 16,
          border: "1px solid var(--border)",
          padding: "24px 28px",
          marginBottom: 18,
        },
        children: [
          _jsx(QField, {
            label: "Motivation / Background",
            hint: "Why was this project created? What need or problem does it solve?",
            children: suggestedField(
              "motivation",
              ctx.motivation,
              (value) => setContextField("motivation", value),
              "Describe the motivation behind this project...",
            ),
          }),
          _jsx(QField, {
            label: "Problem Statement",
            hint: "What specific problem does this project address?",
            children: suggestedField(
              "problemStatement",
              ctx.problemStatement,
              (value) => setContextField("problemStatement", value),
              "State the problem clearly...",
            ),
          }),
          _jsx(QField, {
            label: "Objectives",
            hint: "List the key goals of this project (one per line)",
            children: suggestedField(
              "objectives",
              objectivesText,
              setObjectiveDraft,
              "Implement user authentication\nCreate REST API endpoints\nBuild responsive UI...",
              5,
            ),
          }),
          _jsx(QField, {
            label: "Target Users",
            hint: "Who is the intended audience of this system?",
            children: suggestedField(
              "targetUsers",
              ctx.targetUsers,
              (value) => setContextField("targetUsers", value),
              "e.g. Students, faculty members, college administrators...",
              3,
            ),
          }),
          _jsx(QField, {
            label: "Expected Benefits",
            hint: "What improvements or outcomes does this project deliver?",
            children: suggestedField(
              "expectedBenefits",
              ctx.expectedBenefits,
              (value) => setContextField("expectedBenefits", value),
              "e.g. Reduced manual paperwork, faster data retrieval...",
              3,
            ),
          }),
          _jsx(QField, {
            label: "Limitations",
            hint: "What are the known limitations or constraints of this project?",
            children: suggestedField(
              "limitations",
              ctx.limitations,
              (value) => setContextField("limitations", value),
              "Describe known limitations or constraints...",
              3,
            ),
          }),
          _jsx(QField, {
            label: "Future Scope",
            hint: "What enhancements could be made in future versions?",
            children: suggestedField(
              "futureIdeas",
              ctx.futureIdeas,
              (value) => setContextField("futureIdeas", value),
              "e.g. Mobile app, AI-powered analytics, third-party integrations...",
              3,
            ),
          }),
          _jsx(QField, {
            label: "Additional Notes",
            hint: "Anything else you'd like to include in the documentation?",
            children: suggestedField(
              "additionalNotes",
              ctx.additionalNotes,
              (value) => setContextField("additionalNotes", value),
              "Any other relevant information...",
              3,
            ),
          }),
          requiredSections.length > 0 &&
            _jsxs("div", {
              style: {
                marginTop: 8,
                paddingTop: 20,
                borderTop: "1px solid var(--border)",
              },
              children: [
                _jsx("div", {
                  style: {
                    fontSize: 14,
                    fontWeight: 800,
                    color: "var(--text-primary)",
                    marginBottom: 5,
                  },
                  children: "Evidence Required by Selected Sections",
                }),
                _jsx("div", {
                  style: {
                    fontSize: 12.5,
                    color: "var(--text-muted)",
                    marginBottom: 14,
                  },
                  children:
                    "These fields need user-supplied, verifiable information because the selected sections require it.",
                }),
                requiredSections.map((section) =>
                  _jsx(
                    QField,
                    {
                      label: section.title,
                      hint:
                        section.description ||
                        "Provide verifiable information for this section.",
                      children: _jsx(TextArea, {
                        value: additionalInformation[section.id] || "",
                        onChange: (value) =>
                          updateAdditionalInformation(section.id, value),
                      }),
                    },
                    section.id,
                  ),
                ),
              ],
            }),
        ],
      }),
      _jsxs("div", {
        style: { display: "flex", alignItems: "center", gap: 12 },
        children: [
          _jsx("button", {
            onClick: () => onNav("configure-sections"),
            style: {
              padding: "11px 22px",
              background: "white",
              color: "var(--text-secondary)",
              border: "1.5px solid var(--border)",
              borderRadius: 10,
              fontWeight: 600,
              fontSize: 14,
              cursor: "pointer",
            },
            children: "← Back",
          }),
          _jsxs("button", {
            onClick: handleContinue,
            style: {
              padding: "11px 28px",
              background: "var(--primary)",
              color: "white",
              border: "none",
              borderRadius: 10,
              fontWeight: 700,
              fontSize: 14.5,
              cursor: "pointer",
              display: "flex",
              alignItems: "center",
              gap: 8,
            },
            children: [
              "Review & Generate",
              _jsx("svg", {
                width: "15",
                height: "15",
                viewBox: "0 0 24 24",
                fill: "none",
                stroke: "currentColor",
                strokeWidth: "2.5",
                strokeLinecap: "round",
                strokeLinejoin: "round",
                children: _jsx("path", {
                  d: "M5 12h14M12 5l7 7-7 7",
                }),
              }),
            ],
          }),
        ],
      }),
    ],
  });
}
