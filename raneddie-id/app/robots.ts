import type { MetadataRoute } from "next";

export default function robots(): MetadataRoute.Robots {
  return {
    rules: [{ userAgent: "*", allow: "/", disallow: ["/account", "/connect", "/api/"] }],
    sitemap: "https://id.raneddie.com/sitemap.xml",
    host: "https://id.raneddie.com",
  };
}
