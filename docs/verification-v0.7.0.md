# v0.7.0 Integration Tracking

Status: preparation in progress; final integrated verification is pending.

Snapshot: 2026-10-13 17:35 Asia/Shanghai. Progress statements below describe
the evidence available at that time; this page is updated through reviewed
documentation changes.

Source baseline: `caf51809102489dd0ecc5bc185352fe9992a5177`,
which contains PR #23 and the installation-guide update. This baseline does
not yet contain all five features. The final target SHA will be recorded
after the remaining source changes and conflict resolutions are integrated.

## Feature Delivery Evidence

| Feature | Latest recorded feature head | Evidence | State in this documentation baseline |
| --- | --- | --- | --- |
| JPEG XL DNG decoding | `d6d51b92cb1942b7745964325f3e5586baf3996c` | [PR #23](https://github.com/dancancer/RawLab/pull/23), [source report](https://github.com/dancancer/RawLab/blob/d6d51b92cb1942b7745964325f3e5586baf3996c/docs/verification-dng-jpeg-xl-2026-10-12.md), and [local record](verification-dng-jpeg-xl-2026-10-12.md) | Included; combined verification pending |
| Rendered DNG export | `d5d0b276367277f557d5ca8658ee901583dec740` | [PR #24](https://github.com/dancancer/RawLab/pull/24) and [source report](https://github.com/dancancer/RawLab/blob/d5d0b276367277f557d5ca8658ee901583dec740/docs/verification-rendered-dng-export.md) | Integration with `caf5180` in progress; final revision pending |
| Six-language support | `dea349378e38e6ec4706e3b2fcf23b47ec39ce35` | [PR #25](https://github.com/dancancer/RawLab/pull/25), [source report](https://github.com/dancancer/RawLab/blob/dea349378e38e6ec4706e3b2fcf23b47ec39ce35/docs/verification-localization-2026-10-13.md), and [maintenance guide](https://github.com/dancancer/RawLab/blob/dea349378e38e6ec4706e3b2fcf23b47ec39ce35/docs/localization.md) | Not yet included; new DNG/lens control strings need combined coverage |
| Lensfun lens correction | `bdc054120eec3797b0cfef4adbd9d34f733d8968` | [PR #26](https://github.com/dancancer/RawLab/pull/26), [source report](https://github.com/dancancer/RawLab/blob/bdc054120eec3797b0cfef4adbd9d34f733d8968/docs/verification-lens-correction-platforms-2026-10-13.md), and [maintenance guide](https://github.com/dancancer/RawLab/blob/bdc054120eec3797b0cfef4adbd9d34f733d8968/docs/lens-correction-platforms.md) | Integration with `caf5180` in progress; final revision pending |
| Viewport previews, denoise cache and Android memory work | `90809a718b71786308fac750497959cd6e83630c` | [PR #27](https://github.com/dancancer/RawLab/pull/27), [source report](https://github.com/dancancer/RawLab/blob/90809a718b71786308fac750497959cd6e83630c/docs/verification-viewport-development-2026-10-13.md), and [author review record](https://github.com/dancancer/RawLab/blob/90809a718b71786308fac750497959cd6e83630c/reviews/self-viewport-development-20261013.md) | Not yet included; implementation at `e101b798c174356d0223ade1eff31ba8983f91db`, followed by documentation only |

These links identify feature delivery records at the latest recorded heads.
Their results retain the source and platform scope stated in each report.
Conflict resolutions and later source changes need review and affected checks;
the viewport author review is separate from independent review. The records
describe local or dedicated build-host results; GitHub checks must be recorded
separately. Referenced ignored build logs may be absent from a clean checkout.

## Integration Follow-ups at This Snapshot

- **PR #24, rendered DNG:** the author is resolving conflicts and validating
  integration with `caf5180`. The recorded remote head remains `d5d0b276`;
  no replacement head with completed review and checks is recorded here.
- **PR #26, Lensfun:** the author is resolving conflicts and validating the
  combined path. A regression reproduced a Stage3 warning surviving decoder
  `recycle`, causing the next image without Stage3 to skip Lensfun incorrectly.
  The minimal state reset and an actual-DNG-SDK Stage3-to-Lensfun end-to-end
  RED/GREEN check are in progress. The recorded remote head remains `bdc05412`;
  this regression is unresolved in the delivery evidence above.
- **PR #25, six languages:** read-only preparation produced 49 translation
  seeds across six languages in an ignored directory. Production resources
  and call sites have not yet consumed them. The remaining wiring covers new
  DNG/Lensfun text, dynamic state and accessibility labels, and PR #27's iOS
  message for waiting until the preview completes before export. Original
  six-language results retain their scope; resource coverage, placeholders,
  stable state and native layout need combined-source checks.
- **PR #27, viewport/cache:** after the new #24/#26 heads are integrated,
  complete one focused Lensfun + noise reduction + viewport A/B + profile +
  auto-crop combination through FINAL JPEG, 16-bit PNG and rendered DNG.
  Record the final source and affected checks for that combination.

## Final Integrated Verification

The minimum matrix uses one final source revision for all affected clients and
the shared core. Every gate below remains pending. Each result must record the
source SHA, configuration, actual checks, fixture scope and platform limits.

| Gate | Required evidence | Status |
| --- | --- | --- |
| Source and review | Final development SHA, five PRs, and reviews tied to their final source revisions | Pending |
| Fresh build and link | Fresh configure/link on macOS, iOS, Android and Windows: DNG SDK/JPEG XL, TIFF >= 4.7.1, Lensfun, wavelet/chroma and GPU support present together; existing shared C ABI layouts and enum values preserved | Pending |
| Decoder reuse | Actual SDK Stage3 processing followed by an image without Stage3 on the recycled decoder; warning state resets and Lensfun executes correctly for the second image; captured RED/GREEN regression | Pending |
| Cache isolation | Lensfun, noise-reduction and preview-baseline cache keys stay isolated across viewport A/B, profile and auto-crop changes; affected CPU/GPU checks on the combined source | Pending |
| FINAL exports | The focused Lensfun/noise-reduction/viewport/profile/auto-crop combination produces JPEG, PNG16 and rendered DNG without a proxy; cached results match a fresh exact render, and single/batch exports use consistent settings | Pending |
| Six-language integration | New DNG/Lensfun and iOS preview-wait messages, dynamic state and accessibility call sites wired into production resources; six-language keys/placeholders, stable state and affected native layouts checked | Pending |
| Package closure | Dependencies, licenses, Lensfun database and language resources included together; version/build consistency, corresponding source, signatures, runtime dependencies, RAW exclusion and checksums recorded | Pending |

The original Android cold/hot/Scene results under the 1,800,000,000-byte budget
remain per-feature evidence for the measured causes and algorithm budget.
The focused matrix does not require repeating that broad experiment. Those
results do not establish an integrated pass for the final source. Record any
unavailable runtime, GPU, device or camera/lens coverage explicitly alongside
the new results.

Populate results only after the corresponding integrated checks have completed.
Keep per-feature limitations visible when preparing
[the release](releases/v0.7.0-preparation.md).
