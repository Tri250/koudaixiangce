import { Crop } from 'react-image-crop';

export function getOrientedDimensions(
  imageWidth: number,
  imageHeight: number,
  orientationSteps: number,
): { width: number; height: number } {
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
