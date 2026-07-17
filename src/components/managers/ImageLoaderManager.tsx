import { useImageLoader } from '../../hooks/useImageLoader';

interface Props {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  cachedEditStateRef: React.RefObject<any>;
}

export default function ImageLoaderManager({ cachedEditStateRef }: Props) {
  useImageLoader(cachedEditStateRef);

  return null;
}
