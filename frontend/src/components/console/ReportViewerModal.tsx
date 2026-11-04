import React from 'react';
import { X, ExternalLink, RefreshCw, FileText, CheckCircle, XCircle } from 'lucide-react';

interface ReportViewerModalProps {
  isOpen: boolean;
  onClose: () => void;
  reportUrl: string | null;
  workflowName: string;
}

export const ReportViewerModal: React.FC<ReportViewerModalProps> = ({
  isOpen,
  onClose,
  reportUrl,
  workflowName,
}) => {
  const [activeReportTab, setActiveReportTab] = React.useState<'profiling' | 'karate' | 'timeline'>('profiling');

  if (!isOpen || !reportUrl) return null;

  const baseReportPath = reportUrl.substring(0, reportUrl.lastIndexOf('/'));
  const currentIframeSrc =
    activeReportTab === 'profiling'
      ? `${baseReportPath}/workflow-report.html`
      : activeReportTab === 'karate'
      ? `${baseReportPath}/karate-summary.html`
      : `${baseReportPath}/karate-timeline.html`;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/85 backdrop-blur-sm p-4 select-none">
      <div className="w-full max-w-6xl h-[92vh] bg-slate-900 border border-slate-800 rounded-2xl shadow-2xl overflow-hidden flex flex-col">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-3 border-b border-slate-800 bg-slate-950">
          <div className="flex items-center gap-3">
            <div className="p-2 rounded-lg bg-emerald-500/10 text-emerald-400">
              <FileText className="w-5 h-5" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h3 className="font-semibold text-slate-100 text-sm">Execution & Profiling Reports</h3>
                <span className="px-2 py-0.5 rounded-full text-[10px] font-semibold bg-emerald-500/20 text-emerald-400 border border-emerald-500/30">
                  Java 25 &bull; Karate 2.1.4
                </span>
              </div>
              <p className="text-xs text-slate-400 truncate max-w-md">{workflowName}</p>
            </div>
          </div>

          {/* Report Tab Switcher */}
          <div className="flex items-center bg-slate-900 border border-slate-800 rounded-lg p-0.5">
            <button
              onClick={() => setActiveReportTab('profiling')}
              className={`px-3 py-1.5 rounded-md text-xs font-semibold transition-all ${
                activeReportTab === 'profiling'
                  ? 'bg-blue-600 text-white shadow'
                  : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              📊 Executive Profiling
            </button>
            <button
              onClick={() => setActiveReportTab('karate')}
              className={`px-3 py-1.5 rounded-md text-xs font-semibold transition-all ${
                activeReportTab === 'karate'
                  ? 'bg-blue-600 text-white shadow'
                  : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              🥋 Karate Summary
            </button>
            <button
              onClick={() => setActiveReportTab('timeline')}
              className={`px-3 py-1.5 rounded-md text-xs font-semibold transition-all ${
                activeReportTab === 'timeline'
                  ? 'bg-blue-600 text-white shadow'
                  : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              ⏱️ Timeline
            </button>
          </div>

          <div className="flex items-center gap-2">
            <a
              href={currentIframeSrc}
              target="_blank"
              rel="noopener noreferrer"
              className="flex items-center gap-1 px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-medium transition-colors"
            >
              <ExternalLink className="w-3.5 h-3.5" />
              Open Tab
            </a>
            <button
              onClick={onClose}
              className="p-1.5 rounded-lg text-slate-400 hover:text-slate-200 hover:bg-slate-800 transition-colors"
            >
              <X className="w-5 h-5" />
            </button>
          </div>
        </div>

        {/* Iframe View */}
        <div className="flex-1 bg-slate-950 relative">
          <iframe
            key={currentIframeSrc}
            src={currentIframeSrc}
            title="Workflow Execution Report"
            className="w-full h-full border-0"
          />
        </div>
      </div>
    </div>
  );
};
