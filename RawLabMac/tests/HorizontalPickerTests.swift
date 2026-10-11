import Foundation

private func check(_ value: Bool, _ message: String) {
    guard value else { fputs("FAIL: \(message)\n", stderr); exit(1) }
    print("PASS: \(message)")
}

@main struct HorizontalPickerTests {
    static func main() {
        let fit = HorizontalPickerScrollState(contentWidth: 600, viewportWidth: 600, offset: 0)
        check(!fit.needsControls(availableWidth: 600) && !fit.canScrollLeft && !fit.canScrollRight,
              "Equal-width content has no controls or scrolling")
        let overflow = HorizontalPickerScrollState(contentWidth: 601, viewportWidth: 536, offset: 0)
        check(overflow.needsControls(availableWidth: 600) && !overflow.canScrollLeft && overflow.canScrollRight,
              "One-point overflow shows controls and disables only the leading arrow")
        let middle = HorizontalPickerScrollState(contentWidth: 1800, viewportWidth: 600, offset: 600)
        check(middle.canScrollLeft && middle.canScrollRight, "Both directions work in the middle")
        check(middle.pageOffset(towardTrailing: true) == 1110 &&
              middle.pageOffset(towardTrailing: false) == 90, "Paging retains fifteen percent of viewport context")
        let end = HorizontalPickerScrollState(contentWidth: 1800, viewportWidth: 600, offset: 1200)
        check(end.canScrollLeft && !end.canScrollRight &&
              end.pageOffset(towardTrailing: true) == 1200, "Trailing boundary disables and clamps forward scrolling")
        let start = HorizontalPickerScrollState(contentWidth: 1800, viewportWidth: 600, offset: 100)
        check(start.pageOffset(towardTrailing: false) == 0, "Backward paging clamps to zero")
        let bounce = HorizontalPickerScrollState(contentWidth: 1800, viewportWidth: 600, offset: -40)
        check(!bounce.canScrollLeft && bounce.pageOffset(towardTrailing: true) == 510,
              "Leading rubber-band offset does not change the page distance")
        let trailingBounce = HorizontalPickerScrollState(contentWidth: 1800, viewportWidth: 600, offset: 1250)
        check(!trailingBounce.canScrollRight && trailingBounce.pageOffset(towardTrailing: false) == 690,
              "Trailing rubber-band offset remains bounded")
        let shrunk = HorizontalPickerScrollState(contentWidth: 590, viewportWidth: 536, offset: 54)
        check(!shrunk.needsControls(availableWidth: 600),
              "Controls disappear after content shrinks below full width despite their reserved lanes")
        let expanded = HorizontalPickerScrollState(contentWidth: 1800, viewportWidth: 1740, offset: 60)
        check(!expanded.needsControls(availableWidth: 1800), "Window expansion hides unnecessary controls")
        let empty = HorizontalPickerScrollState(contentWidth: 0, viewportWidth: 600, offset: 500)
        check(!empty.needsControls(availableWidth: 600) && empty.maximumOffset == 0 &&
              empty.pageOffset(towardTrailing: false) == 0 && !empty.canScrollLeft && !empty.canScrollRight,
              "Empty and stale geometry cannot scroll out of range")
        let short = HorizontalPickerScrollState(contentWidth: 650, viewportWidth: 600, offset: 0)
        check(short.pageOffset(towardTrailing: true) == 50, "Small overflow scrolls exactly to its edge")
        let fractionalEnd = HorizontalPickerScrollState(contentWidth: 650, viewportWidth: 600, offset: 49.8)
        check(!fractionalEnd.canScrollRight, "Subpixel boundary rounding does not flicker button availability")
    }
}
