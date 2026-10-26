import { describe, expect, it } from "vitest";
import { ELLIPSIS, getPageItems, parsePageParam } from "./pagination";

describe("getPageItems", () => {
  it("shows a single page", () => {
    expect(getPageItems(1, 1)).toEqual([1]);
  });

  it("shows all 5 pages without dots", () => {
    expect(getPageItems(1, 5)).toEqual([1, 2, 3, 4, 5]);
    expect(getPageItems(3, 5)).toEqual([1, 2, 3, 4, 5]);
    expect(getPageItems(5, 5)).toEqual([1, 2, 3, 4, 5]);
  });

  it("shows all 7 pages without dots", () => {
    expect(getPageItems(1, 7)).toEqual([1, 2, 3, 4, 5, 6, 7]);
    expect(getPageItems(4, 7)).toEqual([1, 2, 3, 4, 5, 6, 7]);
    expect(getPageItems(7, 7)).toEqual([1, 2, 3, 4, 5, 6, 7]);
  });

  it("collapses 22 pages from the first page", () => {
    expect(getPageItems(1, 22)).toEqual([1, 2, ELLIPSIS, 22]);
  });

  it("collapses 22 pages from a middle page", () => {
    expect(getPageItems(11, 22)).toEqual([1, ELLIPSIS, 10, 11, 12, ELLIPSIS, 22]);
  });

  it("collapses 22 pages from the last page", () => {
    expect(getPageItems(22, 22)).toEqual([1, ELLIPSIS, 21, 22]);
  });

  it("shows the page number instead of dots when the gap is exactly one page", () => {
    // Gap between 1 and 3 is just page 2.
    expect(getPageItems(4, 22)).toEqual([1, 2, 3, 4, 5, ELLIPSIS, 22]);
    // Gap between 20 and 22 is just page 21.
    expect(getPageItems(19, 22)).toEqual([1, ELLIPSIS, 18, 19, 20, 21, 22]);
  });

  it("uses dots once there are 8 pages", () => {
    expect(getPageItems(1, 8)).toEqual([1, 2, ELLIPSIS, 8]);
  });

  it("clamps an out-of-range current page", () => {
    expect(getPageItems(99, 22)).toEqual([1, ELLIPSIS, 21, 22]);
    expect(getPageItems(0, 22)).toEqual([1, 2, ELLIPSIS, 22]);
  });

  it("returns nothing when there are no pages", () => {
    expect(getPageItems(1, 0)).toEqual([]);
  });
});

describe("parsePageParam", () => {
  it("parses whole numbers >= 1", () => {
    expect(parsePageParam("1")).toBe(1);
    expect(parsePageParam("3")).toBe(3);
  });

  it("falls back to page 1 for missing or invalid values", () => {
    expect(parsePageParam(null)).toBe(1);
    expect(parsePageParam("")).toBe(1);
    expect(parsePageParam("abc")).toBe(1);
    expect(parsePageParam("0")).toBe(1);
    expect(parsePageParam("-2")).toBe(1);
    expect(parsePageParam("2.5")).toBe(1);
    expect(parsePageParam("99999999999999999999")).toBe(1);
  });
});
