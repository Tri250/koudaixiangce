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
    { id: Panel.Portrait, icon: User, title: 'editor.switcher.tooltips.portrait', androidLabel: '人像' },
    { id: Panel.Crop, icon: Crop, title: 'editor.switcher.tooltips.crop', androidLabel: '构图' },
    { id: Panel.Masks, icon: Layers, title: 'editor.switcher.tooltips.masks', androidLabel: '蒙版' },
    { id: Panel.Ai, icon: Paintbrush, title: 'editor.switcher.tooltips.inpaint', androidLabel: 'AI' },
  ],
  [
    { id: Panel.Presets, icon: SwatchBook, title: 'editor.switcher.tooltips.presets', androidLabel: '预设' },
    { id: Panel.Export, icon: FileInput, title: 'editor.switcher.tooltips.export', androidLabel: '导出' },
  ],
];

const androidPanelTabs: Array<PanelOptions> = [
  { id: Panel.Adjustments, icon: SlidersHorizontal, title: 'editor.switcher.tooltips.adjust', androidLabel: '基础' },
  { id: Panel.Metadata, icon: Info, title: 'editor.switcher.tooltips.info', androidLabel: '色彩' },
  { id: Panel.Portrait, icon: User, title: 'editor.switcher.tooltips.portrait', androidLabel: '人像' },
  { id: Panel.Crop, icon: Crop, title: 'editor.switcher.tooltips.crop', androidLabel: '构图' },
  { id: Panel.Masks, icon: Layers, title: 'editor.switcher.tooltips.masks', androidLabel: '蒙版' },
  { id: Panel.Ai, icon: Paintbrush, title: 'editor.switcher.tooltips.inpaint', androidLabel: 'AI' },
  { id: Panel.Presets, icon: SwatchBook, title: 'editor.switcher.tooltips.presets', androidLabel: '预设' },
  { id: Panel.Export, icon: FileInput, title: 'editor.switcher.tooltips.export', androidLabel: '导出' },
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
        className="flex items-center overflow-x-auto bg-bg-secondary border-t border-surface"
        style={{ scrollbarWidth: 'none' as const }}
      >
        {androidPanelTabs.map(({ id, androidLabel }) => (
          <button
            className={`relative flex items-center justify-center shrink-0 px-4 transition-colors duration-200 ${
              activePanel === id ? 'text-accent' : 'text-text-secondary'
            }`}
            key={id}
            onClick={() => onPanelSelect(id)}
            style={{ minHeight: '48px' }}
          >
            <span className="text-xs font-medium whitespace-nowrap">{androidLabel}</span>
            {activePanel === id && (
              <motion.div
                layoutId="android-active-tab-indicator"
                className="absolute bottom-0 left-2 right-2 h-0.5 bg-accent rounded-full"
                transition={isInstantTransition ? { duration: 0 } : { type: 'spring', bounce: 0.2, duration: 0.4 }}
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
              className={`relative rounded-md transition-colors duration-200 ${isHorizontal ? 'p-2 shrink-0' : 'p-2'} ${
                activePanel === id
                  ? 'text-text-primary'
                  : 'text-text-secondary hover:bg-surface hover:text-text-primary'
              }`}
              key={id}
              onClick={() => onPanelSelect(id)}
              data-tooltip={t(title)}
            >
              {activePanel === id && (
                <motion.div
                  layoutId="active-panel-indicator"
                  className="absolute inset-0 bg-surface rounded-md"
                  transition={isInstantTransition ? { duration: 0 } : { type: 'spring', bounce: 0.2, duration: 0.4 }}
                />
              )}
              <Icon size={20} className="relative z-10" />
            </button>
          ))}
        </div>
      ))}
    </div>
  );
}
