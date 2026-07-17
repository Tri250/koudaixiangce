import { useRef, useEffect } from 'react';
import { motion } from 'framer-motion';
import {
  SlidersHorizontal,
  Info,
  Crop,
  Layers,
  Paintbrush,
  SwatchBook,
  FileInput,
  Search,
  User,
  type LucideIcon,
} from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Panel } from '../../ui/AppProperties';
import { useSettingsStore } from '../../../store/useSettingsStore';

interface PanelOptions {
  icon: LucideIcon;
  id: Panel;
  title: string;
  androidLabel?: string;
}

interface RightPanelSwitcherProps {
  activePanel: Panel | null;
  onPanelSelect(id: Panel): void;
  isInstantTransition: boolean;
  layout?: 'horizontal' | 'vertical';
}

const panelGroups: Array<Array<PanelOptions>> = [
  [{ id: Panel.Metadata, icon: Info, title: 'editor.switcher.tooltips.info' }],
  [
    { id: Panel.Adjustments, icon: SlidersHorizontal, title: 'editor.switcher.tooltips.adjust', androidLabel: '基础' },
    { id: Panel.Crop, icon: Crop, title: 'editor.switcher.tooltips.crop', androidLabel: '构图' },
    { id: Panel.Masks, icon: Layers, title: 'editor.switcher.tooltips.masks', androidLabel: '蒙版' },
    { id: Panel.Ai, icon: Paintbrush, title: 'editor.switcher.tooltips.inpaint', androidLabel: 'AI' },
    { id: Panel.Portrait, icon: User, title: 'editor.switcher.tooltips.portrait', androidLabel: '人像' },
  ],
  [
    { id: Panel.Presets, icon: SwatchBook, title: 'editor.switcher.tooltips.presets', androidLabel: '预设' },
    { id: Panel.Export, icon: FileInput, title: 'editor.switcher.tooltips.export', androidLabel: '导出' },
  ],
  [
    { id: Panel.SemanticSearch, icon: Search, title: 'settings.semanticSearch.title', androidLabel: '语义搜索' },
  ],
];

// Android-specific flat tab list with color tab included
const androidPanelTabs: Array<PanelOptions> = [
  { id: Panel.Adjustments, icon: SlidersHorizontal, title: 'editor.switcher.tooltips.adjust', androidLabel: '基础' },
  { id: Panel.Metadata, icon: Info, title: 'editor.switcher.tooltips.info', androidLabel: '色彩' },
  { id: Panel.Crop, icon: Crop, title: 'editor.switcher.tooltips.crop', androidLabel: '构图' },
  { id: Panel.Masks, icon: Layers, title: 'editor.switcher.tooltips.masks', androidLabel: '蒙版' },
  { id: Panel.Ai, icon: Paintbrush, title: 'editor.switcher.tooltips.inpaint', androidLabel: 'AI' },
  { id: Panel.Portrait, icon: User, title: 'editor.switcher.tooltips.portrait', androidLabel: '人像' },
  { id: Panel.Presets, icon: SwatchBook, title: 'editor.switcher.tooltips.presets', androidLabel: '预设' },
  { id: Panel.Export, icon: FileInput, title: 'editor.switcher.tooltips.export', androidLabel: '导出' },
  { id: Panel.SemanticSearch, icon: Search, title: 'settings.semanticSearch.title', androidLabel: '语义搜索' },
];

export default function RightPanelSwitcher({
  activePanel,
  onPanelSelect,
  isInstantTransition,
  layout = 'vertical',
}: RightPanelSwitcherProps) {
  const { t } = useTranslation();
  const osPlatform = useSettingsStore((state: { osPlatform: string }) => state.osPlatform);
  const isAndroid = osPlatform === 'android';
  const isHorizontal = layout === 'horizontal';
  const scrollRef = useRef<HTMLDivElement>(null);

  // Smooth momentum scrolling for Android tabs
  useEffect(() => {
    if (isAndroid && scrollRef.current) {
      (scrollRef.current.style as unknown as Record<string, string>)['webkitOverflowScrolling'] = 'touch';
    }
  }, [isAndroid]);

  // Android: horizontal scrollable text tabs
  if (isAndroid) {
    return (
      <div
        ref={scrollRef}
        className="flex items-center overflow-x-auto liquid-glass-subtle"
        style={{ scrollbarWidth: 'none' as const }}
      >
        {androidPanelTabs.map(({ id, androidLabel }) => (
          <button
            className={`relative flex items-center justify-center shrink-0 px-5 py-2 transition-all duration-200 ease-[cubic-bezier(0.22,1,0.36,1)] rounded-lg mx-0.5 ${
              activePanel === id ? 'text-accent bg-card-active/50' : 'text-text-secondary hover:text-text-primary hover:bg-card-active/25'
            }`}
            key={id}
            onClick={() => onPanelSelect(id)}
            style={{ minHeight: '48px' }}
          >
            <span className="text-[11px] font-medium whitespace-nowrap tracking-wide">{androidLabel}</span>
            {activePanel === id && (
              <motion.div
                layoutId="android-active-tab-indicator"
                className="absolute bottom-1 left-3 right-3 h-[2px] bg-accent rounded-full shadow-[0_0_8px_rgba(0,128,108,0.4)]"
                transition={isInstantTransition ? { duration: 0 } : { type: 'spring', bounce: 0.15, stiffness: 400, damping: 25 }}
              />
            )}
          </button>
        ))}
      </div>
    );
  }

  // Desktop: existing icon-based layout
  return (
    <div className={isHorizontal ? 'flex items-center overflow-x-auto p-1 gap-1' : 'flex flex-col p-1 gap-1 h-full'}>
      {panelGroups.map((group, groupIndex) => (
        <div key={groupIndex} className={isHorizontal ? 'flex items-center gap-1' : 'flex flex-col gap-1'}>
          {groupIndex > 0 && (
            <div
              className={isHorizontal ? 'w-px h-6 bg-surface self-stretch my-auto' : 'w-6 h-px bg-surface self-center'}
            />
          )}
          {group.map(({ id, icon: Icon, title }) => (
            <button
              className={`relative rounded-lg transition-all duration-200 ease-out ${isHorizontal ? 'p-2 shrink-0' : 'p-2'} ${
                activePanel === id
                  ? 'text-text-primary'
                  : 'text-text-secondary/70 hover:bg-surface/80 hover:text-text-primary'
              }`}
              key={id}
              onClick={() => onPanelSelect(id)}
              data-tooltip={t(title)}
            >
              {activePanel === id && (
                <motion.div
                  layoutId="active-panel-indicator"
                  className="absolute inset-[3px] bg-surface rounded-md shadow-sm"
                  transition={isInstantTransition ? { duration: 0 } : { type: 'spring', bounce: 0.15, duration: 0.4 }}
                />
              )}
              <Icon size={20} className="relative z-10" strokeWidth={activePanel === id ? 2.2 : 1.8} />
            </button>
          ))}
        </div>
      ))}
    </div>
  );
}
