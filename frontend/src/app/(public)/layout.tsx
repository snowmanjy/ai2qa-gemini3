import { Navbar } from "@/components/layout/navbar";

/**
 * Layout for public pages (homepage, blog, pricing, etc.)
 * Includes the standard Navbar.
 */
export default function PublicLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <>
      <Navbar />
      {children}
    </>
  );
}
