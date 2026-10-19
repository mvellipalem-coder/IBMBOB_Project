import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import ConfirmationDialog from './ConfirmationDialog.jsx';
import ErrorMessage from './ErrorMessage.jsx';
import FormField from './FormField.jsx';
import LoadingSpinner from './LoadingSpinner.jsx';
import PaymentMethodSelector from '../payment/PaymentMethodSelector.jsx';

describe('ErrorMessage', () => {
  it('is announced as an alert and offers retry', async () => {
    const onRetry = vi.fn();
    render(<ErrorMessage error={new Error('Unable to reach the server.')} onRetry={onRetry} />);
    expect(screen.getByRole('alert')).toHaveTextContent('Unable to reach the server.');
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }));
    expect(onRetry).toHaveBeenCalled();
  });
});

describe('LoadingSpinner', () => {
  it('exposes its label as a status for screen readers', () => {
    render(<LoadingSpinner label="Loading books..." />);
    expect(screen.getByRole('status')).toHaveTextContent('Loading books...');
  });
});

describe('FormField', () => {
  it('links the label, hint and error to the input', () => {
    render(<FormField id="email" label="Email" hint="We never share it." error="Enter a valid email address." required />);
    const input = screen.getByLabelText(/Email/);
    expect(input).toBeRequired();
    expect(input).toHaveAttribute('aria-invalid', 'true');
    expect(input).toHaveAccessibleDescription('We never share it. Enter a valid email address.');
  });
});

describe('ConfirmationDialog', () => {
  it('calls the right handler for each button', async () => {
    const onConfirm = vi.fn();
    const onCancel = vi.fn();
    render(
      <ConfirmationDialog open title="Cancel this order?" message="This cannot be undone." confirmLabel="Yes, cancel" onConfirm={onConfirm} onCancel={onCancel} />,
    );
    expect(screen.getByRole('dialog', { name: 'Cancel this order?' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Yes, cancel' }));
    expect(onConfirm).toHaveBeenCalled();
    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(onCancel).toHaveBeenCalled();
  });

  it('disables both buttons while busy', () => {
    render(<ConfirmationDialog open busy title="t" message="m" onConfirm={vi.fn()} onCancel={vi.fn()} />);
    expect(screen.getByRole('button', { name: 'Cancel' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Please wait...' })).toBeDisabled();
  });
});

describe('PaymentMethodSelector', () => {
  it('offers only the methods the backend supports and reports the choice', async () => {
    const onChange = vi.fn();
    render(<PaymentMethodSelector value="" onChange={onChange} />);
    expect(screen.getByRole('group', { name: 'Payment method' })).toBeInTheDocument();
    expect(screen.getAllByRole('radio')).toHaveLength(3);
    await userEvent.click(screen.getByRole('radio', { name: /UPI/ }));
    expect(onChange).toHaveBeenCalledWith('UPI');
  });

  it('never asks for card numbers, CVV or PIN: it has no text-entry fields at all', () => {
    const { container } = render(<PaymentMethodSelector value="CARD" onChange={vi.fn()} />);
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
    expect(container.querySelectorAll('input:not([type="radio"])')).toHaveLength(0);
  });
});
