import { useState } from 'react';
import { validateSearchText } from '../../utils/validation.js';

/** Keyword search form. Calls `onSearch(text)` with trimmed text; empty text clears the search. */
export default function SearchBar({ initialValue = '', onSearch, placeholder = 'Search by title, author or ISBN' }) {
  const [text, setText] = useState(initialValue);
  const [error, setError] = useState(null);

  const handleSubmit = (event) => {
    event.preventDefault();
    const problem = validateSearchText(text);
    setError(problem);
    if (!problem) onSearch(text.trim());
  };

  return (
    <form className="search-bar" role="search" onSubmit={handleSubmit} noValidate>
      <label htmlFor="search-input" className="visually-hidden">
        Search books
      </label>
      <input
        id="search-input"
        type="search"
        value={text}
        onChange={(event) => setText(event.target.value)}
        placeholder={placeholder}
        maxLength={100}
        aria-invalid={error ? 'true' : undefined}
        aria-describedby={error ? 'search-error' : undefined}
      />
      <button type="submit" className="btn btn--primary">
        Search
      </button>
      {error && (
        <p id="search-error" className="form-field__error search-bar__error">
          {error}
        </p>
      )}
    </form>
  );
}
