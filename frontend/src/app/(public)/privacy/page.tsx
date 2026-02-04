import Link from "next/link";
import { ArrowLeft } from "lucide-react";

export default function PrivacyPolicyPage() {
    return (
        <div className="min-h-screen bg-background text-foreground py-16 px-6">
            <div className="max-w-4xl mx-auto">
                <Link
                    href="/"
                    className="inline-flex items-center text-muted-foreground hover:text-foreground transition mb-8"
                >
                    <ArrowLeft className="h-4 w-4 mr-2" />
                    Back to Home
                </Link>

                <h1 className="text-4xl font-bold mb-8">Privacy Policy</h1>

                <div className="prose prose-invert prose-slate max-w-none">
                    {/* Preamble */}
                    <div className="bg-muted/50 border border-border rounded-xl p-6 mb-8">
                        <p className="text-foreground/80 leading-relaxed">
                            Welcome to Ai2QA. This Privacy Policy describes how <strong>SameThoughts, Inc.</strong>, a Delaware
                            Corporation (&quot;Company&quot;, &quot;we&quot;, or &quot;us&quot;), collects, uses, and protects your personal information
                            when you use the Ai2QA platform and services.
                        </p>
                    </div>

                    <p className="text-muted-foreground text-sm mb-8">
                        <strong>Effective Date:</strong> January 1, 2026 | <strong>Last Updated:</strong> January 15, 2026
                    </p>

                    <h2 className="text-2xl font-semibold mt-8 mb-4">1. Information We Collect</h2>

                    <h3 className="text-xl font-medium mt-6 mb-3 text-foreground/90">Account Information</h3>
                    <p className="text-foreground/80">
                        When you create an account with Ai2QA, we collect:
                    </p>
                    <ul className="list-disc list-inside text-foreground/80 mt-4 space-y-2">
                        <li>Email address</li>
                        <li>Name</li>
                        <li>Organization name (optional)</li>
                        <li>Authentication data via Clerk (our identity provider)</li>
                    </ul>

                    <h3 className="text-xl font-medium mt-6 mb-3 text-foreground/90">Test Run Data</h3>
                    <p className="text-foreground/80">
                        When you use Ai2QA to run tests, we collect:
                    </p>
                    <ul className="list-disc list-inside text-foreground/80 mt-4 space-y-2">
                        <li>Target URLs you submit for testing</li>
                        <li>Test goals and configurations</li>
                        <li>Screenshots and DOM snapshots of tested pages</li>
                        <li>Test results and execution logs</li>
                    </ul>

                    <h3 className="text-xl font-medium mt-6 mb-3 text-foreground/90">Payment Information</h3>
                    <p className="text-foreground/80">
                        Payment processing is handled by Stripe. SameThoughts, Inc. does not store credit card numbers
                        or sensitive payment details directly.
                    </p>

                    <h2 className="text-2xl font-semibold mt-8 mb-4">2. How We Use Your Information</h2>
                    <p className="text-foreground/80">
                        We use the information we collect to:
                    </p>
                    <ul className="list-disc list-inside text-foreground/80 mt-4 space-y-2">
                        <li>Provide and improve the Ai2QA service</li>
                        <li>Process credit purchases and payments</li>
                        <li>Send test completion notifications and reports</li>
                        <li>Provide customer support</li>
                        <li>Detect and prevent fraud or abuse</li>
                    </ul>

                    <h2 className="text-2xl font-semibold mt-8 mb-4">3. Cookies and Tracking Technologies</h2>
                    <p className="text-foreground/80">
                        We use cookies and similar tracking technologies to operate and improve our Service. The types of cookies we use include:
                    </p>
                    <ul className="list-disc list-inside text-foreground/80 mt-4 space-y-2">
                        <li><strong>Essential Cookies:</strong> Required for authentication, security, and core functionality. These cannot be disabled.</li>
                        <li><strong>Analytics Cookies:</strong> Help us understand how visitors interact with our Service (via PostHog). You may opt out of these.</li>
                        <li><strong>Preference Cookies:</strong> Remember your settings and preferences for a better experience.</li>
                    </ul>
                    <p className="text-foreground/80 mt-4">
                        Most web browsers allow you to control cookies through their settings. However, disabling certain cookies may limit your ability to use some features of our Service.
                    </p>

                    <h2 className="text-2xl font-semibold mt-8 mb-4">4. Artificial Intelligence and Machine Learning</h2>
                    <p className="text-foreground/80">
                        Ai2QA uses third-party AI services (such as Google Gemini) to power our autonomous testing capabilities. Regarding AI and your data:
                    </p>
                    <ul className="list-disc list-inside text-foreground/80 mt-4 space-y-2">
                        <li><strong>We do not use your data to train AI models.</strong> Your test plans, screenshots, and results are not used to train, improve, or fine-tune any machine learning models.</li>
                        <li>AI processing is performed in real-time solely to execute your test runs.</li>
                        <li>Test plan content is discarded immediately after analysis and is not stored for AI training purposes.</li>
                        <li>We use commercially available AI APIs under their enterprise terms, which prohibit using customer data for model training.</li>
                    </ul>

                    <h2 className="text-2xl font-semibold mt-8 mb-4">5. Data Retention</h2>
                    <p className="text-foreground/80">
                        Test artifacts (screenshots, reports, DOM snapshots) are automatically deleted after
                        <strong> 90 days</strong> by default. Account information is retained for the duration of your account. You may request earlier deletion of your data by
                        contacting us at{" "}
                        <a href="mailto:legal@ai2qa.com" className="text-cyan-400 hover:text-cyan-300">
                            legal@ai2qa.com
                        </a>.
                    </p>

                    <h2 className="text-2xl font-semibold mt-8 mb-4">6. Data Security</h2>
                    <p className="text-foreground/80">
                        We implement industry-standard security measures to protect your data:
                    </p>
                    <ul className="list-disc list-inside text-foreground/80 mt-4 space-y-2">
                        <li>All data is encrypted in transit (TLS 1.3) and at rest (AES-256)</li>
                        <li>Multi-tenant isolation prevents cross-tenant data access</li>
                        <li>Regular security audits and penetration testing</li>
                        <li>Infrastructure hosted on Google Cloud Platform with SOC 2 compliance</li>
                    </ul>

                    <h2 className="text-2xl font-semibold mt-8 mb-4">7. Third-Party Services and Subprocessors</h2>
                    <p className="text-foreground/80">
                        Ai2QA uses the following third-party services (&quot;Subprocessors&quot;) to provide our Service:
                    </p>
                    <ul className="list-disc list-inside text-foreground/80 mt-4 space-y-2">
                        <li><strong>Clerk</strong> (USA) - Authentication and identity management</li>
                        <li><strong>Stripe</strong> (USA) - Payment processing</li>
                        <li><strong>Google Cloud Platform</strong> (USA) - Infrastructure, storage, and AI services</li>
                        <li><strong>PostHog</strong> (USA/EU) - Product analytics and usage tracking</li>
                    </ul>
                    <p className="text-foreground/80 mt-4">
                        Each Subprocessor is contractually obligated to protect your data in accordance with applicable data protection laws. We maintain an up-to-date list of Subprocessors and will notify customers of any changes.
                    </p>

                    <h2 className="text-2xl font-semibold mt-8 mb-4">8. Data Processing Addendum (DPA)</h2>
                    <p className="text-foreground/80">
                        For enterprise customers who require a Data Processing Addendum to comply with GDPR or other data protection regulations, please contact us at{" "}
                        <a href="mailto:legal@ai2qa.com" className="text-cyan-400 hover:text-cyan-300">
                            legal@ai2qa.com
                        </a>. Our DPA includes Standard Contractual Clauses (SCCs) approved by the European Commission for international data transfers.
                    </p>

                    <h2 className="text-2xl font-semibold mt-8 mb-4">9. Your Rights</h2>
                    <p className="text-foreground/80">
                        Depending on your jurisdiction, you may have the right to:
                    </p>
                    <ul className="list-disc list-inside text-foreground/80 mt-4 space-y-2">
                        <li>Access the personal data we hold about you</li>
                        <li>Request correction or deletion of your data</li>
                        <li>Export your data in a portable format</li>
                        <li>Restrict or object to certain processing activities</li>
                        <li>Withdraw consent where processing is based on consent</li>
                        <li>Lodge a complaint with a supervisory authority</li>
                        <li>Opt out of marketing communications</li>
                    </ul>
                    <p className="text-foreground/80 mt-4">
                        To exercise any of these rights, contact us at{" "}
                        <a href="mailto:legal@ai2qa.com" className="text-cyan-400 hover:text-cyan-300">
                            legal@ai2qa.com
                        </a>. We will respond to your request within 30 days.
                    </p>

                    <h2 className="text-2xl font-semibold mt-8 mb-4">10. California Privacy Rights (CCPA/CPRA)</h2>
                    <p className="text-foreground/80">
                        California residents have additional rights under the California Consumer Privacy Act (CCPA) and the California Privacy Rights Act (CPRA), including:
                    </p>
                    <ul className="list-disc list-inside text-foreground/80 mt-4 space-y-2">
                        <li>The right to know what personal information is collected and how it is used</li>
                        <li>The right to request deletion of personal information</li>
                        <li>The right to opt out of the sale or sharing of personal information</li>
                        <li>The right to non-discrimination for exercising your privacy rights</li>
                    </ul>
                    <p className="text-foreground/80 mt-4">
                        <strong>We do not sell your personal information.</strong> To exercise your California privacy rights, contact us at{" "}
                        <a href="mailto:legal@ai2qa.com" className="text-cyan-400 hover:text-cyan-300">
                            legal@ai2qa.com
                        </a>.
                    </p>

                    <h2 className="text-2xl font-semibold mt-8 mb-4">11. European Privacy Rights (GDPR)</h2>
                    <p className="text-foreground/80">
                        If you are located in the European Economic Area (EEA), United Kingdom, or Switzerland, you have rights under the General Data Protection Regulation (GDPR), including:
                    </p>
                    <ul className="list-disc list-inside text-foreground/80 mt-4 space-y-2">
                        <li>Right of access to your personal data</li>
                        <li>Right to rectification of inaccurate data</li>
                        <li>Right to erasure (&quot;right to be forgotten&quot;)</li>
                        <li>Right to restrict processing</li>
                        <li>Right to data portability</li>
                        <li>Right to object to processing</li>
                    </ul>
                    <p className="text-foreground/80 mt-4">
                        Our legal basis for processing your personal data includes: (a) performance of a contract, (b) legitimate interests, (c) compliance with legal obligations, and (d) your consent where applicable.
                    </p>

                    <h2 className="text-2xl font-semibold mt-8 mb-4">12. International Data Transfers</h2>
                    <p className="text-foreground/80">
                        Your data may be processed in the United States, where our servers and Subprocessors are located. For transfers of personal data from the EEA, UK, or Switzerland to the United States, we rely on:
                    </p>
                    <ul className="list-disc list-inside text-foreground/80 mt-4 space-y-2">
                        <li>Standard Contractual Clauses (SCCs) approved by the European Commission</li>
                        <li>The EU-U.S. Data Privacy Framework, where applicable</li>
                        <li>Other lawful transfer mechanisms as required</li>
                    </ul>
                    <p className="text-foreground/80 mt-4">
                        By using Ai2QA, you acknowledge and consent to the transfer of your data to the United States in accordance with these safeguards.
                    </p>

                    <h2 className="text-2xl font-semibold mt-8 mb-4">13. Children&apos;s Privacy</h2>
                    <p className="text-foreground/80">
                        Ai2QA is not intended for use by children under the age of 16. We do not knowingly collect personal information from children under 16. If we become aware that we have collected personal information from a child under 16, we will take steps to delete such information promptly. If you believe we have collected information from a child under 16, please contact us at{" "}
                        <a href="mailto:legal@ai2qa.com" className="text-cyan-400 hover:text-cyan-300">
                            legal@ai2qa.com
                        </a>.
                    </p>

                    <h2 className="text-2xl font-semibold mt-8 mb-4">14. Changes to This Policy</h2>
                    <p className="text-foreground/80">
                        We may update this Privacy Policy from time to time. We will notify you of any material
                        changes by posting the new policy on this page, updating the &quot;Last Updated&quot; date, and, where required by law, sending you an email notification. Your continued use of the Service after such changes constitutes your acceptance of the updated policy.
                    </p>

                    <h2 className="text-2xl font-semibold mt-8 mb-4">15. Contact Information</h2>
                    <p className="text-foreground/80">
                        For any questions regarding this Privacy Policy, or to exercise your privacy rights, please contact us at:
                    </p>
                    <div className="bg-muted/50 border border-border rounded-lg p-4 mt-4">
                        <p className="text-foreground/80">
                            <strong>SameThoughts, Inc.</strong><br />
                            Email: <a href="mailto:legal@ai2qa.com" className="text-cyan-400 hover:text-cyan-300">legal@ai2qa.com</a><br />
                            Privacy Inquiries: <a href="mailto:privacy@ai2qa.com" className="text-cyan-400 hover:text-cyan-300">privacy@ai2qa.com</a>
                        </p>
                    </div>
                </div>
            </div>
        </div>
    );
}
