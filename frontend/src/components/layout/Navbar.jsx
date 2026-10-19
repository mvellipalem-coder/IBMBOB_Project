import { useEffect, useState } from 'react';
import { Link, NavLink, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext.jsx';
import { useCart } from '../../context/CartContext.jsx';

const navLinkClass = ({ isActive }) => `navbar__link${isActive ? ' navbar__link--active' : ''}`;

function BookIcon() {
  return (
    <svg className="navbar__icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" aria-hidden="true">
      <rect x="3" y="4" width="18" height="16" rx="1" />
      <path d="M12 4v16M6 9h3M6 13h3M15 9h3M15 13h3" />
    </svg>
  );
}

function CartIcon() {
  return (
    <svg className="navbar__icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" aria-hidden="true">
      <path d="M3 4h2l2.4 11h10.2l2-8H6.2" />
      <circle cx="9" cy="19" r="1.3" />
      <circle cx="17" cy="19" r="1.3" />
    </svg>
  );
}

export default function Navbar() {
  const { isAuthenticated, user, logout } = useAuth();
  const { cart } = useCart();
  const [menuOpen, setMenuOpen] = useState(false);
  const location = useLocation();
  const navigate = useNavigate();

  // Close the mobile menu after navigating.
  useEffect(() => {
    setMenuOpen(false);
  }, [location.pathname]);

  const handleLogout = () => {
    logout();
    navigate('/');
  };

  const cartCount = isAuthenticated ? cart.totalQuantity : 0;

  return (
    <header className="navbar">
      <div className="navbar__inner">
        <Link to="/" className="navbar__brand">
          <BookIcon />
          <span>Book Worm</span>
        </Link>

        <button
          type="button"
          className="navbar__toggle"
          aria-expanded={menuOpen}
          aria-controls="main-navigation"
          onClick={() => setMenuOpen((open) => !open)}
        >
          <span className="visually-hidden">{menuOpen ? 'Close menu' : 'Open menu'}</span>
          <span aria-hidden="true">{menuOpen ? '\u2715' : '\u2630'}</span>
        </button>

        <nav
          id="main-navigation"
          className={`navbar__menu${menuOpen ? ' navbar__menu--open' : ''}`}
          aria-label="Main"
        >
          <ul className="navbar__list">
            <li>
              <NavLink to="/" end className={navLinkClass}>
                Home
              </NavLink>
            </li>
            <li>
              <NavLink to="/catalogue" className={navLinkClass}>
                Catalogue
              </NavLink>
            </li>
            {isAuthenticated && (
              <>
                <li>
                  <NavLink to="/orders" className={navLinkClass}>
                    My orders
                  </NavLink>
                </li>
                <li>
                  <NavLink to="/recommendations" className={navLinkClass}>
                    For you
                  </NavLink>
                </li>
                <li>
                  <NavLink to="/addresses" className={navLinkClass}>
                    Addresses
                  </NavLink>
                </li>
              </>
            )}
          </ul>

          <ul className="navbar__list navbar__list--actions">
            <li>
              <NavLink to="/cart" className={navLinkClass}>
                <CartIcon />
                <span>Cart</span>
                {cartCount > 0 && (
                  <span className="navbar__count" aria-label={`${cartCount} items in cart`}>
                    {cartCount}
                  </span>
                )}
              </NavLink>
            </li>
            {isAuthenticated ? (
              <>
                <li className="navbar__user">Hi, {user?.fullName?.split(' ')[0]}</li>
                <li>
                  <button type="button" className="btn btn--secondary btn--small" onClick={handleLogout}>
                    Log out
                  </button>
                </li>
              </>
            ) : (
              <>
                <li>
                  <NavLink to="/login" className={navLinkClass}>
                    Log in
                  </NavLink>
                </li>
                <li>
                  <Link to="/register" className="btn btn--primary btn--small">
                    Sign up
                  </Link>
                </li>
              </>
            )}
          </ul>
        </nav>
      </div>
    </header>
  );
}
