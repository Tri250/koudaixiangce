import React, { useState, useCallback, useRef, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { User, Scissors, Palette, Sparkles, RotateCcw, Loader2, AlertCircle, CheckCircle } from 'lucide-react';
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

// Aligned with Rust FaceReshapeParamsCommand (camelCase via serde rename_all)
const DEFAULT_FACE_PARAMS: FaceReshapeParams = {
  faceSlimming: 0,
  eyeEnlarging: 0,
  noseSlimming: 0,
  lipAdjustment: 0,
  jawAdjustment: 0,
  foreheadAdjustment: 0,
  chinAdjustment: 0,
  eyebrowAdjustment: 0,
};

const DEFAULT_SKIN_PARAMS: SkinSmoothingParams = {
  method: 'bilateral',
  strength: 50,
  texturePreservation: 50,
  radius: 5,
};

// Aligned with Rust BodyReshapeParamsCommand (camelCase via serde rename_all)
const DEFAULT_BODY_PARAMS: BodyReshapeParams = {
  upperLegSlim: 0,
  lowerLegSlim: 0,
  armSlim: 0,
  waistSlim: 0,
  shoulderAdjust: 0,
  neckAdjust: 0,
  hipAdjust: 0,
};

const DEFAULT_HAIR_PARAMS: HairParams = {
  removeFlyaway: false,
  flyawayStrength: 50,
  colorUniformStrength: 0,
  smoothStrength: 0,
};

export default function PortraitPanel() {
  const { t } = useTranslation();
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
    addEyeCatchlight,
    adjustSmile,
    adjustNeckShoulder,
  } = useRetouching();

  const [activeTab, setActiveTab] = useState<PortraitTab>('face');
  const [faceParams, setFaceParams] = useState<FaceReshapeParams>(DEFAULT_FACE_PARAMS);
  const [skinParams, setSkinParams] = useState<SkinSmoothingParams>(DEFAULT_SKIN_PARAMS);
  const [skinColorUniformStrength, setSkinColorUniformStrength] = useState(50);
  const [bodyParams, setBodyParams] = useState<BodyReshapeParams>(DEFAULT_BODY_PARAMS);
  const [hairParams, setHairParams] = useState<HairParams>(DEFAULT_HAIR_PARAMS);
  const [catchlightIntensity, setCatchlightIntensity] = useState(50);
  const [catchlightPosition, setCatchlightPosition] = useState('top-left');
  const [smileAmount, setSmileAmount] = useState(0);
  const [neckAdjust, setNeckAdjust] = useState(0);
  const [shoulderAdjustValue, setShoulderAdjustValue] = useState(0);
  const [isApplying, setIsApplying] = useState(false);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);
  const [statusType, setStatusType] = useState<'success' | 'error'>('success');
  const statusTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const showStatus = useCallback((message: string, type: 'success' | 'error') => {
    if (statusTimeoutRef.current) clearTimeout(statusTimeoutRef.current);
    setStatusMessage(message);
    setStatusType(type);
    statusTimeoutRef.current = setTimeout(() => setStatusMessage(null), 4000);
  }, []);

  // Clear status when switching tabs
  useEffect(() => {
    setStatusMessage(null);
  }, [activeTab]);

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

  const setEditor = useEditorStore((s) => s.setEditor);

  const handleReset = useCallback(() => {
    setFaceParams(DEFAULT_FACE_PARAMS);
    setSkinParams(DEFAULT_SKIN_PARAMS);
    setSkinColorUniformStrength(50);
    setBodyParams(DEFAULT_BODY_PARAMS);
    setHairParams(DEFAULT_HAIR_PARAMS);
    setEditor({ retouchingResultUrl: null });
    setStatusMessage(null);
  }, [setEditor]);

  const handleApplyFaceReshape = useCallback(async () => {
    if (faceDetections.length === 0) {
      showStatus(t('editor.portrait.face.detectFirst'), 'error');
      return;
    }
    setIsApplying(true);
    try {
      const result = await applyFaceReshape(faceDetections[0].landmarks, faceParams);
      if (result) {
        showStatus(t('editor.portrait.face.applySuccess'), 'success');
      } else {
        showStatus(t('editor.portrait.face.applyFailed'), 'error');
      }
    } catch (err) {
      showStatus(t('editor.portrait.face.applyFailed'), 'error');
    } finally {
      setIsApplying(false);
    }
  }, [faceDetections, faceParams, applyFaceReshape, showStatus, t]);

  const handleApplySkinSmoothing = useCallback(async () => {
    setIsApplying(true);
    try {
      const result = await applySkinSmoothing(skinParams.method, skinParams.strength, skinParams.texturePreservation, skinParams.radius);
      if (result) {
        showStatus(t('editor.portrait.skin.applySuccess'), 'success');
      } else {
        showStatus(t('editor.portrait.skin.applyFailed'), 'error');
      }
    } catch (err) {
      showStatus(t('editor.portrait.skin.applyFailed'), 'error');
    } finally {
      setIsApplying(false);
    }
  }, [skinParams, applySkinSmoothing, showStatus, t]);

  const handleAutoBlemishRemove = useCallback(async () => {
    setIsApplying(true);
    try {
      const allLandmarks = faceDetections.map((d) => d.landmarks);
      const result = await applyBlemishRemoval(allLandmarks, 0.5);
      if (result) {
        showStatus(t('editor.portrait.skin.applySuccess'), 'success');
      } else {
        showStatus(t('editor.portrait.skin.applyFailed'), 'error');
      }
    } catch (err) {
      showStatus(t('editor.portrait.skin.applyFailed'), 'error');
    } finally {
      setIsApplying(false);
    }
  }, [applyBlemishRemoval, faceDetections, showStatus, t]);

  const handleSkinColorUniform = useCallback(async () => {
    setIsApplying(true);
    try {
      const allLandmarks = faceDetections.map((d) => d.landmarks);
      const result = await applySkinColorUniform(allLandmarks, skinColorUniformStrength);
      if (result) {
        showStatus(t('editor.portrait.skin.applySuccess'), 'success');
      } else {
        showStatus(t('editor.portrait.skin.applyFailed'), 'error');
      }
    } catch (err) {
      showStatus(t('editor.portrait.skin.applyFailed'), 'error');
    } finally {
      setIsApplying(false);
    }
  }, [skinColorUniformStrength, applySkinColorUniform, faceDetections, showStatus, t]);

  const handleApplyBodyReshape = useCallback(async () => {
    if (bodyDetections.length === 0) {
      showStatus(t('editor.portrait.body.detectFirst'), 'error');
      return;
    }
    setIsApplying(true);
    try {
      const result = await applyBodyReshape(bodyDetections[0].keypoints, bodyParams);
      if (result) {
        showStatus(t('editor.portrait.body.applySuccess'), 'success');
      } else {
        showStatus(t('editor.portrait.body.applyFailed'), 'error');
      }
    } catch (err) {
      showStatus(t('editor.portrait.body.applyFailed'), 'error');
    } finally {
      setIsApplying(false);
    }
  }, [bodyDetections, bodyParams, applyBodyReshape, showStatus, t]);

  const handleApplyHair = useCallback(async () => {
    setIsApplying(true);
    try {
      const result = await applyHairRetouch(hairParams as unknown as Record<string, unknown>);
      if (result) {
        showStatus(t('editor.portrait.hair.applySuccess'), 'success');
      } else {
        showStatus(t('editor.portrait.hair.applyFailed'), 'error');
      }
    } catch (err) {
      showStatus(t('editor.portrait.hair.applyFailed'), 'error');
    } finally {
      setIsApplying(false);
    }
  }, [hairParams, applyHairRetouch, showStatus, t]);

  const handleApplyCatchlight = useCallback(async () => {
    if (faceDetections.length === 0) {
      showStatus(t('editor.portrait.face.detectFirst'), 'error');
      return;
    }
    setIsApplying(true);
    try {
      const result = await addEyeCatchlight(faceDetections[0].landmarks, catchlightIntensity, catchlightPosition);
      if (result) {
        showStatus(t('editor.portrait.face.applySuccess'), 'success');
      } else {
        showStatus(t('editor.portrait.face.applyFailed'), 'error');
      }
    } catch (err) {
      showStatus(t('editor.portrait.face.applyFailed'), 'error');
    } finally {
      setIsApplying(false);
    }
  }, [faceDetections, catchlightIntensity, catchlightPosition, addEyeCatchlight, showStatus, t]);

  const handleApplySmile = useCallback(async () => {
    if (faceDetections.length === 0) {
      showStatus(t('editor.portrait.face.detectFirst'), 'error');
      return;
    }
    setIsApplying(true);
    try {
      const result = await adjustSmile(faceDetections[0].landmarks, smileAmount);
      if (result) {
        showStatus(t('editor.portrait.face.applySuccess'), 'success');
      } else {
        showStatus(t('editor.portrait.face.applyFailed'), 'error');
      }
    } catch (err) {
      showStatus(t('editor.portrait.face.applyFailed'), 'error');
    } finally {
      setIsApplying(false);
    }
  }, [faceDetections, smileAmount, adjustSmile, showStatus, t]);

  const handleApplyNeckShoulder = useCallback(async () => {
    if (bodyDetections.length === 0) {
      showStatus(t('editor.portrait.body.detectFirst'), 'error');
      return;
    }
    setIsApplying(true);
    try {
      const result = await adjustNeckShoulder(bodyDetections[0].keypoints, neckAdjust, shoulderAdjustValue);
      if (result) {
        showStatus(t('editor.portrait.body.applySuccess'), 'success');
      } else {
        showStatus(t('editor.portrait.body.applyFailed'), 'error');
      }
    } catch (err) {
      showStatus(t('editor.portrait.body.applyFailed'), 'error');
    } finally {
      setIsApplying(false);
    }
  }, [bodyDetections, neckAdjust, shoulderAdjustValue, adjustNeckShoulder, showStatus, t]);

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
        value={faceParams.jawAdjustment}
        onChange={(e) => updateFaceParam('jawAdjustment', Number(e.target.value))}
      />
      <Slider
        label={t('editor.portrait.face.forehead')}
        min={-100}
        max={100}
        step={1}
        value={faceParams.foreheadAdjustment}
        onChange={(e) => updateFaceParam('foreheadAdjustment', Number(e.target.value))}
      />
      <Slider
        label={t('editor.portrait.face.chin')}
        min={-100}
        max={100}
        step={1}
        value={faceParams.chinAdjustment}
        onChange={(e) => updateFaceParam('chinAdjustment', Number(e.target.value))}
      />
      <Slider
        label={t('editor.portrait.face.eyebrow')}
        min={-100}
        max={100}
        step={1}
        value={faceParams.eyebrowAdjustment}
        onChange={(e) => updateFaceParam('eyebrowAdjustment', Number(e.target.value))}
      />

      <Button
        className="w-full"
        onClick={handleApplyFaceReshape}
        disabled={isApplying || faceDetections.length === 0}
      >
        {isApplying ? <Loader2 size={16} className="animate-spin" /> : <Sparkles size={16} />}
        <span className="ml-2">{t('editor.portrait.face.apply')}</span>
      </Button>

      <div className="h-px bg-surface my-2" />

      {/* Advanced: Eye Catchlight */}
      <CollapsibleSection
        title={t('editor.portrait.face.advancedTitle')}
        isOpen={true}
        isContentVisible={true}
        onToggle={() => {}}
        canToggleVisibility={false}
      >
        <Slider
          label={t('editor.portrait.face.eyeCatchlightIntensity')}
          min={0}
          max={100}
          step={1}
          value={catchlightIntensity}
          onChange={(e) => setCatchlightIntensity(Number(e.target.value))}
          fillOrigin="min"
        />
        <Dropdown
          options={[
            { label: t('editor.portrait.face.catchlightPositions.topLeft' as any), value: 'top-left' },
            { label: t('editor.portrait.face.catchlightPositions.topRight' as any), value: 'top-right' },
            { label: t('editor.portrait.face.catchlightPositions.center' as any), value: 'center' },
          ]}
          value={catchlightPosition}
          onChange={setCatchlightPosition}
        />
        <Button className="w-full bg-surface" onClick={handleApplyCatchlight} disabled={isApplying || faceDetections.length === 0}>
          <Sparkles size={16} />
          <span className="ml-2">{t('editor.portrait.face.eyeCatchlightIntensity')}</span>
        </Button>

        <div className="h-px bg-surface my-2" />

        {/* Smile Management */}
        <Slider
          label={t('editor.portrait.face.smileManagement')}
          min={-100}
          max={100}
          step={1}
          value={smileAmount}
          onChange={(e) => setSmileAmount(Number(e.target.value))}
        />
        <Button className="w-full bg-surface" onClick={handleApplySmile} disabled={isApplying || faceDetections.length === 0}>
          <Sparkles size={16} />
          <span className="ml-2">{t('editor.portrait.face.smileManagement')}</span>
        </Button>
      </CollapsibleSection>
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
        value={bodyParams.upperLegSlim}
        onChange={(e) => updateBodyParam('upperLegSlim', Number(e.target.value))}
      />
      <Slider
        label={t('editor.portrait.body.lowerLeg')}
        min={-100}
        max={100}
        step={1}
        value={bodyParams.lowerLegSlim}
        onChange={(e) => updateBodyParam('lowerLegSlim', Number(e.target.value))}
      />
      <Slider
        label={t('editor.portrait.body.arm')}
        min={-100}
        max={100}
        step={1}
        value={bodyParams.armSlim}
        onChange={(e) => updateBodyParam('armSlim', Number(e.target.value))}
      />
      <Slider
        label={t('editor.portrait.body.waist')}
        min={-100}
        max={100}
        step={1}
        value={bodyParams.waistSlim}
        onChange={(e) => updateBodyParam('waistSlim', Number(e.target.value))}
      />
      <Slider
        label={t('editor.portrait.body.shoulder')}
        min={-100}
        max={100}
        step={1}
        value={bodyParams.shoulderAdjust}
        onChange={(e) => updateBodyParam('shoulderAdjust', Number(e.target.value))}
      />
      <Slider
        label={t('editor.portrait.body.neck')}
        min={-100}
        max={100}
        step={1}
        value={bodyParams.neckAdjust}
        onChange={(e) => updateBodyParam('neckAdjust', Number(e.target.value))}
      />
      <Slider
        label={t('editor.portrait.body.hip')}
        min={-100}
        max={100}
        step={1}
        value={bodyParams.hipAdjust}
        onChange={(e) => updateBodyParam('hipAdjust', Number(e.target.value))}
      />

      <Button
        className="w-full"
        onClick={handleApplyBodyReshape}
        disabled={isApplying || bodyDetections.length === 0}
      >
        {isApplying ? <Loader2 size={16} className="animate-spin" /> : <Sparkles size={16} />}
        <span className="ml-2">{t('editor.portrait.body.apply')}</span>
      </Button>

      <div className="h-px bg-surface my-2" />

      {/* Neck & Shoulder Fine-tuning */}
      <CollapsibleSection
        title={t('editor.portrait.body.neck') + ' & ' + t('editor.portrait.body.shoulder')}
        isOpen={true}
        isContentVisible={true}
        onToggle={() => {}}
        canToggleVisibility={false}
      >
        <Slider
          label={t('editor.portrait.body.neck')}
          min={-100}
          max={100}
          step={1}
          value={neckAdjust}
          onChange={(e) => setNeckAdjust(Number(e.target.value))}
        />
        <Slider
          label={t('editor.portrait.body.shoulder')}
          min={-100}
          max={100}
          step={1}
          value={shoulderAdjustValue}
          onChange={(e) => setShoulderAdjustValue(Number(e.target.value))}
        />
        <Button className="w-full bg-surface" onClick={handleApplyNeckShoulder} disabled={isApplying || bodyDetections.length === 0}>
          <Sparkles size={16} />
          <span className="ml-2">{t('editor.portrait.body.neck') + ' & ' + t('editor.portrait.body.shoulder')}</span>
        </Button>
      </CollapsibleSection>
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
            <span>{t(labelKey as any)}</span>
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

      {/* Status message */}
      {statusMessage && (
        <div
          className={clsx(
            'shrink-0 flex items-center gap-2 px-4 py-2 text-sm border-t',
            statusType === 'error'
              ? 'bg-red-500/10 text-red-400 border-red-500/20'
              : 'bg-green-500/10 text-green-400 border-green-500/20',
          )}
        >
          {statusType === 'error' ? <AlertCircle size={14} className="shrink-0" /> : <CheckCircle size={14} className="shrink-0" />}
          <span className="truncate">{statusMessage}</span>
        </div>
      )}
    </div>
  );
}
