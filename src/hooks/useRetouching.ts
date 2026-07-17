import { useState, useCallback } from 'react';
import { invoke } from '@tauri-apps/api/core';
import { useEditorStore } from '../store/useEditorStore';

// ── Types ───────────────────────────────────────────────────────────────

export interface FaceLandmark {
  x: number;
  y: number;
}

export interface FaceDetection {
  landmarks: FaceLandmark[];
  bbox: { x: number; y: number; width: number; height: number };
  confidence: number;
}

export interface BodyKeypoint {
  x: number;
  y: number;
  confidence: number;
  name: string;
}

export interface BodyDetection {
  keypoints: BodyKeypoint[];
  confidence: number;
}

export interface LiquifyStroke {
  points: { x: number; y: number }[];
  brushType: BrushType;
  brushSize: number;
  brushPressure: number;
}

export type BrushType = 'push' | 'pull' | 'pucker' | 'bloat' | 'twirl' | 'reconstruct';

export interface FaceReshapeParams {
  faceSlimming: number;
  eyeEnlarging: number;
  noseSlimming: number;
  lipAdjustment: number;
  jaw: number;
  forehead: number;
  chin: number;
  eyebrow: number;
  eyeCatchlightIntensity: number;
  eyeCatchlightPosition: number;
  smileManagement: number;
}

export interface SkinSmoothingParams {
  method: 'neutral_gray' | 'bilateral' | 'frequency_separation';
  strength: number;
  texturePreservation: number;
  radius: number;
}

export interface SkinColorUniformParams {
  strength: number;
}

export interface BodyReshapeParams {
  upperLeg: number;
  lowerLeg: number;
  arm: number;
  waist: number;
  shoulder: number;
  neck: number;
  hip: number;
}

export interface HairParams {
  removeFlyaway: boolean;
  flyawayStrength: number;
  colorUniformStrength: number;
  smoothStrength: number;
}

export interface FillLightParams {
  direction: number;
  intensity: number;
  softness: number;
  colorTemp: number;
}

export interface LensBlurParams {
  blurType: 'gaussian' | 'motion' | 'radial' | 'bokeh';
  focalPoint: [number, number];
  blurAmount: number;
  depthMaskBase64?: string;
}

export interface SeasonalEffectParams {
  effectType: 'spring_bloom' | 'summer_golden' | 'autumn_warmth' | 'winter_cool';
  intensity: number;
}

export interface OldPhotoRestoreParams {
  denoiseStrength: number;
  scratchRemoval: boolean;
  colorize: boolean;
}

// ── Helpers ─────────────────────────────────────────────────────────────

function getImageDataBase64(): string {
  return useEditorStore.getState().selectedImage?.original_base64 || '';
}

function updateSelectedImageBase64(result: string) {
  const state = useEditorStore.getState();
  if (state.selectedImage) {
    state.setEditor({
      selectedImage: { ...state.selectedImage, original_base64: result },
    });
  }
}

function backendFaceToFrontend(face: any): FaceDetection {
  const bboxArr = face.bbox || [0, 0, 0, 0];
  const x1 = bboxArr[0] ?? 0;
  const y1 = bboxArr[1] ?? 0;
  const x2 = bboxArr[2] ?? 0;
  const y2 = bboxArr[3] ?? 0;
  return {
    landmarks: (face.landmarks || []).map((lm: any) => ({
      x: typeof lm.x === 'number' ? lm.x : lm[0] ?? 0,
      y: typeof lm.y === 'number' ? lm.y : lm[1] ?? 0,
    })),
    bbox: {
      x: x1,
      y: y1,
      width: x2 - x1,
      height: y2 - y1,
    },
    confidence: face.confidence ?? 0,
  };
}

function backendPoseToFrontend(pose: any): BodyDetection {
  return {
    keypoints: (pose.keypoints || []).map((kp: any) => ({
      x: kp.x ?? 0,
      y: kp.y ?? 0,
      confidence: kp.confidence ?? 0,
      name: kp.name ?? '',
    })),
    confidence: pose.confidence ?? 0,
  };
}

// ── Hook ────────────────────────────────────────────────────────────────

export function useRetouching() {
  // Face detection state
  const [faceDetections, setFaceDetections] = useState<FaceDetection[]>([]);
  const [isDetectingFaces, setIsDetectingFaces] = useState(false);
  const [faceDetectionError, setFaceDetectionError] = useState<string | null>(null);

  // Body detection state
  const [bodyDetections, setBodyDetections] = useState<BodyDetection[]>([]);
  const [isDetectingBody, setIsDetectingBody] = useState(false);
  const [bodyDetectionError, setBodyDetectionError] = useState<string | null>(null);

  // Liquify state
  const [liquifyStrokes, setLiquifyStrokes] = useState<LiquifyStroke[]>([]);

  // ── Face Detection ──────────────────────────────────────────────────

  const detectFaces = useCallback(async (): Promise<FaceDetection[]> => {
    const imageDataBase64 = getImageDataBase64();
    if (!imageDataBase64) return [];
    setIsDetectingFaces(true);
    setFaceDetectionError(null);
    try {
      const result = await invoke<{ faces: any[] }>('detect_faces_in_image', { imageDataBase64 });
      const faces = (result.faces || []).map(backendFaceToFrontend);
      setFaceDetections(faces);
      return faces;
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      setFaceDetectionError(msg);
      return [];
    } finally {
      setIsDetectingFaces(false);
    }
  }, []);

  // ── Body Detection ──────────────────────────────────────────────────

  const detectBody = useCallback(async (): Promise<BodyDetection[]> => {
    const imageDataBase64 = getImageDataBase64();
    if (!imageDataBase64) return [];
    setIsDetectingBody(true);
    setBodyDetectionError(null);
    try {
      const result = await invoke<{ poses: any[] }>('detect_body_in_image', { imageDataBase64 });
      const poses = (result.poses || []).map(backendPoseToFrontend);
      setBodyDetections(poses);
      return poses;
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      setBodyDetectionError(msg);
      return [];
    } finally {
      setIsDetectingBody(false);
    }
  }, []);

  // ── Face Reshape ────────────────────────────────────────────────────

  const applyFaceReshape = useCallback(
    async (landmarks: FaceLandmark[], params: FaceReshapeParams): Promise<string | null> => {
      const imageDataBase64 = getImageDataBase64();
      if (!imageDataBase64) return null;
      try {
        const result = await invoke<string>('apply_face_reshape', {
          imageDataBase64,
          faceLandmarks: landmarks,
          params: {
            faceSlimming: params.faceSlimming,
            eyeEnlarging: params.eyeEnlarging,
            noseSlimming: params.noseSlimming,
            lipAdjustment: params.lipAdjustment,
            jawAdjustment: params.jaw,
            foreheadAdjustment: params.forehead,
            chinAdjustment: params.chin,
            eyebrowAdjustment: params.eyebrow,
          },
        });
        updateSelectedImageBase64(result);
        return result;
      } catch (err) {
        console.error('applyFaceReshape failed:', err);
        return null;
      }
    },
    [],
  );

  // ── Skin Smoothing ──────────────────────────────────────────────────

  const applySkinSmoothing = useCallback(
    async (params: SkinSmoothingParams): Promise<string | null> => {
      const imageDataBase64 = getImageDataBase64();
      if (!imageDataBase64) return null;
      try {
        const result = await invoke<string>('apply_skin_smoothing', {
          imageDataBase64,
          method: params.method,
          strength: params.strength,
          texturePreservation: params.texturePreservation,
          radius: params.radius,
        });
        updateSelectedImageBase64(result);
        return result;
      } catch (err) {
        console.error('applySkinSmoothing failed:', err);
        return null;
      }
    },
    [],
  );

  // ── Blemish Removal ────────────────────────────────────────────────

  const applyBlemishRemoval = useCallback(
    async (faceLandmarks: FaceLandmark[], sensitivity = 0.5): Promise<string | null> => {
      const imageDataBase64 = getImageDataBase64();
      if (!imageDataBase64) return null;
      try {
        const result = await invoke<string>('auto_remove_blemishes', {
          imageDataBase64,
          faceLandmarks,
          sensitivity,
        });
        updateSelectedImageBase64(result);
        return result;
      } catch (err) {
        console.error('applyBlemishRemoval failed:', err);
        return null;
      }
    },
    [],
  );

  // ── Skin Color Uniform ─────────────────────────────────────────────

  const applySkinColorUniform = useCallback(
    async (faceLandmarks: FaceLandmark[], strength: number): Promise<string | null> => {
      const imageDataBase64 = getImageDataBase64();
      if (!imageDataBase64) return null;
      try {
        const result = await invoke<string>('unify_skin_color', {
          imageDataBase64,
          faceLandmarks,
          strength,
        });
        updateSelectedImageBase64(result);
        return result;
      } catch (err) {
        console.error('applySkinColorUniform failed:', err);
        return null;
      }
    },
    [],
  );

  // ── Body Reshape ────────────────────────────────────────────────────

  const applyBodyReshape = useCallback(
    async (keypoints: BodyKeypoint[], params: BodyReshapeParams): Promise<string | null> => {
      const imageDataBase64 = getImageDataBase64();
      if (!imageDataBase64) return null;
      try {
        const result = await invoke<string>('apply_body_reshape', {
          imageDataBase64,
          bodyKeypoints: keypoints,
          params: {
            upperLegSlim: params.upperLeg,
            lowerLegSlim: params.lowerLeg,
            armSlim: params.arm,
            waistSlim: params.waist,
            shoulderAdjust: params.shoulder,
            neckAdjust: params.neck,
            hipAdjust: params.hip,
          },
        });
        updateSelectedImageBase64(result);
        return result;
      } catch (err) {
        console.error('applyBodyReshape failed:', err);
        return null;
      }
    },
    [],
  );

  // ── Liquify ─────────────────────────────────────────────────────────

  const addLiquifyStroke = useCallback((stroke: LiquifyStroke) => {
    setLiquifyStrokes((prev: LiquifyStroke[]) => [...prev, stroke]);
  }, []);

  const undoLiquifyStroke = useCallback(() => {
    setLiquifyStrokes((prev: LiquifyStroke[]) => prev.slice(0, -1));
  }, []);

  const clearLiquifyStrokes = useCallback(() => {
    setLiquifyStrokes([]);
  }, []);

  const applyLiquify = useCallback(async (strokes: LiquifyStroke[]): Promise<string | null> => {
    const imageDataBase64 = getImageDataBase64();
    if (!imageDataBase64) return null;
    try {
      const result = await invoke<string>('apply_liquify', {
        imageDataBase64,
        strokes: strokes.map((s) => ({
          brushType: s.brushType,
          radius: s.brushSize,
          pressure: s.brushPressure,
          points: s.points.map((p) => [p.x, p.y]),
        })),
      });
      updateSelectedImageBase64(result);
      return result;
    } catch (err) {
      console.error('applyLiquify failed:', err);
      return null;
    }
  }, []);

  const resetLiquifyMesh = useCallback(() => {
    setLiquifyStrokes([]);
  }, []);

  // ── Hair Retouch ────────────────────────────────────────────────────

  const applyHairRetouch = useCallback(async (params: HairParams): Promise<string | null> => {
    const imageDataBase64 = getImageDataBase64();
    if (!imageDataBase64) return null;
    try {
      const result = await invoke<string>('apply_hair_retouch', {
        imageDataBase64,
        params: {
          remove_flyaway: params.removeFlyaway,
          strength: params.removeFlyaway
            ? params.flyawayStrength / 100
            : params.colorUniformStrength / 100 || params.smoothStrength / 100,
          color_uniform: params.colorUniformStrength > 0,
          smooth: params.smoothStrength > 0,
        },
      });
      updateSelectedImageBase64(result);
      return result;
    } catch (err) {
      console.error('applyHairRetouch failed:', err);
      return null;
    }
  }, []);

  // ── Fill Light ──────────────────────────────────────────────────────

  const applyFillLight = useCallback(async (params: FillLightParams): Promise<string | null> => {
    const imageDataBase64 = getImageDataBase64();
    if (!imageDataBase64) return null;
    try {
      const result = await invoke<string>('apply_fill_light', {
        imageDataBase64,
        direction: params.direction,
        intensity: params.intensity,
        softness: params.softness,
        colorTemp: params.colorTemp,
      });
      updateSelectedImageBase64(result);
      return result;
    } catch (err) {
      console.error('applyFillLight failed:', err);
      return null;
    }
  }, []);

  // ── ID Photo ────────────────────────────────────────────────────────

  const processIdPhoto = useCallback(
    async (size: string, backgroundColor?: [number, number, number]): Promise<string | null> => {
      const imageDataBase64 = getImageDataBase64();
      if (!imageDataBase64) return null;
      try {
        const result = await invoke<string>('process_id_photo', {
          imageDataBase64,
          size,
          backgroundColor,
        });
        updateSelectedImageBase64(result);
        return result;
      } catch (err) {
        console.error('processIdPhoto failed:', err);
        return null;
      }
    },
    [],
  );

  // ── Lens Blur ───────────────────────────────────────────────────────

  const applyLensBlur = useCallback(async (params: LensBlurParams): Promise<string | null> => {
    const imageDataBase64 = getImageDataBase64();
    if (!imageDataBase64) return null;
    try {
      const result = await invoke<string>('apply_lens_blur', {
        imageDataBase64,
        blurType: params.blurType,
        focalPoint: params.focalPoint,
        blurAmount: params.blurAmount,
        depthMaskBase64: params.depthMaskBase64,
      });
      updateSelectedImageBase64(result);
      return result;
    } catch (err) {
      console.error('applyLensBlur failed:', err);
      return null;
    }
  }, []);

  // ── Old Photo Restore ───────────────────────────────────────────────

  const restoreOldPhoto = useCallback(
    async (params: OldPhotoRestoreParams): Promise<string | null> => {
      const imageDataBase64 = getImageDataBase64();
      if (!imageDataBase64) return null;
      try {
        const result = await invoke<string>('restore_old_photo', {
          imageDataBase64,
          denoiseStrength: params.denoiseStrength,
          scratchRemoval: params.scratchRemoval,
          colorize: params.colorize,
        });
        updateSelectedImageBase64(result);
        return result;
      } catch (err) {
        console.error('restoreOldPhoto failed:', err);
        return null;
      }
    },
    [],
  );

  // ── Seasonal Effect ─────────────────────────────────────────────────

  const applySeasonalEffect = useCallback(
    async (params: SeasonalEffectParams): Promise<string | null> => {
      const imageDataBase64 = getImageDataBase64();
      if (!imageDataBase64) return null;
      try {
        const result = await invoke<string>('apply_seasonal_effect', {
          imageDataBase64,
          effectType: params.effectType,
          intensity: params.intensity,
        });
        updateSelectedImageBase64(result);
        return result;
      } catch (err) {
        console.error('applySeasonalEffect failed:', err);
        return null;
      }
    },
    [],
  );

  // ── Batch Sync Preset ───────────────────────────────────────────────

  const batchSyncPreset = useCallback(
    async (imagePaths: string[], presetAdjustments: Record<string, unknown>): Promise<void> => {
      try {
        await invoke('batch_sync_preset', { imagePaths, presetAdjustments });
      } catch (err) {
        console.error('batchSyncPreset failed:', err);
      }
    },
    [],
  );

  // ── Clothing Retouch ────────────────────────────────────────────────

  const retouchClothing = useCallback(
    async (bodyKeypoints: BodyKeypoint[], removeWrinkles: number, removeStains: boolean): Promise<string | null> => {
      const imageDataBase64 = getImageDataBase64();
      if (!imageDataBase64) return null;
      try {
        const result = await invoke<string>('retouch_clothing', {
          imageDataBase64,
          bodyKeypoints,
          removeWrinkles,
          removeStains,
        });
        updateSelectedImageBase64(result);
        return result;
      } catch (err) {
        console.error('retouchClothing failed:', err);
        return null;
      }
    },
    [],
  );

  return {
    // Face
    faceDetections,
    isDetectingFaces,
    faceDetectionError,
    detectFaces,

    // Body
    bodyDetections,
    isDetectingBody,
    bodyDetectionError,
    detectBody,

    // Face reshape
    applyFaceReshape,

    // Skin
    applySkinSmoothing,
    applyBlemishRemoval,
    applySkinColorUniform,

    // Body reshape
    applyBodyReshape,

    // Hair
    applyHairRetouch,

    // Liquify
    liquifyStrokes,
    addLiquifyStroke,
    undoLiquifyStroke,
    clearLiquifyStrokes,
    applyLiquify,
    resetLiquifyMesh,

    // Creative tools
    applyFillLight,
    processIdPhoto,
    applyLensBlur,
    restoreOldPhoto,
    applySeasonalEffect,
    batchSyncPreset,
    retouchClothing,
  };
}
