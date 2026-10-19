import { useRef, useState } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import ErrorMessage from '../components/common/ErrorMessage.jsx';
import FormField from '../components/common/FormField.jsx';
import PageContainer from '../components/layout/PageContainer.jsx';
import { useAuth } from '../context/AuthContext.jsx';
import { useDocumentTitle } from '../hooks/useDocumentTitle.js';
import { focusFirstInvalid } from '../utils/dom.js';
import { fieldErrorsToMap, validateRegistration } from '../utils/validation.js';

export default function RegisterPage() {
  useDocumentTitle('Create account');
  const { register, isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [values, setValues] = useState({ fullName: '', email: '', phone: '', password: '', confirmPassword: '' });
  const [errors, setErrors] = useState({});
  const [submitError, setSubmitError] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const formRef = useRef(null);

  if (isAuthenticated) {
    return <Navigate to="/" replace />;
  }

  const setField = (name) => (event) => setValues((previous) => ({ ...previous, [name]: event.target.value }));

  const handleSubmit = async (event) => {
    event.preventDefault();
    const found = validateRegistration(values);
    setErrors(found);
    setSubmitError(null);
    if (Object.keys(found).length > 0) {
      focusFirstInvalid(formRef.current);
      return;
    }
    setSubmitting(true);
    try {
      await register({
        fullName: values.fullName.trim(),
        email: values.email.trim(),
        password: values.password,
        phone: values.phone.trim(),
      });
      // Registration does not log the user in. Send them to the login page.
      navigate('/login', {
        replace: true,
        state: { registered: true, email: values.email.trim(), from: location.state?.from },
      });
    } catch (error) {
      const serverErrors = fieldErrorsToMap(error.fieldErrors);
      if (Object.keys(serverErrors).length > 0) {
        setErrors(serverErrors);
        focusFirstInvalid(formRef.current);
      } else if (error.code === 'EMAIL_ALREADY_REGISTERED') {
        setErrors({ email: error.message });
        focusFirstInvalid(formRef.current);
      } else {
        setSubmitError(error);
      }
      setSubmitting(false);
    }
  };

  return (
    <PageContainer title="Create your account" narrow>
      <form ref={formRef} className="form card" onSubmit={handleSubmit} noValidate>
        {submitError && <ErrorMessage error={submitError} title="Could not create your account" />}
        <p className="form__note">Fields marked * are required.</p>
        <FormField
          id="register-name"
          label="Full name"
          value={values.fullName}
          onChange={setField('fullName')}
          error={errors.fullName}
          autoComplete="name"
          maxLength={100}
          required
        />
        <FormField
          id="register-email"
          label="Email"
          type="email"
          value={values.email}
          onChange={setField('email')}
          error={errors.email}
          autoComplete="email"
          maxLength={254}
          required
        />
        <FormField
          id="register-phone"
          label="Phone (optional)"
          type="tel"
          value={values.phone}
          onChange={setField('phone')}
          error={errors.phone}
          autoComplete="tel"
          maxLength={20}
        />
        <FormField
          id="register-password"
          label="Password"
          type="password"
          value={values.password}
          onChange={setField('password')}
          error={errors.password}
          hint="8 to 72 characters with a lower-case letter, an upper-case letter and a digit."
          autoComplete="new-password"
          maxLength={72}
          required
        />
        <FormField
          id="register-confirm"
          label="Confirm password"
          type="password"
          value={values.confirmPassword}
          onChange={setField('confirmPassword')}
          error={errors.confirmPassword}
          autoComplete="new-password"
          maxLength={72}
          required
        />
        <button type="submit" className="btn btn--primary btn--block" disabled={submitting}>
          {submitting ? 'Creating account...' : 'Create account'}
        </button>
        <p className="form__footer">
          Already have an account? <Link to="/login">Log in</Link>
        </p>
      </form>
    </PageContainer>
  );
}
