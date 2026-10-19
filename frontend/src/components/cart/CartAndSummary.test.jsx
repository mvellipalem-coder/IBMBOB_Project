import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import CartItem from './CartItem.jsx';
import OrderSummary from './OrderSummary.jsx';
import { cartItem } from '../../test/fixtures.js';

describe('CartItem', () => {
  const renderItem = (item, handlers = {}) =>
    render(
      <MemoryRouter>
        <ul>
          <CartItem item={item} onQuantityChange={vi.fn()} onRemove={vi.fn()} {...handlers} />
        </ul>
      </MemoryRouter>,
    );

  it('shows the server-provided line total', () => {
    renderItem(cartItem);
    expect(screen.getByText(/1,798\.00/)).toBeInTheDocument();
  });

  it('stops the + button at the server-provided maximum', () => {
    renderItem({ ...cartItem, quantity: 3, maxQuantity: 3 });
    expect(screen.getByRole('button', { name: 'Increase quantity of Clean Code' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Decrease quantity of Clean Code' })).toBeEnabled();
  });

  it('stops the - button at one copy', () => {
    renderItem({ ...cartItem, quantity: 1, maxQuantity: 3 });
    expect(screen.getByRole('button', { name: 'Decrease quantity of Clean Code' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Increase quantity of Clean Code' })).toBeEnabled();
  });

  it('asks to change the quantity and to remove', async () => {
    const onQuantityChange = vi.fn();
    const onRemove = vi.fn();
    renderItem(cartItem, { onQuantityChange, onRemove });
    expect(screen.getByLabelText('Qty')).toHaveTextContent('2');
    await userEvent.click(screen.getByRole('button', { name: 'Increase quantity of Clean Code' }));
    expect(onQuantityChange).toHaveBeenLastCalledWith(cartItem, 3);
    await userEvent.click(screen.getByRole('button', { name: 'Decrease quantity of Clean Code' }));
    expect(onQuantityChange).toHaveBeenLastCalledWith(cartItem, 1);
    await userEvent.click(screen.getByRole('button', { name: 'Remove Clean Code from cart' }));
    expect(onRemove).toHaveBeenCalledWith(cartItem);
  });

  it('warns when stock dropped below the quantity in the cart', () => {
    renderItem({ ...cartItem, quantity: 5, maxQuantity: 2 });
    expect(screen.getByRole('alert')).toHaveTextContent('Only 2 available');
  });
});

describe('OrderSummary', () => {
  it('displays the amounts it is given without recalculating them', () => {
    render(<OrderSummary subtotal={1798} pointsDiscount={100} pointsRedeemed={100} total={1698} />);
    expect(screen.getByText(/1,798\.00/)).toBeInTheDocument();
    expect(screen.getByText(/-.*100\.00/)).toBeInTheDocument();
    expect(screen.getByText(/1,698\.00/)).toBeInTheDocument();
  });

  it('hides the discount row when no points were used', () => {
    render(<OrderSummary subtotal={500} pointsDiscount={0} total={500} />);
    expect(screen.queryByText(/Gift points/)).not.toBeInTheDocument();
  });
});
