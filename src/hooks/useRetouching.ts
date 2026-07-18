import { useState, useCallback } from 'react';
import { invoke } from '@tauri-apps/api/core';
import { useEditorStore } from '../store/useEditorStore';
import { Adjustments } from '../utils/adjustments';

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
  bbox: { x: number; y: number; width: number; height: number };
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
  jawAdjustment: number;
  foreheadAdjustment: number;
  chinAdjustment: number;
  eyebrowAdjustment: number;
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
  upperLegSlim: number;
  lowerLegSlim: number;
  armSlim: number;
  waistSlim: number;
  shoulderAdjust: number;
  neckAdjust: number;
  hipAdjust: number;
}

export interface HairParams {
  removeFlyaway: boolean;
  flyawayStrength: number;
  colorUniformStrength: number;
  smoothStrength: number;
}

// ── Helper: extract transform adjustments for js_adjustments ──────────

const getTransformAdjustments = (adj: Adjustments) => ({
  transformDistortion: adj.transformDistortion,
  transformVertical: adj.transformVertical,
  transformHorizontal: adj.transformHorizontal,
  transformRotate: adj.transformRotate,
  transformAspect: adj.transformAspect,
  transformScale: adj.transformScale,
  transformXOffset: adj.transformXOffset,
  transformYOffset: adj.transformYOffset,
  lensDistortionAmount: adj.lensDistortionAmount,
  lensVignetteAmount: adj.lensVignetteAmount,
  lensTcaAmount: adj.lensTcaAmount,
  lensDistortionParams: adj.lensDistortionParams,
  lensMaker: adj.lensMaker,
  lensModel: adj.lensModel,
  lensDistortionEnabled: adj.lensDistortionEnabled,
  lensTcaEnabled: adj.lensTcaEnabled,
  lensVignetteEnabled: adj.lensVignetteEnabled,
});

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

  // Get current adjustments from editor store
  const adjustments = useEditorStore((s) => s.adjustments);
  const setEditor = useEditorStore((s) => s.setEditor);

  // Helper: publish a destructive retouching result image (base64 data URL)
  // to the editor store so the canvas displays it.
  const publishRetouchingResult = useCallback(
    (result: string | null) => {
      if (result) {
        setEditor({ retouchingResultUrl: result });
      }
    },
    [setEditor],
  );

  // ── Face Detection ──────────────────────────────────────────────────

  const detectFaces = useCallback(async (): Promise<FaceDetection[]> => {
    setIsDetectingFaces(true);
    setFaceDetectionError(null);
    try {
      const jsAdjustments = getTransformAdjustments(adjustments);
      const result = await invoke<FaceDetection[]>('detect_faces_in_image', { jsAdjustments });
      setFaceDetections(result);
      return result;
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      setFaceDetectionError(msg);
      return [];
    } finally {
      setIsDetectingFaces(false);
    }
  }, [adjustments]);

  // ── Body Detection ──────────────────────────────────────────────────

  const detectBody = useCallback(async (): Promise<BodyDetection[]> => {
    setIsDetectingBody(true);
    setBodyDetectionError(null);
    try {
      const jsAdjustments = getTransformAdjustments(adjustments);
      const result = await invoke<BodyDetection[]>('detect_body_in_image', { jsAdjustments });
      setBodyDetections(result);
      return result;
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      setBodyDetectionError(msg);
      return [];
    } finally {
      setIsDetectingBody(false);
    }
  }, [adjustments]);

  // ── Face Reshape ────────────────────────────────────────────────────

  const applyFaceReshape = useCallback(
    async (faceLandmarks: FaceLandmark[], params: FaceReshapeParams): Promise<string | null> => {
      try {
        const jsAdjustments = getTransformAdjustments(adjustments);
        const result = await invoke<string>('apply_face_reshape', {
          jsAdjustments,
          faceLandmarks,
          params,
        });
        publishRetouchingResult(result);
        return result;
      } catch (err) {
        console.error('applyFaceReshape failed:', err);
        return null;
      }
    },
    [adjustments, publishRetouchingResult],
  );

  // ── Skin Smoothing ──────────────────────────────────────────────────

  const applySkinSmoothing = useCallback(
    async (method: string, strength: number, texturePreservation: number, radius: number): Promise<string | null> => {
      try {
        const jsAdjustments = getTransformAdjustments(adjustments);
        const result = await invoke<string>('apply_skin_smoothing', { jsAdjustments, method, strength, texturePreservation, radius });
        publishRetouchingResult(result);
        return result;
      } catch (err) {
        console.error('applySkinSmoothing failed:', err);
        return null;
      }
    },
    [adjustments, publishRetouchingResult],
  );

  // ── Blemish Removal ────────────────────────────────────────────────

  const applyBlemishRemoval = useCallback(async (faceLandmarks?: FaceLandmark[][], sensitivity?: number): Promise<string | null> => {
    try {
      const jsAdjustments = getTransformAdjustments(adjustments);
      const result = await invoke<string>('auto_remove_blemishes', {
        jsAdjustments,
        faceLandmarks: faceLandmarks ?? [],
        sensitivity: sensitivity ?? 0.5,
      });
      publishRetouchingResult(result);
      return result;
    } catch (err) {
      console.error('applyBlemishRemoval failed:', err);
      return null;
    }
  }, [adjustments, publishRetouchingResult]);

  // ── Skin Color Uniform ─────────────────────────────────────────────

  const applySkinColorUniform = useCallback(
    async (faceLandmarks: FaceLandmark[][], strength: number): Promise<string | null> => {
      try {
        const jsAdjustments = getTransformAdjustments(adjustments);
        const result = await invoke<string>('unify_skin_color', { jsAdjustments, faceLandmarks, strength });
        publishRetouchingResult(result);
        return result;
      } catch (err) {
        console.error('applySkinColorUniform failed:', err);
        return null;
      }
    },
    [adjustments, publishRetouchingResult],
  );

  // ── Body Reshape ────────────────────────────────────────────────────

  const applyBodyReshape = useCallback(
    async (bodyKeypoints: BodyKeypoint[], params: BodyReshapeParams): Promise<string | null> => {
      try {
        const jsAdjustments = getTransformAdjustments(adjustments);
        const result = await invoke<string>('apply_body_reshape', {
          jsAdjustments,
          bodyKeypoints,
          params,
        });
        publishRetouchingResult(result);
        return result;
      } catch (err) {
        console.error('applyBodyReshape failed:', err);
        return null;
      }
    },
    [adjustments, publishRetouchingResult],
  );

  // ── Liquify ─────────────────────────────────────────────────────────

  const addLiquifyStroke = useCallback((stroke: LiquifyStroke) => {
    setLiquifyStrokes((prev) => [...prev, stroke]);
  }, []);

  const undoLiquifyStroke = useCallback(() => {
    setLiquifyStrokes((prev) => prev.slice(0, -1));
  }, []);

  const clearLiquifyStrokes = useCallback(() => {
    setLiquifyStrokes([]);
  }, []);

  const applyLiquify = useCallback(
    async (strokes: LiquifyStroke[]): Promise<string | null> => {
      try {
        const jsAdjustments = getTransformAdjustments(adjustments);
        // Map frontend LiquifyStroke to Rust LiquifyStrokeCommand format
        const mappedStrokes = strokes.map((s) => ({
          brushType: s.brushType,
          radius: s.brushSize,
          pressure: s.brushPressure,
          points: s.points.map((p) => [p.x, p.y]),
        }));
        const result = await invoke<string>('apply_liquify', { jsAdjustments, strokes: mappedStrokes });
        publishRetouchingResult(result);
        return result;
      } catch (err) {
        console.error('applyLiquify failed:', err);
        return null;
      }
    },
    [adjustments, publishRetouchingResult],
  );

  const resetLiquifyMesh = useCallback(() => {
    setLiquifyStrokes([]);
  }, []);

  const applyHairRetouch = useCallback(
    async (params: Record<string, unknown>): Promise<string | null> => {
      try {
        const jsAdjustments = getTransformAdjustments(adjustments);
        const result = await invoke<string>('apply_hair_retouch', { jsAdjustments, params });
        publishRetouchingResult(result);
        return result;
      } catch (err) {
        console.error('applyHairRetouch failed:', err);
        return null;
      }
    },
    [adjustments, publishRetouchingResult],
  );

  const addEyeCatchlight = useCallback(
    async (faceLandmarks: FaceLandmark[], intensity: number, lightPosition: string): Promise<string | null> => {
      try {
        const jsAdjustments = getTransformAdjustments(adjustments);
        // Map the human-readable light position string to a normalized
        // (x, y) tuple centred on (0.5, 0.5) as expected by the Rust backend.
        // The backend computes the catchlight offset as (pos - 0.5) * radius * 2.
        const positionMap: Record<string, [number, number]> = {
          'top-left': [0.3, 0.3],
          'top-right': [0.7, 0.3],
          'top-center': [0.5, 0.3],
          'center': [0.5, 0.5],
          'bottom-left': [0.3, 0.7],
          'bottom-right': [0.7, 0.7],
        };
        const lightPos = positionMap[lightPosition] ?? [0.5, 0.5];
        const result = await invoke<string>('add_eye_catchlight', { jsAdjustments, faceLandmarks, intensity, lightPosition: lightPos });
        publishRetouchingResult(result);
        return result;
      } catch (err) {
        console.error('addEyeCatchlight failed:', err);
        return null;
      }
    },
    [adjustments, publishRetouchingResult],
  );

  const adjustSmile = useCallback(
    async (faceLandmarks: FaceLandmark[], smileAmount: number): Promise<string | null> => {
      try {
        const jsAdjustments = getTransformAdjustments(adjustments);
        const result = await invoke<string>('adjust_smile', { jsAdjustments, faceLandmarks, smileAmount });
        publishRetouchingResult(result);
        return result;
      } catch (err) {
        console.error('adjustSmile failed:', err);
        return null;
      }
    },
    [adjustments, publishRetouchingResult],
  );

  const adjustNeckShoulder = useCallback(
    async (bodyKeypoints: BodyKeypoint[], neckAdjust: number, shoulderAdjust: number): Promise<string | null> => {
      try {
        const jsAdjustments = getTransformAdjustments(adjustments);
        const result = await invoke<string>('adjust_neck_shoulder', { jsAdjustments, bodyKeypoints, neckAdjust, shoulderAdjust });
        publishRetouchingResult(result);
        return result;
      } catch (err) {
        console.error('adjustNeckShoulder failed:', err);
        return null;
      }
    },
    [adjustments, publishRetouchingResult],
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

    // Advanced portrait
    addEyeCatchlight,
    adjustSmile,
    adjustNeckShoulder,

    // Liquify
    liquifyStrokes,
    addLiquifyStroke,
    undoLiquifyStroke,
    clearLiquifyStrokes,
    applyLiquify,
    resetLiquifyMesh,
  };
}
