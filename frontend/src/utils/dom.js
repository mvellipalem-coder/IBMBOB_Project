/** Moves keyboard focus to the first invalid field of a form so the user (and screen readers) land on the problem. */
export function focusFirstInvalid(formElement) {
  if (!formElement) return;
  window.requestAnimationFrame(() => {
    formElement.querySelector('[aria-invalid="true"]')?.focus();
  });
}
