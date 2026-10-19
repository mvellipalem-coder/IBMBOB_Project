import { useEffect } from 'react';
import { Link } from 'react-router-dom';
import Notice from '../common/Notice.jsx';

const SUCCESS_VISIBLE_MS = 6000;

/**
 * Result banner for useAddToCart(), shown as a toast so it is visible wherever the button was.
 * Shows the server's message, with a cart link on success. Success closes itself; errors stay until dismissed.
 */
export default function AddToCartFeedback({ feedback, onDismiss }) {
  const success = feedback?.type === 'success';

  useEffect(() => {
    if (!success) return undefined;
    const timer = setTimeout(onDismiss, SUCCESS_VISIBLE_MS);
    return () => clearTimeout(timer);
  }, [feedback, success, onDismiss]);

  if (!feedback) return null;
  return (
    <div className="toast">
      <Notice type={success ? 'success' : 'error'} onDismiss={onDismiss}>
        {feedback.message} {success && <Link to="/cart">View cart</Link>}
      </Notice>
    </div>
  );
}
