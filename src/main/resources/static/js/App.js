import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { useCallback, useEffect, useRef, useState } from "react";
import { api, pollUntil } from "./api.js";
import {
  defaultAdditionalContext,
  defaultState,
  defaultStudentInfo,
} from "./types.js";
import Layout from "./components/Layout.js";
import WorkflowLayout from "./components/WorkflowLayout.js";
import { ToastProvider, useToast } from "./components/Toast.js";
import Landing from "./pages/Landing.js";
import Login from "./pages/Login.js";
import Signup from "./pages/Signup.js";
import Dashboard from "./pages/Dashboard.js";
import Usage from "./pages/Usage.js";
import AddProject from "./pages/AddProject.js";
import Analyzing from "./pages/Analyzing.js";
import AnalysisResult from "./pages/AnalysisResult.js";
import TemplateRecommendation from "./pages/TemplateRecommendation.js";
import TemplatePreview from "./pages/TemplatePreview.js";
import ConfigureSections from "./pages/ConfigureSections.js";
import StudentDetails from "./pages/StudentDetails.js";
import AdditionalInfo from "./pages/AdditionalInfo.js";
import FinalReview from "./pages/FinalReview.js";
import GenerationProgress from "./pages/GenerationProgress.js";
import Completed from "./pages/Completed.js";
import DocumentEditor from "./pages/DocumentEditor.js";
import MyProjects from "./pages/MyProjects.js";
import ProjectDetails from "./pages/ProjectDetails.js";
import Templates from "./pages/Templates.js";
import Settings from "./pages/Settings.js";
import Help from "./pages/Help.js";
import Marketplace from "./pages/Marketplace.js";
import MarketplaceSell from "./pages/MarketplaceSell.js";
import MarketplaceListings from "./pages/MarketplaceListings.js";

const WORKFLOW_PAGES = [
  "add-project",
  "analyzing",
  "analysis-result",
  "template-recommendation",
  "template-preview",
  "student-details",
  "configure-sections",
  "additional-info",
  "final-review",
  "generating",
  "completed",
];
const ROUTES = new Set([
  "landing",
  "login",
  "signup",
  "dashboard",
  "usage",
  "my-projects",
  "project-details",
  "templates",
  "settings",
  "help",
  "marketplace",
  "marketplace-sell",
  "marketplace-listings",
  "marketplace-moderation",
  "document-editor",
  ...WORKFLOW_PAGES,
]);
const ACTIVE_GENERATION_STATUSES = new Set([
  "QUEUED_FOR_GENERATION",
  "GENERATING_DOCUMENTATION",
  "VALIDATING_ASSETS",
  "ASSEMBLING_DOCUMENT",
  "VALIDATING_DOCUMENT",
  "PREPARING_PDF",
  "RECOVERING",
]);
const ACTIVE_ANALYSIS_STATUSES = new Set([
  "ANALYZING_PROJECT",
  "INDEXING_PROJECT",
  "WAITING_FOR_INDEXING",
  "RECOVERING_INDEXING",
]);
const ANALYSIS_READY_STATUS = "WAITING_FOR_USER_CONFIGURATION";
const CONFIGURATION_PAGES = new Set([
  "analysis-result",
  "template-recommendation",
  "template-preview",
  "student-details",
  "configure-sections",
  "additional-info",
  "final-review",
]);
const APP_STATE_STORAGE_KEY = "docgen-app-state";
const authHomeButtonStyle = {
  position: "fixed",
  top: 16,
  right: 20,
  zIndex: 100,
  padding: "9px 14px",
  border: "1px solid #E2E8F0",
  borderRadius: 8,
  background: "white",
  color: "#475569",
  fontSize: 13,
  fontWeight: 600,
  cursor: "pointer",
  boxShadow: "0 2px 8px rgba(15, 23, 42, .08)",
};

function pageFromLocation() {
  const route = window.location.hash.slice(1);
  if (route.startsWith("marketplace/project/") || route.startsWith("marketplace/category/")) return "marketplace";
  return ROUTES.has(route) ? route : "landing";
}

function restoreAppState() {
  try {
    const serialized = sessionStorage.getItem(APP_STATE_STORAGE_KEY);
    if (!serialized) return defaultState;
    const saved = JSON.parse(serialized);
    if (!saved || typeof saved !== "object" || Array.isArray(saved))
      return defaultState;
    return {
      ...defaultState,
      ...saved,
      user: null,
      projectFile: null,
      studentInfo: {
        ...defaultStudentInfo,
        ...(saved.studentInfo || {}),
      },
      additionalContext: {
        ...defaultAdditionalContext,
        ...(saved.additionalContext || {}),
      },
    };
  } catch (error) {
    console.warn("Unable to restore the previous workspace state.", error);
    return defaultState;
  }
}

function isGenerationActive(status) {
  return ACTIVE_GENERATION_STATUSES.has(status);
}

function isAnalysisActive(status) {
  return ACTIVE_ANALYSIS_STATUSES.has(status);
}

function isWorkflowResumable(status) {
  return isAnalysisActive(status) || isGenerationActive(status) || status === ANALYSIS_READY_STATUS;
}

function updateJobStatus(jobs, jobId, status, progress, projectName = "") {
  let found = false;
  const updated = (jobs || []).map((job) => {
    if (job.jobId !== jobId) return job;
    found = true;
    return { ...job, status, progress, updatedAt: new Date().toISOString() };
  });
  return found
    ? updated
    : [
        {
          jobId,
          projectName,
          status,
          progress,
          updatedAt: new Date().toISOString(),
        },
        ...updated,
      ];
}

function clearStoredAppState() {
  try {
    sessionStorage.removeItem(APP_STATE_STORAGE_KEY);
  } catch (error) {
    console.warn("Unable to clear the saved workspace state.", error);
  }
}

function normalizeSections(analysis) {
  const definitions = analysis?.template?.sections || [];
  const recs = new Map(
    (analysis?.recommendations || []).map((r) => [r.sectionId, r]),
  );
  const byId = new Map(definitions.map((d) => [d.id, d]));
  const initiallyEnabled = new Set(
    definitions
      .filter((d) => d.defaultEnabled || recs.get(d.id)?.recommended)
      .map((d) => d.id),
  );
  const requireAncestors = (id) => {
    let current = byId.get(id);
    while (current?.parentId) {
      initiallyEnabled.add(current.parentId);
      current = byId.get(current.parentId);
    }
  };
  [...initiallyEnabled].forEach(requireAncestors);
  return definitions
    .slice()
    .sort((a, b) => Number(a.order || 0) - Number(b.order || 0))
    .map((d) => {
      const r = recs.get(d.id) || {};
      return {
        ...d,
        id: d.id,
        title: d.title,
        enabled: initiallyEnabled.has(d.id),
        contentEnabled: r.contentRecommended !== false,
        imageEnabled: Boolean(r.imageRecommended),
        diagramEnabled: Boolean(r.diagramRecommended) && Boolean(d.diagramType),
        imageIds: [],
        captions: {},
        recommended: Boolean(r.recommended),
        contentRecommended: Boolean(r.contentRecommended),
        imageRecommended: Boolean(r.imageRecommended),
        diagramRecommended: Boolean(r.diagramRecommended),
        reason: r.reason || d.description || "",
        chapterId: d.parentId || (d.chapter ? d.id : undefined),
      };
    });
}

function AppInner() {
  const toast = useToast();
  const toastRef = useRef(toast);
  toastRef.current = toast;
  const [page, setPageState] = useState(pageFromLocation);
  const pageRef = useRef(page);
  const trackingJobRef = useRef(null);
  const trackingAnalysisJobRef = useRef(null);
  const previewRequestRef = useRef(0);
  const previewErrorRef = useRef("");
  const authReadyRef = useRef(false);
  const authenticatedRef = useRef(false);
  const pendingRouteRef = useRef(sessionStorage.getItem("docgen-post-auth-route"));
  const [appState, setAppState] = useState(restoreAppState);
  const restoredAppStateRef = useRef(appState);
  const appStateRef = useRef(appState);
  appStateRef.current = appState;
  const [authReady, setAuthReady] = useState(false);
  const [authConfig, setAuthConfig] = useState({
    googleAuthEnabled: false,
    passwordResetEnabled: false,
  });
  const onState = useCallback(
    (patch) => setAppState((prev) => ({ ...prev, ...patch })),
    [],
  );
  const loadGenerationPreview = useCallback(
    async (jobId) => {
      const requestId = ++previewRequestRef.current;
      const preview = await api.documentation.preview(jobId);
      if (
        requestId === previewRequestRef.current &&
        appStateRef.current.currentProjectId === jobId
      ) {
        previewErrorRef.current = "";
        onState({ preview, generationPreviewError: null });
      }
      return preview;
    },
    [onState],
  );
  const reportGenerationPreviewError = useCallback(
    (error) => {
      const message = error.message || "The live document preview could not be updated.";
      if (previewErrorRef.current === message) return;
      previewErrorRef.current = message;
      onState({ generationPreviewError: message });
      toastRef.current.warning("Live preview unavailable", message);
    },
    [onState],
  );
  const setPage = useCallback((nextPage, { replace = false } = {}) => {
    if (
      authReadyRef.current &&
      !authenticatedRef.current &&
      ["marketplace-sell", "marketplace-listings", "marketplace-moderation"].includes(nextPage)
    ) {
      pendingRouteRef.current = nextPage;
      sessionStorage.setItem("docgen-post-auth-route", nextPage);
      nextPage = "login";
      replace = true;
    }
    if (!ROUTES.has(nextPage)) return;
    pageRef.current = nextPage;
    const nextUrl = `${window.location.pathname}${window.location.search}#${nextPage}`;
    if (window.location.hash !== `#${nextPage}`) {
      if (replace) window.history.replaceState({ page: nextPage }, "", nextUrl);
      else window.history.pushState({ page: nextPage }, "", nextUrl);
    }
    setPageState(nextPage);
    if (CONFIGURATION_PAGES.has(nextPage)) {
      setAppState((previous) => ({
        ...previous,
        workflowResumePage: nextPage,
        workflowResumeJobId: previous.currentProjectId,
      }));
    }
  }, []);

  useEffect(() => {
    const syncPageFromHistory = () => {
      let route = pageFromLocation();
      if (
        authReadyRef.current &&
        !authenticatedRef.current &&
        ["marketplace-sell", "marketplace-listings", "marketplace-moderation"].includes(route)
      ) {
        pendingRouteRef.current = route;
        sessionStorage.setItem("docgen-post-auth-route", route);
        route = "login";
        window.history.replaceState({ page: route }, "", `${window.location.pathname}${window.location.search}#${route}`);
      } else if (
        authReadyRef.current &&
        !authenticatedRef.current &&
        !["landing", "login", "signup", "marketplace"].includes(route)
      ) {
        route = "landing";
        window.history.replaceState(
          { page: route },
          "",
          `${window.location.pathname}${window.location.search}#${route}`,
        );
      }
      pageRef.current = route;
      setPageState(route);
    };
    window.addEventListener("popstate", syncPageFromHistory);
    window.addEventListener("hashchange", syncPageFromHistory);
    return () => {
      window.removeEventListener("popstate", syncPageFromHistory);
      window.removeEventListener("hashchange", syncPageFromHistory);
    };
  }, []);

  useEffect(() => {
    try {
      const { user, projectFile, ...persistedState } = appState;
      sessionStorage.setItem(
        APP_STATE_STORAGE_KEY,
        JSON.stringify(persistedState),
      );
    } catch (error) {
      console.warn("Unable to save the current workspace state.", error);
    }
  }, [appState]);

  const refreshWorkspace = useCallback(async () => {
    const [template, jobs, usageSummary, usageActivity] = await Promise.all([
      api.documentation.template(),
      api.documentation.jobs(),
      api.auth.usageSummary().catch(() => null),
      api.auth.usage().catch(() => []),
    ]);
    onState({
      template,
      templates: template ? [template] : [],
      jobs: jobs?.jobs || [],
      usageSummary,
      usageActivity: Array.isArray(usageActivity) ? usageActivity : [],
      usageCount: Number(usageSummary?.used || 0),
      sections: appStateRef.current.sections.length
        ? appStateRef.current.sections
        : normalizeSections({ template, recommendations: [] }),
    });
  }, [onState]);

  const loadAnalysisResult = useCallback(
    async (jobId, fallbackProjectName = "") => {
      const analysis = await api.documentation.analysis(jobId);
      const suggestions = await api.documentation
        .contextSuggestions(jobId)
        .catch(() => null);
      const savedWorkflow = appStateRef.current;
      const preserveConfiguration =
        savedWorkflow.currentProjectId === jobId &&
        savedWorkflow.workflowResumeJobId === jobId &&
        CONFIGURATION_PAGES.has(savedWorkflow.workflowResumePage) &&
        savedWorkflow.sections.length > 0;
      onState({
        currentProjectId: jobId,
        analysis,
        template: analysis.template,
        templates: analysis.template ? [analysis.template] : [],
        sections: preserveConfiguration
          ? savedWorkflow.sections
          : normalizeSections(analysis),
        projectName:
          analysis.projectFacts?.projectName ||
          fallbackProjectName ||
          appStateRef.current.projectName ||
          "",
        projectType: analysis.projectFacts?.projectType || "",
        technologies: analysis.projectFacts?.technologies || [],
        frameworks: analysis.projectFacts?.frameworks || [],
        totalFiles:
          analysis.projectFacts?.analyzedFileCount ||
          analysis.projectFacts?.allFiles?.length ||
          0,
        sourceFiles: analysis.projectFacts?.textFileCount || 0,
        componentCount: analysis.projectFacts?.modules?.length || 0,
        selectedTemplate:
          (preserveConfiguration && savedWorkflow.selectedTemplate) ||
          analysis.template?.name ||
          analysis.templateId ||
          "",
        selectedTemplateId:
          (preserveConfiguration && savedWorkflow.selectedTemplateId) ||
          analysis.templateId,
        contextSuggestions: suggestions,
        analysisProgress: 100,
        analysisStatus: ANALYSIS_READY_STATUS,
      });
    },
    [onState],
  );

  const trackAnalysis = useCallback(
    async (jobId, projectName = "") => {
      if (trackingAnalysisJobRef.current === jobId) return;
      trackingAnalysisJobRef.current = jobId;
      try {
        const status = await pollUntil(
          jobId,
          (jobStatus) =>
            [
              ANALYSIS_READY_STATUS,
              "COMPLETED",
              "FAILED",
              "CANCELLED",
            ].includes(jobStatus.status),
          {
            onStatus: (jobStatus) => {
              onState({
                currentProjectId: jobId,
                analysisStatus: jobStatus.status,
                analysisProgress: jobStatus.progress || 0,
                analysisMessage: jobStatus.message || "",
                jobs: updateJobStatus(
                  appStateRef.current.jobs,
                  jobId,
                  jobStatus.status,
                  jobStatus.progress || 0,
                  projectName,
                ),
              });
            },
            onPollingError: () => {
                onState({
                  analysisMessage:
                    "Status updates are temporarily unavailable. Analysis may still be running; retrying automatically.",
                });
            },
          },
        );
        if (status.status === "FAILED" || status.status === "CANCELLED") {
          onState({
            analysisStatus: status.status,
            analysisProgress: status.progress || 100,
            analysisMessage: status.message || "Project analysis stopped.",
          });
          await refreshWorkspace();
          if (pageRef.current === "analyzing") setPage("project-details");
          return;
        }
        await loadAnalysisResult(jobId, projectName);
        await refreshWorkspace();
        if (pageRef.current === "analyzing") setPage("analysis-result");
      } finally {
        if (trackingAnalysisJobRef.current === jobId)
          trackingAnalysisJobRef.current = null;
      }
    },
    [loadAnalysisResult, onState, refreshWorkspace, setPage],
  );

  const trackGeneration = useCallback(
    async (jobId) => {
      if (trackingJobRef.current === jobId) return;
      trackingJobRef.current = jobId;
      try {
        let finalStatus;
        try {
          finalStatus = await pollUntil(
            jobId,
            (status) =>
              ["COMPLETED", "FAILED", "CANCELLED"].includes(status.status),
            {
              onStatus: (status) => {
                onState({
                  generationStatus: status.status,
                  generationProgress: status.progress || 0,
                  generationMessage: status.message || "",
                  jobs: updateJobStatus(
                    appStateRef.current.jobs,
                    jobId,
                    status.status,
                    status.progress || 0,
                  ),
                });
                if (
                  [
                    "GENERATING_DOCUMENTATION",
                    "ASSEMBLING_DOCUMENT",
                    "VALIDATING_DOCUMENT",
                    "PREPARING_PDF",
                    "COMPLETED",
                  ].includes(status.status)
                ) {
                  loadGenerationPreview(jobId).catch(reportGenerationPreviewError);
                }
              },
              onPollingError: () => {
                onState({
                  generationMessage:
                    "Status updates are temporarily unavailable. Generation may still be running; retrying automatically.",
                });
              },
            },
          );
        } catch (error) {
          if (error.status !== 404) {
            error.statusPollingError = true;
            throw error;
          }
          onState({
            generationStatus: "CANCELLED",
            generationProgress: 100,
            generationMessage: "This project was deleted.",
          });
          return;
        }
        if (finalStatus.status === "FAILED") {
          const error = new Error(
            finalStatus.message || "Documentation generation failed.",
          );
          error.generationFailed = true;
          throw error;
        }
        if (finalStatus.status === "CANCELLED") {
          onState({
            generationStatus: "CANCELLED",
            generationProgress: 100,
            generationMessage:
              finalStatus.message || "Documentation generation was cancelled.",
          });
          return;
        }
        onState({
          generationProgress: 100,
          generationStatus: "COMPLETED",
          generationMessage: finalStatus.message || "Documentation completed.",
        });
        await refreshWorkspace();
        if (pageRef.current === "generating") setPage("completed");
      } finally {
        if (trackingJobRef.current === jobId) trackingJobRef.current = null;
      }
    },
    [loadGenerationPreview, onState, refreshWorkspace, reportGenerationPreviewError, setPage],
  );

  useEffect(() => {
    let alive = true;
    let authenticated = false;
    (async () => {
      try {
        const config = await api.auth
          .config()
          .catch(() => ({
            googleAuthEnabled: false,
            passwordResetEnabled: false,
          }));
        if (!alive) return;
        setAuthConfig(config);
        const me = await api.auth.me();
        if (!alive) return;
        if (me?.authenticated && me.user) {
          authenticated = true;
          authenticatedRef.current = true;
          setAppState((prev) => ({ ...prev, user: me.user }));
          const template = await api.documentation.template();
          const [jobs, usageSummary, usageActivity] = await Promise.all([
            api.documentation.jobs(),
            api.auth.usageSummary().catch(() => null),
            api.auth.usage().catch(() => []),
          ]);
          if (!alive) return;
          setAppState((prev) => ({
            ...prev,
            user: me.user,
            template,
            templates: template ? [template] : [],
            jobs: jobs?.jobs || [],
            usageSummary,
            usageActivity,
            usageCount: Number(usageSummary?.used || 0),
          }));
          const jobsList = jobs?.jobs || [];
          const savedJob = jobsList.find(
            (job) =>
              job.jobId === restoredAppStateRef.current.currentProjectId,
          );
          const restoredJob =
            (savedJob && isWorkflowResumable(savedJob.status) && savedJob) ||
            jobsList.find((job) => isGenerationActive(job.status)) ||
            jobsList.find((job) => isAnalysisActive(job.status)) ||
            jobsList.find((job) => job.status === ANALYSIS_READY_STATUS) ||
            savedJob;
          if (restoredJob) {
            const status = await api.documentation.status(restoredJob.jobId);
            if (!alive) return;
            const recoveredState = {
              currentProjectId: restoredJob.jobId,
              projectName:
                restoredJob.projectName ||
                restoredAppStateRef.current.projectName,
              ...(isGenerationActive(status.status)
                ? {
                    generationStatus: status.status,
                    generationProgress: status.progress || 0,
                    generationMessage: status.message || "",
                  }
                : {
                    analysisStatus: status.status,
                    analysisProgress: status.progress || 0,
                    analysisMessage: status.message || "",
                  }),
            };
            setAppState((prev) => ({ ...prev, ...recoveredState }));
            if (isGenerationActive(status.status)) {
              loadGenerationPreview(restoredJob.jobId).catch(reportGenerationPreviewError);
              trackGeneration(restoredJob.jobId).catch((error) => {
                toastRef.current.error(
                  "Unable to resume generation",
                  error.message || "Unable to check generation progress.",
                );
              });
            } else if (
              isAnalysisActive(status.status) ||
              status.status === ANALYSIS_READY_STATUS
            ) {
              trackAnalysis(restoredJob.jobId, recoveredState.projectName).catch(
                (error) => {
                  toastRef.current.error(
                    "Unable to resume project analysis",
                    error.message || "Unable to check analysis progress.",
                  );
                },
              );
            } else if (
              status.status === "COMPLETED" &&
              pageRef.current === "generating"
            ) {
              setPage("completed");
            }
          }
          if (["landing", "login", "signup"].includes(pageRef.current)) {
            const destination = pendingRouteRef.current || "dashboard";
            setPage(destination, { replace: true });
            pendingRouteRef.current = null;
            sessionStorage.removeItem("docgen-post-auth-route");
          }
        } else if (["marketplace-sell", "marketplace-listings", "marketplace-moderation"].includes(pageRef.current)) {
          pendingRouteRef.current = pageRef.current;
          sessionStorage.setItem("docgen-post-auth-route", pageRef.current);
          setPage("login", { replace: true });
        } else if (!["landing", "login", "signup", "marketplace"].includes(pageRef.current)) {
          setPage("landing", { replace: true });
        }
      } catch (error) {
        if (authenticated || error.status !== 401)
          console.warn("Unable to restore the authenticated workspace.", error);
        if (!authenticated) {
          authenticatedRef.current = false;
          clearStoredAppState();
          setAppState(defaultState);
        }
        if (
          alive &&
          !authenticated &&
          !["landing", "login", "signup", "marketplace"].includes(pageRef.current)
        )
          setPage("landing", { replace: true });
      } finally {
        if (alive) {
          authReadyRef.current = true;
          setAuthReady(true);
        }
      }
    })();
    return () => {
      alive = false;
    };
  }, [loadGenerationPreview, onState, reportGenerationPreviewError, setPage, trackAnalysis, trackGeneration]);

  const handleLogin = async (email, password) => {
    const data = await api.auth.login(email, password);
    authenticatedRef.current = true;
    onState({ user: data.user });
    await refreshWorkspace();
    setPage(pendingRouteRef.current || "dashboard");
    pendingRouteRef.current = null;
    sessionStorage.removeItem("docgen-post-auth-route");
  };
  const handleSignup = async (email, name, password) => {
    const data = await api.auth.register(email, name, password);
    authenticatedRef.current = true;
    onState({ user: data.user });
    await refreshWorkspace();
    setPage(pendingRouteRef.current || "dashboard");
    pendingRouteRef.current = null;
    sessionStorage.removeItem("docgen-post-auth-route");
  };
  const handleLogout = async () => {
    await api.auth.logout();
    authenticatedRef.current = false;
    clearStoredAppState();
    setAppState(defaultState);
    setPage("landing", { replace: true });
  };

  const handleAnalyze = async ({ githubUrl, projectName, projectZip }) => {
    onState({
      githubUrl,
      projectName,
      projectFile: projectZip,
      error: null,
      analysisProgress: 0,
      analysisMessage: "Submitting project for analysis...",
    });
    setPage("analyzing");
    try {
      const initial = await api.documentation.analyze({
        githubUrl,
        projectName,
        projectZip,
      });
      onState({
        currentProjectId: initial.jobId,
        analysisStatus: initial.status,
        analysis: initial,
        analysisProgress: 0,
        jobs: [
          {
            jobId: initial.jobId,
            projectName: projectName || appStateRef.current.projectName || "",
            status: initial.status,
            progress: 0,
            updatedAt: new Date().toISOString(),
          },
          ...(appStateRef.current.jobs || []).filter(
            (job) => job.jobId !== initial.jobId,
          ),
        ],
      });
      refreshWorkspace().catch((error) =>
        toastRef.current.warning(
          "Project is processing",
          `The project list could not be refreshed: ${error.message || "Please reload the page."}`,
        ),
      );
      await trackAnalysis(initial.jobId, projectName);
    } catch (error) {
      if (pageRef.current !== "analyzing") return;
      onState({ error: error.message || "Unable to analyze the project." });
      toast.error(
        "Project analysis failed",
        error.message || "Unable to analyze the project.",
      );
      setPage("add-project");
    }
  };

  const handleGenerate = async (outputFormats) => {
    previewRequestRef.current += 1;
    previewErrorRef.current = "";
    const s = appState;
    const student = s.studentInfo || {};
    const ctx = s.additionalContext || {};
    const selectedSections = (s.sections || [])
      .filter((x) => x.enabled)
      .map((x) => x.id);
    const selectedDiagrams = (s.sections || [])
      .filter((x) => x.enabled && x.diagramEnabled)
      .map((x) => x.id);
    const sectionConfigurations = (s.sections || [])
      .filter((x) => x.enabled)
      .map((x) => ({
        sectionId: x.id,
        enabled: true,
        contentEnabled: x.contentEnabled !== false,
        imageEnabled: x.imageEnabled === true,
        diagramEnabled: x.diagramEnabled === true,
        imageIds: x.imageIds || [],
      }));
    const studentDetails = {
      name: student.studentName || "",
      rollNumber: student.rollNumber || "",
      enrollmentNumber: student.enrollmentNumber || "",
      course: student.course || "",
      department: student.department || "",
      academicYear: student.academicYear || "",
      collegeName: student.collegeName || "",
      universityName: student.universityName || "",
      guideName: student.guideName || "",
      guideDesignation: student.guideDesignation || "",
      hodName: student.hodName || "",
      hodDesignation: student.hodDesignation || "",
      submissionDate: student.submissionDate || "",
      collegeLogoAssetId: student.collegeLogoAssetId || "",
      guideSignatureAssetId: student.guideSignatureAssetId || "",
      hodSignatureAssetId: student.hodSignatureAssetId || "",
      teamMembers: (student.teamMembers || [])
        .map((m) => (typeof m === "string" ? m : m.name))
        .filter(Boolean),
      projectTitleOverride: student.projectTitle || "",
      coverLayout: student.coverLayout || defaultStudentInfo.coverLayout,
    };
    const payload = {
      templateId:
        s.selectedTemplateId || s.analysis?.templateId || s.template?.id,
      selectedSections,
      selectedDiagrams,
      sectionConfigurations,
      studentDetails,
      studentContext: { ...ctx, source: ctx.source || "USER_EDITED" },
      additionalInformation: s.additionalInformation || {},
      outputFormat:
        outputFormats.includes("pdf") && !outputFormats.includes("docx")
          ? "pdf"
          : "docx",
    };
    onState({
      outputFormats,
      generationProgress: 0,
      generationStatus: "QUEUED_FOR_GENERATION",
      generationMessage: "Starting documentation generation...",
      preview: null,
      generationPreviewError: null,
      error: null,
    });
    setPage("generating");
    try {
      await api.documentation.generate(s.currentProjectId, payload);
      await trackGeneration(s.currentProjectId);
    } catch (error) {
      const generationFailed =
        error.generationFailed || !error.statusPollingError;
      const message = error.statusPollingError
        ? `Could not confirm generation status: ${error.message || "the status request failed"}. The job may still be running; resume this job to check again.`
        : error.message || "Documentation generation failed.";
      onState({
        error: message,
        ...(generationFailed
          ? {
              generationStatus: "FAILED",
              generationMessage: message,
            }
          : { generationMessage: message }),
      });
      toast.error(
        generationFailed ? "Generation failed" : "Status check interrupted",
        message,
      );
    }
  };

  const resumeWorkflow = useCallback(
    async (jobId = appStateRef.current.currentProjectId) => {
      if (!jobId) return;
      try {
        const job = (appStateRef.current.jobs || []).find(
          (item) => item.jobId === jobId,
        );
        const status = await api.documentation.status(jobId);
        onState({
          currentProjectId: jobId,
          projectName:
            job?.projectName || appStateRef.current.projectName || "",
          jobs: updateJobStatus(
            appStateRef.current.jobs,
            jobId,
            status.status,
            status.progress || 0,
            job?.projectName || "",
          ),
          ...(isGenerationActive(status.status)
            ? {
                generationStatus: status.status,
                generationProgress: status.progress || 0,
                generationMessage: status.message || "",
              }
            : {
                analysisStatus: status.status,
                analysisProgress: status.progress || 0,
                analysisMessage: status.message || "",
              }),
        });
        if (isGenerationActive(status.status)) {
          setPage("generating");
          if (
            [
              "GENERATING_DOCUMENTATION",
              "ASSEMBLING_DOCUMENT",
              "VALIDATING_DOCUMENT",
              "PREPARING_PDF",
            ].includes(status.status)
          ) {
            loadGenerationPreview(jobId).catch(reportGenerationPreviewError);
          }
          await trackGeneration(jobId);
          return;
        }
        if (isAnalysisActive(status.status)) {
          setPage("analyzing");
          await trackAnalysis(jobId, job?.projectName || "");
          return;
        }
        if (status.status === ANALYSIS_READY_STATUS) {
          await loadAnalysisResult(jobId, job?.projectName || "");
          const savedPage =
            appStateRef.current.workflowResumeJobId === jobId &&
            CONFIGURATION_PAGES.has(appStateRef.current.workflowResumePage)
              ? appStateRef.current.workflowResumePage
              : "analysis-result";
          setPage(savedPage);
          return;
        }
        if (status.status === "COMPLETED") {
          setPage("project-details");
          return;
        }
        if (status.status === "FAILED" || status.status === "CANCELLED") {
          setPage("project-details");
          return;
        }
        throw new Error(`Project cannot be resumed from status ${status.status}.`);
      } catch (error) {
        toastRef.current.error(
          "Unable to resume project",
          error.message || "Please try again.",
        );
      }
    },
    [loadAnalysisResult, loadGenerationPreview, onState, reportGenerationPreviewError, setPage, trackAnalysis, trackGeneration],
  );

  const handleCancel = async () => {
    if (!appState.currentProjectId) return;
    const cancellingAnalysis = pageRef.current === "analyzing";
    try {
      await api.documentation.cancel(appState.currentProjectId);
      onState({
        ...(cancellingAnalysis
          ? {
              analysisStatus: "CANCELLED",
              analysisProgress: 100,
              analysisMessage: "Project analysis was cancelled.",
            }
          : {
              generationStatus: "CANCELLED",
              generationProgress: 100,
              generationMessage: "Documentation generation was cancelled.",
            }),
      });
      setPage("dashboard");
      try {
        await refreshWorkspace();
      } catch (error) {
        toast.warning(
          "Generation cancelled",
          `The project list could not be refreshed: ${error.message || "Please reload the page."}`,
        );
      }
    } catch (error) {
      toast.error(
        "Unable to cancel generation",
        error.message || "Please try again.",
      );
    }
  };

  if (!authReady) return null;
  if (page === "marketplace") return _jsx(Marketplace, { onNav: setPage, state: appState });
  if (page === "landing") return _jsxs("div", { children: [
    _jsx(Landing, { onNav: setPage }),
    _jsx("button", {
      type: "button",
      onClick: () => setPage("marketplace"),
      style: { position: "fixed", right: 18, bottom: 18, zIndex: 40, padding: "12px 17px", border: 0, borderRadius: 999, background: "#172033", color: "#fff", fontWeight: 750, boxShadow: "0 8px 28px rgba(15,23,42,.2)", cursor: "pointer" },
      children: "Explore free Marketplace",
    }),
  ] });
  if (page === "login")
    return _jsx(Login, {
      onNav: setPage,
      onLogin: handleLogin,
      googleEnabled: authConfig.googleAuthEnabled === true,
      onGoogle: () => window.location.assign("/oauth2/authorization/google"),
    });
  if (page === "signup")
    return _jsxs("div", {
      children: [
        _jsx("button", {
          className: "auth-home-button",
          type: "button",
          onClick: () => setPage("landing"),
          style: authHomeButtonStyle,
          children: "← Back to home",
        }),
        _jsx(Signup, {
          onNav: setPage,
          onSignup: handleSignup,
          googleEnabled: authConfig.googleAuthEnabled === true,
          onGoogle: () =>
            window.location.assign("/oauth2/authorization/google"),
        }),
      ],
    });

  const renderWorkflow = () => {
    switch (page) {
      case "add-project":
        return _jsx(AddProject, {
          onNav: setPage,
          onAnalyze: handleAnalyze,
          state: appState,
          onState,
        });
      case "analyzing":
        return _jsx(Analyzing, {
          onNav: setPage,
          state: appState,
          onCancel: handleCancel,
        });
      case "analysis-result":
        return _jsx(AnalysisResult, { onNav: setPage, state: appState });
      case "template-recommendation":
        return _jsx(TemplateRecommendation, {
          onNav: setPage,
          state: appState,
          onState,
        });
      case "template-preview":
        return _jsx(TemplatePreview, {
          onNav: setPage,
          state: appState,
          onState,
        });
      case "configure-sections":
        return _jsx(ConfigureSections, {
          onNav: setPage,
          state: appState,
          onState,
        });
      case "student-details":
        return _jsx(StudentDetails, {
          onNav: setPage,
          state: appState,
          onState,
        });
      case "additional-info":
        return _jsx(AdditionalInfo, {
          onNav: setPage,
          state: appState,
          onState,
        });
      case "final-review":
        return _jsx(FinalReview, {
          onNav: setPage,
          state: appState,
          onState,
          onGenerate: handleGenerate,
        });
      case "generating":
        return _jsx(GenerationProgress, {
          onNav: setPage,
          state: appState,
          onCancel: handleCancel,
        });
      case "completed":
        return _jsx(Completed, { onNav: setPage, state: appState });
      default:
        return null;
    }
  };
  const renderPage = () => {
    switch (page) {
      case "dashboard":
        return _jsx(Dashboard, {
          onNav: setPage,
          state: appState,
          onResume: resumeWorkflow,
        });
      case "usage":
        return _jsx(Usage, { onNav: setPage, state: appState });
      case "my-projects":
        return _jsx(MyProjects, {
          onNav: setPage,
          state: appState,
          onState,
          onResume: resumeWorkflow,
        });
      case "project-details":
        return _jsx(ProjectDetails, { onNav: setPage, state: appState, onState });
      case "document-editor":
        return _jsx(DocumentEditor, { onNav: setPage, state: appState });
      case "templates":
        return _jsx(Templates, { onNav: setPage, state: appState });
      case "settings":
        return _jsx(Settings, {
          onNav: setPage,
          state: appState,
          onState,
          onLogout: handleLogout,
        });
      case "help":
        return _jsx(Help, { onNav: setPage });
      case "marketplace-sell":
        return _jsx(MarketplaceSell, { onNav: setPage, state: appState });
      case "marketplace-listings":
        return _jsx(MarketplaceListings, { onNav: setPage, state: appState, mode: "seller" });
      case "marketplace-moderation":
        return _jsx(MarketplaceListings, { onNav: setPage, state: appState, mode: "moderation" });
      default:
        return _jsx(Dashboard, { onNav: setPage, state: appState });
    }
  };

  const isWorkflow = WORKFLOW_PAGES.includes(page);
  const resumableJobs = (appState.jobs || []).filter((job) =>
    isWorkflowResumable(job.status),
  );
  const resumableJob =
    resumableJobs.find(
      (job) => job.jobId === appState.currentProjectId,
    ) ||
    resumableJobs.find((job) => isGenerationActive(job.status)) ||
    resumableJobs.find((job) => isAnalysisActive(job.status)) ||
    resumableJobs[0];
  const resumableStatus =
    resumableJob?.jobId === appState.currentProjectId
      ? isGenerationActive(appState.generationStatus)
        ? appState.generationStatus
        : isWorkflowResumable(appState.analysisStatus)
          ? appState.analysisStatus
          : resumableJob.status
      : resumableJob?.status;
  const resumeAlreadyVisible =
    resumableJob?.jobId === appState.currentProjectId &&
    ((isAnalysisActive(resumableStatus) && page === "analyzing") ||
      (isGenerationActive(resumableStatus) && page === "generating") ||
      (resumableStatus === ANALYSIS_READY_STATUS &&
        CONFIGURATION_PAGES.has(page)));
  return _jsxs("div", {
    children: [
      _jsx(Layout, {
        page,
        state: appState,
        onNav: setPage,
        onLogout: handleLogout,
        children: isWorkflow
          ? _jsx(WorkflowLayout, {
              page,
              onNav: setPage,
              children: renderWorkflow(),
            })
          : renderPage(),
      }),
      resumableJob && !resumeAlreadyVisible
        ? _jsx("button", {
            type: "button",
              onClick: () => resumeWorkflow(resumableJob.jobId),
              "aria-label": `Resume ${resumableJob.projectName || "project"}: ${resumableStatus}`,
              className: "resume-workflow-button",
              style: {
                position: "fixed",
                right: 20,
                bottom: 20,
                zIndex: 80,
                display: "flex",
                alignItems: "center",
                gap: 8,
                maxWidth: "min(420px, calc(100vw - 32px))",
                padding: "11px 16px",
                border: "1px solid var(--primary-border)",
                borderRadius: 10,
                background: "white",
                color: "var(--primary)",
                fontWeight: 700,
                cursor: "pointer",
                boxShadow: "0 6px 18px rgba(15, 23, 42, .16)",
              },
              children: [
                _jsx("span", {
                  className: "resume-workflow-dot",
                  "aria-hidden": "true",
                }),
                _jsxs("span", {
                  className: "resume-workflow-copy",
                  children: [
                    _jsx("strong", {
                      children:
                        resumableStatus === ANALYSIS_READY_STATUS
                          ? "Continue setup"
                          : isGenerationActive(resumableStatus)
                            ? "Resume generation"
                            : "Resume analysis",
                    }),
                    _jsx("small", {
                      children:
                        resumableJob.projectName ||
                        resumableJob.jobId ||
                        "Your project",
                    }),
                  ],
                }),
                _jsx("span", {
                  className: "resume-workflow-arrow",
                  "aria-hidden": "true",
                  children: "→",
                }),
              ],
            })
        : null,
    ],
  });
}

export default function App() {
  return _jsx(ToastProvider, { children: _jsx(AppInner, {}) });
}
