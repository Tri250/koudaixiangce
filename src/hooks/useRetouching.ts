import { useState, useCallback } from 'react';
import { invoke } from '@tauri-apps/api/core';

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

  const detectFaces = useCallback(async (imagePath: string): Promise<FaceDetection[]> => {
    setIsDetectingFaces(true);
    setFaceDetectionError(null);
    try {
      const result = await invoke<FaceDetection[]>('detect_faces_in_image', { imagePath });
      setFaceDetections(result);
      return result;
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      setFaceDetectionError(msg);
      return [];
    } finally {
      setIsDetectingFaces(false);
    }
  }, []);

  // ── Body Detection ──────────────────────────────────────────────────

  const detectBody = useCallback(async (imagePath: string): Promise<BodyDetection[]> => {
    setIsDetectingBody(true);
    setBodyDetectionError(null);
    try {
      const result = await invoke<BodyDetection[]>('detect_body_in_image', { imagePath });
      setBodyDetections(result);
      return result;
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
    async (imagePath: string, params: FaceReshapeParams, landmarks: FaceLandmark[]): Promise<string | null> => {
      try {
        const result = await invoke<string>('apply_face_reshape', {
          imagePath,
          params,
          landmarks,
        });
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
    async (imagePath: string, params: SkinSmoothingParams): Promise<string | null> => {
      try {
        const result = await invoke<string>('apply_skin_smoothing', { imagePath, params });
        return result;
      } catch (err) {
        console.error('applySkinSmoothing failed:', err);
        return null;
      }
    },
    [],
  );

  // ── Blemish Removal ────────────────────────────────────────────────

  const applyBlemishRemoval = useCallback(async (imagePath: string): Promise<string | null> => {
    try {
      const result = await invoke<string>('auto_remove_blemishes', { imagePath });
      return result;
    } catch (err) {
      console.error('applyBlemishRemoval failed:', err);
      return null;
    }
  }, []);

  // ── Skin Color Uniform ─────────────────────────────────────────────

  const applySkinColorUniform = useCallback(
    async (imagePath: string, params: SkinColorUniformParams): Promise<string | null> => {
      try {
        const result = await invoke<string>('unify_skin_color', { imagePath, params });
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
    async (imagePath: string, params: BodyReshapeParams, keypoints: BodyKeypoint[]): Promise<string | null> => {
      try {
        const result = await invoke<string>('apply_body_reshape', {
          imagePath,
          params,
          keypoints,
        });
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
    setLiquifyStrokes((prev) => [...prev, stroke]);
  }, []);

  const undoLiquifyStroke = useCallback(() => {
    setLiquifyStrokes((prev) => prev.slice(0, -1));
  }, []);

  const clearLiquifyStrokes = useCallback(() => {
    setLiquifyStrokes([]);
  }, []);

  const applyLiquify = useCallback(
    async (imagePath: string, strokes: LiquifyStroke[]): Promise<string | null> => {
      try {
        const result = await invoke<string>('apply_liquify', { imagePath, strokes });
        return result;
      } catch (err) {
        console.error('applyLiquify failed:', err);
        return null;
      }
    },
    [],
  );

  const resetLiquifyMesh = useCallback(() => {
    setLiquifyStrokes([]);
  }, []);

  const applyHairRetouch = useCallback(
    async (imagePath: string, params: Record<string, unknown>): Promise<string | null> => {
      try {
        const result = await invoke<string>('apply_hair_retouch', { imagePath, params });
        return result;
      } catch (err) {
        console.error('applyHairRetouch failed:', err);
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
  };
}
