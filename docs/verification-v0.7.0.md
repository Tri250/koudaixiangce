# v0.7.0 Integration Tracking

Status: preparation in progress; final integrated verification is pending.

Snapshot: 2026-10-13 18:06 Asia/Shanghai. Progress statements below describe
the evidence available at that time; this page is updated through reviewed
documentation changes.

Source baseline: `802354aa9b54e540fc2ca1c55be09038a5f621bd`,
which contains PR #23, PR #24, the installation-guide update and the PR #28
documentation cleanup. This baseline does not yet contain all five features.
The final target SHA will be recorded
after the remaining source changes and conflict resolutions are integrated.

## Completed Development Merges

| PR | Delivered source head | Development merge |
| --- | --- | --- |
| [#23, JPEG XL DNG](https://github.com/dancancer/RawLab/pull/23) | `d6d51b92cb1942b7745964325f3e5586baf3996c` | `caf51809102489dd0ecc5bc185352fe9992a5177` |
| [#28, documentation cleanup](https://github.com/dancancer/RawLab/pull/28) | `bdbb1d4343c918f0e95dc0fc10a11db491f121ce` | `261b9323bea13f562bcfee7db736fcf1728704f5` |
| [#24, rendered DNG](https://github.com/dancancer/RawLab/pull/24) | `5f0dea614138813cb58b6e3637fa997363ab14b4` | `802354aa9b54e540fc2ca1c55be09038a5f621bd` |

The last merge has parents `261b9323` and `5f0dea61`. These are completed
development deliveries; final five-feature verification remains pending.

## Feature Delivery Evidence

| Feature | Latest recorded source | Evidence | State in this documentation baseline |
| --- | --- | --- | --- |
| JPEG XL DNG decoding | `d6d51b92cb1942b7745964325f3e5586baf3996c` | [PR #23](https://github.com/dancancer/RawLab/pull/23), [source report](https://github.com/dancancer/RawLab/blob/d6d51b92cb1942b7745964325f3e5586baf3996c/docs/verification-dng-jpeg-xl-2026-10-12.md), and [local record](verification-dng-jpeg-xl-2026-10-12.md) | Included; combined verification pending |
| Rendered DNG export | `5f0dea614138813cb58b6e3637fa997363ab14b4` | [PR #24](https://github.com/dancancer/RawLab/pull/24) and [included verification report](verification-rendered-dng-export.md) | Included; independent re-review completed with no Must/Should findings; combined verification pending |
| Six-language support | `dea349378e38e6ec4706e3b2fcf23b47ec39ce35` | [PR #25](https://github.com/dancancer/RawLab/pull/25), [source report](https://github.com/dancancer/RawLab/blob/dea349378e38e6ec4706e3b2fcf23b47ec39ce35/docs/verification-localization-2026-10-13.md), and [maintenance guide](https://github.com/dancancer/RawLab/blob/dea349378e38e6ec4706e3b2fcf23b47ec39ce35/docs/localization.md) | Not yet included; new DNG/lens control strings need combined coverage |
| Lensfun lens correction | Local `ab19154c044bc36583c95abd5c706f4bcf77a0ca`; published `bdc054120eec3797b0cfef4adbd9d34f733d8968` | [PR #26](https://github.com/dancancer/RawLab/pull/26), [last published source report](https://github.com/dancancer/RawLab/blob/bdc054120eec3797b0cfef4adbd9d34f733d8968/docs/verification-lens-correction-platforms-2026-10-13.md), and [published maintenance guide](https://github.com/dancancer/RawLab/blob/bdc054120eec3797b0cfef4adbd9d34f733d8968/docs/lens-correction-platforms.md) | Not yet included; local Stage3 feature gates GREEN; new-base checks, independent review binding and publication pending |
| Viewport previews, denoise cache and Android memory work | `90809a718b71786308fac750497959cd6e83630c` | [PR #27](https://github.com/dancancer/RawLab/pull/27), [source report](https://github.com/dancancer/RawLab/blob/90809a718b71786308fac750497959cd6e83630c/docs/verification-viewport-development-2026-10-13.md), and [author review record](https://github.com/dancancer/RawLab/blob/90809a718b71786308fac750497959cd6e83630c/reviews/self-viewport-development-20261013.md) | Not yet included; implementation at `e101b798c174356d0223ade1eff31ba8983f91db`, followed by documentation only |

These links identify published or included feature delivery records. The new
Lensfun source is local and its current results are summarized below; its
published links retain their earlier source scope. All results retain the
source and platform scope stated in each report.
Conflict resolutions and later source changes need review and affected checks;
the viewport author review is separate from independent review. The records
describe local or dedicated build-host results; GitHub checks must be recorded
separately. Referenced ignored build logs may be absent from a clean checkout.

## Integration Follow-ups at This Snapshot

- **PR #24, rendered DNG:** delivered and merged. Targeted checks recorded in
  the included report comprise Mac CTest 22/22 and real-JXL CPU/Metal
  single/batch DNG comparisons with maximum PNG16 error 1/65535; three iOS
  framework slices; Windows targeted tests 4/4 and complete DNG runtime on
  CPU/Auto/Force D3D11; and Android dual-ABI builds, lint, APK and notices.
  Independent re-review bound to `5f0dea61` found no Must/Should issues. Its
  local artifact is `reviews/pr-24-development-integration-rereview-20261013-1759.md`;
  that artifact is not included in this source baseline. Android JUnit was
  UP-TO-DATE, with no new device runtime. The real-JXL smoke used a 128-pixel
  long edge and does not cover large native-size DNG output. Original platform
  limits remain in the included report. Hosted CI runs/checks were zero.
- **PR #26, Lensfun:** local `ab19154c` has actual-SDK Stage3 feature checks
  GREEN across four clients. The required `WarpRectilinear` fixture and
  Stage3-to-no-Stage3 JXL/plain decoder reuse, white balance and C ABI checks
  passed, closing the reproduced warning-state leak in that local source.
  Recorded gates include Mac 23/23 and LensOff 2/2; Windows 21/21 plus 144
  checks; Android dual-ABI builds and an owned real-RAW instrumented run with
  Lensfun enabled; and iOS three framework slices, app and native checks. The two original
  independent reviewers still need to bind their conclusions to the new
  source, which has not been pushed or merged. The author has been assigned
  integration with `802354aa` and only the affected DNG/Lensfun/SDK intersection
  checks. This follow-up does not repeat the completed four-client Stage3
  feature matrix; its new source binding and final integrated result remain
  pending.
- **PR #25, six languages:** read-only preparation produced 49 translation
  seeds across six languages in an ignored directory. Production resources
  and call sites have not yet consumed them. The remaining wiring covers new
  DNG/Lensfun text, dynamic state and accessibility labels, and PR #27's iOS
  message for waiting until the preview completes before export. Original
  six-language results retain their scope; resource coverage, placeholders,
  stable state and native layout need combined-source checks after the
  Lensfun-inclusive target is available.
- **PR #27, viewport/cache:** PR #24 is included; after the new #26 head is
  integrated into `802354aa`,
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
