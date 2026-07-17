import { useImageProcessing } from '../../hooks/useImageProcessing';

interface Props {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  transformWrapperRef: React.RefObject<any>;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  prevAdjustmentsRef: React.RefObject<any>;
  previewJobIdRef: React.RefObject<number>;
  latestRenderedJobIdRef: React.RefObject<number>;
  currentResRef: React.RefObject<number>;
}

export default function ImageProcessingManager(props: Props) {
  useImageProcessing(props.transformWrapperRef, props.prevAdjustmentsRef, {
    previewJobIdRef: props.previewJobIdRef,
    latestRenderedJobIdRef: props.latestRenderedJobIdRef,
    currentResRef: props.currentResRef,
  });

  return null;
}
