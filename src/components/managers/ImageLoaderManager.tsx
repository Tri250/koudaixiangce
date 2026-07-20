import { useImageLoader } from '../../hooks/useImageLoader';

interface Props {
  cachedEditStateRef: React.RefObject<unknown>;
}

export default function ImageLoaderManager({ cachedEditStateRef }: Props) {
  useImageLoader(cachedEditStateRef);

  return null;
}
