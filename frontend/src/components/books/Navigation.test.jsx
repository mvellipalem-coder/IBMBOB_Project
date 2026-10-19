import { act, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import Breadcrumbs from '../layout/Breadcrumbs.jsx';
import AddToCartFeedback from './AddToCartFeedback.jsx';
import CategoryNav from './CategoryNav.jsx';

const categories = [
  { id: 4, name: 'Programming' },
  { id: 5, name: 'Fiction' },
];

describe('CategoryNav', () => {
  it('lists All plus every category and marks the selected one', () => {
    render(<CategoryNav categories={categories} value="5" onSelect={vi.fn()} />);
    const nav = screen.getByRole('navigation', { name: 'Categories' });
    expect(within(nav).getAllByRole('button').map((b) => b.textContent)).toEqual(['All', 'Programming', 'Fiction']);
    expect(within(nav).getByRole('button', { name: 'Fiction' })).toHaveAttribute('aria-current', 'true');
    expect(within(nav).getByRole('button', { name: 'All' })).not.toHaveAttribute('aria-current');
  });

  it('reports the chosen id, and an empty id for All', async () => {
    const onSelect = vi.fn();
    render(<CategoryNav categories={categories} value="5" onSelect={onSelect} />);
    await userEvent.click(screen.getByRole('button', { name: 'Programming' }));
    expect(onSelect).toHaveBeenLastCalledWith('4');
    await userEvent.click(screen.getByRole('button', { name: 'All' }));
    expect(onSelect).toHaveBeenLastCalledWith('');
  });
});

describe('Breadcrumbs', () => {
  it('links earlier steps and marks the last one as the current page', () => {
    render(
      <MemoryRouter>
        <Breadcrumbs items={[{ label: 'Home', to: '/' }, { label: 'Catalogue', to: '/catalogue' }, { label: 'Clean Code' }]} />
      </MemoryRouter>,
    );
    expect(screen.getByRole('link', { name: 'Home' })).toHaveAttribute('href', '/');
    expect(screen.getByRole('link', { name: 'Catalogue' })).toHaveAttribute('href', '/catalogue');
    expect(screen.queryByRole('link', { name: 'Clean Code' })).not.toBeInTheDocument();
    expect(screen.getByText('Clean Code')).toHaveAttribute('aria-current', 'page');
  });
});

describe('AddToCartFeedback', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  const renderFeedback = (feedback, onDismiss) =>
    render(
      <MemoryRouter>
        <AddToCartFeedback feedback={feedback} onDismiss={onDismiss} />
      </MemoryRouter>,
    );

  it('dismisses a success message by itself after a few seconds', () => {
    const onDismiss = vi.fn();
    renderFeedback({ type: 'success', message: 'Added.' }, onDismiss);
    expect(screen.getByRole('link', { name: 'View cart' })).toBeInTheDocument();
    act(() => vi.advanceTimersByTime(5000));
    expect(onDismiss).not.toHaveBeenCalled();
    act(() => vi.advanceTimersByTime(1500));
    expect(onDismiss).toHaveBeenCalledTimes(1);
  });

  it('keeps an error message until the customer dismisses it', () => {
    const onDismiss = vi.fn();
    renderFeedback({ type: 'error', message: 'Only 1 copy is available.' }, onDismiss);
    act(() => vi.advanceTimersByTime(60000));
    expect(onDismiss).not.toHaveBeenCalled();
    expect(screen.getByText(/Only 1 copy is available/)).toBeInTheDocument();
  });

  it('renders nothing without feedback', () => {
    const { container } = renderFeedback(null, vi.fn());
    expect(container).toBeEmptyDOMElement();
  });
});
