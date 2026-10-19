import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import CartItem from '../components/cart/CartItem.jsx';
import OrderSummary from '../components/cart/OrderSummary.jsx';
import ConfirmationDialog from '../components/common/ConfirmationDialog.jsx';
import EmptyState from '../components/common/EmptyState.jsx';
import ErrorMessage from '../components/common/ErrorMessage.jsx';
import LoadingSpinner from '../components/common/LoadingSpinner.jsx';
import Notice from '../components/common/Notice.jsx';
import PageContainer from '../components/layout/PageContainer.jsx';
import { useCart } from '../context/CartContext.jsx';
import { useDocumentTitle } from '../hooks/useDocumentTitle.js';

const CART_CRUMBS = [{ label: 'Home', to: '/' }, { label: 'Shopping Cart' }];

export default function CartPage() {
  useDocumentTitle('Your cart');
  const navigate = useNavigate();
  const { cart, loading, error, refresh, updateItem, removeItem } = useCart();
  const [busyItemId, setBusyItemId] = useState(null);
  const [actionError, setActionError] = useState(null);
  const [toRemove, setToRemove] = useState(null);

  const run = async (itemId, action) => {
    setBusyItemId(itemId);
    setActionError(null);
    try {
      await action();
    } catch (err) {
      setActionError(err);
      refresh(); // re-sync with the server, which is the source of truth
    } finally {
      setBusyItemId(null);
    }
  };

  const handleQuantityChange = (item, quantity) => run(item.itemId, () => updateItem(item.itemId, quantity));

  const confirmRemove = async () => {
    const item = toRemove;
    await run(item.itemId, () => removeItem(item.itemId));
    setToRemove(null);
  };

  const hasProblem = cart.items.some((item) => item.quantity > item.maxQuantity);

  if (loading && cart.items.length === 0) {
    return <PageContainer title="Shopping Cart" breadcrumbs={CART_CRUMBS}><LoadingSpinner label="Loading your cart..." /></PageContainer>;
  }
  if (error && cart.items.length === 0) {
    return (
      <PageContainer title="Shopping Cart" breadcrumbs={CART_CRUMBS}>
        <ErrorMessage error={error} title="Could not load your cart" onRetry={refresh} />
      </PageContainer>
    );
  }
  if (cart.items.length === 0) {
    return (
      <PageContainer title="Shopping Cart" breadcrumbs={CART_CRUMBS}>
        <EmptyState title="Your cart is empty" message="Find something to read and add it to your cart.">
          <Link to="/catalogue" className="btn btn--primary">Browse books</Link>
        </EmptyState>
      </PageContainer>
    );
  }

  return (
    <PageContainer title="Shopping Cart" breadcrumbs={CART_CRUMBS} subtitle={`${cart.totalQuantity} ${cart.totalQuantity === 1 ? 'item' : 'items'}`}>
      {actionError && <ErrorMessage error={actionError} title="Could not update your cart" />}
      {hasProblem && (
        <Notice type="warning">
          Some books have limited stock. Adjust the highlighted quantities before checkout.
        </Notice>
      )}
      <div className="two-column">
        <ul className="cart-list" aria-label="Cart items">
          {cart.items.map((item) => (
            <CartItem
              key={item.itemId}
              item={item}
              busy={busyItemId === item.itemId}
              onQuantityChange={handleQuantityChange}
              onRemove={setToRemove}
            />
          ))}
        </ul>
        <OrderSummary
          itemCount={cart.totalQuantity}
          title="Grand Total"
          subtotal={cart.subtotal}
          note="Gift points and the final total are confirmed by the server at checkout."
        >
          <button
            type="button"
            className="btn btn--primary btn--block"
            onClick={() => navigate('/checkout')}
            disabled={hasProblem || busyItemId !== null}
          >
            Proceed to checkout
          </button>
          <Link to="/catalogue" className="btn btn--secondary btn--block">Continue shopping</Link>
        </OrderSummary>
      </div>

      <ConfirmationDialog
        open={toRemove !== null}
        title="Remove this book?"
        message={toRemove ? `"${toRemove.book.title}" will be removed from your cart.` : ''}
        confirmLabel="Remove"
        danger
        busy={busyItemId !== null}
        onConfirm={confirmRemove}
        onCancel={() => setToRemove(null)}
      />
    </PageContainer>
  );
}
