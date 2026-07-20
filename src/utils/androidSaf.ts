/**
 * Android SAF (Storage Access Framework) file picker bridge.
 *
 * On Android, the Tauri dialog plugin does not work. These functions provide
 * an alternative by calling the native SAF file picker through the Android
 * activity's JavaScript bridge.
 *
 * Usage:
 *   import { androidSafOpen, androidSafSave } from '../utils/androidSaf';
 *
 *   const uri = await androidSafOpen('image/*');
 *   const uri = await androidSafSave('output.png', 'image/png');
 */

interface AndroidSafWindow extends Window {
  __TAURI__?: { platform?: string };
  __androidSafOpen?: (mimeTypes: string, callbackName: string) => void;
  __androidSafSave?: (defaultName: string, mimeTypes: string, callbackName: string) => void;
  [key: string]: unknown;
}

const androidWindow = window as unknown as AndroidSafWindow;

let callbackCounter = 0;

function isAndroid(): boolean {
  return !!androidWindow.__TAURI__?.platform?.startsWith?.('android')
    || /android/i.test(navigator.userAgent);
}

/**
 * Opens the Android SAF file picker for selecting a file.
 * @param mimeTypes - MIME type filter, e.g. 'image/*' or 'image/jpeg,image/png'
 * @returns The content URI of the selected file, or null if cancelled.
 */
export function androidSafOpen(mimeTypes: string = '*/*'): Promise<string | null> {
  return new Promise((resolve) => {
    if (!isAndroid()) {
      resolve(null);
      return;
    }

    const callbackName = `__safOpenCb_${++callbackCounter}`;
    androidWindow[callbackName] = (uri: string | null) => {
      delete androidWindow[callbackName];
      resolve(uri);
    };

    // Call the native method via the JS bridge set up in useAndroidBackHandler
    if (typeof androidWindow.__androidSafOpen === 'function') {
      androidWindow.__androidSafOpen(mimeTypes, callbackName);
    } else {
      // Fallback: try to invoke Tauri command directly
      delete androidWindow[callbackName];
      resolve(null);
    }
  });
}

/**
 * Opens the Android SAF file picker for saving/creating a file.
 * @param defaultName - Default filename to suggest, e.g. 'output.png'
 * @param mimeTypes - MIME type filter, e.g. 'image/png'
 * @returns The content URI where the file should be written, or null if cancelled.
 */
export function androidSafSave(defaultName: string, mimeTypes: string = '*/*'): Promise<string | null> {
  return new Promise((resolve) => {
    if (!isAndroid()) {
      resolve(null);
      return;
    }

    const callbackName = `__safSaveCb_${++callbackCounter}`;
    androidWindow[callbackName] = (uri: string | null) => {
      delete androidWindow[callbackName];
      resolve(uri);
    };

    if (typeof androidWindow.__androidSafSave === 'function') {
      androidWindow.__androidSafSave(defaultName, mimeTypes, callbackName);
    } else {
      delete androidWindow[callbackName];
      resolve(null);
    }
  });
}
