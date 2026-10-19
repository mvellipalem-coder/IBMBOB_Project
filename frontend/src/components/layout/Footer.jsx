import { Link } from 'react-router-dom';

export default function Footer() {
  return (
    <footer className="footer">
      <div className="container footer__inner">
        <div>
          <p className="footer__brand">Book Worm</p>
          <p className="footer__text">Physical books delivered to your door.</p>
        </div>
        <nav aria-label="Footer">
          <ul className="footer__links">
            <li>
              <Link to="/catalogue">Catalogue</Link>
            </li>
            <li>
              <Link to="/orders">My orders</Link>
            </li>
            <li>
              <Link to="/cart">Cart</Link>
            </li>
          </ul>
        </nav>
        <p className="footer__copy">&copy; {new Date().getFullYear()} Book Worm</p>
      </div>
    </footer>
  );
}
