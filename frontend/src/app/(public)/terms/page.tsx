import Link from "next/link";
import { ArrowLeft, Shield, AlertTriangle } from "lucide-react";

export default function TermsOfServicePage() {
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

                <h1 className="text-4xl font-bold mb-8">Terms of Service</h1>

                <div className="prose prose-invert prose-slate max-w-none">
                    {/* Preamble */}
                    <div className="bg-muted/50 border border-border rounded-xl p-6 mb-8">
                        <p className="text-foreground/80 leading-relaxed">
                            Welcome to Ai2QA. These Terms of Service (&quot;Terms&quot;) constitute a binding legal agreement
                            between you and <strong>SameThoughts, Inc.</strong>, a Delaware Corporation (&quot;Company&quot;, &quot;we&quot;, or &quot;us&quot;),
                            regarding your use of the Ai2QA platform and services.
                        </p>
                    </div>

                    <p className="text-muted-foreground text-sm mb-8">
                        <strong>Effective Date:</strong> January 1, 2026 | <strong>Last Updated:</strong> January 27, 2026
                    </p>

                    <h2 className="text-2xl font-semibold mt-8 mb-4">1. Acceptance of Terms</h2>
                    <p className="text-foreground/80">
                        By accessing or using the Ai2QA platform (the &quot;Service&quot;), you agree to be bound by these Terms.
                        If you do not agree to these Terms, you may not access or use the Service.
                    </p>

                    <h2 className="text-2xl font-semibold mt-8 mb-4">2. Description of Service</h2>
                    <p className="text-foreground/80">
                        Ai2QA is an AI-powered autonomous QA testing platform that uses artificial intelligence agents
                        to navigate, test, and validate web applications. The Service includes features such as:
                    </p>
                    <ul className="list-disc list-inside text-foreground/80 mt-4 space-y-2">
                        <li>Autonomous test execution via AI agents</li>
                        <li>Self-healing test capabilities</li>
                        <li>Test recording and reporting</li>
                        <li>Multi-persona testing (The Auditor, The Gremlin, The White Hat)</li>
                    </ul>

                    <h2 className="text-2xl font-semibold mt-8 mb-4">3. User Responsibilities</h2>
                    <p className="text-foreground/80">
                        You are solely responsible for:
                    </p>
                    <ul className="list-disc list-inside text-foreground/80 mt-4 space-y-2">
                        <li>Ensuring you have authorization to test any target URLs submitted to Ai2QA</li>
                        <li>Using the Service only against staging/QA environments, not production systems with live data</li>
                        <li>Complying with all applicable laws and regulations</li>
                        <li>Maintaining the confidentiality of your account credentials</li>
                    </ul>

                    {/* Acceptable Use Policy Section */}
                    <div className="mt-12 mb-8 p-6 bg-muted/70 border border-amber-500/30 rounded-xl">
                        <div className="flex items-center gap-3 mb-4">
                            <Shield className="h-6 w-6 text-amber-400" />
                            <h2 className="text-2xl font-semibold text-amber-400 m-0">4. Acceptable Use Policy</h2>
                        </div>
                        <p className="text-foreground/80 mb-6">
                            This Acceptable Use Policy (&quot;AUP&quot;) governs your use of the Ai2QA Service. Violation of this
                            policy may result in immediate suspension or termination of your account.
                        </p>

                        <h3 className="text-xl font-semibold mt-6 mb-3 text-foreground">4.1 Authorization Requirement</h3>
                        <p className="text-foreground/80 mb-4">
                            <strong>You must have explicit written authorization to test any website or application
                            submitted to Ai2QA.</strong> By submitting a URL for testing, you represent and warrant that:
                        </p>
                        <ul className="list-disc list-inside text-foreground/80 space-y-2 ml-4">
                            <li>You own the target website/application, OR</li>
                            <li>You have written permission from the owner to perform automated testing, OR</li>
                            <li>You are an authorized employee/contractor with testing permissions</li>
                        </ul>
                        <p className="text-muted-foreground text-sm mt-4">
                            Unauthorized testing of third-party systems may constitute a violation of computer fraud
                            and abuse laws, including the U.S. Computer Fraud and Abuse Act (18 U.S.C. § 1030).
                        </p>

                        <h3 className="text-xl font-semibold mt-6 mb-3 text-foreground">4.2 Prohibited Activities</h3>
                        <p className="text-foreground/80 mb-4">
                            The following activities are strictly prohibited:
                        </p>
                        <ul className="list-disc list-inside text-foreground/80 space-y-2 ml-4">
                            <li><strong>Unauthorized Access:</strong> Testing websites you do not own or have explicit permission to test</li>
                            <li><strong>Production Systems:</strong> Testing production environments with live customer data without explicit safeguards</li>
                            <li><strong>Sensitive Targets:</strong> Testing government (.gov, .mil), financial (.bank), healthcare, or critical infrastructure systems without authorization</li>
                            <li><strong>Malicious Intent:</strong> Using the Service to discover vulnerabilities for exploitation or sale</li>
                            <li><strong>Denial of Service:</strong> Intentionally overloading target systems or causing service disruption</li>
                            <li><strong>Data Exfiltration:</strong> Attempting to extract, download, or steal data from target systems</li>
                            <li><strong>Click Fraud:</strong> Using the Service to generate fraudulent ad clicks or impressions</li>
                            <li><strong>Credential Harvesting:</strong> Attempting to capture or store user credentials</li>
                            <li><strong>Bypassing Security:</strong> Attempting to circumvent Ai2QA&apos;s security controls or rate limits</li>
                            <li><strong>Illegal Activities:</strong> Any use that violates applicable local, state, national, or international law</li>
                        </ul>

                        <h3 className="text-xl font-semibold mt-6 mb-3 text-foreground">4.3 Compliance</h3>
                        <p className="text-foreground/80 mb-4">
                            You must comply with all applicable laws, regulations, and industry standards, including but not limited to:
                        </p>
                        <ul className="list-disc list-inside text-foreground/80 space-y-2 ml-4">
                            <li>Computer Fraud and Abuse Act (CFAA)</li>
                            <li>General Data Protection Regulation (GDPR) where applicable</li>
                            <li>California Consumer Privacy Act (CCPA) where applicable</li>
                            <li>Payment Card Industry Data Security Standard (PCI-DSS) for payment systems</li>
                            <li>Health Insurance Portability and Accountability Act (HIPAA) for healthcare systems</li>
                        </ul>

                        <h3 className="text-xl font-semibold mt-6 mb-3 text-foreground">4.4 Monitoring and Enforcement</h3>
                        <div className="flex items-start gap-3 p-4 bg-secondary/50 rounded-lg mt-4">
                            <AlertTriangle className="h-5 w-5 text-amber-400 flex-shrink-0 mt-0.5" />
                            <div className="text-foreground/80 text-sm">
                                <p className="mb-2">
                                    <strong>Ai2QA actively monitors for policy violations.</strong> We employ automated
                                    systems to detect and block:
                                </p>
                                <ul className="list-disc list-inside space-y-1 ml-2">
                                    <li>Attempts to test blocked domains and sensitive infrastructure</li>
                                    <li>SSRF (Server-Side Request Forgery) attacks</li>
                                    <li>Unusual patterns indicative of abuse</li>
                                    <li>Rate limit violations</li>
                                </ul>
                                <p className="mt-3">
                                    Violations may result in: (1) immediate account suspension, (2) permanent termination,
                                    (3) reporting to law enforcement where appropriate, and (4) legal action for damages.
                                </p>
                            </div>
                        </div>
                    </div>

                    <h2 className="text-2xl font-semibold mt-8 mb-4">5. Prohibited Uses</h2>
                    <p className="text-foreground/80">
                        In addition to the Acceptable Use Policy above, you may not use Ai2QA to:
                    </p>
                    <ul className="list-disc list-inside text-foreground/80 mt-4 space-y-2">
                        <li>Reverse engineer, decompile, or disassemble the Service</li>
                        <li>Resell or redistribute access to the Service without authorization</li>
                        <li>Interfere with or disrupt the integrity of the Service</li>
                        <li>Upload malicious code or content through the Service</li>
                    </ul>

                    <h2 className="text-2xl font-semibold mt-8 mb-4">6. Credits, Payments, and Refunds</h2>
                    <p className="text-foreground/80">
                        Ai2QA operates on a pay-per-credit model. Regarding credits and payments:
                    </p>
                    <ul className="list-disc list-inside text-foreground/80 mt-4 space-y-2">
                        <li><strong>Credit Purchases:</strong> Credits are purchased in advance and deducted when you run tests. Each test run consumes one (1) credit.</li>
                        <li><strong>Credit Expiration:</strong> All credits expire 12 months from the date of purchase. Expired credits are non-refundable and cannot be restored.</li>
                        <li><strong>Free Credits:</strong> New users receive complimentary credits upon registration. Free credits have no cash value and are non-transferable.</li>
                        <li><strong>Refund Policy:</strong> Credit purchases are generally non-refundable. However, we may issue refunds at our sole discretion in cases of: (a) Service unavailability exceeding 24 consecutive hours, (b) duplicate charges due to technical errors, or (c) credits purchased within the last 7 days that remain unused. To request a refund, contact{" "}
                            <a href="mailto:support@ai2qa.com" className="text-cyan-400 hover:text-cyan-300">support@ai2qa.com</a>.
                        </li>
                        <li><strong>Price Changes:</strong> We reserve the right to modify pricing at any time. Price changes will not affect credits already purchased.</li>
                    </ul>

                    <h2 className="text-2xl font-semibold mt-8 mb-4">7. Service Availability</h2>
                    <p className="text-foreground/80">
                        We strive to maintain high availability of the Service. However:
                    </p>
                    <ul className="list-disc list-inside text-foreground/80 mt-4 space-y-2">
                        <li><strong>Uptime Target:</strong> We target 99.5% uptime for the Service, measured on a monthly basis, excluding scheduled maintenance.</li>
                        <li><strong>Scheduled Maintenance:</strong> We may perform scheduled maintenance with at least 24 hours advance notice when possible. Maintenance windows are typically scheduled during low-usage periods.</li>
                        <li><strong>No Guarantee:</strong> We do not guarantee uninterrupted or error-free operation of the Service. The Service is provided on an &quot;as available&quot; basis.</li>
                        <li><strong>Service Credits:</strong> If the Service experiences downtime exceeding our 99.5% target in a given month, affected customers may request service credits by contacting support within 30 days of the incident.</li>
                    </ul>

                    <h2 className="text-2xl font-semibold mt-8 mb-4">8. AI-Generated Results Disclaimer</h2>
                    <div className="bg-muted/50 border border-border rounded-xl p-6 mt-4">
                        <p className="text-foreground/80">
                            <strong>IMPORTANT:</strong> Ai2QA uses artificial intelligence to perform automated testing. You acknowledge and agree that:
                        </p>
                        <ul className="list-disc list-inside text-foreground/80 mt-4 space-y-2">
                            <li>AI-generated test results may contain errors, false positives, or false negatives</li>
                            <li>The Service may not detect all bugs, vulnerabilities, or issues in your applications</li>
                            <li>AI-generated reports and recommendations should be reviewed by qualified personnel before acting upon them</li>
                            <li>You are solely responsible for validating test results and making decisions based on them</li>
                            <li>SameThoughts, Inc. makes no representations or warranties regarding the accuracy, completeness, or reliability of AI-generated outputs</li>
                        </ul>
                        <p className="text-muted-foreground text-sm mt-4">
                            The Service is intended to assist, not replace, human QA processes and professional judgment.
                        </p>
                    </div>

                    <h2 className="text-2xl font-semibold mt-8 mb-4">9. Limitation of Liability</h2>
                    <p className="text-foreground/80">
                        TO THE MAXIMUM EXTENT PERMITTED BY APPLICABLE LAW:
                    </p>
                    <ul className="list-disc list-inside text-foreground/80 mt-4 space-y-2">
                        <li><strong>Exclusion of Damages:</strong> SameThoughts, INC. SHALL NOT BE LIABLE FOR ANY INDIRECT, INCIDENTAL, SPECIAL, CONSEQUENTIAL, OR PUNITIVE DAMAGES, INCLUDING BUT NOT LIMITED TO LOSS OF DATA, LOSS OF PROFITS, LOSS OF REVENUE, BUSINESS INTERRUPTION, OR ANY DAMAGES ARISING FROM YOUR USE OF THE SERVICE, REGARDLESS OF WHETHER WE HAVE BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.</li>
                        <li><strong>Liability Cap:</strong> IN NO EVENT SHALL SameThoughts, INC.&apos;S TOTAL AGGREGATE LIABILITY ARISING OUT OF OR RELATED TO THESE TERMS OR YOUR USE OF THE SERVICE EXCEED THE GREATER OF: (A) THE TOTAL AMOUNTS PAID BY YOU TO SameThoughts, INC. IN THE TWELVE (12) MONTHS IMMEDIATELY PRECEDING THE EVENT GIVING RISE TO THE CLAIM, OR (B) ONE HUNDRED UNITED STATES DOLLARS (USD $100).</li>
                        <li><strong>Essential Purpose:</strong> THE LIMITATIONS OF LIABILITY SET FORTH IN THIS SECTION SHALL APPLY EVEN IF ANY REMEDY FAILS OF ITS ESSENTIAL PURPOSE.</li>
                    </ul>
                    <p className="text-foreground/80 mt-4">
                        Some jurisdictions do not allow the exclusion or limitation of certain damages. In such jurisdictions, our liability shall be limited to the maximum extent permitted by law.
                    </p>

                    <h2 className="text-2xl font-semibold mt-8 mb-4">10. Disclaimer of Warranties</h2>
                    <p className="text-foreground/80">
                        THE SERVICE IS PROVIDED &quot;AS IS&quot; AND &quot;AS AVAILABLE&quot; WITHOUT WARRANTIES OF ANY KIND, WHETHER EXPRESS, IMPLIED, STATUTORY, OR OTHERWISE. SameThoughts, INC. SPECIFICALLY DISCLAIMS ALL IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, TITLE, AND NON-INFRINGEMENT. WE DO NOT WARRANT THAT THE SERVICE WILL BE UNINTERRUPTED, ERROR-FREE, SECURE, OR FREE OF VIRUSES OR OTHER HARMFUL COMPONENTS.
                    </p>

                    <h2 className="text-2xl font-semibold mt-8 mb-4">11. Indemnification</h2>
                    <p className="text-foreground/80">
                        You agree to indemnify, defend, and hold harmless SameThoughts, Inc. and its officers, directors,
                        employees, agents, and successors from any claims, damages, losses, liabilities, costs, and expenses (including reasonable
                        attorneys&apos; fees) arising from or related to: (a) your use of the Service; (b) your violation of these Terms;
                        (c) your violation of any third-party rights, including intellectual property rights; (d) unauthorized testing activities conducted
                        using your account; or (e) any content you submit to the Service.
                    </p>

                    <h2 className="text-2xl font-semibold mt-8 mb-4">12. Dispute Resolution and Arbitration</h2>
                    <div className="bg-muted/50 border border-border rounded-xl p-6 mt-4">
                        <p className="text-foreground/80 mb-4">
                            <strong>PLEASE READ THIS SECTION CAREFULLY. IT AFFECTS YOUR LEGAL RIGHTS, INCLUDING YOUR RIGHT TO FILE A LAWSUIT IN COURT.</strong>
                        </p>
                        <h3 className="text-lg font-semibold mt-4 mb-2 text-foreground/90">12.1 Informal Resolution</h3>
                        <p className="text-foreground/80 mb-4">
                            Before initiating any arbitration or court proceeding, you agree to first contact us at{" "}
                            <a href="mailto:legal@ai2qa.com" className="text-cyan-400 hover:text-cyan-300">legal@ai2qa.com</a>{" "}
                            and attempt to resolve the dispute informally for at least 30 days.
                        </p>
                        <h3 className="text-lg font-semibold mt-4 mb-2 text-foreground/90">12.2 Binding Arbitration</h3>
                        <p className="text-foreground/80 mb-4">
                            If we cannot resolve the dispute informally, any dispute, claim, or controversy arising out of or relating to these Terms or the Service shall be resolved exclusively by binding arbitration administered by the American Arbitration Association (&quot;AAA&quot;) in accordance with its Commercial Arbitration Rules. The arbitration shall be conducted in Wilmington, Delaware, or another mutually agreed location. The arbitrator&apos;s decision shall be final and binding, and judgment on the award may be entered in any court of competent jurisdiction.
                        </p>
                        <h3 className="text-lg font-semibold mt-4 mb-2 text-foreground/90">12.3 Class Action Waiver</h3>
                        <p className="text-foreground/80 mb-4">
                            <strong>YOU AND SameThoughts, INC. AGREE THAT EACH PARTY MAY BRING CLAIMS AGAINST THE OTHER ONLY IN YOUR OR ITS INDIVIDUAL CAPACITY, AND NOT AS A PLAINTIFF OR CLASS MEMBER IN ANY PURPORTED CLASS, CONSOLIDATED, OR REPRESENTATIVE PROCEEDING.</strong> The arbitrator may not consolidate more than one person&apos;s claims and may not preside over any form of class or representative proceeding.
                        </p>
                        <h3 className="text-lg font-semibold mt-4 mb-2 text-foreground/90">12.4 Exceptions</h3>
                        <p className="text-foreground/80">
                            Notwithstanding the above, either party may seek injunctive or other equitable relief in any court of competent jurisdiction to prevent the actual or threatened infringement of intellectual property rights.
                        </p>
                    </div>

                    <h2 className="text-2xl font-semibold mt-8 mb-4">13. Data Retention</h2>
                    <p className="text-foreground/80">
                        Test artifacts (screenshots, reports) are retained for 90 days by default. You may request
                        earlier deletion by contacting us at{" "}
                        <a href="mailto:legal@ai2qa.com" className="text-cyan-400 hover:text-cyan-300">
                            legal@ai2qa.com
                        </a>. For more information, please see our{" "}
                        <Link href="/privacy" className="text-cyan-400 hover:text-cyan-300">
                            Privacy Policy
                        </Link>.
                    </p>

                    <h2 className="text-2xl font-semibold mt-8 mb-4">14. Administrative Access to Test Reports</h2>
                    <p className="text-foreground/80">
                        By using the Service, you acknowledge and agree that authorized SameThoughts, Inc.
                        administrators may access, review, and inspect your test run data, including but not
                        limited to test reports, executed steps, target URLs, and test outcomes. Such access may
                        occur for the following purposes:
                    </p>
                    <ul className="list-disc list-inside text-foreground/80 mt-4 space-y-2">
                        <li><strong>Customer Support:</strong> To investigate and resolve support requests, troubleshoot errors, or diagnose issues with your test runs</li>
                        <li><strong>Platform Integrity:</strong> To detect, investigate, and prevent violations of the Acceptable Use Policy, including unauthorized testing or abuse of the Service</li>
                        <li><strong>Compliance and Legal Obligations:</strong> To comply with applicable laws, regulations, legal processes, or enforceable governmental requests</li>
                        <li><strong>Service Improvement:</strong> To analyze anonymized or aggregated usage data for improving the reliability and performance of the Service</li>
                    </ul>
                    <p className="text-foreground/80 mt-4">
                        All administrative access to user test reports is logged for compliance purposes,
                        including the identity of the administrator, the time of access, and the stated reason
                        for review. Access is restricted to authorized personnel and is subject to internal
                        access controls and audit procedures.
                    </p>

                    <h2 className="text-2xl font-semibold mt-8 mb-4">15. Termination</h2>
                    <p className="text-foreground/80">
                        SameThoughts, Inc. reserves the right to suspend or terminate your access to Ai2QA at any time,
                        with or without cause, and with or without notice. Upon termination:
                    </p>
                    <ul className="list-disc list-inside text-foreground/80 mt-4 space-y-2">
                        <li>Your right to use the Service immediately ceases</li>
                        <li>Any unused credits will be forfeited and are non-refundable</li>
                        <li>Sections of these Terms that by their nature should survive termination shall survive, including but not limited to: Limitation of Liability, Indemnification, Dispute Resolution, and Governing Law</li>
                    </ul>

                    <h2 className="text-2xl font-semibold mt-8 mb-4">16. Force Majeure</h2>
                    <p className="text-foreground/80">
                        SameThoughts, Inc. shall not be liable for any failure or delay in performing its obligations under these Terms due to circumstances beyond its reasonable control, including but not limited to: acts of God, natural disasters, war, terrorism, riots, embargoes, acts of civil or military authorities, fire, floods, pandemics, strikes, labor disputes, utility or communications failures, or cyberattacks. In such events, our obligations shall be suspended for the duration of the force majeure event.
                    </p>

                    <h2 className="text-2xl font-semibold mt-8 mb-4">17. Modifications to Terms</h2>
                    <p className="text-foreground/80">
                        We reserve the right to modify these Terms at any time. If we make material changes, we will notify you by:
                    </p>
                    <ul className="list-disc list-inside text-foreground/80 mt-4 space-y-2">
                        <li>Posting the updated Terms on our website with a new &quot;Last Updated&quot; date</li>
                        <li>Sending an email notification to the address associated with your account (for material changes)</li>
                        <li>Displaying a prominent notice within the Service</li>
                    </ul>
                    <p className="text-foreground/80 mt-4">
                        Your continued use of the Service after the effective date of the revised Terms constitutes your acceptance of the changes. If you do not agree to the revised Terms, you must stop using the Service.
                    </p>

                    <h2 className="text-2xl font-semibold mt-8 mb-4">18. Governing Law</h2>
                    <p className="text-foreground/80">
                        These Terms shall be governed by and construed in accordance with the laws of the State of
                        Delaware, United States, without regard to its conflict of law provisions. Subject to the arbitration provisions above, any legal action or proceeding arising out of these Terms shall be brought exclusively in the state or federal courts located in Wilmington, Delaware, and you consent to the personal jurisdiction of such courts.
                    </p>

                    <h2 className="text-2xl font-semibold mt-8 mb-4">19. Severability</h2>
                    <p className="text-foreground/80">
                        If any provision of these Terms is held to be invalid, illegal, or unenforceable by a court of competent jurisdiction, such provision shall be modified to the minimum extent necessary to make it valid and enforceable, or if modification is not possible, shall be severed from these Terms. The invalidity of any provision shall not affect the validity or enforceability of any other provision of these Terms, and the remaining provisions shall continue in full force and effect.
                    </p>

                    <h2 className="text-2xl font-semibold mt-8 mb-4">20. Entire Agreement</h2>
                    <p className="text-foreground/80">
                        These Terms, together with our Privacy Policy and any other legal notices or agreements published by us on the Service, constitute the entire agreement between you and SameThoughts, Inc. regarding your use of the Service. These Terms supersede any prior agreements, communications, or understandings, whether written or oral, relating to the subject matter hereof.
                    </p>

                    <h2 className="text-2xl font-semibold mt-8 mb-4">21. Contact Information</h2>
                    <p className="text-foreground/80">
                        For any questions regarding these Terms, please contact us at:
                    </p>
                    <div className="bg-muted/50 border border-border rounded-lg p-4 mt-4">
                        <p className="text-foreground/80">
                            <strong>SameThoughts, Inc.</strong><br />
                            Email: <a href="mailto:legal@ai2qa.com" className="text-cyan-400 hover:text-cyan-300">legal@ai2qa.com</a><br />
                            Support: <a href="mailto:support@ai2qa.com" className="text-cyan-400 hover:text-cyan-300">support@ai2qa.com</a>
                        </p>
                    </div>

                    <p className="text-muted-foreground/70 text-sm mt-8 pt-8 border-t border-border">
                        By using Ai2QA, you acknowledge that you have read, understood, and agree to be bound by
                        these Terms of Service, including the Acceptable Use Policy, Arbitration Agreement, and Class Action Waiver.
                    </p>
                </div>
            </div>
        </div>
    );
}
