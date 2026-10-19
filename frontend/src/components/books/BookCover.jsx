import { useState } from 'react';

/** Only http(s) image URLs are rendered. This blocks javascript: and other unexpected schemes. */
function safeImageUrl(url) {
  if (typeof url !== 'string') return null;
  try {
    const parsed = new URL(url);
    return parsed.protocol === 'https:' || parsed.protocol === 'http:' ? parsed.href : null;
  } catch {
    return null;
  }
}

/** Stable colour for a title, so each book without a cover keeps its own look between visits. */
function hueFor(text = '') {
  let hue = 0;
  for (const character of text) hue = (hue * 31 + character.codePointAt(0)) % 360;
  return hue;
}

/**
 * Book cover image. When the book has no cover (or it fails to load) a styled placeholder with the
 * title is shown instead, so layouts never break. The placeholder is decorative: the title is
 * always available as text next to it.
 */
export default function BookCover({ book, size = 'medium' }) {
  const [failed, setFailed] = useState(false);
  const src = safeImageUrl(book.imageUrl);

  if (!src || failed) {
    return (
      <div
        className={`book-cover book-cover--placeholder book-cover--${size}`}
        style={{ '--cover-hue': hueFor(book.title) }}
        aria-hidden="true"
      >
        <span className="book-cover__title">{book.title}</span>
      </div>
    );
  }
  return (
    <img
      className={`book-cover book-cover--${size}`}
      src={src}
      alt={`Cover of ${book.title}`}
      loading="lazy"
      referrerPolicy="no-referrer"
      onError={() => setFailed(true)}
    />
  );
}
