import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { MIN_PASSWORD_LENGTH, clerkError, isEmail, passwordChecks, safeRedirect, usernameError } from "./auth-helpers.ts";

describe("safeRedirect", () => {
  it("allows local paths and trusted RanEddie origins", () => {
    assert.equal(safeRedirect("/account"), "/account");
    assert.equal(safeRedirect("/connect?game=snakebrawl&state=abc"), "/connect?game=snakebrawl&state=abc");
    assert.equal(safeRedirect("https://raneddie.com/"), "https://raneddie.com/");
    assert.equal(safeRedirect("https://www.raneddie.com/products?x=1"), "https://www.raneddie.com/products?x=1");
  });

  it("rejects other sites", () => {
    for (const bad of [
      "https://evil.com",
      "https://raneddie.com.evil.com/",
      "https://evil.com/https://raneddie.com",
      "http://raneddie.com/",
      "//evil.com",
      "javascript:alert(1)",
      "data:text/html,hi",
      "raneddie.com",
      "",
      null,
      undefined,
    ]) {
      assert.equal(safeRedirect(bad), null, String(bad));
    }
  });

  it("rejects paths that browsers turn into another site", () => {
    // Browsers drop tabs/newlines and treat backslashes as slashes, so these all become //evil.com
    for (const bad of ["/\t/evil.com", "/\n/evil.com", "/\r/evil.com", "/\\evil.com", "/\\/evil.com", "/%09/evil.com\u0000"]) {
      assert.equal(safeRedirect(bad), null, JSON.stringify(bad));
    }
  });

  it("rejects absurdly long values", () => {
    assert.equal(safeRedirect("/" + "a".repeat(3000)), null);
  });
});

describe("passwordChecks", () => {
  it("requires the same length Clerk enforces", () => {
    assert.equal(MIN_PASSWORD_LENGTH, 15);
    assert.equal(passwordChecks("x".repeat(14))[0].ok, false);
    assert.equal(passwordChecks("x".repeat(15))[0].ok, true);
    assert.match(passwordChecks("")[0].label, /15/);
  });
});

describe("clerkError", () => {
  it("shows Clerk's friendly wording", () => {
    const e = clerkError({ errors: [{ code: "form_identifier_not_found", message: "x" }] });
    assert.match(e.message, /couldn't find a RanEddie ID/);
  });

  it("explains the password length rule", () => {
    const e = clerkError({ errors: [{ code: "form_password_length_too_short", message: "x" }] });
    assert.match(e.message, /15/);
  });

  it("turns network failures into a clear message instead of a stuck form", () => {
    assert.equal(clerkError(new TypeError("Failed to fetch")).code, "network_error");
    assert.equal(clerkError(new Error("Network request failed")).code, "network_error");
  });

  it("has a fallback for anything else", () => {
    assert.ok(clerkError(undefined).message.length > 0);
  });
});

describe("isEmail / usernameError", () => {
  it("validates emails", () => {
    assert.equal(isEmail("a@b.co"), true);
    assert.equal(isEmail("nope"), false);
    assert.equal(isEmail("a b@c.com"), false);
  });

  it("validates usernames", () => {
    assert.equal(usernameError("Snake_Master"), null);
    assert.ok(usernameError("abc"));
    assert.ok(usernameError("1snake"));
    assert.ok(usernameError("snake master"));
    assert.ok(usernameError("a".repeat(21)));
  });
});
