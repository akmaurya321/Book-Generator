import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { useEffect, useState } from "react";
import { api } from "../api.js";

const shellStyle = { minHeight: "100vh", background: "#F8FAFC", color: "#172033", fontFamily: "Inter, system-ui, sans-serif" };
const buttonStyle = { border: 0, borderRadius: 9, background: "#E85D35", color: "#fff", padding: "11px 16px", fontWeight: 700, cursor: "pointer" };

function listingSlugFromLocation() {
  const route = window.location.hash.slice(1);
  return route.startsWith("marketplace/project/")
    ? decodeURIComponent(route.slice("marketplace/project/".length))
    : "";
}

function categoryFromLocation() {
  const route = window.location.hash.slice(1);
  if (!route.startsWith("marketplace/category/")) return "";
  return decodeURIComponent(route.slice("marketplace/category/".length));
}

function FileActions({ listing }) {
  return _jsxs("div", {
    style: { display: "flex", gap: 10, flexWrap: "wrap", marginTop: 18 },
    children: [
      listing.projectAvailable && _jsx("a", {
        href: api.marketplace.downloadUrl(listing.slug, "project"),
        style: buttonStyle,
        children: "Free project download",
      }),
      listing.documentAvailable && _jsx("a", {
        href: api.marketplace.downloadUrl(listing.slug, "documentation"),
        style: { ...buttonStyle, background: "#172033" },
        children: "Free documentation download",
      }),
    ],
  });
}

export default function Marketplace({ onNav, state }) {
  const [items, setItems] = useState([]);
  const [categories, setCategories] = useState([]);
  const [query, setQuery] = useState("");
  const [category, setCategory] = useState(categoryFromLocation);
  const [originType, setOriginType] = useState("");
  const [currentPage, setCurrentPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [selectedSlug, setSelectedSlug] = useState(listingSlugFromLocation);
  const [selected, setSelected] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    Promise.all([api.marketplace.browse({ category: categoryFromLocation(), originType }), api.marketplace.categories()])
      .then(([result, categoryList]) => {
        if (!active) return;
        setItems(result.items || []);
        setCurrentPage(result.page || 0);
        setTotalPages(result.totalPages || 0);
        setCategories(categoryList || []);
        setError("");
      })
      .catch((loadError) => active && setError(loadError.message || "Marketplace could not load."))
      .finally(() => active && setLoading(false));
    return () => { active = false; };
  }, []);

  useEffect(() => {
    const syncRoute = () => {
      const slug = listingSlugFromLocation();
      const routeCategory = categoryFromLocation();
      setSelectedSlug(slug);
      setCategory(routeCategory);
      if (!slug) {
        api.marketplace.browse({ query, category: routeCategory, originType })
          .then(result => {
            setItems(result.items || []);
            setCurrentPage(result.page || 0);
            setTotalPages(result.totalPages || 0);
          })
          .catch(loadError => setError(loadError.message || "Marketplace could not load."));
      }
    };
    window.addEventListener("popstate", syncRoute);
    window.addEventListener("hashchange", syncRoute);
    return () => {
      window.removeEventListener("popstate", syncRoute);
      window.removeEventListener("hashchange", syncRoute);
    };
  }, [query, originType]);

  useEffect(() => {
    let active = true;
    if (!selectedSlug) {
      setSelected(null);
      return () => { active = false; };
    }
    setSelected({ slug: selectedSlug, loading: true });
    api.marketplace.listing(selectedSlug)
      .then(item => active && setSelected(item))
      .catch(loadError => active && setSelected({ slug: selectedSlug, error: loadError.message || "Listing could not load." }));
    return () => { active = false; };
  }, [selectedSlug]);

  const search = async (event, nextCategory = category, nextPage = 0, nextQuery = query, nextOrigin = originType) => {
    event?.preventDefault();
    setLoading(true);
    setError("");
    try {
      const result = await api.marketplace.browse({ query: nextQuery, category: nextCategory, originType: nextOrigin, page: nextPage });
      setItems(result.items || []);
      setCurrentPage(result.page || 0);
      setTotalPages(result.totalPages || 0);
    } catch (loadError) {
      setError(loadError.message || "Search failed.");
    } finally {
      setLoading(false);
    }
  };

  const openListing = (item) => {
    const url = `${window.location.pathname}${window.location.search}#marketplace/project/${encodeURIComponent(item.slug)}`;
    window.history.pushState({ page: "marketplace", slug: item.slug }, "", url);
    setSelectedSlug(item.slug);
  };
  const backToBrowse = () => {
    window.history.pushState({ page: "marketplace" }, "", `${window.location.pathname}${window.location.search}#marketplace`);
    setSelectedSlug("");
  };
  const selectCategory = (value) => {
    const route = value
      ? `marketplace/category/${encodeURIComponent(value)}`
      : "marketplace";
    window.history.pushState({ page: "marketplace", category: value }, "", `${window.location.pathname}${window.location.search}#${route}`);
    setCategory(value);
    search(undefined, value, 0);
  };
  const selectOrigin = (value) => {
    setOriginType(value);
    search(undefined, category, 0, query, value);
  };

  return _jsxs("div", {
    style: shellStyle,
    children: [
      _jsxs("header", {
        style: { height: 68, padding: "0 clamp(18px, 5vw, 72px)", display: "flex", alignItems: "center", justifyContent: "space-between", background: "#fff", borderBottom: "1px solid #E7ECF2" },
        children: [
          _jsx("button", { type: "button", onClick: () => onNav(state.user ? "dashboard" : "landing"), style: { border: 0, background: "none", fontWeight: 800, fontSize: 18, color: "#172033", cursor: "pointer" }, children: "DocGen AI" }),
          _jsxs("nav", { style: { display: "flex", gap: 10, alignItems: "center" }, children: [
            _jsx("span", { style: { fontWeight: 700, color: "#E85D35" }, children: "Marketplace" }),
            state.user
              ? _jsxs("div", { style: { display: "flex", gap: 8, flexWrap: "wrap", justifyContent: "flex-end" }, children: [
                _jsx("button", { type: "button", onClick: () => onNav("marketplace-listings"), style: { ...buttonStyle, background: "#172033" }, children: "My listings" }),
                (state.user.roles || []).includes("ROLE_ADMIN") && _jsx("button", { type: "button", onClick: () => onNav("marketplace-moderation"), style: { ...buttonStyle, background: "#475569" }, children: "Review queue" }),
                _jsx("button", { type: "button", onClick: () => onNav("marketplace-sell"), style: buttonStyle, children: "Share your work" }),
              ] })
              : _jsx("button", { type: "button", onClick: () => onNav("marketplace-sell"), style: buttonStyle, children: "Sign in to share" }),
          ] }),
        ],
      }),
      _jsxs("main", {
        style: { maxWidth: 1180, margin: "0 auto", padding: "42px 22px 80px" },
        children: [
          selected
            ? _jsxs("section", {
                style: { maxWidth: 760, margin: "12px auto", padding: 28, background: "#fff", border: "1px solid #E7ECF2", borderRadius: 18 },
                children: [
                  _jsx("button", { type: "button", onClick: backToBrowse, style: { background: "none", border: 0, color: "#E85D35", cursor: "pointer", fontWeight: 700 }, children: "← Back to Marketplace" }),
                  selected.loading ? _jsx("p", { children: "Loading listing…" }) : _jsxs("div", { children: [
                    _jsx("h1", { style: { fontSize: 30, marginBottom: 8 }, children: selected.title }),
                    _jsxs("p", { style: { color: "#64748B" }, children: [selected.category, " · ", selected.listingType?.replaceAll("_", " "), " · Shared by ", selected.sellerName] }),
                    _jsx("p", { style: { lineHeight: 1.7, whiteSpace: "pre-wrap" }, children: selected.description }),
                    selected.technologies && _jsx("p", { style: { color: "#64748B" }, children: `Technologies: ${selected.technologies}` }),
                    _jsx("p", { style: { background: "#F8FAFC", borderRadius: 10, padding: 16, lineHeight: 1.6 }, children: selected.previewText }),
                    selected.error && _jsx("p", { role: "alert", style: { color: "#DC2626" }, children: selected.error }),
                    _jsx(FileActions, { listing: selected }),
                  ] }),
                ],
              })
            : _jsxs("div", { children: [
                _jsx("div", { style: { maxWidth: 750, margin: "0 auto 28px", textAlign: "center" }, children: [
                  _jsx("p", { style: { color: "#E85D35", fontWeight: 800, letterSpacing: 1.3, textTransform: "uppercase" }, children: "Free project sharing" }),
                  _jsx("h1", { style: { fontSize: "clamp(34px, 5vw, 54px)", lineHeight: 1.08, margin: "10px 0 14px" }, children: "Discover projects. Learn by building." }),
                  _jsx("p", { style: { color: "#64748B", fontSize: 17, lineHeight: 1.65 }, children: "Browse community projects and documentation. Every published resource is free to explore and download." }),
                ] }),
                _jsxs("form", { onSubmit: search, style: { display: "flex", gap: 10, maxWidth: 850, margin: "0 auto 18px", flexWrap: "wrap" }, children: [
                  _jsx("input", { value: query, onChange: event => setQuery(event.target.value), placeholder: "Search projects, technologies, documentation…", style: { flex: "1 1 320px", minWidth: 200, padding: "13px 15px", border: "1px solid #CBD5E1", borderRadius: 9, fontSize: 15 } }),
                  _jsx("button", { type: "submit", style: buttonStyle, children: "Search" }),
                ] }),
                _jsxs("div", { style: { display: "flex", justifyContent: "center", gap: 8, flexWrap: "wrap", marginBottom: 30 }, children: [
                  _jsx("button", { type: "button", onClick: () => selectCategory(""), style: { ...buttonStyle, background: category ? "#fff" : "#172033", color: category ? "#172033" : "#fff", border: "1px solid #CBD5E1" }, children: "All" }),
                  categories.map(item => _jsx("button", { type: "button", onClick: () => selectCategory(item), style: { ...buttonStyle, background: category === item ? "#172033" : "#fff", color: category === item ? "#fff" : "#172033", border: "1px solid #CBD5E1" }, children: item }, item)),
                ] }),
                _jsxs("div", { "aria-label": "Filter by resource origin", style: { display: "flex", justifyContent: "center", gap: 8, flexWrap: "wrap", margin: "-14px 0 28px" }, children: [
                  _jsx("button", { type: "button", onClick: () => selectOrigin(""), style: { ...buttonStyle, background: originType ? "#fff" : "#172033", color: originType ? "#172033" : "#fff", border: "1px solid #CBD5E1" }, children: "All sources" }),
                  _jsx("button", { type: "button", onClick: () => selectOrigin("DOCGEN"), style: { ...buttonStyle, background: originType === "DOCGEN" ? "#172033" : "#fff", color: originType === "DOCGEN" ? "#fff" : "#172033", border: "1px solid #CBD5E1" }, children: "Generated with DocGen AI" }),
                  _jsx("button", { type: "button", onClick: () => selectOrigin("COMMUNITY"), style: { ...buttonStyle, background: originType === "COMMUNITY" ? "#172033" : "#fff", color: originType === "COMMUNITY" ? "#fff" : "#172033", border: "1px solid #CBD5E1" }, children: "Community uploads" }),
                ] }),
                error && _jsx("p", { role: "alert", style: { color: "#B91C1C", textAlign: "center" }, children: error }),
                loading
                  ? _jsx("p", { style: { textAlign: "center", color: "#64748B" }, children: "Loading projects…" })
                  : items.length === 0
                    ? _jsx("div", { style: { textAlign: "center", padding: 38, color: "#64748B" }, children: "No published resources yet. Be the first to share your work." })
                    : _jsx("div", { style: { display: "grid", gridTemplateColumns: "repeat(auto-fill,minmax(min(100%,300px),1fr))", gap: 18 }, children: items.map(item => _jsxs("article", {
                        style: { padding: 21, borderRadius: 15, border: "1px solid #E2E8F0", background: "#fff", display: "flex", flexDirection: "column", minHeight: 235 },
                        children: [
                          _jsxs("div", { style: { fontSize: 12, color: "#E85D35", fontWeight: 800, textTransform: "uppercase" }, children: [item.category, " · ", item.originType] }),
                          _jsx("h2", { style: { fontSize: 19, margin: "12px 0 6px" }, children: item.title }),
                          _jsx("p", { style: { fontSize: 13, color: "#64748B", margin: "0 0 10px" }, children: `By ${item.sellerName} · ${item.listingType.replaceAll("_", " ")}` }),
                          _jsx("p", { style: { color: "#475569", lineHeight: 1.55, flex: 1, margin: 0 }, children: item.previewText }),
                          _jsx("button", { type: "button", onClick: () => openListing(item), style: { ...buttonStyle, alignSelf: "flex-start", marginTop: 16 }, children: "View free resource" }),
                        ],
                      }, item.id)) }),
                totalPages > 1 && _jsxs("div", { style: { display: "flex", justifyContent: "center", alignItems: "center", gap: 12, marginTop: 26 }, children: [
                  _jsx("button", { type: "button", disabled: currentPage <= 0 || loading, onClick: () => search(undefined, category, currentPage - 1), children: "Previous" }),
                  _jsxs("span", { style: { color: "#64748B", fontSize: 13 }, children: ["Page ", currentPage + 1, " of ", totalPages] }),
                  _jsx("button", { type: "button", disabled: currentPage + 1 >= totalPages || loading, onClick: () => search(undefined, category, currentPage + 1), children: "Next" }),
                ] }),
              ] }),
        ],
      }),
    ],
  });
}
