package hooman.morphe.patches.swiftkey.support

import app.morphe.patcher.patch.PatchException

/**
 * សារជំនួសឲ្យ "Failed to match the fingerprint: …@hash" របស់ patcher។
 *
 * វាប្រាប់អ្នកប្រើថាមេតូដគោលដៅបានប្រែនៅក្នុង APK របស់គេ ហើយបំណះនេះបានផ្ទៀងផ្ទាត់
 * តែលើកំណែណា ជំនួសឲ្យការបោះ hash ដែលគ្មានន័យ។
 */
internal fun unsupportedSwiftKeyVersion(target: String, fingerprint: String) = PatchException(
    "SwiftKey: $target changed in this APK build, so $fingerprint no longer matches. These " +
        "patches are verified for 9.13.13.5; 9.13.14.5 is declared experimental and its " +
        "fingerprints still have to be re-derived from that build.",
)
