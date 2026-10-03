import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { useEffect, useState } from "react";
import { api } from "../api.js";

export default function MarketplaceListings({ onNav, state, mode = "seller" }) {
  const [items, setItems] = useState([]);
  const [queue, setQueue] = useState([]);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState("");
  const [loading, setLoading] = useState(true);
  const isAdmin = (state.user?.roles || []).includes("ROLE_ADMIN");
  const moderationPage = mode === "moderation";

  const load = () => {
    setLoading(true);
    const ownRequest = moderationPage ? Promise.resolve([]) : api.marketplace.ownListings();
    const queueRequest = isAdmin ? api.marketplace.moderationQueue() : Promise.resolve([]);
    return Promise.all([ownRequest, queueRequest])
      .then(([mine, pending]) => { setItems(mine); setQueue(pending); })
      .catch(loadError => setError(loadError.message || "Listings could not be loaded."))
      .finally(() => setLoading(false));
  };

  useEffect(() => { load(); }, [mode, isAdmin]);

  const decide = async (listing, approve) => {
    setBusy(listing.id); setError("");
    try {
      const reason = approve ? "" : window.prompt("Reason for rejection:");
      if (!approve && !reason?.trim()) return;
      await api.marketplace.moderate(listing.id, { approve, reason });
      await load();
    } catch (moderationError) {
      setError(moderationError.message || "Moderation failed.");
    } finally {
      setBusy("");
    }
  };

  const manage = async (listing, action) => {
    setBusy(listing.id); setError("");
    try {
      if (action === "publish") await api.marketplace.publishApproved(listing.id);
      if (action === "archive") await api.marketplace.archive(listing.id);
      if (action === "suspend") {
        const reason = window.prompt("Reason for suspending this public listing:");
        if (!reason?.trim()) return;
        await api.marketplace.suspend(listing.id, reason);
      }
      if (action === "edit") {
        const title = window.prompt("Listing title:", listing.title);
        if (!title?.trim()) return;
        const description = window.prompt("Listing description:", listing.description);
        if (!description?.trim()) return;
        const category = window.prompt("Category:", listing.category);
        if (!category?.trim()) return;
        const technologies = window.prompt("Technologies:", listing.technologies || "");
        await api.marketplace.update(listing.id, { title, description, category, technologies });
      }
      await load();
    } catch (manageError) {
      setError(manageError.message || "Listing update failed.");
    } finally {
      setBusy("");
    }
  };

  const card = (item, moderation = false) => _jsxs("article", { style: { padding: 18, borderRadius: 12, border: "1px solid #E2E8F0", marginBottom: 12, background: "#fff" }, children: [
    _jsxs("div", { style: { display: "flex", justifyContent: "space-between", gap: 10, flexWrap: "wrap" }, children: [
      _jsx("strong", { children: item.title }),
      _jsx("span", { style: { color: item.status === "PUBLISHED" ? "#047857" : "#B45309", fontSize: 13, fontWeight: 700 }, children: item.status.replaceAll("_", " ") }),
    ] }),
    _jsxs("p", { style: { color: "#64748B", fontSize: 13, margin: "8px 0" }, children: [
      item.originType === "DOCGEN" ? "Generated with DocGen AI" : "Community upload",
      " · ", item.listingType?.replaceAll("_", " "),
      moderation && item.sellerName ? ` · Submitted by ${item.sellerName}` : "",
    ] }),
    _jsx("p", { style: { color: "#64748B", lineHeight: 1.5 }, children: item.description }),
    item.rejectionReason && _jsx("p", { style: { color: "#B91C1C" }, children: `Review note: ${item.rejectionReason}` }),
    !moderation && _jsxs("div", { style: { display: "flex", flexWrap: "wrap", gap: 8 }, children: [
      item.status === "APPROVED" && _jsx("button", { type: "button", disabled: !!busy, onClick: () => manage(item, "publish"), children: busy === item.id ? "Saving…" : "Publish now" }),
      ["REJECTED", "PUBLISHED"].includes(item.status) && _jsx("button", { type: "button", disabled: !!busy, onClick: () => manage(item, "edit"), children: "Edit listing" }),
      item.status === "PUBLISHED" && _jsx("button", { type: "button", disabled: !!busy, onClick: () => manage(item, "archive"), children: "Archive" }),
      isAdmin && item.status === "PUBLISHED" && _jsx("button", { type: "button", disabled: !!busy, onClick: () => manage(item, "suspend"), children: "Suspend" }),
    ] }),
    moderation && _jsxs("div", { style: { display: "flex", gap: 8 }, children: [
      _jsx("button", { type: "button", disabled: !!busy, onClick: () => decide(item, true), children: busy === item.id ? "Saving…" : "Approve for seller to publish" }),
      _jsx("button", { type: "button", disabled: !!busy, onClick: () => decide(item, false), children: "Reject" }),
    ] }),
  ] }, item.id);

  return _jsxs("main", { style: { minHeight: "100vh", background: "#F8FAFC", padding: "32px 18px", color: "#172033", fontFamily: "Inter, system-ui, sans-serif" }, children: [
    _jsx("button", { type: "button", onClick: () => onNav("marketplace"), style: { border: 0, background: "none", color: "#E85D35", cursor: "pointer", fontWeight: 700 }, children: "← Marketplace" }),
    _jsxs("div", { style: { maxWidth: 900, margin: "20px auto" }, children: [
      _jsx("h1", { children: moderationPage ? "Marketplace review queue" : "My Marketplace listings" }),
      moderationPage && _jsx("p", { style: { color: "#64748B" }, children: "Only submissions awaiting moderator review appear here. Approved listings remain private until the seller publishes them." }),
      error && _jsx("p", { role: "alert", style: { color: "#B91C1C" }, children: error }),
      loading && _jsx("p", { role: "status", children: "Loading listings…" }),
      moderationPage && !isAdmin && _jsx("p", { role: "alert", style: { color: "#B91C1C" }, children: "Moderator access is required to view this queue." }),
      !moderationPage && (items.length ? items.map(item => card(item)) : !loading && _jsx("p", { style: { color: "#64748B" }, children: "You haven't submitted any listings yet." })),
      moderationPage && isAdmin && (queue.length ? queue.map(item => card(item, true)) : !loading && _jsx("p", { style: { color: "#64748B" }, children: "No listings waiting for review." })),
    ] }),
  ] });
}
