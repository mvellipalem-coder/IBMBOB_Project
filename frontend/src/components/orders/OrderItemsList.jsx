import { Link } from 'react-router-dom';
import { formatMoney } from '../../utils/format.js';

/** Table of the items in an order (a snapshot taken at checkout). */
export default function OrderItemsList({ items }) {
  return (
    <div className="table-wrap">
      <table className="table">
        <caption className="visually-hidden">Items in this order</caption>
        <thead>
          <tr>
            <th scope="col">Book</th>
            <th scope="col" className="table__number">Price</th>
            <th scope="col" className="table__number">Qty</th>
            <th scope="col" className="table__number">Total</th>
          </tr>
        </thead>
        <tbody>
          {items.map((item) => (
            <tr key={item.bookId}>
              <th scope="row">
                <Link to={`/books/${item.bookId}`}>{item.title}</Link>
                <span className="table__sub">ISBN {item.isbn}</span>
              </th>
              <td className="table__number">{formatMoney(item.unitPrice)}</td>
              <td className="table__number">{item.quantity}</td>
              <td className="table__number">{formatMoney(item.lineTotal)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
