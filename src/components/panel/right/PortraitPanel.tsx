import React, { useState, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { User, Scissors, Palette, Sparkles, RotateCcw, Loader2 } from 'lucide-react';
import clsx from 'clsx';
import Slider from '../../ui/Slider';
import CollapsibleSection from '../../ui/CollapsibleSection';
import Switch from '../../ui/Switch';
import Button from '../../ui/Button';
import Dropdown from '../../ui/Dropdown';
import Text from '../../ui/Text';
import { TextColors, TextVariants } from '../../../types/typography';
import { useEditorStore } from '../../../store/useEditorStore';
import {
  useRetouching,
  FaceReshapeParams,
  SkinSmoothingParams,
  BodyReshapeParams,
  HairParams,
} from '../../../hooks/useRetouching';

type PortraitTab = 'face' | 'skin' | 'body' | 'hair';

const TAB_CONFIG: { id: PortraitTab; icon: typeof User; labelKey: string }[] = [
  { id: 'face', icon: User, labelKey: 'editor.portrait.tabs.face' },
  { id: 'skin', icon: Palette, labelKey: 'editor.portrait.tabs.skin' },
  { id: 'body', icon: User, labelKey: 'editor.portrait.tabs.body' },
  { id: 'hair', icon: Scissors, labelKey: 'editor.portrait.tabs.hair' },
];

const DEFAULT_FACE_PARAMS: FaceReshapeParams = {
  faceSlimming: 0,
  eyeEnlarging: 0,
  noseSlimming: 0,
  lipAdjustment: 0,
  jaw: 0,
  forehead: 0,
  chin: 0,
  eyebrow: 0,
  eyeCatchlightIntensity: 0,
  eyeCatchlightPosition: 50,
  smileManagement: 0,
};

const DEFAULT_SKIN_PARAMS: SkinSmoothingParams = {
  method: 'bilateral',
  strength: 50,
  texturePreservation: 50,
  radius: 5,
};

const DEFAULT_BODY_PARAMS: BodyReshapeParams = {
  upperLeg: 0,
  lowerLeg: 0,
  arm: 0,
  waist: 0,
  shoulder: 0,
  neck: 0,
  hip: 0,
};

const DEFAULT_HAIR_PARAMS: HairParams = {
  removeFlyaway: false,
  flyawayStrength: 50,
  colorUniformStrength: 0,
  smoothStrength: 0,
};

export default function PortraitPanel() {
  const { t } = useTranslation();
  const selectedImage = useEditorStore((s) => s.selectedImage);
  const {
    faceDetections,
    isDetectingFaces,
    detectFaces,
    bodyDetections,
    isDetectingBody,
    detectBody,
    applyFaceReshape,
    applySkinSmoothing,
    applyBlemishRemoval,
    applySkinColorUniform,
    applyBodyReshape,
    applyHairRetouch,
  } = useRetouching();

  const [activeTab, setActiveTab] = useState<PortraitTab>('face');
  const [faceParams, setFaceParams] = useState<FaceReshapeParams>(DEFAULT_FACE_PARAMS);
  const [skinParams, setSkinParams] = useState<SkinSmoothingParams>(DEFAULT_SKIN_PARAMS);
  const [skinColorUniformStrength, setSkinColorUniformStrength] = useState(50);
  const [bodyParams, setBodyParams] = useState<BodyReshapeParams>(DEFAULT_BODY_PARAMS);
  const [hairParams, setHairParams] = useState<HairParams>(DEFAULT_HAIR_PARAMS);
  const [isApplying, setIsApplying] = useState(false);

  const updateFaceParam = useCallback((key: keyof FaceReshapeParams, value: number) => {
    setFaceParams((prev) => ({ ...prev, [key]: value }));
  }, []);

  const updateBodyParam = useCallback((key: keyof BodyReshapeParams, value: number) => {
    setBodyParams((prev) => ({ ...prev, [key]: value }));
  }, []);

  const handleDetectFaces = useCallback(async () => {
    await detectFaces();
  }, [detectFaces]);

  const handleDetectBody = useCallback(async () => {
    await detectBody();
  }, [detectBody]);

  const handleReset = useCallback(() => {
    setFaceParams(DEFAULT_FACE_PARAMS);
    setSkinParams(DEFAULT_SKIN_PARAMS);
    setSkinColorUniformStrength(50);
    setBodyParams(DEFAULT_BODY_PARAMS);
    setHairParams(DEFAULT_HAIR_PARAMS);
  }, []);

  const handleApplyFaceReshape = useCallback(async () => {
    if (faceDetections.length === 0) return;
    setIsApplying(true);
    try {
      await applyFaceReshape(faceDetections[0].landmarks, faceParams);
    } finally {
      setIsApplying(false);
    }
  }, [faceDetections, faceParams, applyFaceReshape]);

  const handleApplySkinSmoothing = useCallback(async () => {
    setIsApplying(true);
    try {
      await applySkinSmoothing(skinParams);
    } finally {
      setIsApplying(false);
    }
  }, [skinParams, applySkinSmoothing]);

  const handleAutoBlemishRemove = useCallback(async () => {
    if (faceDetections.length === 0) return;
    setIsApplying(true);
    try {
      await applyBlemishRemoval(faceDetections[0].landmarks);
    } finally {
      setIsApplying(false);
    }
  }, [faceDetections, applyBlemishRemoval]);

  const handleSkinColorUniform = useCallback(async () => {
    if (faceDetections.length === 0) return;
    setIsApplying(true);
    try {
      await applySkinColorUniform(faceDetections[0].landmarks, skinColorUniformStrength);
    } finally {
      setIsApplying(false);
    }
  }, [faceDetections, skinColorUniformStrength, applySkinColorUniform]);

  const handleApplyBodyReshape = useCallback(async () => {
    if (bodyDetections.length === 0) return;
    setIsApplying(true);
    try {
      await applyBodyReshape(bodyDetections[0].keypoints, bodyParams);
    } finally {
      setIsApplying(false);
    }
  }, [bodyDetections, bodyParams, applyBodyReshape]);

  const handleApplyHair = useCallback(async () => {
    setIsApplying(true);
    try {
      await applyHairRetouch(hairParams);
    } finally {
      setIsApplying(false);
    }
  }, [hairParams, applyHairRetouch]);

  const skinMethodOptions = [
    { label: t('editor.portrait.skin.method.neutralGray'), value: 'neutral_gray' as const },
    { label: t('editor.portrait.skin.method.bilateral'), value: 'bilateral' as const },
    { label: t('editor.portrait.skin.method.frequencySeparation'), value: 'frequency_separation' as const },
  ];

  const renderFaceTab = () => (
    <div className="space-y-2">
      {isDetectingFaces && (
        <div className="flex items-center gap-2 p-3 bg-card-active rounded-md border border-surface">
          <Loader2 size={16} className="animate-spin shrink-0" />
          <Text variant={TextVariants.small}>{t('editor.portrait.face.detecting')}</Text>
        </div>
      )}

      <Button className="w-full" onClick={handleDetectFaces} disabled={isDetectingFaces}>
        {isDetectingFaces ? <Loader2 size={16} className="animate-spin" /> : <User size={16} />}
        <span className="ml-2">{t('editor.portrait.face.detectFace')}</span>
      </Button>

      {faceDetections.length > 0 && (
        <Text variant={TextVariants.small} color={TextColors.success} className="text-center">
          {t('editor.portrait.face.detectedCount', { count: faceDetections.length })}
        </Text>
      )}

      <Slider
        label={t('editor.portrait.face.faceSlimming')}
        min={-100}
        max={100}
        step={1}
        value={faceParams.faceSlimming}
        onChange={(e) => updateFaceParam('faceSlimming', Number(e.target.value))}
      />
      <Slider
        label={t('editor.portrait.face.eyeEnlarging')}
        min={-100}
        max={100}
        step={1}
        value={faceParams.eyeEnlarging}
        onChange={(e) => updateFaceParam('eyeEnlarging', Number(e.target.value))}
      />
      <Slider
        label={t('editor.portrait.face.noseSlimming')}
        min={-100}
        max={100}
        step={1}
        value={faceParams.noseSlimming}
        onChange={(e) => updateFaceParam('noseSlimming', Number(e.target.value))}
      />
      <Slider
        label={t('editor.portrait.face.lipAdjustment')}
        min={-100}
        max={100}
        step={1}
        value={faceParams.lipAdjustment}
        onChange={(e) => updateFaceParam('lipAdjustment', Number(e.target.value))}
      />
      <Slider
        label={t('editor.portrait.face.jaw')}
        min={-100}
        max={100}
        step={1}
        value={faceParams.jaw}
        onChange={(e) => updateFaceParam('jaw', Number(e.target.value))}
      />
      <Slider
        label={t('editor.portrait.face.forehead')}
        min={-100}
        max={100}
        step={1}
        value={faceParams.forehead}
        onChange={(e) => updateFaceParam('forehead', Number(e.target.value))}
      />
      <Slider
        label={t('editor.portrait.face.chin')}
        min={-100}
        max={100}
        step={1}
        value={faceParams.chin}
        onChange={(e) => updateFaceParam('chin', Number(e.target.value))}
      />
      <Slider
        label={t('editor.portrait.face.eyebrow')}
        min={-100}
        max={100}
        step={1}
        value={faceParams.eyebrow}
        onChange={(e) => updateFaceParam('eyebrow', Number(e.target.value))}
      />

      <CollapsibleSection
        title={t('editor.portrait.face.advancedTitle')}
        isOpen={false}
        onToggle={() => {}}
        canToggleVisibility={false}
        isContentVisible={true}
      >
        <div className="space-y-2 pt-2">
          <Slider
            label={t('editor.portrait.face.eyeCatchlightIntensity')}
            min={0}
            max={100}
            step={1}
            value={faceParams.eyeCatchlightIntensity}
            onChange={(e) => updateFaceParam('eyeCatchlightIntensity', Number(e.target.value))}
            fillOrigin="min"
          />
          <Slider
            label={t('editor.portrait.face.eyeCatchlightPosition')}
            min={0}
            max={100}
            step={1}
            value={faceParams.eyeCatchlightPosition}
            onChange={(e) => updateFaceParam('eyeCatchlightPosition', Number(e.target.value))}
            fillOrigin="min"
          />
          <Slider
            label={t('editor.portrait.face.smileManagement')}
            min={-100}
            max={100}
            step={1}
            value={faceParams.smileManagement}
            onChange={(e) => updateFaceParam('smileManagement', Number(e.target.value))}
          />
        </div>
      </CollapsibleSection>

      <Button
        className="w-full"
        onClick={handleApplyFaceReshape}
        disabled={isApplying || faceDetections.length === 0}
      >
        {isApplying ? <Loader2 size={16} className="animate-spin" /> : <Sparkles size={16} />}
        <span className="ml-2">{t('editor.portrait.face.apply')}</span>
      </Button>
    </div>
  );

  const renderSkinTab = () => (
    <div className="space-y-2">
      <div className="mb-2">
        <Text variant={TextVariants.label} className="mb-1 block">
          {t('editor.portrait.skin.smoothingMethod')}
        </Text>
        <Dropdown
          options={skinMethodOptions}
          value={skinParams.method}
          onChange={(val) => setSkinParams((prev) => ({ ...prev, method: val }))}
        />
      </div>

      <Slider
        label={t('editor.portrait.skin.strength')}
        min={0}
        max={100}
        step={1}
        value={skinParams.strength}
        onChange={(e) => setSkinParams((prev) => ({ ...prev, strength: Number(e.target.value) }))}
        fillOrigin="min"
      />
      <Slider
        label={t('editor.portrait.skin.texturePreservation')}
        min={0}
        max={100}
        step={1}
        value={skinParams.texturePreservation}
        onChange={(e) => setSkinParams((prev) => ({ ...prev, texturePreservation: Number(e.target.value) }))}
        fillOrigin="min"
      />
      <Slider
        label={t('editor.portrait.skin.radius')}
        min={1}
        max={20}
        step={1}
        value={skinParams.radius}
        onChange={(e) => setSkinParams((prev) => ({ ...prev, radius: Number(e.target.value) }))}
        fillOrigin="min"
      />

      <Button className="w-full" onClick={handleApplySkinSmoothing} disabled={isApplying}>
        {isApplying ? <Loader2 size={16} className="animate-spin" /> : <Palette size={16} />}
        <span className="ml-2">{t('editor.portrait.skin.applySmoothing')}</span>
      </Button>

      <div className="h-px bg-surface my-2" />

      <Button className="w-full bg-surface" onClick={handleAutoBlemishRemove} disabled={isApplying}>
        <Sparkles size={16} />
        <span className="ml-2">{t('editor.portrait.skin.autoBlemishRemove')}</span>
      </Button>

      <div className="h-px bg-surface my-2" />

      <Slider
        label={t('editor.portrait.skin.colorUniformStrength')}
        min={0}
        max={100}
        step={1}
        value={skinColorUniformStrength}
        onChange={(e) => setSkinColorUniformStrength(Number(e.target.value))}
        fillOrigin="min"
      />
      <Button className="w-full bg-surface" onClick={handleSkinColorUniform} disabled={isApplying}>
        <Palette size={16} />
        <span className="ml-2">{t('editor.portrait.skin.applyColorUniform')}</span>
      </Button>

    </div>
  );

  const renderBodyTab = () => (
    <div className="space-y-2">
      {isDetectingBody && (
        <div className="flex items-center gap-2 p-3 bg-card-active rounded-md border border-surface">
          <Loader2 size={16} className="animate-spin shrink-0" />
          <Text variant={TextVariants.small}>{t('editor.portrait.body.detecting')}</Text>
        </div>
      )}

      <Button className="w-full bg-surface" onClick={handleDetectBody} disabled={isDetectingBody}>
        {isDetectingBody ? <Loader2 size={16} className="animate-spin" /> : <User size={16} />}
        <span className="ml-2">{t('editor.portrait.body.detectBody')}</span>
      </Button>

      {bodyDetections.length > 0 && (
        <Text variant={TextVariants.small} color={TextColors.success} className="text-center">
          {t('editor.portrait.body.detectedCount', { count: bodyDetections.length })}
        </Text>
      )}

      <Slider
        label={t('editor.portrait.body.upperLeg')}
        min={-100}
        max={100}
        step={1}
        value={bodyParams.upperLeg}
        onChange={(e) => updateBodyParam('upperLeg', Number(e.target.value))}
      />
      <Slider
        label={t('editor.portrait.body.lowerLeg')}
        min={-100}
        max={100}
        step={1}
        value={bodyParams.lowerLeg}
        onChange={(e) => updateBodyParam('lowerLeg', Number(e.target.value))}
      />
      <Slider
        label={t('editor.portrait.body.arm')}
        min={-100}
        max={100}
        step={1}
        value={bodyParams.arm}
        onChange={(e) => updateBodyParam('arm', Number(e.target.value))}
      />
      <Slider
        label={t('editor.portrait.body.waist')}
        min={-100}
        max={100}
        step={1}
        value={bodyParams.waist}
        onChange={(e) => updateBodyParam('waist', Number(e.target.value))}
      />
      <Slider
        label={t('editor.portrait.body.shoulder')}
        min={-100}
        max={100}
        step={1}
        value={bodyParams.shoulder}
        onChange={(e) => updateBodyParam('shoulder', Number(e.target.value))}
      />
      <Slider
        label={t('editor.portrait.body.neck')}
        min={-100}
        max={100}
        step={1}
        value={bodyParams.neck}
        onChange={(e) => updateBodyParam('neck', Number(e.target.value))}
      />
      <Slider
        label={t('editor.portrait.body.hip')}
        min={-100}
        max={100}
        step={1}
        value={bodyParams.hip}
        onChange={(e) => updateBodyParam('hip', Number(e.target.value))}
      />

      <Button
        className="w-full"
        onClick={handleApplyBodyReshape}
        disabled={isApplying || bodyDetections.length === 0}
      >
        {isApplying ? <Loader2 size={16} className="animate-spin" /> : <Sparkles size={16} />}
        <span className="ml-2">{t('editor.portrait.body.apply')}</span>
      </Button>
    </div>
  );

  const renderHairTab = () => (
    <div className="space-y-2">
      <Switch
        checked={hairParams.removeFlyaway}
        label={t('editor.portrait.hair.removeFlyaway')}
        onChange={(val) => setHairParams((prev) => ({ ...prev, removeFlyaway: val }))}
      />
      {hairParams.removeFlyaway && (
        <Slider
          label={t('editor.portrait.hair.flyawayStrength')}
          min={0}
          max={100}
          step={1}
          value={hairParams.flyawayStrength}
          onChange={(e) =>
            setHairParams((prev) => ({ ...prev, flyawayStrength: Number(e.target.value) }))
          }
          fillOrigin="min"
        />
      )}
      <Slider
        label={t('editor.portrait.hair.colorUniformStrength')}
        min={0}
        max={100}
        step={1}
        value={hairParams.colorUniformStrength}
        onChange={(e) =>
          setHairParams((prev) => ({ ...prev, colorUniformStrength: Number(e.target.value) }))
        }
        fillOrigin="min"
      />
      <Slider
        label={t('editor.portrait.hair.smoothStrength')}
        min={0}
        max={100}
        step={1}
        value={hairParams.smoothStrength}
        onChange={(e) =>
          setHairParams((prev) => ({ ...prev, smoothStrength: Number(e.target.value) }))
        }
        fillOrigin="min"
      />

      <Button className="w-full" onClick={handleApplyHair} disabled={isApplying}>
        {isApplying ? <Loader2 size={16} className="animate-spin" /> : <Scissors size={16} />}
        <span className="ml-2">{t('editor.portrait.hair.apply')}</span>
      </Button>
    </div>
  );

  return (
    <div className="flex flex-col h-full select-none overflow-hidden">
      <div className="p-4 flex justify-between items-center shrink-0 border-b border-surface">
        <Text variant={TextVariants.title}>{t('editor.portrait.title')}</Text>
        <button
          className="p-2 rounded-full hover:bg-surface transition-colors"
          onClick={handleReset}
          data-tooltip={t('editor.portrait.resetTooltip')}
        >
          <RotateCcw size={18} />
        </button>
      </div>

      {/* Tab navigation */}
      <div className="flex shrink-0 border-b border-surface">
        {TAB_CONFIG.map(({ id, icon: Icon, labelKey }) => (
          <button
            key={id}
            className={clsx(
              'flex-1 flex items-center justify-center gap-1.5 py-2.5 text-sm font-medium transition-colors',
              activeTab === id
                ? 'text-accent border-b-2 border-accent'
                : 'text-text-secondary hover:text-text-primary hover:bg-card-active',
            )}
            onClick={() => setActiveTab(id)}
          >
            <Icon size={16} />
            <span>{t(labelKey)}</span>
          </button>
        ))}
      </div>

      {/* Tab content */}
      <div className="flex-1 overflow-y-auto overflow-x-hidden p-4">
        {activeTab === 'face' && renderFaceTab()}
        {activeTab === 'skin' && renderSkinTab()}
        {activeTab === 'body' && renderBodyTab()}
        {activeTab === 'hair' && renderHairTab()}
      </div>
    </div>
  );
}
