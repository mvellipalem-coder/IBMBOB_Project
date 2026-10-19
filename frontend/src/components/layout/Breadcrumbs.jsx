import { Link } from 'react-router-dom';

/**
 * Breadcrumb trail such as Home / Non-fiction / Joy of Minimalism.
 * `items` is a list of { label, to }. The last item is the current page and is not a link.
 */
export default function Breadcrumbs({ items }) {
  return (
    <nav className="breadcrumbs" aria-label="Breadcrumb">
      <ol>
        {items.map((item, index) => {
          const last = index === items.length - 1;
          return (
            <li key={`${item.label}-${index}`}>
              {last || !item.to ? <span aria-current={last ? 'page' : undefined}>{item.label}</span> : <Link to={item.to}>{item.label}</Link>}
            </li>
          );
        })}
      </ol>
    </nav>
  );
}
