import { useEffect } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext.jsx';
import Footer from './Footer.jsx';
import Navbar from './Navbar.jsx';
import Notice from '../common/Notice.jsx';

/** Page frame shared by every route: skip link, navbar, main content, footer. */
export default function Layout() {
  const { sessionExpired, dismissSessionExpired } = useAuth();
  const location = useLocation();

  // After navigating, move focus to the main region so keyboard and screen-reader users start at the content.
  useEffect(() => {
    document.getElementById('main-content')?.focus({ preventScroll: true });
    window.scrollTo(0, 0);
  }, [location.pathname]);

  return (
    <div className="app-shell">
      <a href="#main-content" className="skip-link">
        Skip to main content
      </a>
      <Navbar />
      <main id="main-content" className="app-main" tabIndex={-1}>
        {sessionExpired && (
          <div className="container">
            <Notice type="warning" onDismiss={dismissSessionExpired}>
              Your session has ended. Please log in again.
            </Notice>
          </div>
        )}
        <Outlet />
      </main>
      <Footer />
    </div>
  );
}
