import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { toBirthDate, validateProfile } from "./profile.ts";

const NOW = new Date(2026, 10, 9); // 9 Nov 2026

describe("toBirthDate", () => {
  it("formats the form's month index (0–11) as YYYY-MM-DD", () => {
    assert.equal(toBirthDate(2005, 0, 9), "2005-01-09");
    assert.equal(toBirthDate(1999, 11, 31), "1999-12-31");
  });
});

describe("validateProfile", () => {
  it("accepts an adult", () => {
    assert.deepEqual(validateProfile({ birthDate: "2000-05-17", country: "IL" }, NOW), {
      ok: true,
      value: { birthDate: "2000-05-17", country: "IL" },
    });
  });

  it("enforces the minimum age of 13 on the exact birthday", () => {
    assert.equal(validateProfile({ birthDate: "2013-11-09", country: "US" }, NOW).ok, true); // turns 13 today
    const tooYoung = validateProfile({ birthDate: "2013-11-10", country: "US" }, NOW);
    assert.deepEqual(tooYoung, { ok: false, error: "underage" });
  });

  it("rejects impossible, future and malformed dates", () => {
    for (const birthDate of ["2001-02-30", "2027-01-01", "1800-01-01", "01-02-2000", "2000/01/01", "", "2000-1-1"]) {
      assert.deepEqual(validateProfile({ birthDate, country: "US" }, NOW), { ok: false, error: "invalid" }, birthDate);
    }
  });

  it("rejects bad countries and non-objects", () => {
    for (const country of ["us", "USA", "U", "", "1A"]) {
      assert.equal(validateProfile({ birthDate: "2000-01-01", country }, NOW).ok, false, country);
    }
    for (const input of [null, undefined, "x", 5, [], { birthDate: 5, country: "US" }]) {
      assert.equal(validateProfile(input, NOW).ok, false);
    }
  });
});
