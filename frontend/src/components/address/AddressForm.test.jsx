import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import AddressForm from './AddressForm.jsx';
import { ApiError } from '../../services/httpClient.js';

async function fillValidForm() {
  await userEvent.type(screen.getByLabelText(/Recipient name/), 'Asha Rao');
  await userEvent.type(screen.getByLabelText(/^Phone/), '+91 98765 43210');
  await userEvent.type(screen.getByLabelText(/Address line 1/), '12 MG Road');
  await userEvent.type(screen.getByLabelText(/^City/), 'Pune');
  await userEvent.type(screen.getByLabelText(/^State/), 'Maharashtra');
  await userEvent.type(screen.getByLabelText(/Postal code/), '411001');
}

describe('AddressForm', () => {
  it('shows validation messages and does not submit an empty form', async () => {
    const onSubmit = vi.fn();
    render(<AddressForm onSubmit={onSubmit} />);
    await userEvent.click(screen.getByRole('button', { name: 'Save address' }));
    expect(screen.getByText('Enter the recipient name.')).toBeInTheDocument();
    expect(screen.getByText('Enter the street address.')).toBeInTheDocument();
    expect(screen.getByLabelText(/Recipient name/)).toHaveAttribute('aria-invalid', 'true');
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('submits trimmed values using the backend field names', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(<AddressForm onSubmit={onSubmit} />);
    await fillValidForm();
    await userEvent.click(screen.getByLabelText('Use as my default address'));
    await userEvent.click(screen.getByRole('button', { name: 'Save address' }));
    expect(onSubmit).toHaveBeenCalledWith({
      recipientName: 'Asha Rao',
      phone: '+91 98765 43210',
      line1: '12 MG Road',
      line2: null,
      city: 'Pune',
      state: 'Maharashtra',
      postalCode: '411001',
      countryCode: 'IN',
      isDefault: true,
    });
  });

  it('shows server field errors next to the matching input', async () => {
    const onSubmit = vi.fn().mockRejectedValue(
      new ApiError({ status: 400, code: 'VALIDATION_FAILED', message: 'Validation failed', fieldErrors: [{ field: 'postalCode', message: 'is not valid for the country' }] }),
    );
    render(<AddressForm onSubmit={onSubmit} />);
    await fillValidForm();
    await userEvent.click(screen.getByRole('button', { name: 'Save address' }));
    expect(await screen.findByText('is not valid for the country')).toBeInTheDocument();
    expect(screen.getByLabelText(/Postal code/)).toHaveAttribute('aria-invalid', 'true');
  });

  it('shows a general error when the server rejects the request without field details', async () => {
    const onSubmit = vi.fn().mockRejectedValue(
      new ApiError({ status: 409, code: 'ADDRESS_LIMIT_REACHED', message: 'You can save at most 10 addresses.' }),
    );
    render(<AddressForm onSubmit={onSubmit} />);
    await fillValidForm();
    await userEvent.click(screen.getByRole('button', { name: 'Save address' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('You can save at most 10 addresses.');
  });
});
