import { createContext, ReactNode, useCallback, useContext, useEffect, useRef, useState } from "react";
import { api, ApiError } from "../api/client";
import type { AuthSession } from "../api/types";

type AccountSessionStatus = "checking" | "ready" | "recovering";

type AccountSessionContextValue = {
  session: AuthSession | null;
  status: AccountSessionStatus;
  error: Error | null;
  refresh: () => Promise<void>;
  acceptSession: (session: AuthSession) => void;
  clearSession: () => void;
  logout: () => Promise<void>;
};

const anonymous: AuthSession = { authenticated: false, mustChangePassword: false };
const AccountSessionContext = createContext<AccountSessionContextValue | null>(null);
const RETRY_DELAYS_MS = [250, 750];
const REVALIDATE_INTERVAL_MS = 5 * 60 * 1000;
const SESSION_REQUEST_TIMEOUT_MS = 6000;

export function AccountSessionProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<AuthSession | null>(null);
  const [status, setStatus] = useState<AccountSessionStatus>("checking");
  const [error, setError] = useState<Error | null>(null);
  const sessionRef = useRef<AuthSession | null>(null);
  const inFlightRef = useRef<Promise<void> | null>(null);

  const storeSession = useCallback((next: AuthSession) => {
    sessionRef.current = next;
    setSession(next);
    setStatus("ready");
    setError(null);
  }, []);

  const clearSession = useCallback(() => storeSession(anonymous), [storeSession]);

  const refresh = useCallback(() => {
    if (inFlightRef.current) return inFlightRef.current;

    const operation = (async () => {
      setStatus(sessionRef.current ? "recovering" : "checking");
      setError(null);
      for (let attempt = 0; attempt <= RETRY_DELAYS_MS.length; attempt += 1) {
        try {
          const controller = new AbortController();
          const timeoutId = window.setTimeout(() => controller.abort(), SESSION_REQUEST_TIMEOUT_MS);
          try {
            storeSession(await api.accountSession(controller.signal));
            return;
          } finally {
            window.clearTimeout(timeoutId);
          }
        } catch (caught) {
          const normalized = caught instanceof Error ? caught : new Error("Account session check failed");
          if (!isTransient(normalized)) {
            clearSession();
            return;
          }
          if (attempt === RETRY_DELAYS_MS.length) {
            setStatus("recovering");
            setError(normalized);
            return;
          }
          await delay(RETRY_DELAYS_MS[attempt]);
        }
      }
    })().finally(() => {
      inFlightRef.current = null;
    });
    inFlightRef.current = operation;
    return operation;
  }, [clearSession, storeSession]);

  const logout = useCallback(async () => {
    try {
      await api.accountLogout();
    } finally {
      clearSession();
    }
  }, [clearSession]);

  useEffect(() => {
    void refresh();
    const onOnline = () => void refresh();
    const onVisibilityChange = () => {
      if (document.visibilityState === "visible") void refresh();
    };
    const intervalId = window.setInterval(() => void refresh(), REVALIDATE_INTERVAL_MS);
    window.addEventListener("online", onOnline);
    document.addEventListener("visibilitychange", onVisibilityChange);
    return () => {
      window.clearInterval(intervalId);
      window.removeEventListener("online", onOnline);
      document.removeEventListener("visibilitychange", onVisibilityChange);
    };
  }, [refresh]);

  return (
    <AccountSessionContext.Provider value={{
      session,
      status,
      error,
      refresh,
      acceptSession: storeSession,
      clearSession,
      logout
    }}>
      {children}
    </AccountSessionContext.Provider>
  );
}

export function useAccountSession() {
  const value = useContext(AccountSessionContext);
  if (!value) throw new Error("useAccountSession must be used inside AccountSessionProvider");
  return value;
}

function isTransient(error: Error) {
  return !(error instanceof ApiError) || error.status >= 500 || error.status === 408 || error.status === 429;
}

function delay(milliseconds: number) {
  return new Promise<void>(resolve => window.setTimeout(resolve, milliseconds));
}
