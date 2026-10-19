/** Read-only address block. Works for saved addresses and for an order's shipping address. */
export default function AddressDisplay({ address }) {
  return (
    <address className="address-display">
      <strong>{address.recipientName}</strong>
      <br />
      {address.line1}
      {address.line2 && (
        <>
          <br />
          {address.line2}
        </>
      )}
      <br />
      {address.city}, {address.state} {address.postalCode}
      <br />
      {address.countryCode}
      <br />
      Phone: {address.phone}
    </address>
  );
}
