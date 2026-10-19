import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: "standalone", // imagen Docker mínima (docker-compose.yml)
  cacheComponents: true,
  partialPrefetching: true,
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
};

export default nextConfig;
