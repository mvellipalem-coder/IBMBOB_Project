import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import BookCard from './BookCard.jsx';
import { book, outOfStockBook } from '../../test/fixtures.js';

const renderCard = (props) =>
  render(
    <MemoryRouter>
      <BookCard {...props} />
    </MemoryRouter>,
  );

describe('BookCard', () => {
  it('shows title, author, price and availability, and links to the details page', () => {
    renderCard({ book, onAddToCart: vi.fn() });
    expect(screen.getByRole('link', { name: 'Clean Code' })).toHaveAttribute('href', '/books/9');
    expect(screen.getByText(/Robert C. Martin/)).toBeInTheDocument();
    expect(screen.getByText(/899\.00/)).toBeInTheDocument();
    expect(screen.getByText('In stock')).toBeInTheDocument();
  });

  it('shows a placeholder cover when the book has no image', () => {
    const { container } = renderCard({ book, onAddToCart: vi.fn() });
    expect(container.querySelector('img')).toBeNull();
    expect(container.querySelector('.book-cover--placeholder')).not.toBeNull();
  });

  it('shows the cover image with alt text when the book has one', () => {
    renderCard({ book: { ...book, imageUrl: 'https://img.example.com/cc.jpg' }, onAddToCart: vi.fn() });
    expect(screen.getByAltText('Cover of Clean Code')).toHaveAttribute('src', 'https://img.example.com/cc.jpg');
  });

  it('refuses to render a non-http image URL', () => {
    const { container } = renderCard({ book: { ...book, imageUrl: 'javascript:alert(1)' }, onAddToCart: vi.fn() });
    expect(container.querySelector('img')).toBeNull();
  });

  it('calls onAddToCart with the book when the button is used', async () => {
    const onAddToCart = vi.fn();
    renderCard({ book, onAddToCart });
    await userEvent.click(screen.getByRole('button', { name: 'Add Clean Code to cart' }));
    expect(onAddToCart).toHaveBeenCalledWith(book);
  });

  it('disables the button for an out-of-stock book', () => {
    renderCard({ book: outOfStockBook, onAddToCart: vi.fn() });
    expect(screen.getByText('Out of stock')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Add Effective Java to cart/ })).toBeDisabled();
  });
});
