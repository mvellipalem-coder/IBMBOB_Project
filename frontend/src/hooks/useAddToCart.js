import { useCallback, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext.jsx';
import { useCart } from '../context/CartContext.jsx';

/**
 * Shared "add to cart" behaviour for book cards and the details page.
 * Signed-out visitors are sent to the login page. The cart is changed only when the server confirms it.
 */
export function useAddToCart() {
  const { isAuthenticated } = useAuth();
  const { addItem } = useCart();
  const navigate = useNavigate();
  const location = useLocation();
  const [pendingId, setPendingId] = useState(null);
  const [feedback, setFeedback] = useState(null); // { bookId, type: 'success' | 'error', message }

  const add = useCallback(
    async (book, quantity = 1) => {
      if (!isAuthenticated) {
        navigate('/login', { state: { from: location.pathname + location.search } });
        return;
      }
      setPendingId(book.id);
      setFeedback(null);
      try {
        await addItem(book.id, quantity);
        setFeedback({ bookId: book.id, type: 'success', message: `"${book.title}" was added to your cart.` });
      } catch (error) {
        setFeedback({ bookId: book.id, type: 'error', message: error.message });
      } finally {
        setPendingId(null);
      }
    },
    [isAuthenticated, addItem, navigate, location],
  );

  const clearFeedback = useCallback(() => setFeedback(null), []);

  return { add, pendingId, feedback, clearFeedback };
}
