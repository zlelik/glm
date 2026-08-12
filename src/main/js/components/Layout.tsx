import { useContext, type MouseEvent } from "react";
import { useTranslation } from 'react-i18next';
import { Outlet, Link } from "react-router-dom";
import LanguageSelector from '../i18n/LanguageSelector';
import { UserContext } from './UserProvider';

// Reads a cookie value by name (used to get the CSRF token Spring Security stores in XSRF-TOKEN).
function getCookie(name: string): string {
  const match = document.cookie.match(new RegExp('(?:^|; )' + name + '=([^;]*)'));
  return match ? decodeURIComponent(match[1]) : '';
}

const Layout = () => {
  const { t } = useTranslation();
  const { currentUser } = useContext(UserContext);

  // CSRF is enabled, so logout must be a POST carrying the X-XSRF-TOKEN header (a GET link would be rejected).
  const handleLogout = (e: MouseEvent<HTMLAnchorElement>) => {
    e.preventDefault();
    fetch('/logout', {
      method: 'POST',
      headers: { 'X-XSRF-TOKEN': getCookie('XSRF-TOKEN') },
    }).finally(() => { window.location.href = '/'; });
  };

  // Fill the viewport as a column (100dvh handles the mobile address bar): the nav takes its natural height
  // and <main> takes the rest, so a page like the live game can size itself to exactly fit (no scrolling).
  return (
    <div className="app">
      <nav aria-label="Main">
        <ul>
          <li><Link to="/">{t('home')}</Link></li>
          <li><Link to="/howtoplay">{t('how_to_play')}</Link></li>
          {currentUser && <li><Link to="/gamehub">{t('gamehub')}</Link></li>}
          {currentUser && <li><Link to="/profile">{t('profile')}</Link></li>}
          <li><Link to="/contact">{t('contact')}</Link></li>
          <li>
            {currentUser
              ? <a href="/logout" onClick={handleLogout}>{t('logout')} [{currentUser.nickName}]</a>
              : <a href="/login">{t('login')}</a>}
          </li>
          <li><LanguageSelector /></li>
        </ul>
      </nav>
      <main className="app-main">
        <Outlet />
      </main>
    </div>
  );
};

export default Layout;
