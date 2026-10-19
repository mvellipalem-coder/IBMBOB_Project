import Breadcrumbs from './Breadcrumbs.jsx';

/** Standard page wrapper: breadcrumbs, heading, optional intro and content inside the centred container. */
export default function PageContainer({ title, subtitle, actions, breadcrumbs, children, narrow = false, medium = false }) {
  const width = narrow ? ' container--narrow' : medium ? ' container--medium' : '';
  return (
    <div className={`container${width}`}>
      {breadcrumbs && <Breadcrumbs items={breadcrumbs} />}
      {(title || actions) && (
        <div className="page-header">
          <div>
            {title && <h1 className="page-header__title">{title}</h1>}
            {subtitle && <p className="page-header__subtitle">{subtitle}</p>}
          </div>
          {actions && <div className="page-header__actions">{actions}</div>}
        </div>
      )}
      {children}
    </div>
  );
}
