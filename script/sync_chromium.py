#!/usr/bin/env python
#
# Copyright (c) 2015 The mogoweb project. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import optparse
import os
import sys

import constants
import resource_util
import shutil

sys.path.append(os.path.join(os.path.dirname(__file__), "dirsync-2.1"))
from dirsync import sync

java_srcs = [
    "base/android/java/src",
    "chrome/android/java/src",
    "chrome/android/webapk/libs/client/src",
    "chrome/android/webapk/libs/common/src",
    "components/autofill/android/java/src",
    "components/background_task_scheduler/android/java/src",
    "components/bookmarks/common/android/java/src",
    "components/crash/android/java/src",
    "components/dom_distiller/content/browser/android/java/src",
    "components/dom_distiller/core/android/java/src",
    "components/feature_engagement_tracker/internal/android/java/src",
    "components/feature_engagement_tracker/public/android/java/src",
    "components/gcm_driver/android/java/src",
    "components/gcm_driver/instance_id/android/java/src",
    "components/invalidation/impl/android/java/src",
    "components/location/android/java/src",
    "components/minidump_uploader/android/java/src",
    "components/navigation_interception/android/java/src",
    "components/ntp_tiles/android/java/src",
    "components/offline_items_collection/core/android/java/src",
    "components/payments/content/android/java/src",
    "components/policy/android/java/src",
    "components/precache/android/java/src",
    "components/safe_browsing_db/android/java/src",
    "components/safe_json/android/java/src",
    "components/signin/core/browser/android/java/src",
    "components/spellcheck/browser/android/java/src",
    "components/sync/android/java/src",
    "components/url_formatter/android/java/src",
    "components/variations/android/java/src",
    "components/web_contents_delegate_android/android/java/src",
    "components/web_restrictions/browser/java/src",
    "content/public/android/java/src",
    "device/bluetooth/android/java/src",
    "device/gamepad/android/java/src",
    "device/generic_sensor/android/java/src",
    "device/geolocation/android/java/src",
    "device/power_save_blocker/android/java/src",
    "device/sensors/android/java/src",
    "device/usb/android/java/src",
    "media/base/android/java/src",
    "media/capture/content/android/java/src",
    "media/capture/video/android/java/src",
    "media/midi/java/src",
    "mojo/android/system/src",
    "mojo/public/java/bindings/src",
    "mojo/public/java/system/src",
    "net/android/java/src",
    "printing/android/java/src",
    "services/device/android/java/src",
    "services/device/battery/android/java/src",
    "services/device/nfc/android/java/src",
    "services/device/public/java/src",
    "services/device/screen_orientation/android/java/src",
    "services/device/time_zone_monitor/android/java/src",
    "services/device/vibration/android/java/src",
    "services/service_manager/public/java/src",
    "services/shape_detection/android/java/src",
    "third_party/android_data_chart/java/src",
    "third_party/android_media/java/src",
    "third_party/android_protobuf/src/java/src/device/main/java",
    "third_party/android_protobuf/src/java/src/main/java",
    "third_party/android_swipe_refresh/java/src",
    "third_party/cacheinvalidation/src/java",
    "third_party/custom_tabs_client/src/customtabs/src",
    "third_party/gif_player/src",
    "third_party/jsr-305/src/ri/src/main/java",
    "third_party/leakcanary/src/leakcanary-android-no-op/src/main/java",
    "ui/android/java/src"
]

special_java_files = [
    ["gen/chrome/android/chrome_public_apk__native_libraries_java/java_cpp_template/org/chromium/base/library_loader/NativeLibraries.java",
     "org/chromium/base/library_loader/"
    ],
    ["gen/chrome/android/chrome_public_apk__build_config_java/java_cpp_template/org/chromium/base/BuildConfig.java",
     "org/chromium/base/"
    ]
]

res_dirs = [
    ["chrome/android/java/res", "chrome_res"],
    ["third_party/android_media/java/res", "androidmedia_res"],
    ["content/public/android/java/res", "content_res"],
    ["media/base/android/java/res", "media_res"],
    ["chrome/android/java/res_chromium", "chrome_res"],
    ["components/web_contents_delegate_android/android/java/res", "delegate_res"],
    ["components/autofill/android/java/res", "autofill_res"],
    ["third_party/android_data_chart/java/res", "datausagechart_res"],
    ["ui/android/java/res", "ui_res"]
]

gen_res_dirs = [
    ["gen/chrome/app/policy/android", "chrome_res"],
    ["gen/ui/android/ui_strings_grd_grit_output", "ui_res"],
    ["gen/components/strings/java/res", "delegate_res"],
    ["gen/content/public/android/content_strings_grd_grit_output", "content_res"],
    ["gen/chrome/android/chrome_strings_grd_grit_output", "chrome_res"],
    ["gen/chrome/java/res", "chrome_res"],
    ["gen/components/strings/java/res", "autofill_res"],
    ["gen/third_party/android_tools/google_play_services_basement_java/res", "gms_res"]
]

jar_dirs = [
    "third_party/gvr-android-sdk",
    "third_party/android_tools"
]

so_files = [
    "libchrome.so",
    "libchromium_android_linker.so"
]

data_files = [
    "chrome_100_percent.pak",
    "icudtl.dat",
    "natives_blob.bin",
    "resources.pak",
    "snapshot_blob.bin",
]

def sync_java_files(options):
    app_java_dir = os.path.join(constants.DIR_APP_ROOT, "src", "main", "java")
    for java_dir in java_srcs:
        chrome_java_dir = os.path.join(options.chromium_root, java_dir)
        sync(chrome_java_dir, app_java_dir, "sync")

    # copy special java files
    for special_java_file in special_java_files:
        src_file = os.path.join(options.chromium_root, "out", options.buildtype, special_java_file[0])
        dst = os.path.join(constants.DIR_APP_ROOT, "src", "main", "java", special_java_file[1])
        shutil.copy(src_file, dst)

def sync_res_files(options):
    for res_dir in res_dirs:
        chrome_res_dir = os.path.join(options.chromium_root, res_dir[0])
        app_res_dir = os.path.join(constants.DIR_LIBRARIES_ROOT, res_dir[1], "src", "main", "res")
        sync(chrome_res_dir, app_res_dir, "sync")

    for gen_res_dir in gen_res_dirs:
        chrome_gen_res_dir = os.path.join(options.chromium_root, "out", options.buildtype, gen_res_dir[0])
        app_res_dir = os.path.join(constants.DIR_LIBRARIES_ROOT, gen_res_dir[1], "src", "main", "res")
        args = {'exclude': ['values-\S+'], 'include': ['values-zh-rCN']}
        sync(chrome_gen_res_dir, app_res_dir, "sync", **args)

def sync_so_files(options):
    app_lib_dir = os.path.join(constants.DIR_APP_ROOT, "src", "main", "jniLibs", "armeabi-v7a")
    for so_file in so_files:
        chrome_so = os.path.join(options.chromium_root, "out", options.buildtype, so_file)
        shutil.copy(chrome_so, app_lib_dir)

def sync_jar_files(options):
    app_lib_dir = os.path.join(constants.DIR_APP_ROOT, "libs")
    args = {'only':['.+\\.jar$'],
            'ignore': ['.+interface\\.jar$', '^android_support_', '^support-annotations']}
    for jar_dir in jar_dirs:
        chrome_java_lib_dir = os.path.join(options.chromium_root, "out", options.buildtype, "lib.java", jar_dir)
        sync(chrome_java_lib_dir, app_lib_dir, "sync", **args)

def sync_chromium_res_files(options):
    library_res_dir = os.path.join(constants.DIR_LIBRARIES_ROOT, "chrome_res", "src", "main", "res")
    chrome_res_dir = os.path.join(options.chromium_root, "chrome", "android", "java", "res")
    sync(chrome_res_dir, library_res_dir, "sync")

    chrome_res_dir = os.path.join(options.chromium_root, "chrome", "android", "java", "res_chromium")
    sync(chrome_res_dir, library_res_dir, "sync")

    # sync chrome generated string resources
    chrome_gen_res_dir = os.path.join(options.chromium_root, "out", options.buildtype,
                                      "gen", "chrome", "java", "res")
    sync(chrome_gen_res_dir, library_res_dir, "sync")

    # sync grd generated string resources
    chrome_grd_res_dir = os.path.join(options.chromium_root, "out", options.buildtype,
                                      "obj", "chrome", "chrome_strings_grd.gen", "chrome_strings_grd", "res_grit")
    args = {'exclude': ['values-\S+'], 'include': ['values-zh-rCN']}
    sync(chrome_grd_res_dir, library_res_dir, "sync", **args)

    # remove duplicate strings in android_chrome_strings.xml and generated_resources.xml
    resource_util.remove_duplicated_strings(library_res_dir + '/values/android_chrome_strings.xml',
                                            library_res_dir + '/values/generated_resources.xml')

def sync_ui_res_files(options):
    library_res_dir = os.path.join(constants.DIR_LIBRARIES_ROOT, "ui_res", "src", "main", "res")
    ui_res_dir = os.path.join(options.chromium_root, "ui", "android", "java", "res")

    sync(ui_res_dir, library_res_dir, "sync")

    # sync grd generated string resources
    ui_grd_res_dir = os.path.join(options.chromium_root, "out", options.buildtype,
                                      "obj", "ui", "android", "ui_strings_grd.gen", "ui_strings_grd", "res_grit")
    args = {'exclude': ['values-\S+'], 'include': ['values-zh-rCN']}
    sync(ui_grd_res_dir, library_res_dir, "sync", **args)

def sync_content_res_files(options):
    library_res_dir = os.path.join(constants.DIR_LIBRARIES_ROOT, "content_res", "src", "main", "res")
    content_res_dir = os.path.join(options.chromium_root, "content", "public", "android", "java", "res")
    sync(content_res_dir, library_res_dir, "sync")

    # sync grd generated string resources
    content_grd_res_dir = os.path.join(options.chromium_root, "out", options.buildtype,
                                  "obj", "content", "content_strings_grd.gen", "content_strings_grd", "res_grit")
    args = {'exclude': ['values-\S+'], 'include': ['values-zh-rCN']}
    sync(content_grd_res_dir, library_res_dir, "sync", **args)

def sync_datausagechart_res_files(options):
    library_res_dir = os.path.join(constants.DIR_LIBRARIES_ROOT, "datausagechart_res", "src", "main", "res")
    datausagechart_res_dir = os.path.join(options.chromium_root, "third_party", "android_data_chart", "java", "res")
    sync(datausagechart_res_dir, library_res_dir, "sync")

def sync_androidmedia_res_files(options):
    library_res_dir = os.path.join(constants.DIR_LIBRARIES_ROOT, "androidmedia_res", "src", "main", "res")
    media_res_dir = os.path.join(options.chromium_root, "third_party", "android_media", "java", "res")
    sync(media_res_dir, library_res_dir, "sync")

def sync_manifest_files(options):
    main_dir = os.path.join(constants.DIR_APP_ROOT, "src", "main")
    public_apk_gen_dir = os.path.join(options.chromium_root, "out", options.buildtype,
                                      "gen/chrome/android/chrome_public_apk")
    args = {'only': ['AndroidManifest\\.xml']}
    sync(public_apk_gen_dir, main_dir, "sync", **args)

def sync_data_files(options):
    assets_dir = os.path.join(constants.DIR_APP_ROOT, "src", "main", "assets")
    pak_gen_dir = os.path.join(options.chromium_root, "out", options.buildtype, "locales")
    sync(pak_gen_dir, assets_dir, "sync")

    for data_file in data_files:
        chrome_data_file = os.path.join(options.chromium_root, "out", options.buildtype, data_file)
        if data_file == "snapshot_blob.bin":
            assets_dir = os.path.join(assets_dir, "snapshot_blob_32.bin")
        shutil.copy(chrome_data_file, assets_dir)

def main(argv):
    parser = optparse.OptionParser(usage='Usage: %prog [options]', description=__doc__)
    parser.add_option('--chromium_root',
                      default="/work/chromium/master/chromium-android/src",
                      help="The root of chromium sources")
    parser.add_option('--buildtype',
                      default="Default",
                      help="build type of chromium build(Default, Debug or Release, etc), default Default")
    options, args = parser.parse_args(argv)

    sync_java_files(options)
    sync_res_files(options)
    sync_jar_files(options)
    sync_so_files(options)
    # sync_chromium_res_files(options)
    # sync_ui_res_files(options)
    # sync_content_res_files(options)
    # sync_datausagechart_res_files(options)
    # sync_androidmedia_res_files(options)
    sync_manifest_files(options)
    sync_data_files(options)

if __name__ == '__main__':
    main(sys.argv)