package dev.rishi.tvlauncher

/**
 * Vendor setup, service and promo tools that register a launcher icon but don't belong on a
 * home screen. They must stay *enabled* (disabling some breaks first boot or the remote), so
 * they are hidden instead. Users can bring them back via Customize > Show hidden apps.
 * Verified on a TCL 32ES568; Google ones seen on the Android TV / Google TV emulator.
 */
val DEFAULT_HIDDEN_PACKAGES: Set<String> = setOf(
    // TCL
    "com.tcl.copydatatotv", // Phone-to-TV data transfer
    "com.tcl.factory.view", // Factory menu
    "com.tcl.framework.custom",
    "com.tcl.initsetup", // First-boot setup
    "com.tcl.partnercustomizer",
    "com.tcl.rc.ota", // Remote firmware updater
    "com.tcl.tcl_bt_rcu_service", // Bluetooth remote service
    "com.tcl.tv.tclhome_passive", // TCL Home promo channel
    "com.tcl.tvweishi", // TV guard / cleaner
    "com.tcl.useragreement",
    "com.tcl.usercenter", // TCL account
    // Google
    "com.google.android.tv.remote.service", // Android TV Remote Service
    "com.google.android.tv.axel", // Third Party Notices
)
