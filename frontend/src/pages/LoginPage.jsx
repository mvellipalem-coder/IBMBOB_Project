import { useRef, useState } from 'react';
import { Link, Navigate, useLocation } from 'react-router-dom';
import ErrorMessage from '../components/common/ErrorMessage.jsx';
import FormField from '../components/common/FormField.jsx';
import Notice from '../components/common/Notice.jsx';
import PageContainer from '../components/layout/PageContainer.jsx';
import { useAuth } from '../context/AuthContext.jsx';
import { useDocumentTitle } from '../hooks/useDocumentTitle.js';
import { focusFirstInvalid } from '../utils/dom.js';
import { fieldErrorsToMap, validateLogin } from '../utils/validation.js';

/** Only allow in-app paths as a post-login destination (prevents open redirects). */
function safeDestination(value) {
  return typeof value === 'string' && value.startsWith('/') && !value.startsWith('//') ? value : '/';
}

export default function LoginPage() {
  useDocumentTitle('Log in');
  const { login, isAuthenticated } = useAuth();
  const location = useLocation();
  const destination = safeDestination(location.state?.from);
  const [values, setValues] = useState({ email: location.state?.email ?? '', password: '' });
  const [errors, setErrors] = useState({});
  const [submitError, setSubmitError] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const formRef = useRef(null);

  if (isAuthenticated) {
    return <Navigate to={destination} replace />;
  }

  const setField = (name) => (event) => setValues((previous) => ({ ...previous, [name]: event.target.value }));

  const handleSubmit = async (event) => {
    event.preventDefault();
    const found = validateLogin(values);
    setErrors(found);
    setSubmitError(null);
    if (Object.keys(found).length > 0) {
      focusFirstInvalid(formRef.current);
      return;
    }
    setSubmitting(true);
    try {
      // On success the session is saved and this page redirects to `destination`.
      await login({ email: values.email.trim(), password: values.password });
    } catch (error) {
      const serverErrors = fieldErrorsToMap(error.fieldErrors);
      if (Object.keys(serverErrors).length > 0) setErrors(serverErrors);
      else setSubmitError(error);
      setSubmitting(false);
    }
  };

  return (
    <PageContainer title="Log in" narrow>
      {location.state?.registered && (
        <Notice type="success">Your account was created. Log in to continue.</Notice>
      )}
      {location.state?.from && !location.state?.registered && (
        <Notice type="info">Please log in to continue.</Notice>
      )}
      <form ref={formRef} className="form card" onSubmit={handleSubmit} noValidate>
        {submitError && <ErrorMessage error={submitError} title="Could not log you in" />}
        <FormField
          id="login-email"
          label="Email"
          type="email"
          value={values.email}
          onChange={setField('email')}
          error={errors.email}
          autoComplete="email"
          required
        />
        <FormField
          id="login-password"
          label="Password"
          type="password"
          value={values.password}
          onChange={setField('password')}
          error={errors.password}
          autoComplete="current-password"
          required
        />
        <button type="submit" className="btn btn--primary btn--block" disabled={submitting}>
          {submitting ? 'Logging in...' : 'Log in'}
        </button>
        <p className="form__footer">
          New here? <Link to="/register">Create an account</Link>
        </p>
      </form>
    </PageContainer>
  );
}
