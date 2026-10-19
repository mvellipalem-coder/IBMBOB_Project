import { screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { book, emptyCart, page } from '../test/fixtures.js';
import { mockBackend } from '../test/mockBackend.js';
import { renderApp, signIn } from '../test/renderApp.jsx';

const categories = [{ id: 4, name: 'Programming', description: null }];

describe('Journey: landing page', () => {
  it('shows category navigation and the Bestsellers and New Launches sections to visitors', async () => {
    const backend = mockBackend({
      'GET /api/categories': { body: categories },
      'GET /api/books': { body: page([book]) },
    });
    renderApp('/');

    const bestsellers = await screen.findByRole('region', { name: 'Bestsellers this Month' });
    const launches = screen.getByRole('region', { name: 'New Launches' });
    expect(await within(bestsellers).findByRole('link', { name: 'Clean Code' })).toBeInTheDocument();
    expect(await within(launches).findByRole('link', { name: 'Clean Code' })).toBeInTheDocument();
    expect(await screen.findByRole('navigation', { name: 'Categories' })).toBeInTheDocument();

    const sorts = backend.callsTo('GET /api/books').map((call) => call.query.sort);
    expect(sorts).toEqual(expect.arrayContaining(['rating_desc', 'newest']));
  });

  it('asks a signed-out visitor to log in for recommendations without calling the backend', async () => {
    const backend = mockBackend({
      'GET /api/categories': { body: categories },
      'GET /api/books': { body: page([book]) },
    });
    renderApp('/');
    const section = await screen.findByRole('region', { name: 'Recommended for You' });
    expect(within(section).getByRole('link', { name: 'Log in' })).toHaveAttribute('href', '/login');
    expect(backend.callsTo('GET /api/recommendations')).toHaveLength(0);
  });

  it('shows personalised recommendations to a signed-in customer', async () => {
    signIn();
    mockBackend({
      'GET /api/cart': { body: emptyCart },
      'GET /api/categories': { body: categories },
      'GET /api/books': { body: page([]) },
      'GET /api/recommendations': {
        body: { strategy: 'PURCHASE_HISTORY', books: [{ ...book, id: 11, title: 'Refactoring' }] },
      },
    });
    renderApp('/');
    const section = await screen.findByRole('region', { name: 'Recommended for You' });
    expect(await within(section).findByRole('link', { name: 'Refactoring' })).toBeInTheDocument();
    expect(within(section).getByText(/books you have bought before/)).toBeInTheDocument();
  });
});
