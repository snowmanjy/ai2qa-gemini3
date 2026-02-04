import { Metadata } from "next";
import Link from "next/link";

export const metadata: Metadata = {
  title: "Blog | Ai2QA - AI Testing Tools Compared",
  description: "Compare AI-powered QA testing tools. Honest reviews, pricing comparisons, and recommendations for startups and developers.",
};

const articles = [
  {
    slug: "bugs-ai-finds-automated-tests-miss",
    title: "5 Bugs AI Finds That Automated Tests Miss Every Time",
    description: "Your test suite is green. CI/CD passes. Then users find bugs. Discover 5 types of bugs that slip past Selenium, Cypress, and Playwright.",
    date: "January 27, 2026",
    readTime: "10 min read",
    category: "Featured",
    tags: ["AI Testing", "Edge Cases", "Security"],
  },
  {
    slug: "best-ai-qa-testing-tools-2026",
    title: "Best AI QA Testing Tools for Startups (2026)",
    description: "Compare the top 7 AI-powered QA testing tools for startups. Pricing, features, and honest recommendations based on team size and budget.",
    date: "January 28, 2026",
    readTime: "12 min read",
    category: "Pillar Article",
    tags: ["AI Testing", "Comparison", "Startups"],
  },
  {
    slug: "testrigor-alternatives-2026",
    title: "testRigor Alternatives for Small Teams (2026)",
    description: "testRigor's $900+/month pricing is steep for small teams. Compare affordable alternatives including Ai2QA, Katalon, and mabl.",
    date: "January 28, 2026",
    readTime: "8 min read",
    category: "Comparison",
    tags: ["testRigor", "Alternatives"],
  },
  {
    slug: "selenium-alternatives-ai-2026",
    title: "Selenium Alternatives: AI Testing Without Scripts (2026)",
    description: "Tired of maintaining Selenium scripts? Discover AI testing alternatives with zero setup, self-healing tests, and no coding required.",
    date: "January 28, 2026",
    readTime: "8 min read",
    category: "Comparison",
    tags: ["Selenium", "No-Code"],
  },
  {
    slug: "mabl-vs-ai2qa-startups-2026",
    title: "mabl vs Ai2QA: Which is Right for Your Startup?",
    description: "mabl focuses on auto-healing for mid-size teams. Ai2QA offers zero-setup testing for solo devs. Find out which fits your startup.",
    date: "January 28, 2026",
    readTime: "7 min read",
    category: "Comparison",
    tags: ["mabl", "vs"],
  },
  {
    slug: "applitools-alternatives-startups-2026",
    title: "Cheaper Applitools Alternatives for Startups (2026)",
    description: "Applitools is $699-969/month. If visual testing isn't your primary concern, here are more affordable alternatives.",
    date: "January 28, 2026",
    readTime: "7 min read",
    category: "Comparison",
    tags: ["Applitools", "Visual Testing"],
  },
  {
    slug: "rainforest-qa-alternatives-2026",
    title: "Rainforest QA Alternatives with Pay-Per-Run Pricing",
    description: "Rainforest QA's subscription can scale to $94K/year. Compare pay-per-run alternatives for startups with variable testing needs.",
    date: "January 28, 2026",
    readTime: "7 min read",
    category: "Comparison",
    tags: ["Rainforest QA", "Pay-Per-Run"],
  },
];

export default function BlogPage() {
  return (
    <main className="min-h-screen bg-background pt-40 pb-20">
      <div className="max-w-4xl mx-auto px-6">
        {/* Hero */}
        <div className="text-center mb-16">
          <h1 className="text-4xl md:text-5xl font-bold mb-4">
            AI Testing Tools Compared
          </h1>
          <p className="text-lg text-muted-foreground max-w-2xl mx-auto">
            Honest comparisons, verified pricing, and clear recommendations for startups and developers.
          </p>
        </div>

        {/* Article Grid */}
        <div className="space-y-6">
          {articles.map((article) => (
            <Link
              key={article.slug}
              href={`/blog/${article.slug}`}
              className="block p-6 rounded-xl border border-border hover:border-primary/50 hover:shadow-lg transition-all bg-card"
            >
              <div className="flex flex-wrap gap-2 mb-3">
                {article.tags.map((tag) => (
                  <span
                    key={tag}
                    className="text-xs px-2 py-1 rounded-full bg-muted text-muted-foreground"
                  >
                    {tag}
                  </span>
                ))}
              </div>
              <h2 className="text-xl font-semibold mb-2 group-hover:text-primary">
                {article.title}
              </h2>
              <p className="text-muted-foreground text-sm mb-3">
                {article.description}
              </p>
              <div className="text-xs text-muted-foreground">
                {article.date} &bull; {article.readTime}
              </div>
            </Link>
          ))}
        </div>

        {/* CTA */}
        <div className="mt-16 text-center p-8 rounded-2xl bg-gradient-to-r from-primary/10 to-primary/5 border border-primary/20">
          <h3 className="text-2xl font-semibold mb-3">
            Ready to try AI-powered testing?
          </h3>
          <p className="text-muted-foreground mb-6">
            Zero setup. Three AI personas. Pay only for what you use.
          </p>
          <Link
            href="/sign-up"
            className="inline-flex items-center justify-center px-6 py-3 rounded-lg bg-primary text-primary-foreground font-medium hover:bg-primary/90 transition"
          >
            Try Ai2QA Free
          </Link>
        </div>
      </div>
    </main>
  );
}
