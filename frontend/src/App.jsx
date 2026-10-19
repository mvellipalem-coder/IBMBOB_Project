import { Route, Routes } from 'react-router-dom';
import Layout from './components/layout/Layout.jsx';
import ProtectedRoute from './components/layout/ProtectedRoute.jsx';
import AddressPage from './pages/AddressPage.jsx';
import BookDetailsPage from './pages/BookDetailsPage.jsx';
import CartPage from './pages/CartPage.jsx';
import CataloguePage from './pages/CataloguePage.jsx';
import CheckoutPage from './pages/CheckoutPage.jsx';
import HomePage from './pages/HomePage.jsx';
import LoginPage from './pages/LoginPage.jsx';
import NotFoundPage from './pages/NotFoundPage.jsx';
import OrderConfirmationPage from './pages/OrderConfirmationPage.jsx';
import OrderDetailsPage from './pages/OrderDetailsPage.jsx';
import OrderHistoryPage from './pages/OrderHistoryPage.jsx';
import PaymentPage from './pages/PaymentPage.jsx';
import RecommendationsPage from './pages/RecommendationsPage.jsx';
import RegisterPage from './pages/RegisterPage.jsx';

/** Route table only. Each page lives in src/pages. Protected routes require a signed-in user. */
export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<HomePage />} />
        <Route path="catalogue" element={<CataloguePage />} />
        <Route path="books/:id" element={<BookDetailsPage />} />
        <Route path="login" element={<LoginPage />} />
        <Route path="register" element={<RegisterPage />} />

        <Route element={<ProtectedRoute />}>
          <Route path="cart" element={<CartPage />} />
          <Route path="addresses" element={<AddressPage />} />
          <Route path="checkout" element={<CheckoutPage />} />
          <Route path="orders" element={<OrderHistoryPage />} />
          <Route path="orders/:orderId" element={<OrderDetailsPage />} />
          <Route path="orders/:orderId/payment" element={<PaymentPage />} />
          <Route path="orders/:orderId/confirmation" element={<OrderConfirmationPage />} />
          <Route path="recommendations" element={<RecommendationsPage />} />
        </Route>

        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
