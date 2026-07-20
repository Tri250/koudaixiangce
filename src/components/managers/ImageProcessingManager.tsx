import { useImageProcessing } from '../../hooks/useImageProcessing';
import type { Adjustments } from '../../utils/adjustments';

interface Props {
  transformWrapperRef: React.RefObject<HTMLElement | null>;
  prevAdjustmentsRef: React.RefObject<Adjustments | null>;
  previewJobIdRef: React.RefObject<number>;
  latestRenderedJobIdRef: React.RefObject<number>;
  currentResRef: React.RefObject<number>;
}

export default function ImageProcessingManager(props: Props) {
  useImageProcessing(props.transformWrapperRef.current, props.prevAdjustmentsRef, {
    previewJobIdRef: props.previewJobIdRef,
    latestRenderedJobIdRef: props.latestRenderedJobIdRef,
    currentResRef: props.currentResRef,
  });

  return null;
}
