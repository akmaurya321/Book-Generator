import { jsx as _jsx, jsxs as _jsxs } from 'react/jsx-runtime';
import { useState } from 'react';
export default function Analyzing({ state, onCancel }) {
  const [cancelPending, setCancelPending] = useState(false);
  const progress = Math.max(0, Math.min(100, Number(state.analysisProgress || 0)));
  const steps = [
    ['RECEIVED','Receiving project'], ['ANALYZING_PROJECT','Analyzing project structure'], ['INDEXING_PROJECT','Indexing project evidence'], ['WAITING_FOR_INDEXING','Finishing evidence indexing'], ['WAITING_FOR_USER_CONFIGURATION','Analysis ready']
  ];
  const current = steps.findIndex(([id]) => id === state.analysisStatus);
  return _jsxs('div', { style:{padding:32,maxWidth:760}, children:[
    _jsx('h1',{style:{fontSize:22,fontWeight:800,color:'var(--text-primary)',margin:'0 0 6px'},children:'Analyzing Your Project'}),
    _jsx('p',{style:{fontSize:14.5,color:'var(--text-secondary)',margin:'0 0 28px'},children:state.analysisMessage||'Reading the project and preparing evidence-based recommendations.'}),
    _jsxs('div',{style:{background:'white',border:'1px solid var(--border)',borderRadius:16,padding:28},children:[
      _jsxs('div',{style:{display:'flex',alignItems:'center',gap:22,marginBottom:26},children:[_jsxs('div',{style:{width:120,height:120,borderRadius:'50%',border:'8px solid #E2E8F0',display:'flex',alignItems:'center',justifyContent:'center',position:'relative'},children:[_jsx('div',{style:{position:'absolute',inset:-8,borderRadius:'50%',background:`conic-gradient(var(--primary) ${progress}%, transparent ${progress}%)`,mask:'radial-gradient(farthest-side, transparent calc(100% - 8px), #000 0)'}}),_jsxs('div',{style:{position:'relative',fontSize:24,fontWeight:900,color:'var(--primary)'},children:[progress,'%']})]}),_jsx('div',{style:{flex:1},children:_jsx('div',{style:{fontSize:14,color:'var(--text-secondary)',lineHeight:1.7},children:'The backend is performing the authoritative analysis. This screen reflects the real job status; no client-side progress is simulated.'})})]}),
      _jsx('div',{children:steps.map(([id,label],i)=>_jsxs('div',{style:{display:'flex',alignItems:'center',gap:10,padding:'11px 0',borderBottom:i<steps.length-1?'1px solid #F1F5F9':'none'},children:[_jsx('div',{style:{width:22,height:22,borderRadius:'50%',background:i<current?'var(--success)':i===current?'var(--primary)':'#F1F5F9',color:i<=current?'white':'#94A3B8',display:'flex',alignItems:'center',justifyContent:'center',fontSize:11,fontWeight:800},children:i<current?'✓':i===current?'•':'○'}),_jsx('span',{style:{fontSize:13.5,fontWeight:i===current?700:500,color:i===current?'var(--primary)':i<current?'var(--success)':'var(--text-muted)'},children:label})]},id))}),
      onCancel && state.currentProjectId && !['WAITING_FOR_USER_CONFIGURATION','COMPLETED','FAILED','CANCELLED'].includes(state.analysisStatus)
        ? _jsx('div',{style:{display:'flex',justifyContent:'flex-end',marginTop:20},children:_jsx('button',{type:'button',disabled:cancelPending,onClick:async()=>{setCancelPending(true);try{await onCancel()}finally{setCancelPending(false)}},style:{padding:'9px 16px',background:'white',color:'var(--text-secondary)',border:'1px solid var(--border)',borderRadius:9,fontWeight:600,cursor:cancelPending?'wait':'pointer',opacity:cancelPending?0.7:1},children:cancelPending?'Cancelling…':'Cancel Analysis'})})
        : null
    ]})
  ]});
}
