import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
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
  loading: boolean;
}

const defaultFallback: ClerkContextType = {
  getToken: async () => null,
  user: null,
  isSignedIn: false,
  signOut: async () => {},
  loading: false,
};

const ClerkContext = createContext<ClerkContextType>(defaultFallback);

function ClerkContextPopulator({ children }: { children: React.ReactNode }) {
  const { getToken } = clerkUseAuth();
  const { user, isSignedIn, isLoaded } = clerkUseUser();
  const { signOut } = clerkUseClerk();

  return (
    <ClerkContext.Provider value={{ getToken, user, isSignedIn: !!isSignedIn, signOut, loading: !isLoaded }}>
      {children}
    </ClerkContext.Provider>
  );
}

const CLERK_PUBLISHABLE_KEY = 'pk_test_YnJpZWYtc2Vhc25haWwtMTIuY2xlcmsuYWNjb3VudHMuZGV2JA';

function AndroidAuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<any>(null);
  const [isSignedIn, setIsSignedIn] = useState(false);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const loadAuthState = async () => {
      try {
        const storedUser = localStorage.getItem('android_user');
        const storedToken = localStorage.getItem('android_token');
        
        if (storedUser && storedToken) {
          try {
            setUser(JSON.parse(storedUser));
            setIsSignedIn(true);
          } catch {
            localStorage.removeItem('android_user');
            localStorage.removeItem('android_token');
          }
        }
      } catch {
        console.warn('Failed to load Android auth state');
      } finally {
        setLoading(false);
      }
    };

    loadAuthState();
  }, []);

  const getToken = useCallback(async () => {
    return localStorage.getItem('android_token');
  }, []);

  const signOut = useCallback(async () => {
    localStorage.removeItem('android_user');
    localStorage.removeItem('android_token');
    setUser(null);
    setIsSignedIn(false);
  }, []);

  return (
    <ClerkContext.Provider value={{ getToken, user, isSignedIn, signOut, loading }}>
      {children}
    </ClerkContext.Provider>
  );
}

export function ClerkProviderFallback({ children }: { children: React.ReactNode }) {
  const osPlatform = useSettingsStore((s) => s.osPlatform);
  const isAndroid = osPlatform === 'android';

  if (isAndroid) {
    return <AndroidAuthProvider>{children}</AndroidAuthProvider>;
  }

  return (
    <ClerkProvider publishableKey={CLERK_PUBLISHABLE_KEY} routerPush={() => {}} routerReplace={() => {}}>
      <ClerkContextPopulator>{children}</ClerkContextPopulator>
    </ClerkProvider>
  );
}

export function useClerkAuth() {
  const ctx = useContext(ClerkContext);
  return { getToken: ctx.getToken, loading: ctx.loading };
}

export function useClerkUser() {
  const ctx = useContext(ClerkContext);
  return { user: ctx.user, isSignedIn: ctx.isSignedIn, loading: ctx.loading };
}

export function useClerkInstance() {
  const ctx = useContext(ClerkContext);
  return { signOut: ctx.signOut };
}

export function ClerkShowFallback({ when, children }: { when: string; children?: React.ReactNode }) {
  const osPlatform = useSettingsStore((s) => s.osPlatform);
  const ctx = useContext(ClerkContext);

  if (osPlatform === 'android') {
    const condition = when === 'authenticated' ? ctx.isSignedIn : !ctx.isSignedIn;
    return condition ? <>{children}</> : null;
  }

  return <ClerkShow when={when as any}>{children}</ClerkShow>;
}

export function ClerkSignInFallback(props: any) {
  const osPlatform = useSettingsStore((s) => s.osPlatform);
  if (osPlatform === 'android') return null;
  return <ClerkSignIn {...props} />;
}
