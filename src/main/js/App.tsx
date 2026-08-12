import { useState, useEffect } from 'react';
import ReactDOMClient from "react-dom/client";
import { BrowserRouter, Routes, Route } from "react-router-dom";
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import Contact from "./components/Contact";
import Layout from "./components/Layout";
import Home from "./components/Home";
import HowToPlay from "./components/HowToPlay";
import GameHub from "./components/GameHub";
import ProfilePage from "./components/ProfilePage";
import NoPage from "./components/NoPage";
import { UserProvider } from './components/UserProvider';
// Initialise i18next at the entry point so translations are configured before any component renders.
import './i18n/i18n';

const queryClient = new QueryClient();

function App() {
  const [isAuthenticated, setIsAuthenticated] = useState(false);

  useEffect(() => {
    // The server renders the #authenticated marker element (see index.html) only when the user is
    // authenticated, so its presence indicates that a user is logged in.
    setIsAuthenticated(document.getElementById('authenticated') !== null);
  }, []);

  return (
    <QueryClientProvider client={queryClient}>
      <UserProvider isAuthenticated={isAuthenticated}>
        <BrowserRouter>
          <Routes>
            <Route path="/" element={<Layout/>}>
              <Route index element={<Home />} />
              <Route path="howtoplay" element={<HowToPlay />} />
              <Route path="gamehub" element={<GameHub/>} />
              <Route path="profile" element={<ProfilePage />} />
              <Route path="contact" element={<Contact />} />
              <Route path="*" element={<NoPage />} />
            </Route>
          </Routes>
        </BrowserRouter>
      </UserProvider>
    </QueryClientProvider>
  );
}

const container = document.getElementById('react');
if (!container) {
  throw new Error('Root element #react not found');
}
ReactDOMClient.createRoot(container).render(<App />);
