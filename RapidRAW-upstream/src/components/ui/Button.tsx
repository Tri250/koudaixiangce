import clsx from 'clsx';

interface ButtonProps {
  autoFocus?: boolean;
  children: any;
  className?: string;
  disabled?: boolean;
  onClick: any;
  size?: string;
  title?: string;
  variant?: string;
}

const Button = ({ children, onClick, disabled, className = '', ...props }: ButtonProps) => {
  const baseClasses = `
    flex items-center justify-center gap-2 
    font-semibold py-2 px-4 rounded-md 
    text-button-text text-md
    transition-all duration-100 ease-out
    hover:scale-[1.03] active:scale-[.98] hover:shadow-[0_0_12px_rgba(91,155,213,0.2)]
    disabled:opacity-50 disabled:cursor-not-allowed disabled:shadow-none disabled:hover:scale-100
  `;

  const hasSurfaceBg = className.includes('bg-surface');

  const combinedClasses = clsx(
    baseClasses,
    {
      'bg-accent shadow-shiny': !hasSurfaceBg,
      'bg-surface': hasSurfaceBg,
    },
    className,
  );

  return (
    <button onClick={onClick} disabled={disabled} className={combinedClasses} {...props}>
      {children}
    </button>
  );
};

export default Button;
