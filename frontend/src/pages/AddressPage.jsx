import { useState } from 'react';
import AddressCard from '../components/address/AddressCard.jsx';
import AddressForm from '../components/address/AddressForm.jsx';
import ConfirmationDialog from '../components/common/ConfirmationDialog.jsx';
import EmptyState from '../components/common/EmptyState.jsx';
import ErrorMessage from '../components/common/ErrorMessage.jsx';
import LoadingSpinner from '../components/common/LoadingSpinner.jsx';
import Notice from '../components/common/Notice.jsx';
import PageContainer from '../components/layout/PageContainer.jsx';
import { useAsyncData } from '../hooks/useAsyncData.js';
import { useDocumentTitle } from '../hooks/useDocumentTitle.js';
import { createAddress, deleteAddress, listAddresses, updateAddress } from '../services/addressService.js';

export default function AddressPage() {
  useDocumentTitle('My addresses');
  const addresses = useAsyncData(({ signal }) => listAddresses({ signal }), []);
  // `mode` is null (list), 'new', or an address being edited.
  const [mode, setMode] = useState(null);
  const [toDelete, setToDelete] = useState(null);
  const [deleting, setDeleting] = useState(false);
  const [message, setMessage] = useState(null);
  const [actionError, setActionError] = useState(null);

  const handleSave = async (values) => {
    // Errors propagate to AddressForm, which shows field and server messages.
    if (mode && mode !== 'new') {
      await updateAddress(mode.id, values);
      setMessage('Address updated.');
    } else {
      await createAddress(values);
      setMessage('Address added.');
    }
    setMode(null);
    addresses.reload();
  };

  const handleDelete = async () => {
    setDeleting(true);
    setActionError(null);
    try {
      await deleteAddress(toDelete.id);
      setMessage('Address deleted.');
      addresses.reload();
    } catch (error) {
      setActionError(error);
    } finally {
      setDeleting(false);
      setToDelete(null);
    }
  };

  return (
    <PageContainer
      title="My addresses"
      actions={
        mode === null && (
          <button type="button" className="btn btn--primary" onClick={() => { setMessage(null); setMode('new'); }}>
            Add new address
          </button>
        )
      }
    >
      {message && <Notice type="success" onDismiss={() => setMessage(null)}>{message}</Notice>}
      {actionError && <ErrorMessage error={actionError} title="Could not delete the address" />}

      {mode !== null && (
        <section className="card" aria-labelledby="address-form-title">
          <h2 id="address-form-title">{mode === 'new' ? 'Add a new address' : 'Edit address'}</h2>
          <AddressForm
            initialValues={mode === 'new' ? undefined : mode}
            onSubmit={handleSave}
            onCancel={() => setMode(null)}
            submitLabel={mode === 'new' ? 'Add address' : 'Save changes'}
          />
        </section>
      )}

      {addresses.loading && <LoadingSpinner label="Loading addresses..." />}
      {addresses.error && (
        <ErrorMessage error={addresses.error} title="Could not load addresses" onRetry={addresses.reload} />
      )}
      {addresses.data && addresses.data.length === 0 && mode === null && (
        <EmptyState title="No saved addresses" message="Add a delivery address to use at checkout." />
      )}
      {addresses.data && addresses.data.length > 0 && (
        <ul className="address-list" aria-label="Saved addresses">
          {addresses.data.map((address) => (
            <li key={address.id}>
              <AddressCard
                address={address}
                onEdit={(selected) => { setMessage(null); setMode(selected); }}
                onDelete={setToDelete}
              />
            </li>
          ))}
        </ul>
      )}

      <ConfirmationDialog
        open={toDelete !== null}
        title="Delete this address?"
        message="Past orders keep their own copy of the delivery address."
        confirmLabel="Delete"
        danger
        busy={deleting}
        onConfirm={handleDelete}
        onCancel={() => setToDelete(null)}
      />
    </PageContainer>
  );
}
