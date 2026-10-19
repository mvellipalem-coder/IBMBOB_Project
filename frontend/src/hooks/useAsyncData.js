import { useCallback, useEffect, useRef, useState } from 'react';

/**
 * Loads data with an async function and tracks loading and error state.
 * `loader` receives `{ signal }` and must return a promise. The request is cancelled when `deps` change
 * or the component unmounts. Call `reload()` to fetch again.
 */
export function useAsyncData(loader, deps = [], { enabled = true } = {}) {
  const [state, setState] = useState({ data: null, loading: enabled, error: null });
  const [reloadToken, setReloadToken] = useState(0);
  const loaderRef = useRef(loader);
  loaderRef.current = loader;

  useEffect(() => {
    if (!enabled) {
      setState({ data: null, loading: false, error: null });
      return undefined;
    }
    const controller = new AbortController();
    setState((previous) => ({ ...previous, loading: true, error: null }));
    loaderRef
      .current({ signal: controller.signal })
      .then((data) => {
        if (!controller.signal.aborted) setState({ data, loading: false, error: null });
      })
      .catch((error) => {
        if (controller.signal.aborted || error?.name === 'AbortError') return;
        setState({ data: null, loading: false, error });
      });
    return () => controller.abort();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, reloadToken, enabled]);

  const reload = useCallback(() => setReloadToken((token) => token + 1), []);
  return { ...state, reload };
}
