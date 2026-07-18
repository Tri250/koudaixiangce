import React, { createContext, useContext } from 'react';
import {
  ClerkProvider,
  useAuth as clerkUseAuth,
  useUser as clerkUseUser,
  useClerk as clerkUseClerk,
  Show as ClerkShow,
  SignIn as ClerkSignIn,
} from '@clerk/react';
import { useSettingsStore } from '../store/useSettingsStore';

interface ClerkContextType {
  getToken: () => Promise<string | null>;
  user: any;
  isSignedIn: boolean;
  signOut: () => Promise<void>;
}

const defaultFallback: ClerkContextType = {
  getToken: async () => null,
  user: null,
  isSignedIn: false,
  signOut: async () => {},
};

const ClerkContext = createContext<ClerkContextType>(defaultFallback);

function ClerkContextPopulator({ children }: { children: React.ReactNode }) {
  const { getToken } = clerkUseAuth();
  const { user, isSignedIn } = clerkUseUser();
  const { signOut } = clerkUseClerk();

  return (
    <ClerkContext.Provider value={{ getToken, user, isSignedIn, signOut }}>
      {children}
    </ClerkContext.Provider>
  );
}

const CLERK_PUBLISHABLE_KEY = 'pk_test_YnJpZWYtc2Vhc25haWwtMTIuY2xlcmsuYWNjb3VudHMuZGV2JA';

export function ClerkProviderFallback({ children }: { children: React.ReactNode }) {
  const osPlatform = useSettingsStore((s) => s.osPlatform);
  const isAndroid = osPlatform === 'android';

  if (isAndroid) {
    return (
      <ClerkContext.Provider value={defaultFallback}>
        {children}
      </ClerkContext.Provider>
    );
  }

  return (
    <ClerkProvider publishableKey={CLERK_PUBLISHABLE_KEY} routerPush={() => {}} routerReplace={() => {}}>
      <ClerkContextPopulator>{children}</ClerkContextPopulator>
    </ClerkProvider>
  );
}

export function useClerkAuth() {
  const ctx = useContext(ClerkContext);
  return { getToken: ctx.getToken };
}

export function useClerkUser() {
  const ctx = useContext(ClerkContext);
  return { user: ctx.user, isSignedIn: ctx.isSignedIn };
}

export function useClerkInstance() {
  const ctx = useContext(ClerkContext);
  return { signOut: ctx.signOut };
}

export function ClerkShowFallback({ when, children }: { when: string; children?: React.ReactNode }) {
  const osPlatform = useSettingsStore((s) => s.osPlatform);
  if (osPlatform === 'android') return null;
  return <ClerkShow when={when}>{children}</ClerkShow>;
}

export function ClerkSignInFallback(props: any) {
  const osPlatform = useSettingsStore((s) => s.osPlatform);
  if (osPlatform === 'android') return null;
  return <ClerkSignIn {...props} />;
}
