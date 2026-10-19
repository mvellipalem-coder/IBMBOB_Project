import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import * as cartService from '../services/cartService.js';
import { useAuth } from './AuthContext.jsx';

const CartContext = createContext(null);
const EMPTY_CART = { items: [], totalQuantity: 0, subtotal: 0 };

/** Holds the server-side cart for the signed-in user. The server is the source of truth for all totals. */
export function CartProvider({ children }) {
  const { isAuthenticated } = useAuth();
  const [cart, setCart] = useState(EMPTY_CART);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const refresh = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setCart(await cartService.getCart());
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (isAuthenticated) {
      refresh();
    } else {
      setCart(EMPTY_CART);
      setError(null);
    }
  }, [isAuthenticated, refresh]);

  // Each mutation returns the updated cart from the server. Errors are thrown so the caller can show them.
  const addItem = useCallback(async (bookId, quantity = 1) => {
    const updated = await cartService.addCartItem(bookId, quantity);
    setCart(updated);
    return updated;
  }, []);

  const updateItem = useCallback(async (itemId, quantity) => {
    const updated = await cartService.updateCartItem(itemId, quantity);
    setCart(updated);
    return updated;
  }, []);

  const removeItem = useCallback(async (itemId) => {
    const updated = await cartService.removeCartItem(itemId);
    setCart(updated);
    return updated;
  }, []);

  /** Use after the server changed the cart itself (checkout clears it, buy-again fills it). */
  const replaceCart = useCallback((next) => setCart(next ?? EMPTY_CART), []);

  const value = useMemo(
    () => ({ cart, loading, error, refresh, addItem, updateItem, removeItem, replaceCart }),
    [cart, loading, error, refresh, addItem, updateItem, removeItem, replaceCart],
  );

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>;
}

export function useCart() {
  const context = useContext(CartContext);
  if (!context) throw new Error('useCart must be used inside <CartProvider>.');
  return context;
}
