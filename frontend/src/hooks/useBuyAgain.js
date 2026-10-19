import { useCallback, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useCart } from '../context/CartContext.jsx';
import { buyAgain } from '../services/orderService.js';

/**
 * Copies the books of a past order into the cart (POST /api/orders/{id}/buy-again).
 * Navigates to the cart when everything was added; otherwise exposes `issues`.
 */
export function useBuyAgain() {
  const navigate = useNavigate();
  const { replaceCart } = useCart();
  const [working, setWorking] = useState(false);
  const [error, setError] = useState(null);
  const [issues, setIssues] = useState(null);

  const run = useCallback(
    async (orderId) => {
      setWorking(true);
      setError(null);
      setIssues(null);
      try {
        const result = await buyAgain(orderId);
        replaceCart(result.cart);
        if (result.issues.length === 0) navigate('/cart');
        else setIssues(result.issues);
      } catch (caught) {
        setError(caught);
      } finally {
        setWorking(false);
      }
    },
    [navigate, replaceCart],
  );

  const clearIssues = useCallback(() => setIssues(null), []);
  return { run, working, error, issues, clearIssues };
}
