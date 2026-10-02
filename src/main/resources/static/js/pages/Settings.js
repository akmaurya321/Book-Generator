import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { useState } from "react";
import { api } from "../api.js";
import { useToast } from "../components/Toast.js";

export default function Settings({ onNav, state, onState, onLogout }) {
    const user = state.user || {};
    const [uploading, setUploading] = useState(false);
    const [imageFailed, setImageFailed] = useState(false);
    const toast = useToast();
    const initial = user.name?.slice(0, 1)?.toUpperCase() || "A";

    const handleImageSelect = async (event) => {
        const file = event.target.files?.[0];
        event.target.value = "";
        if (!file) return;
        if (!["image/png", "image/jpeg"].includes(file.type)) {
            toast.error("Unsupported image", "Choose a PNG or JPEG image.");
            return;
        }
        if (file.size > 5 * 1024 * 1024) {
            toast.error("Image is too large", "Profile images must be 5 MB or smaller.");
            return;
        }

        setUploading(true);
        try {
            const result = await api.auth.uploadAvatar(file);
            onState({ user: { ...user, avatarUrl: result.avatarUrl } });
            setImageFailed(false);
            toast.success("Profile photo updated");
        } catch (error) {
            toast.error("Could not update photo", error.message);
        } finally {
            setUploading(false);
        }
    };

    return _jsxs("div", {
        className: "settings-page",
        style: { padding: 28, maxWidth: 700 },
        children: [
            _jsxs("div", {
                style: { marginBottom: 22 },
                children: [
                    _jsx("h1", {
                        style: { fontSize: 20, fontWeight: 700, color: "var(--text-primary)", margin: "0 0 4px" },
                        children: "Account",
                    }),
                    _jsx("p", {
                        style: { fontSize: 14, color: "var(--text-secondary)", margin: 0 },
                        children: "Manage your profile photo and account details.",
                    }),
                ],
            }),
            _jsxs("section", {
                style: { background: "white", borderRadius: 14, border: "1px solid var(--border)", padding: 20, marginBottom: 14 },
                children: [
                    _jsx("h2", {
                        style: { fontSize: 15, fontWeight: 700, color: "var(--text-primary)", margin: "0 0 18px" },
                        children: "Profile",
                    }),
                    _jsxs("div", {
                        style: { display: "flex", alignItems: "center", gap: 18, flexWrap: "wrap", marginBottom: 20 },
                        children: [
                            _jsx("div", {
                                style: {
                                    width: 84,
                                    height: 84,
                                    flex: "0 0 84px",
                                    display: "flex",
                                    alignItems: "center",
                                    justifyContent: "center",
                                    overflow: "hidden",
                                    borderRadius: "50%",
                                    background: "var(--primary)",
                                    color: "white",
                                    fontSize: 30,
                                    fontWeight: 700,
                                },
                                children: user.avatarUrl && !imageFailed
                                    ? _jsx("img", {
                                        src: user.avatarUrl,
                                        alt: `${user.name || "Account"} profile`,
                                        width: "84",
                                        height: "84",
                                        onError: () => setImageFailed(true),
                                        style: { width: "100%", height: "100%", objectFit: "cover" },
                                    })
                                    : initial,
                            }),
                            _jsxs("div", {
                                style: { flex: "1 1 220px" },
                                children: [
                                    _jsx("div", {
                                        style: { fontSize: 14, fontWeight: 700, color: "var(--text-primary)", marginBottom: 5 },
                                        children: "Profile photo",
                                    }),
                                    _jsx("div", {
                                        style: { fontSize: 13, color: "var(--text-secondary)", marginBottom: 12 },
                                        children: "Use a PNG or JPEG image, up to 5 MB.",
                                    }),
                                    _jsx("input", {
                                        id: "profile-image-upload",
                                        type: "file",
                                        accept: "image/png,image/jpeg",
                                        onChange: handleImageSelect,
                                        style: {
                                            position: "absolute",
                                            width: 1,
                                            height: 1,
                                            padding: 0,
                                            margin: -1,
                                            overflow: "hidden",
                                            clip: "rect(0, 0, 0, 0)",
                                            whiteSpace: "nowrap",
                                            border: 0,
                                        },
                                    }),
                                    _jsx("label", {
                                        for: "profile-image-upload",
                                        ariaDisabled: uploading,
                                        style: {
                                            display: "inline-block",
                                            padding: "9px 16px",
                                            border: "1px solid var(--border)",
                                            borderRadius: 8,
                                            background: "white",
                                            color: "var(--text-primary)",
                                            fontSize: 13,
                                            fontWeight: 650,
                                            cursor: uploading ? "wait" : "pointer",
                                            opacity: uploading ? 0.7 : 1,
                                            pointerEvents: uploading ? "none" : "auto",
                                        },
                                        children: uploading ? "Uploading..." : user.avatarUrl ? "Change photo" : "Upload photo",
                                    }),
                                ],
                            }),
                        ],
                    }),
                    [["Name", user.name], ["Email", user.email], ["Provider", user.provider]].map(([label, value]) =>
                        _jsxs("div", {
                            style: { display: "flex", justifyContent: "space-between", gap: 20, padding: "11px 0", borderTop: "1px solid #F1F5F9" },
                            children: [
                                _jsx("span", { style: { fontSize: 13, color: "var(--text-muted)" }, children: label }),
                                _jsx("strong", { style: { fontSize: 13, color: "var(--text-primary)", overflowWrap: "anywhere" }, children: value || "—" }),
                            ],
                        }, label),
                    ),
                ],
            }),
            _jsxs("div", {
                style: { background: "#FEF2F2", borderRadius: 14, border: "1px solid #FECACA", padding: 20 },
                children: [
                    _jsx("h2", { style: { fontSize: 15, fontWeight: 700, color: "#DC2626", margin: "0 0 8px" }, children: "Session" }),
                    _jsx("p", { style: { fontSize: 13, color: "#9B1C1C", margin: "0 0 14px" }, children: "Sign out from this browser session." }),
                    _jsx("button", {
                        type: "button",
                        onClick: () => onLogout?.(),
                        style: { padding: "9px 20px", background: "#DC2626", color: "white", border: "none", borderRadius: 8, fontWeight: 700, fontSize: 14, cursor: "pointer" },
                        children: "Logout",
                    }),
                ],
            }),
        ],
    });
}
