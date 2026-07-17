import 'i18next';

// eslint-disable-next-line @typescript-eslint/no-explicit-any
type SimpleTFunction = (key: string, options?: any) => any;

// eslint-disable-next-line @typescript-eslint/no-explicit-any
declare module 'react-i18next' {
  export function useTranslation(
    ns?: string | string[],
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    options?: any,
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
  ): { t: SimpleTFunction; i18n: any; ready: boolean };
}

declare module 'i18next' {
  interface CustomTypeOptions {
    defaultNS: 'translation';
    allowObjectInHTMLChildren: true;
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    resources: { translation: Record<string, any> };
  }
}
