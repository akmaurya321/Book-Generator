import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { useState } from "react";
import { api } from "../api.js";

function hasAncestor(section, ancestorId, sectionsById) {
  let parentId = section.parentId;
  while (parentId) {
    if (parentId === ancestorId) return true;
    parentId = sectionsById.get(parentId)?.parentId;
  }
  return false;
}

export default function ConfigureSections({ onNav, state, onState }) {
  const [sections, setSections] = useState(state.sections || []);
  const [uploadingSectionId, setUploadingSectionId] = useState("");
  const [uploadError, setUploadError] = useState("");
  const sectionsById = new Map(sections.map((section) => [section.id, section]));

  const toggle = (id) => {
    setSections((current) => {
      const currentById = new Map(current.map((section) => [section.id, section]));
      const target = currentById.get(id);
      if (!target) return current;
      if (target.enabled) {
        return current.map((section) =>
          section.id === id || hasAncestor(section, id, currentById)
            ? { ...section, enabled: false }
            : section,
        );
      }
      const enabledIds = new Set([id]);
      let parentId = target.parentId;
      while (parentId) {
        enabledIds.add(parentId);
        parentId = currentById.get(parentId)?.parentId;
      }
      return current.map((section) =>
        enabledIds.has(section.id) ? { ...section, enabled: true } : section,
      );
    });
  };

  const uploadImages = async (sectionId, files) => {
    if (!state.currentProjectId || files.length === 0) return;
    setUploadingSectionId(sectionId);
    setUploadError("");
    try {
      for (const file of files) {
        const asset = await api.documentation.uploadImage(
          state.currentProjectId,
          sectionId,
          file,
        );
        if (!asset?.assetId) {
          throw new Error(`The server did not return an asset ID for ${file.name}.`);
        }
        setSections((current) =>
          current.map((section) =>
            section.id === sectionId
              ? {
                  ...section,
                  imageEnabled: true,
                  imageIds: [...(section.imageIds || []), asset.assetId],
                  imageNames: [...(section.imageNames || []), asset.originalFilename || file.name],
                }
              : section,
          ),
        );
      }
    } catch (error) {
      setUploadError(error.message || "Unable to upload the selected image.");
    } finally {
      setUploadingSectionId("");
    }
  };

  const removeImage = (sectionId, assetId) => {
    setSections((current) =>
      current.map((section) => {
        if (section.id !== sectionId) return section;
        const imageIds = (section.imageIds || []).filter((id) => id !== assetId);
        const imageNames = (section.imageNames || []).filter(
          (_, index) => section.imageIds?.[index] !== assetId,
        );
        return { ...section, imageIds, imageNames, imageEnabled: imageIds.length > 0 };
      }),
    );
  };

  const continueNext = () => {
    onState({ sections });
    onNav("additional-info");
  };

  return _jsxs("div", {
    style: { padding: 24, maxWidth: 1050 },
    children: [
      _jsx("h1", {
        style: {
          fontSize: 22,
          fontWeight: 800,
          color: "var(--text-primary)",
          margin: "0 0 6px",
        },
        children: "Configure Sections",
      }),
      _jsx("p", {
        style: {
          fontSize: 14.5,
          color: "var(--text-secondary)",
          margin: "0 0 18px",
        },
        children:
          "Choose the chapters and sections to include. Selecting a subsection also selects its parent chapter; unrelated dependency chapters stay optional.",
      }),
      _jsxs("div", {
        style: {
          background: "white",
          border: "1px solid var(--border)",
          borderRadius: 14,
          padding: 20,
        },
        children: [
          sections.map((section) =>
            _jsxs(
              "div",
              {
                style: {
                  padding: "11px 8px",
                  borderBottom: "1px solid var(--border)",
                },
                children: [
                  _jsxs("label", {
                    style: {
                      display: "flex",
                      alignItems: "flex-start",
                      gap: 10,
                      cursor: "pointer",
                    },
                    children: [
                      _jsx("input", {
                        type: "checkbox",
                        checked: !!section.enabled,
                        onChange: () => toggle(section.id),
                      }),
                      _jsxs("span", {
                        children: [
                          _jsx("strong", { children: section.title }),
                          section.description &&
                            _jsx("small", {
                              style: {
                                display: "block",
                                color: "var(--text-secondary)",
                                marginTop: 3,
                              },
                              children: section.description,
                            }),
                        ],
                      }),
                    ],
                  }),
                  section.enabled &&
                    _jsxs("div", {
                      style: {
                        display: "flex",
                        flexDirection: "column",
                        alignItems: "flex-start",
                        gap: 8,
                        margin: "10px 0 0 28px",
                      },
                      children: [
                        section.diagramType &&
                          _jsxs("label", {
                            style: {
                              display: "flex",
                              alignItems: "center",
                              gap: 8,
                              color: "var(--text-secondary)",
                              fontSize: 13,
                              cursor: "pointer",
                            },
                            children: [
                              _jsx("input", {
                                type: "checkbox",
                                checked: !!section.diagramEnabled,
                                onChange: () =>
                                  setSections((current) =>
                                    current.map((item) =>
                                      item.id === section.id
                                        ? { ...item, diagramEnabled: !item.diagramEnabled }
                                        : item,
                                    ),
                                  ),
                              }),
                              `Generate ${section.diagramType.replaceAll("_", " ")} diagram`,
                            ],
                          }),
                        _jsxs("label", {
                          style: {
                            display: "flex",
                            alignItems: "center",
                            gap: 8,
                            color: "var(--text-secondary)",
                            fontSize: 13,
                            cursor: state.currentProjectId ? "pointer" : "not-allowed",
                          },
                          children: [
                            "Attach image(s): ",
                            _jsx("input", {
                              type: "file",
                              accept: "image/png,image/jpeg",
                              multiple: true,
                              disabled: !state.currentProjectId || Boolean(uploadingSectionId),
                              onChange: (event) => {
                                const files = Array.from(event.target.files || []);
                                event.target.value = "";
                                uploadImages(section.id, files);
                              },
                            }),
                            uploadingSectionId === section.id && _jsx("span", { children: "Uploading…" }),
                          ],
                        }),
                        (section.imageIds || []).length > 0 &&
                          _jsxs("ul", {
                            style: { margin: 0, paddingLeft: 20, color: "var(--text-secondary)", fontSize: 12 },
                            children: (section.imageIds || []).map((assetId, index) =>
                              _jsxs("li", {
                                children: [
                                  section.imageNames?.[index] || assetId,
                                  " ",
                                  _jsx("button", {
                                    type: "button",
                                    onClick: () => removeImage(section.id, assetId),
                                    children: "Remove",
                                  }),
                                ],
                              }, assetId),
                            ),
                          }),
                      ],
                    }),
                ],
              },
              section.id,
            ),
          ),
          uploadError &&
            _jsx("p", {
              role: "alert",
              style: { color: "var(--danger, #b42318)", marginTop: 12 },
              children: uploadError,
            }),
          _jsxs("div", {
            style: {
              display: "flex",
              justifyContent: "space-between",
              marginTop: 20,
            },
            children: [
              _jsx("button", {
                onClick: () => onNav("student-details"),
                style: {
                  padding: "10px 20px",
                  background: "white",
                  border: "1px solid var(--border)",
                  borderRadius: 9,
                },
                children: "Back",
              }),
              _jsx("button", {
                onClick: continueNext,
                disabled:
                  uploadingSectionId !== "" ||
                  !sections.some((section) => section.enabled),
                style: {
                  padding: "10px 24px",
                  background: "var(--primary)",
                  border: "none",
                  borderRadius: 9,
                  color: "white",
                  fontWeight: 700,
                },
                children: "Continue",
              }),
            ],
          }),
        ],
      }),
    ],
  });
}
