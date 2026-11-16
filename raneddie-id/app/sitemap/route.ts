import { NextResponse } from "next/server";

const SITE_URL = "https://id.raneddie.com";
const pages = [
  "/",
  "/privacy",
  "/terms",
  "/sign-in",
  "/sign-up",
  "/forgot-password",
];

export function GET() {
  const lastModified = new Date().toISOString();
  const urls = pages
    .map(
      (path) => `  <url>
    <loc>${SITE_URL}${path === "/" ? "" : path}</loc>
    <lastmod>${lastModified}</lastmod>
  </url>`,
    )
    .join("\n");

  return new NextResponse(
    `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n${urls}\n</urlset>\n`,
    {
      headers: {
        "Content-Type": "application/xml; charset=utf-8",
        "Cache-Control": "public, max-age=3600, s-maxage=3600",
      },
    },
  );
}
