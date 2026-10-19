/**
 * Category list from the wireframes: a vertical sidebar on wide screens, a scrolling row on phones.
 * `value` is the selected category id ('' = all). `onSelect(id)` receives the id as a string.
 */
export default function CategoryNav({ categories = [], value = '', onSelect }) {
  const items = [{ id: '', name: 'All' }, ...categories];
  return (
    <nav className="category-nav" aria-label="Categories">
      <ul>
        {items.map((category) => {
          const id = String(category.id);
          return (
            <li key={id || 'all'}>
              <button
                type="button"
                className="category-nav__item"
                aria-current={id === String(value) ? 'true' : undefined}
                onClick={() => onSelect(id)}
              >
                {category.name}
              </button>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
