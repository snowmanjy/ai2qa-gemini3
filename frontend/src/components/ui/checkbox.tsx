"use client"

import * as React from "react"
import { cn } from "@/lib/utils"
import { Check } from "lucide-react"

export interface CheckboxProps
  extends React.InputHTMLAttributes<HTMLInputElement> {}

const Checkbox = React.forwardRef<HTMLInputElement, CheckboxProps>(
  ({ className, ...props }, ref) => {
    return (
      <div className="relative">
        <input
          type="checkbox"
          className={cn(
            "peer h-4 w-4 shrink-0 rounded-sm border border-primary ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50",
            "appearance-none bg-transparent cursor-pointer",
            "checked:bg-primary checked:border-primary",
            className
          )}
          ref={ref}
          {...props}
        />
        <Check className="absolute left-0.5 top-0.5 h-3 w-3 text-primary-foreground hidden peer-checked:block pointer-events-none" />
      </div>
    )
  }
)
Checkbox.displayName = "Checkbox"

export { Checkbox }
