import { createContext, useMemo, type ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import { getJson } from '../http';
import type { PlayerDto } from '../types';

export interface UserContextValue {
  currentUser: PlayerDto | null;
  isLoading: boolean;
}

export const UserContext = createContext<UserContextValue>({ currentUser: null, isLoading: false });

interface UserProviderProps {
  children: ReactNode;
  isAuthenticated: boolean;
}

export const UserProvider = ({ children, isAuthenticated }: UserProviderProps) => {
  // Fetch the current player only when authenticated. TanStack Query handles caching, loading and
  // error state, so no manual useState/useEffect is needed.
  const { data, isLoading } = useQuery({
    queryKey: ['currentUser'],
    queryFn: () => getJson<PlayerDto>('/api/player/me'),
    enabled: isAuthenticated,
  });

  // Memoise so the context value reference changes only when the user or loading flag change.
  const value = useMemo<UserContextValue>(
    () => ({ currentUser: data ?? null, isLoading }),
    [data, isLoading]
  );

  return <UserContext.Provider value={value}>{children}</UserContext.Provider>;
};
