export const defaultStudentInfo = {
  projectTitle: '', course: '', department: '', academicYear: '', studentName: '', rollNumber: '', enrollmentNumber: '',
  teamMembers: [], collegeName: '', universityName: '', guideName: '', guideDesignation: '', hodName: '', hodDesignation: '', submissionDate: '',
  coverLayout: {
    titleX: 50, titleY: 15, titleScale: 1, logoX: 50, logoY: 31, logoScale: 1,
    logoZoom: 1, logoCropX: 50, logoCropY: 50, bodyX: 50, bodyY: 55, bodyScale: 1
  }
};
export const defaultAdditionalContext = {
  motivation: '', problemStatement: '', objectives: [], targetUsers: '', expectedBenefits: '', limitations: '', futureIdeas: '', additionalNotes: '', source: 'USER'
};
export const defaultState = {
  user: null, projectName: '', projectType: '', githubUrl: '', projectFile: null, technologies: [], frameworks: [],
  totalFiles: 0, sourceFiles: 0, componentCount: 0, selectedTemplate: '', selectedTemplateId: '', template: null,
  selectedCatalogTemplateId: '', selectedCatalogTemplateVersion: null, selectedCatalogTemplate: null, usePrivateFormat: false,
  templates: [], sections: [], studentInfo: defaultStudentInfo, additionalContext: defaultAdditionalContext,
  contextSuggestions: null, additionalInformation: {}, outputFormats: ['pdf', 'docx'], currentProjectId: null,
  jobs: [], usageCount: 0, usageSummary: null, usageActivity: [], analysis: null, analysisStatus: null, analysisProgress: 0,
  analysisMessage: '', generationStatus: null, generationProgress: 0, generationMessage: '', generationPreviewError: null, preview: null, error: null,
  workflowResumePage: null, workflowResumeJobId: null
};
export const CHAPTER_MAP = {};
