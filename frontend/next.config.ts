import type { NextConfig } from "next";
import { config } from "dotenv";
import { resolve } from "path";

// Load environment variables from the root .env.local file
// This allows sharing env vars between frontend and backend
config({ path: resolve(__dirname, "../.env.local") });

const nextConfig: NextConfig = {
  // Output standalone for Docker deployment
  output: "standalone",
};

export default nextConfig;
