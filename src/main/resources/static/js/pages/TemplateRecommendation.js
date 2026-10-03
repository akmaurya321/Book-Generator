import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { useState } from "react";

export default function TemplateRecommendation({ onNav, state, onState }) {
  const template = state.template;
  const [selected, setSelected] = useState(
    state.selectedTemplateId || template?.id || "",
  );
  const sections = (template?.sections || [])
    .slice()
    .sort((a, b) => Number(a.order || 0) - Number(b.order || 0));
  const useTemplate = () => {
    if (!template?.id) return;
    onState({
      selectedTemplateId: template.id,
      selectedTemplate: template.name || template.id,
    });
    onNav("student-details");
  };

  return _jsxs("div", {
    style: { padding: 32, maxWidth: 920 },
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
            },
            children: "Recommended Template",
          }),
          _jsx("p", {
            style: {
              fontSize: 14.5,
              color: "var(--text-secondary)",
              margin: 0,
            },
            children:
              "This template is supplied by the backend and remains the source of truth for your report structure and formatting.",
          }),
        ],
      }),
      _jsxs("div", {
        style: {
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          gap: 14,
          flexWrap: "wrap",
          padding: 16,
          marginBottom: 18,
          background: "#F8FAFC",
          border: "1px solid var(--border)",
          borderRadius: 12,
        },
        children: [
          _jsxs("div", {
            children: [
              _jsx("strong", { children: state.selectedCatalogTemplate?.college || "College Template Library" }),
              _jsx("div", {
                style: { color: "var(--text-secondary)", fontSize: 12, marginTop: 3 },
                children: state.selectedCatalogTemplate
                  ? `Selected published version ${state.selectedCatalogTemplate.version}`
                  : "Choose an optional published college format or analyze a private format for this project.",
              }),
            ],
          }),
          _jsx("button", {
            type: "button",
            onClick: () => onNav("templates"),
            style: {
              padding: "9px 13px",
              background: "white",
              color: "var(--primary)",
              border: "1px solid var(--primary)",
              borderRadius: 8,
              fontWeight: 700,
              cursor: "pointer",
            },
            children: "Browse Template Library",
          }),
        ],
      }),
      template
        ? _jsxs("div", {
            onClick: () => setSelected(template.id),
            style: {
              background: "white",
              border: `2px solid ${selected === template.id ? "var(--primary)" : "var(--border)"}`,
              borderRadius: 16,
              overflow: "hidden",
              maxWidth: 420,
            },
            children: [
              _jsx("div", {
                style: {
                  background: "var(--primary)",
                  color: "white",
                  fontSize: 11.5,
                  fontWeight: 800,
                  textAlign: "center",
                  padding: "6px 0",
                },
                children: "BACKEND TEMPLATE",
              }),
              _jsxs("div", {
                style: { padding: 20 },
                children: [
                  _jsx("div", {
                    style: {
                      fontWeight: 800,
                      fontSize: 16,
                      color: "var(--text-primary)",
                      marginBottom: 6,
                    },
                    children: template.name || template.id,
                  }),
                  _jsx("div", {
                    style: {
                      fontSize: 13,
                      color: "var(--text-secondary)",
                      lineHeight: 1.6,
                      marginBottom: 14,
                    },
                    children:
                      template.description ||
                      "Documentation template returned by the backend.",
                  }),
                  _jsx("div", {
                    style: {
                      fontSize: 11.5,
                      fontWeight: 700,
                      color: "var(--text-muted)",
                      textTransform: "uppercase",
                      letterSpacing: 0.8,
                      marginBottom: 7,
                    },
                    children: `${sections.length} sections`,
                  }),
                  sections.slice(0, 8).map((section) =>
                    _jsxs(
                      "div",
                      {
                        style: { display: "flex", gap: 6, marginBottom: 4 },
                        children: [
                          _jsx("span", {
                            style: { color: "var(--success)" },
                            children: "OK",
                          }),
                          _jsx("span", {
                            style: {
                              fontSize: 12.5,
                              color: "var(--text-secondary)",
                            },
                            children: section.title,
                          }),
                        ],
                      },
                      section.id,
                    ),
                  ),
                  sections.length > 8 &&
                    _jsxs("div", {
                      style: {
                        fontSize: 12,
                        color: "var(--text-muted)",
                        marginTop: 6,
                      },
                      children: ["+", sections.length - 8, " more"],
                    }),
                  _jsx("button", {
                    onClick: (event) => {
                      event.stopPropagation();
                      useTemplate();
                    },
                    style: {
                      width: "100%",
                      marginTop: 16,
                      padding: "10px 0",
                      background: "var(--primary)",
                      color: "white",
                      border: "none",
                      borderRadius: 9,
                      fontWeight: 700,
                      fontSize: 13.5,
                    },
                    children: "Enter Cover Details",
                  }),
                ],
              }),
            ],
          })
        : _jsx("div", {
            style: {
              padding: 40,
              textAlign: "center",
              color: "var(--text-muted)",
            },
            children: "Template data is not available from the backend yet.",
          }),
    ],
  });
}
