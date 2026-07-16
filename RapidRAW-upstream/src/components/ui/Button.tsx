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
  glass?: boolean;
}

const Button = ({ children, onClick, disabled, className = '', glass = false, ...props }: ButtonProps) => {
  const baseClasses = `
    flex items-center justify-center gap-2
    font-semibold py-2 px-4 rounded-md
    text-button-text text-md
    transition-all duration-150 ease-[cubic-bezier(0.22,1,0.36,1)]
    hover:scale-[1.03] active:scale-[.97]
    hover:shadow-[0_0_16px_rgba(91,155,213,0.25)]
    active:shadow-[0_0_4px_rgba(91,155,213,0.15)]
    disabled:opacity-50 disabled:cursor-not-allowed disabled:shadow-none disabled:hover:scale-100
  `;

  const hasSurfaceBg = className.includes('bg-surface');

  const combinedClasses = clsx(
    baseClasses,
    {
      'bg-accent shadow-shiny': !hasSurfaceBg && !glass,
      'bg-surface': hasSurfaceBg && !glass,
      'liquid-glass hover:liquid-glass-hover active:liquid-glass-subtle': glass,
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
