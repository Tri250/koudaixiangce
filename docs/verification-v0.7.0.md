# v0.7.0 Integration Tracking

Status: preparation in progress; final integrated verification is pending.

Documentation baseline: `caf51809102489dd0ecc5bc185352fe9992a5177`,
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

The six-language delivery results remain evidence for their original scope.
New rendered-DNG and Lensfun interface strings are an integration follow-up:
complete resource coverage, placeholders and native layout checks on the final
combined source.

## Final Integrated Verification

| Gate | Required evidence | Status |
| --- | --- | --- |
| Source and review | Final development SHA, five PRs, and reviews tied to their final source revisions | Pending |
| Shared core | Build configuration, actual registered tests, results, RAW fixtures, and CPU/GPU coverage | Pending |
| Feature composition | Language resources and controls for DNG export and lens correction; preview/export behavior; stable saved edits and batch snapshots | Pending |
| Native clients | macOS, iOS, Android and Windows builds and affected model/native/UI checks against the combined source | Pending |
| Platform limits | Explicit skipped or unavailable runtime, GPU, device and camera/lens combinations | Pending |
| Release packaging | Version/build consistency, corresponding source, licenses, dependencies, signatures, RAW exclusion and checksums | Pending |

Populate results only after the corresponding integrated checks have completed.
Keep per-feature limitations visible when preparing
[the release](releases/v0.7.0-preparation.md).
