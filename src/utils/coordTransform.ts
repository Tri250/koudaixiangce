import { Crop } from 'react-image-crop';

export interface Coord2D {
  x: number;
  y: number;
}

export interface Size2D {
  width: number;
  height: number;
}

export interface ImageTransformContext {
  offsetX: number;
  offsetY: number;
  scale: number;
  imageWidth: number;
  imageHeight: number;
  crop?: Crop | null;
}

export function stageToImageCoords(
  stageX: number,
  stageY: number,
  offsetX: number,
  offsetY: number,
  scale: number,
): Coord2D {
  return {
    x: stageX / scale - offsetX,
    y: stageY / scale - offsetY,
  };
}

export function imageToStageCoords(
  imageX: number,
  imageY: number,
  offsetX: number,
  offsetY: number,
  scale: number,
): Coord2D {
  return {
    x: (imageX + offsetX) * scale,
    y: (imageY + offsetY) * scale,
  };
}

export function getCropAbsoluteValues(
  crop: Crop | null | undefined,
  imageWidth: number,
  imageHeight: number,
): { x: number; y: number; width: number; height: number } {
  if (!crop) {
    return { x: 0, y: 0, width: imageWidth, height: imageHeight };
  }
  const isPercent = crop.unit === '%';
  return {
    x: isPercent ? ((crop.x ?? 0) / 100) * imageWidth : (crop.x ?? 0),
    y: isPercent ? ((crop.y ?? 0) / 100) * imageHeight : (crop.y ?? 0),
    width: isPercent ? ((crop.width ?? 0) / 100) * imageWidth : (crop.width ?? imageWidth),
    height: isPercent ? ((crop.height ?? 0) / 100) * imageHeight : (crop.height ?? imageHeight),
  };
}

export function stageToCroppedImageCoords(
  stageX: number,
  stageY: number,
  ctx: ImageTransformContext,
): Coord2D {
  const { offsetX, offsetY, scale, imageWidth, imageHeight, crop } = ctx;
  const imgCoords = stageToImageCoords(stageX, stageY, offsetX, offsetY, scale);
  const cropRect = getCropAbsoluteValues(crop, imageWidth, imageHeight);
  return {
    x: imgCoords.x + cropRect.x,
    y: imgCoords.y + cropRect.y,
  };
}

export function croppedImageToStageCoords(
  croppedX: number,
  croppedY: number,
  ctx: ImageTransformContext,
): Coord2D {
  const { offsetX, offsetY, scale, imageWidth, imageHeight, crop } = ctx;
  const cropRect = getCropAbsoluteValues(crop, imageWidth, imageHeight);
  return imageToStageCoords(croppedX - cropRect.x, croppedY - cropRect.y, offsetX, offsetY, scale);
}

export function getOrientedDimensions(
  imageWidth: number,
  imageHeight: number,
  orientationSteps: number,
): Size2D {
  const safeWidth = isFinite(imageWidth) && imageWidth > 0 ? imageWidth : 1;
  const safeHeight = isFinite(imageHeight) && imageHeight > 0 ? imageHeight : 1;
  const isSwapped = orientationSteps === 1 || orientationSteps === 3;
  return {
    width: isSwapped ? safeHeight : safeWidth,
    height: isSwapped ? safeWidth : safeHeight,
  };
}

export function calculateCenteredCrop(
  imageWidth: number,
  imageHeight: number,
  orientationSteps: number,
  aspectRatio: number | null,
  rotation: number = 0,
): Crop | null {
  if (!aspectRatio || !isFinite(aspectRatio) || aspectRatio <= 0) return null;
  if (!isFinite(imageWidth) || !isFinite(imageHeight)) return null;
  if (imageWidth <= 0 || imageHeight <= 0) return null;
  if (!isFinite(rotation)) rotation = 0;

  const { width: W, height: H } = getOrientedDimensions(imageWidth, imageHeight, orientationSteps);

  const angle = Math.abs(rotation);
  const rad = ((angle % 180) * Math.PI) / 180;
  const sin = Math.sin(rad);
  const cos = Math.cos(rad);

  const denom1 = aspectRatio * sin + cos;
  const denom2 = aspectRatio * cos + sin;
  if (Math.abs(denom1) < 1e-10 || Math.abs(denom2) < 1e-10) return null;
  const h_c = Math.min(H / denom1, W / denom2);
  const w_c = aspectRatio * h_c;

  return {
    unit: 'px',
    x: Math.round((W - w_c) / 2),
    y: Math.round((H - h_c) / 2),
    width: Math.round(w_c),
    height: Math.round(h_c),
  };
}

export function clamp(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max);
}

export function lerp(a: number, b: number, t: number): number {
  return a + (b - a) * t;
}

export function normalizeToPixel(
  normX: number,
  normY: number,
  width: number,
  height: number,
): Coord2D {
  return {
    x: normX * width,
    y: normY * height,
  };
}

export function pixelToNormalized(
  px: number,
  py: number,
  width: number,
  height: number,
): Coord2D {
  return {
    x: width > 0 ? px / width : 0,
    y: height > 0 ? py / height : 0,
  };
}
