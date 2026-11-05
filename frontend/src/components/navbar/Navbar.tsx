import React, { useState, useRef, useEffect, Suspense } from 'react';
import {
  Play,
  Save,
  Plus,
  FileText,
  Layers,
  Terminal,
  Server,
  Sparkles,
  ChevronDown,
  Sliders,
  Trash2,
  History,
  AlertTriangle,
  Sun,
  Moon,
  Laptop,
  Check
} from 'lucide-react';
import { TestWorkflow, WorkflowExecution } from '../../types/workflow';
import { useTheme, Theme } from '../../context/ThemeContext';

const ExecutionHistoryModal = React.lazy(() =>
  import('./ExecutionHistoryModal').then((m) => ({ default: m.ExecutionHistoryModal }))
);

interface NavbarProps {
  workflows: TestWorkflow[];
  selectedWorkflow: TestWorkflow | null;
  onSelectWorkflow: (workflow: TestWorkflow) => void;
  onCreateNewWorkflow: () => void;
  onOpenSettings: () => void;
  onSaveWorkflow: () => void;
  onDeleteWorkflow: (workflowId: string) => void;
  onRunWorkflow: () => void;
  isRunning: boolean;
  environment: string;
  onEnvironmentChange: (env: string) => void;
  latestExecution: WorkflowExecution | null;
  workflowExecutions?: WorkflowExecution[];
  onViewReport: (url: string) => void;
  isSidebarOpen: boolean;
  onToggleSidebar: () => void;
  isConsoleOpen: boolean;
  onToggleConsole: () => void;
}

export const Navbar: React.FC<NavbarProps> = ({
  workflows,
  selectedWorkflow,
  onSelectWorkflow,
  onCreateNewWorkflow,
  onOpenSettings,
  onSaveWorkflow,
  onDeleteWorkflow,
  onRunWorkflow,
  isRunning,
  environment,
  onEnvironmentChange,
  latestExecution,
  workflowExecutions = [],
  onViewReport,
  isSidebarOpen,
  onToggleSidebar,
  isConsoleOpen,
  onToggleConsole,
}) => {
  const { theme, resolvedTheme, setTheme } = useTheme();
  const [isThemeMenuOpen, setIsThemeMenuOpen] = useState(false);
  const themeMenuRef = useRef<HTMLDivElement>(null);

  const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false);
  const [isHistoryModalOpen, setIsHistoryModalOpen] = useState(false);

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (themeMenuRef.current && !themeMenuRef.current.contains(e.target as Node)) {
        setIsThemeMenuOpen(false);
      }
    };
    if (isThemeMenuOpen) {
      document.addEventListener('mousedown', handleClickOutside);
      return () => document.removeEventListener('mousedown', handleClickOutside);
    }
  }, [isThemeMenuOpen]);

  const confirmDelete = () => {
    if (selectedWorkflow) {
      onDeleteWorkflow(selectedWorkflow.id);
      setIsDeleteModalOpen(false);
    }
  };

  return (
    <>
      <header className="h-14 bg-white dark:bg-slate-900 border-b border-slate-200 dark:border-slate-800 flex items-center justify-between px-4 z-30 select-none shrink-0 transition-colors">
        {/* Brand & Catalog toggle */}
        <div className="flex items-center gap-3">
          <button
            onClick={onToggleSidebar}
            title="Toggle API Catalog"
            className={`p-2 rounded-lg border transition-all ${
              isSidebarOpen
                ? 'bg-blue-600/10 border-blue-500/30 text-blue-500 dark:text-blue-400'
                : 'border-slate-200 dark:border-slate-800 text-slate-500 dark:text-slate-400 hover:text-slate-800 dark:hover:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-800'
            }`}
          >
            <Layers className="w-4 h-4" />
          </button>

          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-lg bg-gradient-to-tr from-blue-600 via-indigo-600 to-emerald-500 flex items-center justify-center shadow-lg shadow-blue-500/20">
              <Sparkles className="w-4 h-4 text-white" />
            </div>
            <div>
              <div className="flex items-center gap-1.5">
                <span className="font-bold text-xs tracking-wider text-slate-900 dark:text-slate-100 uppercase">MeterFlow</span>
                <span className="text-[10px] font-mono font-semibold px-1.5 py-0.2 rounded bg-blue-500/20 text-blue-600 dark:text-blue-400 border border-blue-500/30">
                  Karate E2E
                </span>
              </div>
              <p className="text-[10px] text-slate-500 dark:text-slate-400 hidden sm:block">Self-Hosted Visual Test Orchestrator</p>
            </div>
          </div>
        </div>

        {/* Middle: Workflow Selector, New Workflow, Settings, Save, Delete */}
        <div className="flex items-center gap-2">
          <div className="relative">
            <select
              value={selectedWorkflow?.id || ''}
              onChange={(e) => {
                const wf = workflows.find((w) => w.id === e.target.value);
                if (wf) onSelectWorkflow(wf);
              }}
              className="appearance-none bg-slate-50 dark:bg-slate-950 border border-slate-200 dark:border-slate-800 hover:border-slate-300 dark:hover:border-slate-700 text-slate-800 dark:text-slate-200 text-xs font-medium rounded-lg pl-3 pr-8 py-1.5 focus:outline-none focus:border-blue-500 cursor-pointer min-w-[200px]"
            >
              {workflows.map((wf) => (
                <option key={wf.id} value={wf.id}>
                  {wf.name} (v{wf.version})
                </option>
              ))}
            </select>
            <ChevronDown className="w-3.5 h-3.5 absolute right-2.5 top-1/2 -translate-y-1/2 text-slate-400 pointer-events-none" />
          </div>

          <button
            onClick={onCreateNewWorkflow}
            title="Create New Workflow with Custom Name"
            className="flex items-center gap-1 px-2.5 py-1.5 rounded-lg border border-slate-200 dark:border-slate-800 hover:border-slate-300 dark:hover:border-slate-700 bg-slate-50 dark:bg-slate-950 text-slate-700 dark:text-slate-300 hover:text-slate-900 dark:hover:text-white text-xs font-medium transition-colors"
          >
            <Plus className="w-3.5 h-3.5 text-blue-500 dark:text-blue-400" />
            <span className="hidden md:inline">New Flow</span>
          </button>

          <button
            onClick={onOpenSettings}
            title="Workflow Settings, Headers, Dynamic Macros & Secrets"
            className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg border border-slate-200 dark:border-slate-800 hover:border-slate-300 dark:hover:border-slate-700 bg-slate-50 dark:bg-slate-950 text-slate-700 dark:text-slate-300 hover:text-slate-900 dark:hover:text-white text-xs font-medium transition-colors"
          >
            <Sliders className="w-3.5 h-3.5 text-indigo-500 dark:text-indigo-400" />
            <span className="hidden md:inline">Settings</span>
          </button>

          <button
            onClick={onSaveWorkflow}
            title="Save Flow Changes"
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-slate-200 dark:border-slate-800 hover:border-slate-300 dark:hover:border-slate-700 bg-slate-50 dark:bg-slate-950 text-slate-700 dark:text-slate-300 hover:text-slate-900 dark:hover:text-white text-xs font-medium transition-colors"
          >
            <Save className="w-3.5 h-3.5 text-emerald-500 dark:text-emerald-400" />
            <span className="hidden md:inline">Save</span>
          </button>

          {/* Delete Workflow Action */}
          <button
            onClick={() => setIsDeleteModalOpen(true)}
            title="Delete this workflow"
            disabled={!selectedWorkflow}
            className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg border border-slate-200 dark:border-slate-800 hover:border-rose-500/50 hover:bg-rose-500/10 text-slate-500 dark:text-slate-400 hover:text-rose-500 dark:hover:text-rose-400 text-xs font-medium transition-colors disabled:opacity-40"
          >
            <Trash2 className="w-3.5 h-3.5" />
            <span className="hidden lg:inline">Delete</span>
          </button>
        </div>

        {/* Right: Environment, Runs History, Report, Run, Theme, Console */}
        <div className="flex items-center gap-2.5">
          {/* Environment Picker */}
          <div className="hidden lg:flex items-center gap-1.5 bg-slate-50 dark:bg-slate-950 border border-slate-200 dark:border-slate-800 rounded-lg px-2 py-1 text-xs">
            <Server className="w-3 h-3 text-slate-400" />
            <select
              value={environment}
              onChange={(e) => onEnvironmentChange(e.target.value)}
              className="bg-transparent text-slate-700 dark:text-slate-300 text-xs focus:outline-none cursor-pointer"
            >
              <option value="default">Env: Local (Mock)</option>
              <option value="staging">Env: Staging</option>
              <option value="production">Env: Production</option>
            </select>
          </div>

          {/* Runs History Modal Trigger */}
          <button
            onClick={() => setIsHistoryModalOpen(true)}
            title="View Execution Runs & Past Reports"
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-slate-200 dark:border-slate-800 hover:border-slate-300 dark:hover:border-slate-700 bg-slate-50 dark:bg-slate-950 text-slate-700 dark:text-slate-300 hover:text-slate-900 dark:hover:text-white text-xs font-medium transition-colors"
          >
            <History className="w-3.5 h-3.5 text-blue-500 dark:text-blue-400" />
            <span className="hidden sm:inline">Runs</span>
            {workflowExecutions.length > 0 && (
              <span className="px-1.5 py-0.2 rounded-full text-[10px] font-mono font-semibold bg-blue-500/20 text-blue-600 dark:text-blue-400 border border-blue-500/30">
                {workflowExecutions.length}
              </span>
            )}
          </button>

          {/* View Latest Report Button */}
          {latestExecution?.reportPath && (
            <button
              onClick={() => onViewReport(latestExecution.reportPath!)}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-emerald-600/10 hover:bg-emerald-600/20 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20 text-xs font-semibold transition-colors shadow"
              title="View latest profiling & Karate test report"
            >
              <FileText className="w-3.5 h-3.5" />
              <span className="hidden sm:inline">HTML Report</span>
            </button>
          )}

          {/* Theme Mode Switcher */}
          <div className="relative" ref={themeMenuRef}>
            <button
              onClick={() => setIsThemeMenuOpen(!isThemeMenuOpen)}
              title={`Theme: ${theme.charAt(0).toUpperCase() + theme.slice(1)} (${resolvedTheme})`}
              className="flex items-center justify-center p-2 rounded-lg border border-slate-200 dark:border-slate-800 hover:border-slate-300 dark:hover:border-slate-700 bg-slate-50 dark:bg-slate-950 text-slate-700 dark:text-slate-300 hover:text-slate-900 dark:hover:text-white transition-colors"
            >
              {theme === 'light' ? (
                <Sun className="w-4 h-4 text-amber-500" />
              ) : theme === 'dark' ? (
                <Moon className="w-4 h-4 text-blue-400" />
              ) : (
                <Laptop className="w-4 h-4 text-emerald-500" />
              )}
            </button>

            {isThemeMenuOpen && (
              <div className="absolute right-0 mt-2 w-44 rounded-xl bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 shadow-2xl py-1 z-50 text-xs text-slate-700 dark:text-slate-200 animate-in fade-in duration-100">
                <div className="px-3 py-1.5 text-[10px] font-semibold tracking-wider text-slate-400 uppercase border-b border-slate-100 dark:border-slate-800">
                  Appearance
                </div>
                <button
                  onClick={() => { setTheme('light'); setIsThemeMenuOpen(false); }}
                  className={`w-full flex items-center justify-between px-3 py-2 text-left hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors ${
                    theme === 'light' ? 'text-amber-500 font-semibold' : ''
                  }`}
                >
                  <span className="flex items-center gap-2">
                    <Sun className="w-3.5 h-3.5 text-amber-500" />
                    Light
                  </span>
                  {theme === 'light' && <Check className="w-3.5 h-3.5" />}
                </button>
                <button
                  onClick={() => { setTheme('dark'); setIsThemeMenuOpen(false); }}
                  className={`w-full flex items-center justify-between px-3 py-2 text-left hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors ${
                    theme === 'dark' ? 'text-blue-400 font-semibold' : ''
                  }`}
                >
                  <span className="flex items-center gap-2">
                    <Moon className="w-3.5 h-3.5 text-blue-400" />
                    Dark
                  </span>
                  {theme === 'dark' && <Check className="w-3.5 h-3.5" />}
                </button>
                <button
                  onClick={() => { setTheme('system'); setIsThemeMenuOpen(false); }}
                  className={`w-full flex items-center justify-between px-3 py-2 text-left hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors ${
                    theme === 'system' ? 'text-emerald-500 font-semibold' : ''
                  }`}
                >
                  <span className="flex items-center gap-2">
                    <Laptop className="w-3.5 h-3.5 text-emerald-500" />
                    System ({resolvedTheme})
                  </span>
                  {theme === 'system' && <Check className="w-3.5 h-3.5" />}
                </button>
              </div>
            )}
          </div>

          {/* Run Workflow CTA */}
          <button
            onClick={onRunWorkflow}
            disabled={isRunning || !selectedWorkflow}
            className="flex items-center gap-2 px-4 py-1.5 rounded-lg bg-gradient-to-r from-emerald-600 to-emerald-500 hover:from-emerald-500 hover:to-emerald-400 text-white text-xs font-semibold transition-all shadow-lg shadow-emerald-600/20 disabled:opacity-50"
          >
            {isRunning ? (
              <div className="w-3.5 h-3.5 border-2 border-white/30 border-t-white rounded-full animate-spin" />
            ) : (
              <Play className="w-3.5 h-3.5 fill-white" />
            )}
            <span>{isRunning ? 'Executing...' : 'Run Flow'}</span>
          </button>

          {/* Console toggle button */}
          <button
            onClick={onToggleConsole}
            title="Toggle Execution Console"
            className={`p-2 rounded-lg border transition-all ${
              isConsoleOpen
                ? 'bg-blue-600/10 border-blue-500/30 text-blue-500 dark:text-blue-400'
                : 'border-slate-200 dark:border-slate-800 text-slate-500 dark:text-slate-400 hover:text-slate-800 dark:hover:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-800'
            }`}
          >
            <Terminal className="w-4 h-4" />
          </button>
        </div>
      </header>

      {/* Delete Confirmation Modal */}
      {isDeleteModalOpen && selectedWorkflow && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm animate-fade-in select-none">
          <div className="relative w-full max-w-md bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-2xl shadow-2xl p-6 text-slate-900 dark:text-slate-100 space-y-4">
            <div className="flex items-start gap-3">
              <div className="p-2.5 rounded-xl bg-rose-500/10 text-rose-500 dark:text-rose-400 border border-rose-500/20 shrink-0">
                <AlertTriangle className="w-6 h-6" />
              </div>
              <div>
                <h3 className="font-bold text-base text-slate-900 dark:text-slate-100">Delete Workflow</h3>
                <p className="text-xs text-slate-500 dark:text-slate-400 mt-1">
                  Are you sure you want to permanently delete{' '}
                  <span className="font-semibold text-rose-500 dark:text-rose-300">"{selectedWorkflow.name}"</span>?
                </p>
                <p className="text-[11px] text-slate-400 dark:text-slate-500 mt-2">
                  This will remove all canvas nodes, AST configurations, and cascade delete all associated execution runs and test reports. This action cannot be undone.
                </p>
              </div>
            </div>

            <div className="flex items-center justify-end gap-3 pt-3 border-t border-slate-200 dark:border-slate-800">
              <button
                onClick={() => setIsDeleteModalOpen(false)}
                className="px-4 py-2 rounded-xl bg-slate-100 dark:bg-slate-800 hover:bg-slate-200 dark:hover:bg-slate-700 text-slate-700 dark:text-slate-300 text-xs font-semibold transition-colors"
              >
                Cancel
              </button>
              <button
                onClick={confirmDelete}
                className="flex items-center gap-1.5 px-4 py-2 rounded-xl bg-rose-600 hover:bg-rose-500 text-white text-xs font-semibold shadow-lg shadow-rose-600/20 transition-all"
              >
                <Trash2 className="w-4 h-4" />
                <span>Delete Workflow</span>
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Execution Runs History Modal */}
      {isHistoryModalOpen && (
        <Suspense fallback={null}>
          <ExecutionHistoryModal
            isOpen={isHistoryModalOpen}
            onClose={() => setIsHistoryModalOpen(false)}
            workflowName={selectedWorkflow?.name || 'Workflow'}
            executions={workflowExecutions}
            onViewReport={onViewReport}
          />
        </Suspense>
      )}
    </>
  );
};
