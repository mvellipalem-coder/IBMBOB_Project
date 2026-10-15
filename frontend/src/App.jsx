import { useEffect, useState } from 'react';
import { Route, Routes } from 'react-router-dom';
import { apiGet } from './services/api.js';

function Home() {
  const [status, setStatus] = useState('checking...');

  useEffect(() => {
    apiGet('/health')
      .then((data) => setStatus(data.status))
      .catch(() => setStatus('unreachable'));
  }, []);

  return (
    <main className="container">
      <h1>EBookStore</h1>
      <p>Backend status: <strong>{status}</strong></p>
    </main>
  );
}

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Home />} />
    </Routes>
  );
}
