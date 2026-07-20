const fs = require('fs');
const path = require('path');

function fixFile(filePath) {
  const fullPath = path.resolve(filePath);
  if (!fs.existsSync(fullPath)) return;
  let content = fs.readFileSync(fullPath, 'utf8');
  let original = content;
  
  // ===== MasksPanel.tsx specific fixes =====
  if (filePath.includes('MasksPanel.tsx')) {
    // SUB_MASK_CONFIG type
    content = content.replace(
      'const SUB_MASK_CONFIG: Record<Mask, any> = {',
      `interface SubMaskParamConfig {
  key: string;
  min: number;
  max: number;
  step: number;
  multiplier?: number;
  defaultValue: number;
}

interface SubMaskConfigEntry {
  parameters?: SubMaskParamConfig[];
  showBrushTools?: boolean;
  showFlowControl?: boolean;
}

const SUB_MASK_CONFIG: Record<Mask, SubMaskConfigEntry> = {`
    );
    
    // BrushTools props - settings: any
    content = content.replace(
      `  settings: any;\n  onSettingsChange: any;\n  onDragStateChange?: (isDragging: boolean) => void;\n}) => {`,
      `  settings: { size: number; feather: number; tool: ToolType };\n  onSettingsChange: (updater: (prev: { size: number; feather: number; tool: ToolType }) => { size: number; feather: number; tool: ToolType }) => void;\n  onDragStateChange?: (isDragging: boolean) => void;\n}) => {`
    );
    
    // onSettingsChange((s: any) => patterns
    content = content.replace(/\(s: any\) => \(\{ \.\.\.s,/g, '(s: { size: number; feather: number; tool: ToolType }) => ({ ...s,');
    
    // FlowBrushTool props
    content = content.replace(
      `  settings: any;\n  onSettingsChange: any;\n  onDragStateChange?: (isDragging: boolean) => void;\n}) => {\n  const { t } = useTranslation();\n\n  return (\n    <div className="space-y-4 border-t border-surface">`,
      `  settings: { size: number; feather: number; tool: ToolType };\n  onSettingsChange: (updater: (prev: { size: number; feather: number; tool: ToolType }) => { size: number; feather: number; tool: ToolType }) => void;\n  onDragStateChange?: (isDragging: boolean) => void;\n}) => {\n  const { t } = useTranslation();\n\n  return (\n    <div className="space-y-4 border-t border-surface">`
    );
    
    // setBrushSettings updater
    content = content.replace(
      '    (updater: any) => {',
      '    (updater: ((prev: { size: number; feather: number; tool: ToolType }) => { size: number; feather: number; tool: ToolType }) | { size: number; feather: number; tool: ToolType }) => {'
    );
    
    // collapsibleState
    content = content.replace(
      "useState<any>({\n    basic: true,\n    curves: false,\n    color: false,\n    details: false,\n    effects: false,\n  })",
      "useState<Record<string, boolean>>({\n    basic: true,\n    curves: false,\n    color: false,\n    details: false,\n    effects: false,\n  })"
    );
    
    // copiedSectionAdjustments
    content = content.replace(
      'useState<any | null>(null)',
      'useState<{ section: string; values: Record<string, unknown> } | null>(null)'
    );
    
    // setAdjustments((prev: any) => patterns
    content = content.replace(/setAdjustments\(\(prev: any\)/g, 'setAdjustments((prev: Adjustments)');
    
    // buildModeSubmenu (icon: any)
    content = content.replace(
      "(label: string, icon: any, mode: SubMaskMode)",
      "(label: string, icon: React.ComponentType<React.SVGProps<SVGSVGElement>>, mode: SubMaskMode)"
    );
    
    // updateContainer data param
    content = content.replace(
      'const updateContainer = (id: string, data: any)',
      'const updateContainer = (id: string, data: Partial<MaskContainer>)'
    );
    
    // updateSubMask data param
    content = content.replace(
      'const updateSubMask = (id: string, data: any)',
      'const updateSubMask = (id: string, data: Partial<SubMask>)'
    );
    
    // DraggableGridItem props
    content = content.replace(
      'function DraggableGridItem({ maskType, onClick, onRightClick, isDraggable, activeMaskContainerId }: any)',
      'function DraggableGridItem({ maskType, onClick, onRightClick, isDraggable, activeMaskContainerId }: { maskType: MaskType; onClick: () => void; onRightClick?: (e: React.MouseEvent) => void; isDraggable: boolean; activeMaskContainerId: string | null })'
    );
    
    // MaskContainerRow props
    content = content.replace(
      /  setIsMaskControlHovered,\n  onAddComponent,\n\}: any\) \{/,
      '  setIsMaskControlHovered,\n  onAddComponent,\n}: {\n  container: MaskContainer;\n  adjustments: Adjustments;\n  setAdjustments: (updater: Adjustments | ((prev: Adjustments) => Adjustments)) => void;\n  activeSubMask: SubMask | null;\n  brushSettings: { size: number; feather: number; tool: ToolType } | null;\n  handleDeleteSubMask: (id: string) => void;\n  handlePasteSubMask: (containerId: string) => void;\n  copySubMaskToClipboard: (subMask: SubMask) => void;\n  copiedSubMask: SubMask | null;\n  analyzingSubMaskId: string | null;\n  setIsMaskControlHovered: (hovered: boolean) => void;\n  onAddComponent: (type: Mask, mode: SubMaskMode, targetContainerId?: string) => void;\n}) {'
    );
    
    // prev.masks.map((m: any)
    content = content.replace('(m: any)', '(m: MaskContainer)');
    
    // SubMaskRow props
    content = content.replace(
      /  setIsMaskControlHovered,\n\}: any\) \{/,
      '  setIsMaskControlHovered,\n}: {\n  subMask: SubMask;\n  containerId: string;\n  handleDeleteSubMask: (id: string) => void;\n  handlePasteSubMask: (containerId: string) => void;\n  copySubMaskToClipboard: (subMask: SubMask) => void;\n  hasCopiedSubMask: boolean;\n  activeDragItem: DragData | null;\n  analyzingSubMaskId: string | null;\n  renamingId: string | null;\n  setRenamingId: (id: string | null) => void;\n  tempName: string;\n  setTempName: (name: string) => void;\n  setIsMaskControlHovered: (hovered: boolean) => void;\n}) {'
    );
    
    // MaskSettingsPanel props
    content = content.replace(
      /  handleGenerateAiDepthMask,\n\}: any\) \{/,
      '  handleGenerateAiDepthMask,\n}: {\n  container: MaskContainer | null;\n  activeSubMask: SubMask | null;\n  updateContainer: (id: string, data: Partial<MaskContainer>) => void;\n  updateSubMask: (id: string, data: Partial<SubMask>) => void;\n  brushSettings: { size: number; feather: number; tool: ToolType } | null;\n  setBrushSettings: (updater: ((prev: { size: number; feather: number; tool: ToolType }) => { size: number; feather: number; tool: ToolType }) | { size: number; feather: number; tool: ToolType }) => void;\n  appSettings: Record<string, unknown>;\n  collapsibleState: Record<string, boolean>;\n  setCollapsibleState: React.Dispatch<React.SetStateAction<Record<string, boolean>>>;\n  copiedSectionAdjustments: { section: string; values: Record<string, unknown> } | null;\n  setCopiedSectionAdjustments: React.Dispatch<React.SetStateAction<{ section: string; values: Record<string, unknown> } | null>>;\n  onDragStateChange: (isDragging: boolean) => void;\n  isSettingsSectionOpen: boolean;\n  setSettingsSectionOpen: React.Dispatch<React.SetStateAction<boolean>>;\n  presets: unknown[];\n  handleGenerateAiDepthMask: () => void;\n}) {'
    );
    
    // generatePresetSubmenu (item: any) -> (item: Record<string, unknown>)
    content = content.replace('.map((item: any)', '.map((item: Record<string, unknown>)');
    
    // handleMaskPropertyChange value
    content = content.replace(
      'const handleMaskPropertyChange = (key: string, value: any)',
      'const handleMaskPropertyChange = (key: string, value: unknown)'
    );
    
    // setMaskContainerAdjustments updater
    content = content.replace(
      'const setMaskContainerAdjustments = (updater: any)',
      'const setMaskContainerAdjustments = (updater: MaskAdjustments | ((prev: MaskAdjustments) => MaskAdjustments))'
    );
    
    // setCollapsibleState((prev: any) patterns
    content = content.replace(/setCollapsibleState\(\(prev: any\)/g, 'setCollapsibleState((prev: Record<string, boolean>)');
    
    // handleSectionContextMenu event
    content = content.replace(
      'const handleSectionContextMenu = (event: any, sectionName: string)',
      'const handleSectionContextMenu = (event: React.MouseEvent<HTMLElement>, sectionName: string)'
    );
    
    // setMaskContainerAdjustments((prev: any) patterns
    content = content.replace(/setMaskContainerAdjustments\(\(prev: any\)/g, 'setMaskContainerAdjustments((prev: MaskAdjustments)');
    
    // resetValues: any
    content = content.replace('const resetValues: any = {}', 'const resetValues: Record<string, unknown> = {}');
    
    // SectionComponent: any
    content = content.replace(
      'const SectionComponent: any = {',
      'const SectionComponent: React.ComponentType<Record<string, unknown>> | undefined = {'
    );
    
    // subMaskConfig.parameters?.map((param: any)
    content = content.replace('(param: any)', '(param: SubMaskParamConfig)');
    
    // t('editor.masks.params.' + param.key as unknown) - fix the cast
    content = content.replace(
      "t('editor.masks.params.' + param.key as unknown)",
      "t(`editor.masks.params.${param.key}` as const)"
    );
  }
  
  // ===== Generic patterns across all files =====
  
  // (prev: any) => in setState calls for Adjustments
  content = content.replace(/\(prev: any\) => \(\{ \.\.\.prev,/g, '(prev: Adjustments) => ({ ...prev,');
  
  if (content !== original) {
    fs.writeFileSync(fullPath, content, 'utf8');
    console.log(`FIXED: ${filePath}`);
  } else {
    console.log(`NO CHANGE: ${filePath}`);
  }
}

// Process files
const targetFiles = [
  'src/components/panel/right/MasksPanel.tsx',
];

for (const file of targetFiles) {
  fixFile(file);
}
