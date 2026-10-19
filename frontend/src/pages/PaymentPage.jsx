import { useRef, useState } from 'react';
import { Link, Navigate, useNavigate, useParams } from 'react-router-dom';
import OrderSummary from '../components/cart/OrderSummary.jsx';
import ErrorMessage from '../components/common/ErrorMessage.jsx';
import LoadingSpinner from '../components/common/LoadingSpinner.jsx';
import Notice from '../components/common/Notice.jsx';
import PageContainer from '../components/layout/PageContainer.jsx';
import { PAYMENT_METHODS } from '../components/payment/PaymentMethodSelector.jsx';
import { useAsyncData } from '../hooks/useAsyncData.js';
import { useDocumentTitle } from '../hooks/useDocumentTitle.js';
import { getOrder } from '../services/orderService.js';
import { processPayment } from '../services/paymentService.js';
import { formatMoney } from '../utils/format.js';
import { newIdempotencyKey } from '../utils/idempotency.js';

/** Illustrated background used by the payment and confirmation screens in the wireframes. */
function Backdrop({ children }) {
  return <div className="backdrop">{children}</div>;
}

/**
 * Payment step for CARD and UPI orders. No card or UPI details are collected: the button asks the
 * backend's simulated gateway to settle the order, and the page only moves on when the backend reports SUCCESS.
 */
export default function PaymentPage() {
  useDocumentTitle('Payment');
  const { orderId } = useParams();
  const navigate = useNavigate();
  const order = useAsyncData(({ signal }) => getOrder(orderId, { signal }), [orderId]);
  const keyRef = useRef(null); // reused when retrying so the customer is never charged twice
  const [paying, setPaying] = useState(false);
  const [error, setError] = useState(null);
  const [failure, setFailure] = useState(null);

  if (order.loading) {
    return <Backdrop><PageContainer medium><LoadingSpinner label="Loading your order..." /></PageContainer></Backdrop>;
  }
  if (order.error) {
    return (
      <Backdrop>
        <PageContainer medium>
          <ErrorMessage error={order.error} title="Could not load this order" onRetry={order.reload} />
        </PageContainer>
      </Backdrop>
    );
  }

  const data = order.data;
  const { payment } = data;
  // Nothing to pay: already settled, COD, or the order was cancelled.
  if (!failure && (payment.method === 'COD' || payment.status !== 'PENDING' || data.status === 'CANCELLED')) {
    return <Navigate to={`/orders/${data.id}/confirmation`} replace />;
  }

  const handlePay = async () => {
    if (!keyRef.current) keyRef.current = newIdempotencyKey();
    setPaying(true);
    setError(null);
    try {
      const result = await processPayment(data.id, keyRef.current);
      if (result.status === 'SUCCESS') {
        navigate(`/orders/${data.id}/confirmation`, { replace: true });
        return;
      }
      // FAILED: the server has already cancelled the order and restored stock and points.
      setFailure(result);
    } catch (err) {
      if (err.code === 'PAYMENT_ALREADY_SETTLED') {
        order.reload(); // payment finished in the meantime, so show the real state
      } else {
        setError(err);
      }
    } finally {
      setPaying(false);
    }
  };

  if (failure) {
    return (
      <Backdrop>
        <PageContainer title="Payment failed" narrow>
          <ErrorMessage
            title="Your payment was not accepted"
            error={`${failure.failureReason || 'The payment was declined.'} Your order ${data.orderNumber} has been cancelled and you were not charged.`}
          />
          <div className="form__actions">
            <Link to={`/orders/${data.id}`} className="btn btn--secondary">View order</Link>
            <Link to="/cart" className="btn btn--primary">Back to cart</Link>
          </div>
        </PageContainer>
      </Backdrop>
    );
  }

  const method = PAYMENT_METHODS.find((entry) => entry.value === payment.method);

  return (
    <Backdrop>
      <PageContainer medium>
        <section className="pay-panel" aria-labelledby="payment-title">
          <div className="pay-panel__header">
            <h1 id="payment-title">Complete your payment</h1>
            <p className="pay-panel__amount">Payable Amount: {formatMoney(data.totalAmount)}</p>
          </div>
          <div className="pay-panel__body">
            <div>
              <p className="pay-panel__method">
                <span className="muted">Order {data.orderNumber}</span>
                <strong>{method ? method.label : payment.method}</strong>
              </p>
              <Notice type="info">
                This is a simulated payment. No card or UPI details are requested or stored.
                Your order is only confirmed after the payment succeeds.
              </Notice>
              {error && <ErrorMessage error={error} title="Payment could not be completed" />}
              <button type="button" className="btn btn--primary btn--block" onClick={handlePay} disabled={paying}>
                {paying ? 'Processing payment...' : 'Pay now'}
              </button>
              {error && <p className="muted">If you retry, you will not be charged twice.</p>}
            </div>
            <OrderSummary
              title="Amount due"
              subtotal={data.subtotal}
              pointsDiscount={data.pointsDiscount}
              pointsRedeemed={data.pointsRedeemed}
              total={data.totalAmount}
            />
          </div>
        </section>
      </PageContainer>
    </Backdrop>
  );
}
