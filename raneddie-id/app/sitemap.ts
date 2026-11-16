import type { MetadataRoute } from "next";

const SITE_URL = "https://id.raneddie.com";

export default function sitemap(): MetadataRoute.Sitemap {
  const lastModified = new Date();
  return [
    { url: SITE_URL, lastModified, changeFrequency: "weekly", priority: 1 },
    { url: `${SITE_URL}/privacy`, lastModified, changeFrequency: "yearly", priority: 0.4 },
    { url: `${SITE_URL}/terms`, lastModified, changeFrequency: "yearly", priority: 0.4 },
    { url: `${SITE_URL}/sign-in`, lastModified, changeFrequency: "monthly", priority: 0.3 },
    { url: `${SITE_URL}/sign-up`, lastModified, changeFrequency: "monthly", priority: 0.4 },
    { url: `${SITE_URL}/forgot-password`, lastModified, changeFrequency: "yearly", priority: 0.2 },
  ];
}
