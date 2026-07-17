import {
  Brush,
  BringToFront,
  Circle,
  Cloud,
  Droplet,
  Droplets,
  Eraser,
  MoreHorizontal,
  RectangleHorizontal,
  Sparkles,
  TriangleRight,
  User,
  Sun,
  Stamp,
  Bandage,
} from 'lucide-react';
import i18n from 'i18next';

export enum Mask {
  AiDepth = 'ai-depth',
  AiForeground = 'ai-foreground',
  AiSky = 'ai-sky',
  AiSubject = 'ai-subject',
  All = 'all',
  Brush = 'brush',
  Flow = 'flow',
  Color = 'color',
  Linear = 'linear',
  Luminance = 'luminance',
  QuickEraser = 'quick-eraser',
  Radial = 'radial',
  Clone = 'clone',
  Heal = 'heal',
}

export enum SubMaskMode {
  Additive = 'additive',
  Subtractive = 'subtractive',
  Intersect = 'intersect',
}

export enum ToolType {
  AiSeletor = 'ai-selector',
  Brush = 'brush',
  Eraser = 'eraser',
  GenerativeReplace = 'generative-replace',
  SelectSubject = 'select-subject',
}

export interface MaskType {
  disabled: boolean;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  icon: any;
  id?: string;
  name: string;
  type: Mask;
}

export interface SubMask {
  id: string;
  invert: boolean;
  mode: SubMaskMode;
  name?: string;
  opacity: number;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  parameters?: any;
  type: Mask;
  visible: boolean;
}

export function formatMaskTypeName(type: string): string {
  const keyMap: Record<string, string> = {
    [Mask.AiDepth]: 'masks.types.depth',
    [Mask.AiSubject]: 'masks.types.subject',
    [Mask.AiForeground]: 'masks.types.foreground',
    [Mask.AiSky]: 'masks.types.sky',
    [Mask.All]: 'masks.types.all',
    [Mask.QuickEraser]: 'masks.types.quickEraser',
    [Mask.Brush]: 'masks.types.brush',
    [Mask.Flow]: 'masks.types.flow',
    [Mask.Color]: 'masks.types.color',
    [Mask.Linear]: 'masks.types.linear',
    [Mask.Luminance]: 'masks.types.luminance',
    [Mask.Radial]: 'masks.types.radial',
  };
  const key = keyMap[type];
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const translate = (k: string) => (i18n.t as any)(k) as string;
  if (key) return translate(key);
  if (type === Mask.Clone) return translate('masks.types.clone');
  if (type === Mask.Heal) return translate('masks.types.heal');
  return type.charAt(0).toUpperCase() + type.slice(1);
}

export function getMaskTypeName(mask: MaskType) {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const translate = (k: string) => (i18n.t as any)(k) as string;
  if (mask.id === 'others') return translate('masks.types.others');
  if (mask.type === Mask.QuickEraser && mask.name === 'Quick Erase') {
    return translate('masks.types.quickErase');
  }
  return formatMaskTypeName(mask.type);
}

export function getSubMaskName(subMask: Pick<SubMask, 'name' | 'type'>) {
  return subMask.name?.trim() || formatMaskTypeName(subMask.type);
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export const MASK_ICON_MAP: Record<Mask, any> = {
  [Mask.AiDepth]: BringToFront,
  [Mask.AiForeground]: User,
  [Mask.AiSky]: Cloud,
  [Mask.AiSubject]: Sparkles,
  [Mask.All]: RectangleHorizontal,
  [Mask.Brush]: Brush,
  [Mask.Flow]: Droplets,
  [Mask.Color]: Droplet,
  [Mask.Linear]: TriangleRight,
  [Mask.Luminance]: Sparkles,
  [Mask.QuickEraser]: Eraser,
  [Mask.Radial]: Circle,
  [Mask.Clone]: Stamp,
  [Mask.Heal]: Bandage,
};

export const MASK_PANEL_CREATION_TYPES: Array<MaskType> = [
  {
    disabled: false,
    icon: Sparkles,
    name: 'Subject',
    type: Mask.AiSubject,
  },
  {
    disabled: false,
    icon: Cloud,
    name: 'Sky',
    type: Mask.AiSky,
  },
  {
    disabled: false,
    icon: User,
    name: 'Foreground',
    type: Mask.AiForeground,
  },
  {
    disabled: false,
    icon: TriangleRight,
    name: 'Linear',
    type: Mask.Linear,
  },
  {
    disabled: false,
    icon: Circle,
    name: 'Radial',
    type: Mask.Radial,
  },
  {
    disabled: false,
    icon: MoreHorizontal,
    id: 'others',
    name: 'Others',
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    type: null as any,
  },
];

export const AI_MANUAL_CLEANUP_TYPES: Array<MaskType> = [
  {
    disabled: false,
    icon: Stamp,
    name: 'Clone',
    type: Mask.Clone,
  },
  {
    disabled: false,
    icon: Bandage,
    name: 'Heal',
    type: Mask.Heal,
  },
];

export const AI_GENERATIVE_CREATION_TYPES: Array<MaskType> = [
  {
    disabled: false,
    icon: Eraser,
    name: 'Quick Erase',
    type: Mask.QuickEraser,
  },
  {
    disabled: false,
    icon: Sparkles,
    name: 'Subject',
    type: Mask.AiSubject,
  },
  {
    disabled: false,
    icon: User,
    name: 'Foreground',
    type: Mask.AiForeground,
  },
  {
    disabled: false,
    icon: Brush,
    name: 'Brush',
    type: Mask.Brush,
  },
  {
    disabled: false,
    icon: TriangleRight,
    name: 'Linear',
    type: Mask.Linear,
  },
  {
    disabled: false,
    icon: Circle,
    name: 'Radial',
    type: Mask.Radial,
  },
];

export const SUB_MASK_COMPONENT_TYPES: Array<MaskType> = [
  {
    disabled: false,
    icon: Sparkles,
    name: 'Subject',
    type: Mask.AiSubject,
  },
  {
    disabled: false,
    icon: Cloud,
    name: 'Sky',
    type: Mask.AiSky,
  },
  {
    disabled: false,
    icon: User,
    name: 'Foreground',
    type: Mask.AiForeground,
  },
  {
    disabled: false,
    icon: TriangleRight,
    name: 'Linear',
    type: Mask.Linear,
  },
  {
    disabled: false,
    icon: Circle,
    name: 'Radial',
    type: Mask.Radial,
  },
  {
    disabled: false,
    icon: MoreHorizontal,
    id: 'others',
    name: 'Others',
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    type: null as any,
  },
];

export const OTHERS_MASK_TYPES: Array<MaskType> = [
  {
    disabled: false,
    icon: BringToFront,
    name: 'Depth',
    type: Mask.AiDepth,
  },
  {
    disabled: false,
    icon: Droplet,
    name: 'Color',
    type: Mask.Color,
  },
  {
    disabled: false,
    icon: Sun,
    name: 'Luminance',
    type: Mask.Luminance,
  },
  {
    disabled: false,
    icon: Brush,
    name: 'Brush',
    type: Mask.Brush,
  },
  {
    disabled: false,
    icon: Droplets,
    name: 'Flow',
    type: Mask.Flow,
  },
  {
    disabled: false,
    icon: RectangleHorizontal,
    name: 'Whole Image',
    type: Mask.All,
  },
];

export const AI_SUB_MASK_COMPONENT_TYPES: Array<MaskType> = [
  ...AI_MANUAL_CLEANUP_TYPES,
  ...AI_GENERATIVE_CREATION_TYPES,
];
