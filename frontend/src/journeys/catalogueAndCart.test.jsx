import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { book, cart, emptyCart, outOfStockBook, page } from '../test/fixtures.js';
import { apiError, mockBackend } from '../test/mockBackend.js';
import { renderApp, signIn } from '../test/renderApp.jsx';

const categories = [
  { id: 4, name: 'Programming', description: null },
  { id: 5, name: 'Fiction', description: null },
];
const publishers = [{ id: 6, name: 'Pearson', description: null }];

describe('Journey: browsing the catalogue', () => {
  it('lists books with filters from the backend and sends the chosen filters as query parameters', async () => {
    const backend = mockBackend({
      'GET /api/categories': { body: categories },
      'GET /api/publishers': { body: publishers },
      'GET /api/books': { body: page([book, outOfStockBook]) },
    });
    renderApp('/catalogue');

    expect(await screen.findByRole('link', { name: 'Clean Code' })).toBeInTheDocument();
    expect(screen.getByText('2 books found')).toBeInTheDocument();
    expect(screen.getAllByText('Out of stock').length).toBeGreaterThan(0);

    const categoryNav = await screen.findByRole('navigation', { name: 'Categories' });
    await userEvent.click(within(categoryNav).getByRole('button', { name: 'Programming' }));
    await userEvent.selectOptions(screen.getByLabelText('Publisher'), '6');
    await userEvent.click(screen.getByLabelText('In stock only'));

    await screen.findByRole('link', { name: 'Clean Code' });
    const last = backend.callsTo('GET /api/books').at(-1);
    expect(last.query).toMatchObject({ categoryId: '4', publisherId: '6', inStock: 'true' });
    expect(within(categoryNav).getByRole('button', { name: 'Programming' })).toHaveAttribute('aria-current', 'true');
  });

  it('searches with the q parameter and the search endpoint', async () => {
    const backend = mockBackend({
      'GET /api/categories': { body: categories },
      'GET /api/publishers': { body: publishers },
      'GET /api/books': { body: page([book]) },
      'GET /api/books/search': { body: page([book]) },
    });
    renderApp('/catalogue');
    await screen.findByRole('link', { name: 'Clean Code' });

    await userEvent.type(screen.getByRole('searchbox', { name: 'Search books' }), 'clean');
    await userEvent.click(screen.getByRole('button', { name: 'Search' }));

    await screen.findByRole('link', { name: 'Clean Code' });
    expect(backend.callsTo('GET /api/books/search').at(-1).query.q).toBe('clean');
  });

  it('rejects one-character searches before calling the backend', async () => {
    const backend = mockBackend({
      'GET /api/categories': { body: categories },
      'GET /api/publishers': { body: publishers },
      'GET /api/books': { body: page([book]) },
    });
    renderApp('/catalogue');
    await screen.findByRole('link', { name: 'Clean Code' });
    await userEvent.type(screen.getByRole('searchbox'), 'c');
    await userEvent.click(screen.getByRole('button', { name: 'Search' }));
    expect(screen.getByText('Type at least 2 characters to search.')).toBeInTheDocument();
    expect(backend.callsTo('GET /api/books/search')).toHaveLength(0);
  });

  it('shows an empty state with a way to clear filters', async () => {
    mockBackend({
      'GET /api/categories': { body: categories },
      'GET /api/publishers': { body: publishers },
      'GET /api/books': { body: page([]) },
    });
    renderApp('/catalogue?categoryId=5');
    expect(await screen.findByText('No books found')).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: 'Clear all filters' }).length).toBeGreaterThan(0);
  });

  it('shows an error with a retry button, and recovers when retried', async () => {
    let fail = true;
    mockBackend({
      'GET /api/categories': { body: categories },
      'GET /api/publishers': { body: publishers },
      'GET /api/books': () => (fail ? apiError(500, 'INTERNAL_ERROR', 'The server ran into a problem.') : { body: page([book]) }),
    });
    renderApp('/catalogue');
    expect(await screen.findByText('Could not load books')).toBeInTheDocument();
    fail = false;
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }));
    expect(await screen.findByRole('link', { name: 'Clean Code' })).toBeInTheDocument();
  });
});

describe('Journey: book details and related books', () => {
  const detail = { ...book, description: 'A handbook of agile craftsmanship.', maxOrderableQuantity: 3 };

  it('shows the book, its availability and related books', async () => {
    mockBackend({
      'GET /api/books/9': { body: detail },
      'GET /api/books/9/related': { body: [{ ...book, id: 11, title: 'The Pragmatic Programmer' }] },
    });
    renderApp('/books/9');
    expect(await screen.findByRole('heading', { name: 'Clean Code' })).toBeInTheDocument();
    expect(screen.getByText('A handbook of agile craftsmanship.')).toBeInTheDocument();
    expect(screen.getByText('9780000000097')).toBeInTheDocument();
    expect(await screen.findByRole('link', { name: 'The Pragmatic Programmer' })).toHaveAttribute('href', '/books/11');
    const options = within(screen.getByLabelText('Quantity')).getAllByRole('option');
    expect(options.map((option) => option.value)).toEqual(['1', '2', '3']);
  });

  it('shows a not-found message for an unknown book', async () => {
    mockBackend({
      'GET /api/books/999': apiError(404, 'BOOK_NOT_FOUND', 'Book not found.'),
      'GET /api/books/999/related': apiError(404, 'BOOK_NOT_FOUND', 'Book not found.'),
    });
    renderApp('/books/999');
    expect(await screen.findByText('Book not found')).toBeInTheDocument();
  });

  it('sends a signed-out visitor to login instead of adding to the cart', async () => {
    const backend = mockBackend({
      'GET /api/books/9': { body: detail },
      'GET /api/books/9/related': { body: [] },
    });
    renderApp('/books/9');
    await userEvent.click(await screen.findByRole('button', { name: 'Add to cart' }));
    expect(await screen.findByRole('heading', { name: 'Log in' })).toBeInTheDocument();
    expect(backend.callsTo('POST /api/cart/items')).toHaveLength(0);
  });

  it('adds the chosen quantity to the cart and confirms only after the server answers', async () => {
    signIn();
    const backend = mockBackend({
      'GET /api/cart': { body: emptyCart },
      'GET /api/books/9': { body: detail },
      'GET /api/books/9/related': { body: [] },
      'POST /api/cart/items': { status: 201, body: cart },
    });
    renderApp('/books/9');
    await userEvent.selectOptions(await screen.findByLabelText('Quantity'), '2');
    await userEvent.click(screen.getByRole('button', { name: 'Add to cart' }));

    expect(await screen.findByText(/was added to your cart/)).toBeInTheDocument();
    expect(backend.callsTo('POST /api/cart/items')[0].body).toEqual({ bookId: 9, quantity: 2 });
    expect(screen.getByLabelText('2 items in cart')).toBeInTheDocument();
  });

  it('shows the server message and no success banner when stock is insufficient', async () => {
    signIn();
    mockBackend({
      'GET /api/cart': { body: emptyCart },
      'GET /api/books/9': { body: detail },
      'GET /api/books/9/related': { body: [] },
      'POST /api/cart/items': apiError(409, 'INSUFFICIENT_STOCK', 'Only 1 copy of "Clean Code" is available.'),
    });
    renderApp('/books/9');
    await userEvent.click(await screen.findByRole('button', { name: 'Add to cart' }));
    expect(await screen.findByText('Only 1 copy of "Clean Code" is available.')).toBeInTheDocument();
    expect(screen.queryByText(/was added to your cart/)).not.toBeInTheDocument();
  });
});

describe('Journey: managing the cart', () => {
  it('shows server totals, updates a quantity and removes a line after confirmation', async () => {
    signIn();
    const updated = { items: [{ ...cart.items[0], quantity: 3, lineTotal: 2697 }], totalQuantity: 3, subtotal: 2697 };
    const backend = mockBackend({
      'GET /api/cart': { body: cart },
      'PUT /api/cart/items/31': { body: updated },
      'DELETE /api/cart/items/31': { body: emptyCart },
    });
    renderApp('/cart');

    expect(await screen.findByRole('link', { name: 'Clean Code' })).toBeInTheDocument();
    expect(screen.getAllByText(/1,798\.00/).length).toBeGreaterThan(0);

    await userEvent.click(screen.getByRole('button', { name: 'Increase quantity of Clean Code' }));
    expect((await screen.findAllByText(/2,697\.00/)).length).toBeGreaterThan(0);
    expect(backend.callsTo('PUT /api/cart/items/31')[0].body).toEqual({ quantity: 3 });

    await userEvent.click(screen.getByRole('button', { name: 'Remove Clean Code from cart' }));
    const dialog = screen.getByRole('dialog', { name: 'Remove this book?' });
    expect(backend.callsTo('DELETE /api/cart/items/31')).toHaveLength(0);
    await userEvent.click(within(dialog).getByRole('button', { name: 'Remove' }));
    expect(await screen.findByText('Your cart is empty')).toBeInTheDocument();
  });

  it('shows a cart error and re-syncs when an update is rejected', async () => {
    signIn();
    mockBackend({
      'GET /api/cart': { body: cart },
      'PUT /api/cart/items/31': apiError(409, 'INSUFFICIENT_STOCK', 'Only 2 copies are available.'),
    });
    renderApp('/cart');
    await userEvent.click(await screen.findByRole('button', { name: 'Increase quantity of Clean Code' }));
    expect(await screen.findByText('Only 2 copies are available.')).toBeInTheDocument();
  });

  it('blocks checkout while a line is above available stock', async () => {
    signIn();
    mockBackend({
      'GET /api/cart': { body: { ...cart, items: [{ ...cart.items[0], quantity: 5, maxQuantity: 2 }] } },
    });
    renderApp('/cart');
    expect(await screen.findByRole('button', { name: 'Proceed to checkout' })).toBeDisabled();
  });
});
