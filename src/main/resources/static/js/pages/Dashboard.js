import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";

export default function Dashboard({ onNav, onResume, state }) {
  const jobs = state.jobs || [];
  const recent = jobs.slice(0, 5);
  const usageLimit = state.usageSummary?.monthlyLimit;
  const usageLabel =
    usageLimit < 0
      ? `${state.usageCount} / Unlimited`
      : `${state.usageCount} / ${usageLimit ?? "-"}`;
  const welcomeName = state.user?.name ? `, ${state.user.name}` : "";
  const metrics = [
    ["Projects", jobs.length],
    ["Completed", jobs.filter((job) => job.status === "COMPLETED").length],
    ["Usage", usageLabel],
  ];

  return _jsxs("div", {
    className: "dashboard-page",
    style: { padding: 32 },
    children: [
      _jsxs("div", {
        style: { marginBottom: 24 },
        children: [
          _jsx("h1", {
            style: { fontSize: 24, fontWeight: 800, margin: "0 0 5px" },
            children: `Welcome${welcomeName}`,
          }),
          _jsx("p", {
            style: { fontSize: 14, color: "var(--text-secondary)" },
            children:
              "Manage your documentation projects and continue unfinished jobs.",
          }),
        ],
      }),
      _jsx("div", {
        className: "dashboard-metrics",
        style: {
          display: "grid",
          gridTemplateColumns: "repeat(3,1fr)",
          gap: 14,
          marginBottom: 22,
        },
        children: metrics.map(([label, value]) =>
          _jsxs(
            "div",
            {
              className: "dashboard-metric-card",
              style: {
                background: "white",
                border: "1px solid var(--border)",
                borderRadius: 14,
                padding: 18,
              },
              children: [
                _jsx("div", {
                  style: { fontSize: 26, fontWeight: 900 },
                  children: value,
                }),
                _jsx("div", {
                  style: {
                    fontSize: 12,
                    color: "var(--text-muted)",
                    marginTop: 4,
                  },
                  children: label,
                }),
              ],
            },
            label,
          ),
        ),
      }),
      _jsxs("div", {
        className: "dashboard-recent-card",
        style: {
          background: "white",
          border: "1px solid var(--border)",
          borderRadius: 14,
          padding: 20,
        },
        children: [
          _jsxs("div", {
            style: {
              display: "flex",
              justifyContent: "space-between",
              marginBottom: 12,
            },
            children: [
              _jsx("strong", { children: "Recent Projects" }),
              _jsx("button", {
                onClick: () => onNav("my-projects"),
                style: {
                  background: "none",
                  border: 0,
                  color: "var(--primary)",
                  fontWeight: 600,
                },
                children: "View all",
              }),
            ],
          }),
          recent.length
            ? recent.map((job) =>
                _jsxs(
                  "div",
                  {
                    className: "dashboard-project-row",
                    style: {
                      display: "flex",
                      justifyContent: "space-between",
                      padding: "11px 0",
                      borderTop: "1px solid #F1F5F9",
                    },
                    children: [
                      _jsx("span", {
                        style: { fontWeight: 600 },
                        children: job.projectName || job.jobId,
                      }),
                      _jsxs("div", {
                        style: {
                          display: "flex",
                          alignItems: "center",
                          gap: 10,
                          fontSize: 12,
                          color: "var(--text-secondary)",
                        },
                        children: [
                          _jsxs("span", {
                            children: [
                              job.status,
                              " - ",
                              Number(job.progress || 0),
                              "%",
                            ],
                          }),
                          _jsx("button", {
                            type: "button",
                            onClick: () => onResume?.(job.jobId),
                            style: {
                              padding: "6px 10px",
                              border: "1px solid var(--border)",
                              borderRadius: 7,
                              background: "white",
                              color: "var(--primary)",
                              fontWeight: 700,
                              cursor: "pointer",
                            },
                            children: [
                              "ANALYZING_PROJECT",
                              "INDEXING_PROJECT",
                              "WAITING_FOR_INDEXING",
                              "RECOVERING_INDEXING",
                              "WAITING_FOR_USER_CONFIGURATION",
                              "QUEUED_FOR_GENERATION",
                              "GENERATING_DOCUMENTATION",
                              "VALIDATING_ASSETS",
                              "ASSEMBLING_DOCUMENT",
                              "VALIDATING_DOCUMENT",
                              "PREPARING_PDF",
                              "RECOVERING",
                            ].includes(job.status)
                              ? "Resume"
                              : "Open",
                          }),
                        ],
                      }),
                    ],
                  },
                  job.jobId,
                ),
              )
            : _jsx("div", {
                style: {
                  padding: 28,
                  textAlign: "center",
                  color: "var(--text-muted)",
                },
                children: "No projects yet.",
              }),
        ],
      }),
      _jsx("button", {
        onClick: () => onNav("add-project"),
        style: {
          marginTop: 16,
          padding: "11px 20px",
          background: "var(--primary)",
          color: "white",
          border: 0,
          borderRadius: 9,
          fontWeight: 700,
        },
        children: "Create Documentation",
      }),
    ],
  });
}
