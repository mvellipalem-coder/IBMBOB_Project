import { useEffect } from 'react';

/** Sets the browser tab title for the current page. */
export function useDocumentTitle(title) {
  useEffect(() => {
    document.title = title ? `${title} | Book Worm` : 'Book Worm';
  }, [title]);
}
