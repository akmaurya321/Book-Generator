import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { useEffect, useState } from "react";

const PRIMARY_ITEMS = [
  { page: "marketplace", label: "Marketplace", icon: "M3 4h18v3H3zM5 7v13h14V7M9 11h6m-6 4h6" },
  { page: "dashboard", label: "Dashboard", icon: "M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6" },
  { page: "my-projects", label: "Projects", icon: "M3 7a2 2 0 012-2h4l2 2h8a2 2 0 012 2v9a2 2 0 01-2 2H5a2 2 0 01-2-2V7z" },
  { page: "templates", label: "Templates", icon: "M4 5a1 1 0 011-1h14a1 1 0 011 1v2a1 1 0 01-1 1H5a1 1 0 01-1-1V5zM4 13a1 1 0 011-1h6a1 1 0 011 1v6a1 1 0 01-1 1H5a1 1 0 01-1-1v-6zM16 13a1 1 0 011-1h2a1 1 0 011 1v6a1 1 0 01-1 1h-2a1 1 0 01-1-1v-6z" },
];

const SECONDARY_ITEMS = [
  { page: "usage", label: "Usage", icon: "M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" },
  { page: "help", label: "Help", icon: "M8.228 9c.549-1.165 2.03-2 3.772-2 2.21 0 4 1.343 4 3 0 1.4-1.278 2.575-3.006 2.907-.542.104-.994.54-.994 1.093m0 3h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" },
  { page: "settings", label: "Settings", icon: "M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z M15 12a3 3 0 11-6 0 3 3 0 016 0z" },
];

function getActivePage(page) {
  if (
    [
      "add-project",
      "analyzing",
      "analysis-result",
      "template-recommendation",
      "template-preview",
      "configure-sections",
      "student-details",
      "additional-info",
      "final-review",
      "generating",
      "completed",
    ].includes(page)
  )
    return "add-project";
  if (page.startsWith("marketplace")) return "marketplace";
  if (page === "project-details") return "my-projects";
  return page;
}

function NavButton({ item, active, collapsed, onClick }) {
  return _jsxs("button", {
    type: "button",
    className: `sidebar-nav-button${active ? " is-active" : ""}`,
    onClick,
    title: collapsed ? item.label : undefined,
    "aria-label": item.label,
    "aria-current": active ? "page" : undefined,
    children: [
      _jsx("svg", {
        width: "18",
        height: "18",
        viewBox: "0 0 24 24",
        fill: "none",
        stroke: "currentColor",
        strokeWidth: "2",
        strokeLinecap: "round",
        strokeLinejoin: "round",
        "aria-hidden": "true",
        children: _jsx("path", { d: item.icon }),
      }),
      _jsx("span", { className: "sidebar-label", children: item.label }),
    ],
  });
}

export default function Layout({ page, state, onNav, onLogout, children }) {
  const active = getActivePage(page);
  const [sidebarOpen, setSidebarOpen] = useState(
    () => window.matchMedia("(min-width: 761px)").matches,
  );
  const [showUserMenu, setShowUserMenu] = useState(false);
  const [mobile, setMobile] = useState(
    () => window.matchMedia("(max-width: 760px)").matches,
  );
  const collapsed = !sidebarOpen && !mobile;

  useEffect(() => {
    const media = window.matchMedia("(max-width: 760px)");
    const handleChange = (event) => {
      setMobile(event.matches);
      setSidebarOpen(!event.matches);
      setShowUserMenu(false);
    };
    media.addEventListener("change", handleChange);
    return () => media.removeEventListener("change", handleChange);
  }, []);

  useEffect(() => {
    if (!mobile || !sidebarOpen) return undefined;
    const previousOverflow = document.body.style.overflow;
    const closeOnEscape = (event) => {
      if (event.key === "Escape") setSidebarOpen(false);
    };
    document.body.style.overflow = "hidden";
    window.addEventListener("keydown", closeOnEscape);
    return () => {
      document.body.style.overflow = previousOverflow;
      window.removeEventListener("keydown", closeOnEscape);
    };
  }, [mobile, sidebarOpen]);

  const navigate = (target) => {
    onNav(target);
    if (mobile) setSidebarOpen(false);
  };
  const toggleSidebar = () => {
    setSidebarOpen((open) => !open);
    setShowUserMenu(false);
  };

  const user = state.user || { name: "Account", email: "" };
  const initial = user.name?.slice(0, 1)?.toUpperCase() || "A";

  return _jsxs("div", {
    className: `app-shell${sidebarOpen ? " sidebar-is-open" : " sidebar-is-closed"}${collapsed ? " sidebar-is-collapsed" : ""}`,
    children: [
      mobile && sidebarOpen
        ? _jsx("button", {
            type: "button",
            className: "sidebar-backdrop",
            onClick: () => setSidebarOpen(false),
            "aria-label": "Close navigation menu",
          })
        : null,
      _jsxs("aside", {
        className: "app-sidebar",
        "aria-label": "Main navigation",
        children: [
          _jsx("button", {
            type: "button",
            className: "sidebar-brand",
            onClick: () => navigate("dashboard"),
            title: collapsed ? "DocGen AI" : undefined,
            children: [
              _jsx("span", {
                className: "sidebar-brand-icon",
                children: _jsx("img", {
                  src: "/assets/docgen-mark.svg",
                  alt: "",
                  width: "34",
                  height: "34",
                  "aria-hidden": "true",
                }),
              }),
              _jsxs("span", {
                className: "sidebar-brand-copy sidebar-label",
                children: [
                  _jsx("strong", { children: "DocGen AI" }),
                  _jsx("small", { children: "Documentation Generator" }),
                ],
              }),
            ],
          }),
          _jsxs("nav", {
            className: "sidebar-navigation",
            children: [
              _jsx("button", {
                type: "button",
                className: `sidebar-new-button${active === "add-project" ? " is-active" : ""}`,
                onClick: () => navigate("add-project"),
                title: collapsed ? "New Documentation" : undefined,
                "aria-label": "New Documentation",
                children: [
                  _jsx("svg", {
                    width: "17",
                    height: "17",
                    viewBox: "0 0 24 24",
                    fill: "none",
                    stroke: "currentColor",
                    strokeWidth: "2.5",
                    strokeLinecap: "round",
                    strokeLinejoin: "round",
                    "aria-hidden": "true",
                    children: _jsx("path", { d: "M12 5v14M5 12h14" }),
                  }),
                  _jsx("span", {
                    className: "sidebar-label",
                    children: "New Documentation",
                  }),
                ],
              }),
              _jsx("div", {
                className: "sidebar-group-label sidebar-label",
                children: "Workspace",
              }),
              PRIMARY_ITEMS.map((item) =>
                _jsx(
                  NavButton,
                  {
                    item,
                    active: active === item.page,
                    collapsed,
                    onClick: () => navigate(item.page),
                  },
                  item.page,
                ),
              ),
              _jsx("div", { className: "sidebar-divider" }),
              _jsx("div", {
                className: "sidebar-group-label sidebar-label",
                children: "Account",
              }),
              SECONDARY_ITEMS.map((item) =>
                _jsx(
                  NavButton,
                  {
                    item,
                    active: active === item.page,
                    collapsed,
                    onClick: () => navigate(item.page),
                  },
                  item.page,
                ),
              ),
            ],
          }),
          _jsxs("div", {
            className: "sidebar-account",
            children: [
              _jsxs("button", {
                type: "button",
                className: `sidebar-user-button${showUserMenu ? " is-open" : ""}`,
                onClick: () => setShowUserMenu((open) => !open),
                title: collapsed ? user.name : undefined,
                "aria-label": `${user.name} account menu`,
                "aria-expanded": showUserMenu,
                children: [
                  _jsx("span", {
                    className: "sidebar-avatar",
                    children: user.avatarUrl
                      ? _jsx("img", {
                          src: user.avatarUrl,
                          alt: "",
                          width: "32",
                          height: "32",
                          style: { width: "100%", height: "100%", borderRadius: "50%", objectFit: "cover" },
                        })
                      : initial,
                  }),
                  _jsxs("span", {
                    className: "sidebar-user-copy sidebar-label",
                    children: [
                      _jsx("strong", { children: user.name }),
                      _jsx("small", { children: user.email }),
                    ],
                  }),
                  _jsx("svg", {
                    className: "sidebar-user-chevron sidebar-label",
                    width: "14",
                    height: "14",
                    viewBox: "0 0 24 24",
                    fill: "none",
                    stroke: "currentColor",
                    strokeWidth: "2",
                    strokeLinecap: "round",
                    strokeLinejoin: "round",
                    "aria-hidden": "true",
                    children: _jsx("path", { d: "M6 9l6 6 6-6" }),
                  }),
                ],
              }),
              showUserMenu
                ? _jsxs("div", {
                    className: "sidebar-user-menu",
                    children: [
                      [
                        { label: "Profile", page: "settings" },
                        { label: "Usage", page: "usage" },
                        { label: "Settings", page: "settings" },
                      ].map((item) =>
                        _jsx(
                          "button",
                          {
                            type: "button",
                            onClick: () => {
                              setShowUserMenu(false);
                              navigate(item.page);
                            },
                            children: item.label,
                          },
                          item.label,
                        ),
                      ),
                      _jsx("div", { className: "sidebar-divider" }),
                      _jsx("button", {
                        type: "button",
                        className: "sidebar-sign-out",
                        onClick: () => {
                          setShowUserMenu(false);
                          onLogout?.();
                        },
                        children: "Sign Out",
                      }),
                    ],
                  })
                : null,
            ],
          }),
        ],
      }),
      _jsxs("div", {
        className: "app-main",
        children: [
          _jsxs("header", {
            className: "app-header",
            children: [
              _jsxs("div", {
                className: "app-header-brand",
                children: [
                  _jsx("button", {
                    type: "button",
                    className: "sidebar-toggle",
                    onClick: toggleSidebar,
                    "aria-label": sidebarOpen
                      ? "Collapse navigation"
                      : "Expand navigation",
                    "aria-expanded": sidebarOpen,
                    children: _jsx("span", {
                      className: "sidebar-toggle-icon",
                      "aria-hidden": "true",
                    }),
                  }),
                  _jsx("span", {
                    className: "app-header-title",
                    children: "DocGen AI",
                  }),
                  _jsx("span", {
                    className: "app-header-subtitle",
                    children: "/ College Documentation Generator",
                  }),
                ],
              }),
              _jsxs("div", {
                className: "app-header-actions",
                children: [
                  _jsxs("button", {
                    type: "button",
                    className: "app-usage-link",
                    onClick: () => navigate("usage"),
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
                        "aria-hidden": "true",
                        children: _jsx("path", {
                          d: "M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z",
                        }),
                      }),
                      _jsx("span", { children: `${state.usageCount || 0} used` }),
                    ],
                  }),
                  _jsx("span", {
                    className: "app-header-avatar",
                    title: user.name,
                    children: user.avatarUrl
                      ? _jsx("img", {
                          src: user.avatarUrl,
                          alt: "",
                          width: "32",
                          height: "32",
                          style: { width: "100%", height: "100%", borderRadius: "50%", objectFit: "cover" },
                        })
                      : initial,
                  }),
                ],
              }),
            ],
          }),
          _jsx("main", {
            className: `page-content app-page-content route-${page}`,
            children,
          }),
        ],
      }),
    ],
  });
}
