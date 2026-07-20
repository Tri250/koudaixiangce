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

let callbackCounter = 0;

function isAndroid(): boolean {
  return !!(window as any).__TAURI__?.platform?.startsWith?.('android')
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
    (window as any)[callbackName] = (uri: string | null) => {
      delete (window as any)[callbackName];
      resolve(uri);
    };

    // Call the native method via the JS bridge set up in useAndroidBackHandler
    if (typeof (window as any).__androidSafOpen === 'function') {
      (window as any).__androidSafOpen(mimeTypes, callbackName);
    } else {
      // Fallback: try to invoke Tauri command directly
      delete (window as any)[callbackName];
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
    (window as any)[callbackName] = (uri: string | null) => {
      delete (window as any)[callbackName];
      resolve(uri);
    };

    if (typeof (window as any).__androidSafSave === 'function') {
      (window as any).__androidSafSave(defaultName, mimeTypes, callbackName);
    } else {
      delete (window as any)[callbackName];
      resolve(null);
    }
  });
}
